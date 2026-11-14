package net.automationse.relayharbor.floating

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import net.automationse.relayharbor.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * One explicitly authorized MediaProjection session, one VirtualDisplay.
 * User scrolls the foreground app manually and taps "次を追加" after each scroll.
 * Control overlay is unmounted before grabbing each frame.
 */
class ScrollingScreenshotService : Service() {
    companion object {
        const val ACTION_CANCEL = "net.automationse.relayharbor.floating.LONG_CANCEL"
        private const val CHANNEL = "rh_screenshot_results"
        private const val FOREGROUND_ID = 30038
        private const val RESULT_ID = 30039
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val stopped = AtomicBoolean(false)
    private var launched = false
    private var busy = false
    private var controls: LinearLayout? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val frames = mutableListOf<Bitmap>()
    private val samples = mutableListOf<ScrollFrameSample>()
    private val steps = mutableListOf<Int>()
    private var band: ScrollBand? = null
    private var info = "半画面ほど下にスクロールして「次を追加」"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancel("ロングスクショを中止しました")
            return START_NOT_STICKY
        }
        if (launched || stopped.get()) return START_NOT_STICKY
        launched = true
        val code = intent?.getIntExtra(ScreenCaptureRequestActivity.EXTRA_RESULT_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(
                ScreenCaptureRequestActivity.EXTRA_RESULT_INTENT, Intent::class.java
            )
        } else intent?.getParcelableExtra(ScreenCaptureRequestActivity.EXTRA_RESULT_INTENT)
        if (code != Activity.RESULT_OK || data == null) {
            cancel("画面共有の許可がありません")
            return START_NOT_STICKY
        }
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "スクリーンショット",
                    NotificationManager.IMPORTANCE_DEFAULT)
            )
            val stopAction = PendingIntent.getService(
                this, 30040, Intent(this, ScrollingScreenshotService::class.java)
                    .setAction(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notice = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("ロングスクショを撮影中")
                .setContentText("対象アプリをスクロールして次の範囲を追加")
                .addAction(0, "キャンセル", stopAction)
                .setOngoing(true).setSilent(true).build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(FOREGROUND_ID, notice,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else startForeground(FOREGROUND_ID, notice)
        } catch (_: Exception) {
            cancel("撮影サービスを開始できませんでした")
            return START_NOT_STICKY
        }
        main.postDelayed({
            if (!stopped.get()) worker.execute {
                try {
                    initializeProjection(code, data)
                    Thread.sleep(460)
                    val image = acquireFrame()
                    main.post {
                        if (stopped.get()) image.recycle()
                        else {
                            frames.add(image)
                            samples.add(toSample(image))
                            showControls()
                        }
                    }
                } catch (e: Exception) {
                    main.post { fail("初回撮影失敗: " + (e.message ?: "画面取得エラー")) }
                }
            }
        }, 450L)
        main.postDelayed({
            if (!stopped.get()) cancel("撮影の制限時間（5分）を超えました")
        }, 300_000L)
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun initializeProjection(code: Int, permission: Intent) {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val token = manager.getMediaProjection(code, permission)
            ?: error("画面共有の許可を確認できません")
        projection = token
        val size = Point().also {
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(it)
        }
        val images = ImageReader.newInstance(
            size.x.coerceAtLeast(1), size.y.coerceAtLeast(1),
            PixelFormat.RGBA_8888, 2
        )
        reader = images
        token.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                main.post {
                    if (!stopped.get()) cancel("Androidが画面共有を終了しました")
                }
            }
        }, main)
        virtualDisplay = token.createVirtualDisplay(
            "RelayHarbor-LongScreenshot", images.width, images.height,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            images.surface, null, main
        )
    }

    /** Obtain latest projected frame after our controls have disappeared. */
    private fun acquireFrame(): Bitmap {
        val images = reader ?: error("画面共有が終了しています")
        val deadline = SystemClock.elapsedRealtime() + 5000L
        while (SystemClock.elapsedRealtime() < deadline && !stopped.get()) {
            val image = images.acquireLatestImage()
            if (image != null) {
                try {
                    val p = image.planes.first()
                    val strideWidth = p.rowStride / p.pixelStride
                    require(strideWidth >= image.width) { "画像の幅が不正です" }
                    val raw = Bitmap.createBitmap(
                        strideWidth, image.height, Bitmap.Config.ARGB_8888
                    )
                    try {
                        raw.copyPixelsFromBuffer(p.buffer)
                        val width = minOf(1440, image.width)
                        val height = (image.height.toFloat() * width / image.width)
                            .roundToInt().coerceAtLeast(1)
                        val cropped = Bitmap.createBitmap(raw, 0, 0, image.width, image.height)
                        try {
                            val scaled = Bitmap.createScaledBitmap(cropped, width, height, true)
                            try {
                                return scaled.copy(Bitmap.Config.ARGB_8888, false)
                                    ?: error("画像変換エラー")
                            } finally {
                                if (scaled !== cropped) scaled.recycle()
                            }
                        } finally {
                            if (cropped !== raw) cropped.recycle()
                        }
                    } finally { raw.recycle() }
                } finally { image.close() }
            }
            Thread.sleep(100)
        }
        error("画像を取得できません（画面保護、または共有終了の可能性）")
    }

    private fun toSample(image: Bitmap): ScrollFrameSample {
        val height = (image.height / 4).coerceIn(40, 900)
        val thumbnail = Bitmap.createScaledBitmap(image, 80, height, true)
        return try {
            val pixels = IntArray(80 * height)
            thumbnail.getPixels(pixels, 0, 80, 0, 0, 80, height)
            ScrollFrameSample(80, height, pixels)
        } finally {
            if (thumbnail !== image) thumbnail.recycle()
        }
    }

    private fun append() {
        if (busy || stopped.get() || frames.isEmpty()) return
        if (frames.size >= ScrollScreenshotMatcher.MAX_FRAMES) {
            info = "最大枚数です。保存・終了を押してください"
            removeControls()
            showControls()
            return
        }
        busy = true
        removeControls()
        worker.execute {
            var next: Bitmap? = null
            try {
                Thread.sleep(520)
                next = acquireFrame()
                val old = frames.last()
                check(old.width == next.width && old.height == next.height) {
                    "画面の向きが変わりました。保存して撮影をやり直してください"
                }
                val after = toSample(next)
                val before = samples.last()
                val region = band ?: ScrollScreenshotMatcher.detectBand(before, after)
                    ?: error("スクロールしてから「次を追加」を押してください")
                val match = ScrollScreenshotMatcher.match(before, after, region)
                    ?: error("画像のつなぎ目が不明です。半画面ほど移動して再試行してください")
                val shiftPx = (match.shift.toFloat() * next.height / after.height)
                    .roundToInt().coerceAtLeast(1)
                val estimated = frames.first().height + steps.sum() + shiftPx
                require(ScrollScreenshotMatcher.fitOutput(next.width, estimated)) {
                    "画像サイズの上限です。ここまでを保存してください"
                }
                frames.add(next)
                samples.add(after)
                steps.add(shiftPx)
                band = region
                next = null
                info = frames.size.toString() + "枚。さらにスクロールするか保存"
            } catch (e: Exception) {
                info = e.message ?: "画像の追加に失敗しました"
            } finally {
                next?.recycle()
                main.post {
                    busy = false
                    if (!stopped.get()) showControls()
                }
            }
        }
    }

    private fun saveAndFinish() {
        if (busy || stopped.get() || frames.isEmpty()) return
        busy = true
        removeControls()
        worker.execute {
            try {
                val merged = composeBitmap()
                val uri = try { savePng(merged) } finally { merged.recycle() }
                main.post {
                    if (!stopped.get()) {
                        notifySaved(uri, frames.size)
                        closeSession()
                    }
                }
            } catch (e: Exception) {
                main.post {
                    if (!stopped.get()) {
                        busy = false
                        info = "保存エラー: " + (e.message ?: "不明なエラー")
                        showControls()
                    }
                }
            }
        }
    }

    private fun composeBitmap(): Bitmap {
        val first = frames.firstOrNull() ?: error("撮影画像がありません")
        if (frames.size == 1) return first.copy(Bitmap.Config.ARGB_8888, false)
            ?: error("画像をコピーできません")
        val region = band ?: error("スクロール範囲がありません")
        val bottom = (region.bottom.toFloat() * first.height / samples.first().height)
            .roundToInt().coerceIn(1, first.height)
        val height = first.height + steps.sum()
        require(ScrollScreenshotMatcher.fitOutput(first.width, height)) {
            "画像の上限サイズを超えました"
        }
        val merged = Bitmap.createBitmap(first.width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(merged)
        var offset = 0
        fun draw(image: Bitmap, top: Int, bottomY: Int) {
            if (bottomY <= top) return
            canvas.drawBitmap(
                image, Rect(0, top, image.width, bottomY),
                Rect(0, offset, image.width, offset + bottomY - top),
                null
            )
            offset += bottomY - top
        }
        draw(first, 0, bottom)
        for (index in 1 until frames.size) {
            draw(frames[index], (bottom - steps[index - 1]).coerceAtLeast(0), bottom)
        }
        draw(frames.last(), bottom, first.height)
        return merged
    }

    private fun removeControls() {
        val old = controls
        controls = null
        if (old != null) runCatching {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeViewImmediate(old)
        }
    }

    private fun showControls() {
        if (stopped.get() || controls != null) return
        if (!FloatingMenuController.allowed(this)) {
            fail("フローティング表示の権限がありません")
            return
        }
        fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), dp(8), dp(9), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.rgb(27, 41, 66))
                cornerRadius = dp(13).toFloat()
            }
            elevation = dp(8).toFloat()
        }
        panel.addView(TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 2
            text = "ロングスクショ " + frames.size + "/" +
                ScrollScreenshotMatcher.MAX_FRAMES + "枚\n" + info
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(49)))
        val row = LinearLayout(this)
        fun button(name: String, action: () -> Unit) {
            val button = TextView(this).apply {
                text = name
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(48, 73, 104))
                    cornerRadius = dp(8).toFloat()
                }
                setOnClickListener { action() }
            }
            row.addView(button, LinearLayout.LayoutParams(0, dp(43), 1f).apply {
                marginEnd = dp(4)
            })
        }
        button("次を追加") { append() }
        button("保存・終了") { saveAndFinish() }
        button("キャンセル") { cancel("ロングスクショを中止しました") }
        panel.addView(row)
        val width = dp(344).coerceAtMost(
            (resources.displayMetrics.widthPixels - dp(12)).coerceAtLeast(dp(180))
        )
        val params = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(62)
        }
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(panel, params)
            controls = panel
        } catch (e: Exception) {
            fail("操作パネルを表示できません: " + (e.message ?: "権限エラー"))
        }
    }

    private fun savePng(image: Bitmap): Uri {
        val filename = "RelayHarbor_Long_" +
            SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + ".png"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RelayHarbor")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: error("保存先を作成できません")
            try {
                resolver.openOutputStream(uri)?.use {
                    check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                } ?: error("画像を書き込めません")
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
        val folder = File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: error("保存先フォルダーがありません"), "RelayHarbor"
        ).apply { mkdirs() }
        val file = File(folder, filename)
        try {
            FileOutputStream(file).use {
                check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            return Uri.fromFile(file)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    private fun notifySaved(uri: Uri, count: Int) {
        val share = if (Build.VERSION.SDK_INT >= 29) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(contentResolver, "Long screenshot", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            PendingIntent.getActivity(
                this, 30042, Intent.createChooser(send, "ロングスクショを共有"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else null
        val notice = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ロングスクショを保存しました")
            .setContentText(count.toString() + "枚を結合 · Pictures/RelayHarbor")
            .setAutoCancel(true)
        if (share != null) notice.setContentIntent(share).addAction(0, "共有", share)
        runCatching {
            getSystemService(NotificationManager::class.java).notify(
                RESULT_ID, notice.build()
            )
        }
        Toast.makeText(this, "ロングスクショを保存しました", Toast.LENGTH_SHORT).show()
    }

    private fun cancel(message: String) {
        if (!stopped.get()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            closeSession()
        }
    }

    private fun fail(message: String) {
        if (!stopped.get()) {
            Toast.makeText(this, message.take(115), Toast.LENGTH_LONG).show()
            closeSession()
        }
    }

    private fun closeSession() {
        if (!stopped.compareAndSet(false, true)) return
        removeControls()
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { reader?.close() }
        reader = null
        runCatching { projection?.stop() }
        projection = null
        frames.forEach { if (!it.isRecycled) it.recycle() }
        frames.clear()
        samples.clear()
        steps.clear()
        worker.shutdownNow()
        FloatingMenuController.restoreAfterCapture(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (!stopped.get()) closeSession()
        super.onDestroy()
    }
}

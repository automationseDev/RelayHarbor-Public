package net.automationse.relayharbor.floating

import java.util.concurrent.atomic.AtomicBoolean
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
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
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import net.automationse.relayharbor.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Exactly one screenshot per explicitly granted MediaProjection token.
 * Creates the required mediaProjection foreground service before retrieving the
 * projection. Never captures in the background without fresh system consent.
 */
class ScreenCaptureService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var used = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (used) return START_NOT_STICKY
        used = true
        val code = intent?.getIntExtra(
            ScreenCaptureRequestActivity.EXTRA_RESULT_CODE, 0
        ) ?: 0
        @Suppress("DEPRECATION")
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(
                ScreenCaptureRequestActivity.EXTRA_RESULT_INTENT, Intent::class.java
            )
        } else {
            intent?.getParcelableExtra(ScreenCaptureRequestActivity.EXTRA_RESULT_INTENT)
        }

        if (code != android.app.Activity.RESULT_OK || permission == null) {
            stopSelf(startId)
            FloatingMenuController.restoreAfterCapture(this)
            return START_NOT_STICKY
        }

        try {
            ensureChannel()
            val ongoing = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("スクリーンショットを撮影しています")
                .setContentText("許可された画面を一度だけ取得します")
                .setOngoing(true)
                .setSilent(true)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(FOREGROUND_ID, ongoing,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(FOREGROUND_ID, ongoing)
            }
        } catch (error: Exception) {
            notifyError("画面キャプチャを開始できませんでした")
            FloatingMenuController.restoreAfterCapture(this)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // Let the consent UI and transient request Activity leave the screen.
        main.postDelayed({
            thread(name = "RelayHarbor-OneShotCapture", isDaemon = true) {
                var result: Uri? = null
                var error: String? = null
                try {
                    result = captureOnce(code, permission)
                } catch (e: Exception) {
                    error = e.message?.take(120) ?: "画面の読み込みに失敗しました"
                } finally {
                    main.post {
                        try {
                            FloatingMenuController.restoreAfterCapture(this)
                            if (result != null) notifySaved(result)
                            else notifyError(error ?: "画面を取得できませんでした")
                        } finally {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf(startId)
                        }
                    }
                }
            }
        }, 650L)
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun captureOnce(resultCode: Int, resultData: Intent): Uri {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, resultData)
            ?: error("画面共有の許可が確認できません")
        val window = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        val size = Point().also { window.defaultDisplay.getRealSize(it) }
        val width = size.x.coerceAtLeast(1)
        val height = size.y.coerceAtLeast(1)
        val density = resources.displayMetrics.densityDpi
        val stopped = AtomicBoolean(false)
        var virtualDisplay: VirtualDisplay? = null
        val images = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val callback = object : MediaProjection.Callback() {
            override fun onStop() { stopped.set(true) }
        }
        try {
            projection.registerCallback(callback, main)
            virtualDisplay = projection.createVirtualDisplay(
                "RelayHarbor-OneShotScreenshot",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                images.surface,
                null, main
            )
            var bitmap: Bitmap? = null
            val deadline = System.currentTimeMillis() + 4600L
            while (System.currentTimeMillis() < deadline && !stopped.get()) {
                val image = images.acquireLatestImage()
                if (image != null) {
                    try {
                        val plane = image.planes.first()
                        val paddedWidth = width +
                            ((plane.rowStride - plane.pixelStride * width) /
                                plane.pixelStride).coerceAtLeast(0)
                        val padded = Bitmap.createBitmap(
                            paddedWidth, height, Bitmap.Config.ARGB_8888
                        )
                        try {
                            padded.copyPixelsFromBuffer(plane.buffer)
                            val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
                            // createBitmap may reuse the source when no crop is needed.
                            // Force an independent copy before recycling the padded buffer.
                            try {
                                bitmap = cropped.copy(Bitmap.Config.ARGB_8888, false)
                                    ?: error("画像を変換できませんでした")
                            } finally {
                                if (cropped !== padded) cropped.recycle()
                            }
                        } finally {
                            padded.recycle()
                        }
                    } finally {
                        image.close()
                    }
                    break
                }
                Thread.sleep(110)
            }
            val captured = bitmap ?: error("キャプチャ画面が届きませんでした（保護画面などの可能性）")
            try {
                return savePng(captured)
            } finally {
                captured.recycle()
            }
        } finally {
            virtualDisplay?.release()
            images.close()
            runCatching { projection.stop() }
        }
    }

    private fun savePng(bitmap: Bitmap): Uri {
        val filename = "RelayHarbor_" +
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
            ) ?: error("写真の保存先を作成できません")
            try {
                resolver.openOutputStream(uri)?.use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        "PNG画像を保存できません"
                    }
                } ?: error("写真の保存先を開けません")
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }

        // Android 8-9: save to application Pictures without broad storage
        // permission; grant a read-only FileProvider URI for user sharing.
        val base = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: error("写真保存フォルダーを開けません")
        val folder = File(base, "RelayHarbor").apply { mkdirs() }
        val file = File(folder, filename)
        try {
            FileOutputStream(file).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            return FileProvider.getUriForFile(
                this, packageName + ".fileprovider", file
            )
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "スクリーンショット",
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "画面の保存結果と共有操作"
                setShowBadge(false)
            }
        )
    }

    private fun notifySaved(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, "RelayHarbor screenshot", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "スクリーンショットを共有")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val share = PendingIntent.getActivity(
            this, 30035, chooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val message = if (Build.VERSION.SDK_INT >= 29)
            "写真のPictures/RelayHarborに保存しました"
        else "アプリの画像フォルダーに保存しました"
        runCatching {
            getSystemService(NotificationManager::class.java).notify(
            SAVED_ID, NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("スクリーンショットを保存しました")
                .setContentText(message)
                .setContentIntent(share)
                .addAction(0, "共有", share)
                .setAutoCancel(true)
                .build()
            )
        }
        Toast.makeText(this, "スクリーンショットを保存しました", Toast.LENGTH_SHORT).show()
    }

    private fun notifyError(message: String) {
        ensureChannel()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(
            SAVED_ID, NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("スクリーンショットを保存できませんでした")
                .setContentText(message.take(160))
                .setAutoCancel(true)
                .build()
            )
        }
        Toast.makeText(this, "スクショ失敗: " + message.take(65), Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val CHANNEL = "rh_screenshot_results"
        private const val FOREGROUND_ID = 30032
        private const val SAVED_ID = 30033
    }
}

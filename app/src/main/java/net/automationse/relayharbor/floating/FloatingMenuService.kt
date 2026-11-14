package net.automationse.relayharbor.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import net.automationse.relayharbor.MainActivity
import net.automationse.relayharbor.R
import kotlin.math.abs

/**
 * User-activated overlay. The visible foreground-service notification always
 * gives the user a way to stop it. No accessibility automation, touch injection,
 * notification snooping, or hidden capture occurs.
 */
class FloatingMenuService : Service() {
    companion object {
        @Volatile private var running: FloatingMenuService? = null

        /** In-process visibility toggle: no background service start is needed. */
        fun hideDuringCapture(hidden: Boolean) {
            val service = running ?: return
            service.mainHandler.post {
                if (running === service) service.setCaptureHidden(hidden)
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var preferences: FloatingMenuPreferences
    private var handle: View? = null
    private var panel: View? = null
    private var handleLayout: WindowManager.LayoutParams? = null
    private var panelLayout: WindowManager.LayoutParams? = null
    private var expanded = false
    private var suspendedForScreenshot = false
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        preferences = FloatingMenuPreferences(this)
        running = this
    }

    private fun setCaptureHidden(hidden: Boolean) {
        suspendedForScreenshot = hidden
        if (hidden) {
            handle?.visibility = View.INVISIBLE
            hidePanel()
        } else if (FloatingMenuController.allowed(this) && preferences.load().enabled) {
            handle?.visibility = View.VISIBLE
            reposition()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            FloatingMenuController.ACTION_DISABLE -> {
                preferences.enabled(false)
                stopSelf()
                return START_NOT_STICKY
            }
            FloatingMenuController.ACTION_HIDE -> {
                setCaptureHidden(true)
                if (handle == null) stopSelf()
                return START_NOT_STICKY
            }
            FloatingMenuController.ACTION_RESTORE -> {
                if (handle != null) {
                    setCaptureHidden(false)
                    return START_STICKY
                }
            }
        }
        if (!FloatingMenuController.allowed(this) || !preferences.load().enabled) {
            preferences.enabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (handle == null) installHandle()
            reposition()
            if (!foregroundStarted) showOngoingNotification()
        } catch (_: Exception) {
            preferences.enabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun displaySize(): Point = Point().also(windowManager.defaultDisplay::getRealSize)

    private fun shape(color: Int, corners: Float = 17f): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(corners.toInt()).toFloat()
        }

    private fun layout(width: Int, height: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private fun installHandle() {
        val label = TextView(this).apply {
            text = "☰"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackground(shape(Color.rgb(37, 64, 110)))
            elevation = dp(6).toFloat()
            contentDescription = "RelayHarbor フローティングメニューを開く・移動する"
            isClickable = true
        }
        var startX = 0f
        var startY = 0f
        var oldX = 0
        var oldY = 0
        var moved = false

        label.setOnTouchListener { view, event ->
            val params = handleLayout ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    oldX = params.x
                    oldY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - startX) > dp(9) ||
                        abs(event.rawY - startY) > dp(9)) moved = true
                    if (moved) {
                        val size = displaySize()
                        params.y = (oldY + (event.rawY - startY).toInt())
                            .coerceIn(dp(55), (size.y - dp(110)).coerceAtLeast(dp(55)))
                        // Slide the handle to either side; never park over content.
                        val center = oldX + (event.rawX - startX) + params.width / 2f
                        val side = if (center < size.x / 2f) "left" else "right"
                        params.x = if (side == "left") 0 else (size.x - params.width).coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(label, params) }
                        hidePanel()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val size = displaySize()
                        preferences.side(if (params.x < size.x / 2) "left" else "right")
                        preferences.yFraction(params.y.toFloat() / size.y.coerceAtLeast(1))
                        reposition()
                    } else {
                        view.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        label.setOnClickListener { if (expanded) hidePanel() else showPanel() }
        val size = dp(preferences.load().handleDp)
        val params = layout(size, size + dp(14))
        handleLayout = params
        handle = label
        windowManager.addView(label, params)
    }

    private fun showPanel() {
        if (expanded || suspendedForScreenshot) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackground(shape(Color.rgb(27, 39, 60), 20f))
            elevation = dp(10).toFloat()
            contentDescription = "RelayHarbor クイック操作"
        }

        fun item(label: String, action: () -> Unit) {
            val textView = TextView(this).apply {
                text = label
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(9), dp(10), dp(9))
                background = shape(Color.rgb(43, 60, 87), 10f)
                setOnClickListener {
                    hidePanel()
                    action()
                }
            }
            val row = LinearLayout.LayoutParams(dp(195), dp(45))
            row.bottomMargin = dp(5)
            container.addView(textView, row)
        }
        item("📷  スクリーンショット") { openCaptureRequest() }
        item("⌂  ホーム") { openTab("home") }
        item("▣  チャット") { openTab("channels") }
        item("♢  通知センター") { openTab("activity") }
        item("▢  小窓表示の使い方") { openPopupGuide() }
        item("⚙  RelayHarborの設定") { openTab("settings") }
        item("×  ランチャーを停止") {
            preferences.enabled(false)
            stopSelf()
        }

        val screen = displaySize()
        val size = dp(215)
        val params = layout(size, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = if (preferences.load().side == "left") dp(6)
                else (screen.x - size - dp(6)).coerceAtLeast(0)
            y = ((handleLayout?.y ?: dp(130)) - dp(8))
                .coerceIn(dp(48), (screen.y - dp(430)).coerceAtLeast(dp(48)))
        }
        container.alpha = preferences.load().opacity
        try {
            windowManager.addView(container, params)
            panel = container
            panelLayout = params
            expanded = true
        } catch (_: Exception) {
            expanded = false
        }
    }

    private fun hidePanel() {
        val old = panel
        panel = null
        panelLayout = null
        expanded = false
        if (old != null) runCatching { windowManager.removeView(old) }
    }

    private fun reposition() {
        val view = handle ?: return
        val options = preferences.load()
        val size = displaySize()
        val width = dp(options.handleDp)
        val p = handleLayout ?: return
        p.width = width
        p.height = width + dp(14)
        p.x = if (options.side == "left") 0 else (size.x - width).coerceAtLeast(0)
        p.y = (size.y * options.yFraction).toInt()
            .coerceIn(dp(55), (size.y - dp(110)).coerceAtLeast(dp(55)))
        view.alpha = options.opacity
        runCatching { windowManager.updateViewLayout(view, p) }
        if (expanded) {
            hidePanel()
            showPanel()
        }
    }

    private fun openTab(tab: String) {
        if (FloatingMenuPolicy.validTab(tab) == null) return
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(FloatingMenuController.EXTRA_TAB, tab)
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "RelayHarborを開けませんでした", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCaptureRequest() {
        val intent = Intent(this, ScreenCaptureRequestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "スクリーンショットの確認画面を開けません", Toast.LENGTH_LONG).show()
        }
    }

    private fun openPopupGuide() {
        val intent = Intent(this, ScreenCaptureRequestActivity::class.java)
            .setAction(ScreenCaptureRequestActivity.ACTION_POPUP_GUIDE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "小窓の設定案内を開けません", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOngoingNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("rh_floating_menu", "サイドランチャー",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "有効化中のフローティングメニュー"
                setShowBadge(false)
            }
        )
        val stop = PendingIntent.getService(
            this, 88, Intent(this, FloatingMenuService::class.java)
                .setAction(FloatingMenuController.ACTION_DISABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 89, Intent(this, MainActivity::class.java)
                .putExtra(FloatingMenuController.EXTRA_TAB, "settings")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, "rh_floating_menu")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("RelayHarbor サイドランチャー")
            .setContentText("画面端からスクショ・通知・チャットを開けます")
            .setContentIntent(open)
            .addAction(0, "停止", stop)
            .setOngoing(true)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(30031, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(30031, notification)
        }
        foregroundStarted = true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hidePanel()
        reposition()
    }

    override fun onDestroy() {
        if (running === this) running = null
        hidePanel()
        handle?.let { runCatching { windowManager.removeView(it) } }
        handle = null
        handleLayout = null
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        super.onDestroy()
    }
}

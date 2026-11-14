package net.automationse.relayharbor.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * User-opt-in screen-edge launcher. No overlay is started at boot and the
 * permission screen is always opened by an explicit user gesture.
 */
data class FloatingMenuOptions(
    val enabled: Boolean = false,
    val side: String = "right",
    val yFraction: Float = 0.38f,
    val opacity: Float = 0.85f,
    val handleDp: Int = 42
)

object FloatingMenuPolicy {
    fun side(value: String?): String = if (value == "left") "left" else "right"
    fun fraction(value: Float): Float = if (value.isFinite()) value.coerceIn(0.12f, 0.82f) else 0.38f
    fun opacity(value: Float): Float = if (value.isFinite()) value.coerceIn(0.4f, 1f) else 0.85f
    fun handleDp(value: Int): Int = value.coerceIn(32, 64)
    fun validTab(value: String?): String? =
        value?.takeIf { it in setOf("home", "channels", "activity", "settings") }
}

class FloatingMenuPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "floating_launcher_v1", Context.MODE_PRIVATE
    )
    fun load(): FloatingMenuOptions = FloatingMenuOptions(
        enabled = prefs.getBoolean("enabled", false),
        side = FloatingMenuPolicy.side(prefs.getString("side", "right")),
        yFraction = FloatingMenuPolicy.fraction(prefs.getFloat("y", 0.38f)),
        opacity = FloatingMenuPolicy.opacity(prefs.getFloat("opacity", 0.85f)),
        handleDp = FloatingMenuPolicy.handleDp(prefs.getInt("handle_dp", 42))
    )
    fun enabled(value: Boolean) = prefs.edit().putBoolean("enabled", value).apply()
    fun side(value: String) = prefs.edit().putString("side", FloatingMenuPolicy.side(value)).apply()
    fun yFraction(value: Float) = prefs.edit().putFloat("y", FloatingMenuPolicy.fraction(value)).apply()
    fun opacity(value: Float) = prefs.edit().putFloat("opacity", FloatingMenuPolicy.opacity(value)).apply()
    fun handleDp(value: Int) = prefs.edit().putInt("handle_dp", FloatingMenuPolicy.handleDp(value)).apply()
}

object FloatingMenuController {
    const val EXTRA_TAB = "net.automationse.relayharbor.floating.OPEN_TAB"
    const val ACTION_ENABLE = "net.automationse.relayharbor.floating.ENABLE"
    const val ACTION_DISABLE = "net.automationse.relayharbor.floating.DISABLE"
    const val ACTION_REFRESH = "net.automationse.relayharbor.floating.REFRESH"
    const val ACTION_HIDE = "net.automationse.relayharbor.floating.HIDE_FOR_CAPTURE"
    const val ACTION_RESTORE = "net.automationse.relayharbor.floating.RESTORE"

    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(context)

    fun permissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:" + context.packageName)
    )

    fun enable(context: Context): Boolean {
        if (!allowed(context)) return false
        FloatingMenuPreferences(context).enabled(true)
        return try {
            ContextCompat.startForegroundService(
                context, Intent(context, FloatingMenuService::class.java)
                    .setAction(ACTION_ENABLE)
            )
            true
        } catch (_: Exception) {
            FloatingMenuPreferences(context).enabled(false)
            false
        }
    }

    fun disable(context: Context) {
        FloatingMenuPreferences(context).enabled(false)
        context.stopService(Intent(context, FloatingMenuService::class.java))
    }

    fun refresh(context: Context) {
        if (FloatingMenuPreferences(context).load().enabled && allowed(context)) {
            runCatching {
                context.startService(
                    Intent(context, FloatingMenuService::class.java).setAction(ACTION_REFRESH)
                )
            }
        }
    }

    fun hideForCapture(context: Context) {
        if (FloatingMenuPreferences(context).load().enabled) {
            FloatingMenuService.hideDuringCapture(true)
        }
    }

    fun restoreAfterCapture(context: Context) {
        if (FloatingMenuPreferences(context).load().enabled && allowed(context)) {
            FloatingMenuService.hideDuringCapture(false)
        }
    }
}

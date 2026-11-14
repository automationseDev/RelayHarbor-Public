package net.automationse.relayharbor.notification

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-only routing configuration. Never stores notification bodies or credentials.
 * Default = disabled globally, with NO selected source apps.
 */
internal class AndroidAppNotificationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "android_app_notification_routes_v1", Context.MODE_PRIVATE
    )

    fun globallyEnabled(): Boolean = prefs.getBoolean("enabled", false)

    fun setGloballyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
    }

    fun routes(): List<AndroidAppRoute> {
        val raw = prefs.getString("routes", null) ?: return emptyList()
        if (raw.length > 24_000) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val distinct = LinkedHashMap<String, AndroidAppRoute>()
            for (i in 0 until minOf(array.length(), AndroidNotificationRules.MAX_ROUTES)) {
                val obj = array.optJSONObject(i) ?: continue
                val packageName = obj.optString("package")
                val channelId = obj.optLong("channel", 0L)
                if (!AndroidNotificationRules.validPackage(packageName) ||
                    channelId <= 0L || packageName in distinct) continue
                distinct[packageName] = AndroidAppRoute(
                    packageName = packageName,
                    channelId = channelId,
                    keyword = obj.optString("keyword").take(80),
                    enabled = obj.optBoolean("enabled", false)
                )
            }
            distinct.values.toList()
        }.getOrDefault(emptyList())
    }

    fun route(packageName: String): AndroidAppRoute? =
        routes().firstOrNull { it.packageName == packageName }

    fun saveRoute(rule: AndroidAppRoute): Boolean {
        if (!AndroidNotificationRules.validPackage(rule.packageName) ||
            rule.channelId <= 0L || rule.keyword.length > 80) return false
        val all = routes().filterNot { it.packageName == rule.packageName } + rule
        if (all.size > AndroidNotificationRules.MAX_ROUTES) return false
        write(all)
        return true
    }

    fun removeRoute(packageName: String) {
        write(routes().filterNot { it.packageName == packageName })
    }

    private fun write(routes: List<AndroidAppRoute>) {
        val array = JSONArray()
        routes.forEach {
            array.put(JSONObject().apply {
                put("package", it.packageName)
                put("channel", it.channelId)
                put("keyword", it.keyword)
                put("enabled", it.enabled)
            })
        }
        prefs.edit().putString("routes", array.toString()).apply()
    }
}

package net.automationse.relayharbor.ui

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** A fixed set of trusted built-in panels. No arbitrary remote code or widgets. */
internal data class HomePanel(
    val id: String,
    val span: Int,
    val height: Int,
    val visible: Boolean = true
)

internal object HomePanelLayout {
    const val COVER = "cover"
    const val EXPANDED = "expanded"
    val miniWidgetIds = setOf("clock", "date", "weather", "agenda", "battery")
    val panelIds = listOf("overview", "apps", "drawer", "sync", "activity", "channels",
        "clock", "weather", "date", "battery", "agenda")
    val titles = mapOf(
        "overview" to "ワークスペース",
        "apps" to "お気に入りアプリ",
        "drawer" to "アプリ一覧",
        "sync" to "同期・通知",
        "activity" to "最近の通知",
        "channels" to "チャットチャンネル",
        "clock" to "時計",
        "date" to "日付",
        "weather" to "現在の天気",
        "agenda" to "次の予定",
        "battery" to "バッテリー"
    )

    fun columns(profile: String): Int = if (profile == EXPANDED) 4 else 2
    fun default(profile: String): List<HomePanel> {
        val wide = profile == EXPANDED
        return listOf(
            HomePanel("overview", if (wide) 2 else 2, 1),
            HomePanel("drawer", if (wide) 2 else 2, 1),
            HomePanel("apps", if (wide) 4 else 2, 3),
            HomePanel("sync", if (wide) 2 else 2, 2),
            HomePanel("activity", if (wide) 2 else 2, 3),
            HomePanel("channels", if (wide) 4 else 2, 3),
            HomePanel("clock", if (wide) 2 else 1, 1),
            HomePanel("weather", if (wide) 2 else 1, 1),
            HomePanel("date", 1, 1),
            HomePanel("battery", 1, 1),
            HomePanel("agenda", if (wide) 2 else 1, 1, visible = false)
        )
    }

    /** Reject unknown/duplicate panels and clamp dimensions before rendering or writing. */
    fun normalize(profile: String, input: List<HomePanel>): List<HomePanel> {
        val base = default(profile)
        val maxWidth = columns(profile)
        val unique = LinkedHashMap<String, HomePanel>()
        input.forEach { panel ->
            if (panel.id in panelIds && panel.id !in unique) {
                unique[panel.id] = panel.copy(
                    span = panel.span.coerceIn(1, maxWidth),
                    height = panel.height.coerceIn(1, 6)
                )
            }
        }
        base.forEach { if (it.id !in unique) unique[it.id] = it }
        return unique.values.toList()
    }

    /** Show built-in information cards without modifying existing card sizes or order. */
    fun enableMiniWidgets(profile: String, current: List<HomePanel>): List<HomePanel> =
        normalize(profile, current).map { panel ->
            if (panel.id in miniWidgetIds) panel.copy(visible = true) else panel
        }

    fun move(profile: String, current: List<HomePanel>, from: String, to: String): List<HomePanel> {
        val normalized = normalize(profile, current)
        val fromIndex = normalized.indexOfFirst { it.id == from }
        val targetIndex = normalized.indexOfFirst { it.id == to }
        if (fromIndex == -1 || targetIndex == -1 || fromIndex == targetIndex) return normalized
        val mutable = normalized.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(targetIndex, item)
        return mutable
    }

    fun resize(
        profile: String, current: List<HomePanel>, id: String,
        widthStep: Int = 0, heightStep: Int = 0
    ): List<HomePanel> = normalize(profile, current).map { panel ->
        if (panel.id != id) panel else panel.copy(
            span = (panel.span + widthStep).coerceIn(1, columns(profile)),
            height = (panel.height + heightStep).coerceIn(1, 6)
        )
    }

    fun visibility(
        profile: String, current: List<HomePanel>, id: String, visible: Boolean
    ): List<HomePanel> = normalize(profile, current).map { panel ->
        if (panel.id == id) panel.copy(visible = visible) else panel
    }

    /**
     * Best-fit row packing: a later narrow panel can occupy a gap left by a
     * wider panel. Preserves each card once and never changes persisted settings.
     */
    fun packedVisible(profile: String, current: List<HomePanel>): List<HomePanel> {
        val remaining = normalize(profile, current).filter { it.visible }.toMutableList()
        val result = mutableListOf<HomePanel>()
        val columns = columns(profile)
        var free = columns
        while (remaining.isNotEmpty()) {
            val next = remaining.indexOfFirst { it.span <= free }
            if (next < 0) {
                free = columns
                continue
            }
            val panel = remaining.removeAt(next)
            result.add(panel)
            free -= panel.span
            if (free == 0) free = columns
        }
        return result
    }

    /** Pack row-major, preserving order with no missing internal columns. */
    fun rows(profile: String, current: List<HomePanel>): List<List<HomePanel>> {
        val maxWidth = columns(profile)
        val result = mutableListOf<List<HomePanel>>()
        var row = mutableListOf<HomePanel>()
        var width = 0
        normalize(profile, current).filter { it.visible }.forEach { panel ->
            if (width + panel.span > maxWidth && row.isNotEmpty()) {
                result.add(row)
                row = mutableListOf()
                width = 0
            }
            row.add(panel)
            width += panel.span
        }
        if (row.isNotEmpty()) result.add(row)
        return result
    }

    fun encode(profile: String, panels: List<HomePanel>): String = buildJsonArray {
        normalize(profile, panels).forEach { panel ->
            add(buildJsonObject {
                put("id", panel.id)
                put("span", panel.span)
                put("height", panel.height)
                put("visible", panel.visible)
            })
        }
    }.toString()

    fun decode(profile: String, raw: String?): List<HomePanel> {
        if (raw.isNullOrBlank() || raw.length > 8192) return default(profile)
        return runCatching {
            val elements = Json.parseToJsonElement(raw) as? JsonArray
                ?: return@runCatching default(profile)
            val parsed = elements.take(24).mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val span = obj["span"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val height = obj["height"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val visible = obj["visible"]?.jsonPrimitive?.booleanOrNull ?: true
                HomePanel(id, span, height, visible)
            }
            normalize(profile, parsed)
        }.getOrElse { default(profile) }
    }
}

/** Layout state stays on-device and is kept independent for outer/inner display widths. */
internal class HomePanelStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "home_panel_layout_v1", Context.MODE_PRIVATE
    )

    fun load(profile: String): List<HomePanel> =
        HomePanelLayout.decode(profile, prefs.getString("panels_" + profile, null))

    fun save(profile: String, panels: List<HomePanel>) {
        prefs.edit().putString("panels_" + profile, HomePanelLayout.encode(profile, panels)).apply()
    }

    fun reset(profile: String): List<HomePanel> {
        prefs.edit().remove("panels_" + profile).apply()
        return HomePanelLayout.default(profile)
    }
}

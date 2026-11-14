package net.automationse.relayharbor.ui

import android.content.Context
import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.data.ChannelSummary

/** Channel shortcuts are local UI preferences; no changes to remote connection data. */
internal class ChannelPinStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "channel_ui_shortcuts_v1", Context.MODE_PRIVATE
    )

    fun load(): Set<Long> = prefs.getStringSet("pinned", emptySet()).orEmpty()
        .asSequence().mapNotNull { it.toLongOrNull() }
        .filter { it > 0 }.take(256).toSet()

    fun save(ids: Set<Long>) {
        prefs.edit().putStringSet("pinned", ids.filter { it > 0 }
            .take(256).map { it.toString() }.toSet()).apply()
    }
}

internal object ChannelPinPolicy {
    fun toggle(current: Set<Long>, id: Long): Set<Long> = when {
        id <= 0L -> current
        id in current -> current - id
        current.size >= 256 -> current
        else -> current + id
    }

    fun visible(
        channels: List<ChannelEntity>,
        summaries: Map<Long, ChannelSummary>,
        query: String,
        filter: String,
        pinned: Set<Long>
    ): List<ChannelEntity> {
        val term = query.trim()
        val filtered = channels.filter { channel ->
            (term.isBlank() || channel.name.contains(term, ignoreCase = true)) &&
                when (filter) {
                    "unread" -> (summaries[channel.id]?.unreadCount ?: 0) > 0
                    "pinned" -> channel.id in pinned
                    else -> true
                }
        }
        val sorted = if (filter == "recent") filtered.sortedWith(
            compareByDescending<ChannelEntity> {
                summaries[it.id]?.lastTimestamp ?: 0L
            }.thenBy { it.position }.thenBy { it.id }
        ) else filtered.sortedWith(compareBy<ChannelEntity> { it.position }.thenBy { it.id })
        // Most-used favorites remain visible at the top of every channel view,
        // while respecting the selected sort within each section.
        return sorted.filter { it.id in pinned } + sorted.filterNot { it.id in pinned }
    }
}

package net.automationse.relayharbor.data

import android.content.Context

/**
 * Device-local channel organization. The immutable routing name (ChannelEntity.name)
 * is never edited here: Relay inbound events still land in their original channel.
 * Display labels, emoji and folders are UI metadata only, never sent to a server.
 */
class ChannelOrganizationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "channel_organization_v1", Context.MODE_PRIVATE
    )

    fun displayName(id: Long, originalName: String): String {
        val alias = prefs.getString("label_$id", "").orEmpty()
        return alias.ifBlank { originalName }
    }

    fun emoji(id: Long, original: String): String =
        prefs.getString("emoji_$id", "").orEmpty().ifBlank { original }

    fun folder(id: Long): String = prefs.getString("folder_$id", "").orEmpty()

    fun present(channel: ChannelEntity): ChannelEntity =
        channel.copy(
            name = displayName(channel.id, channel.name),
            emoji = emoji(channel.id, channel.emoji)
        )

    /** Empty alias restores the original inbound routing name as the displayed title. */
    fun saveAppearance(id: Long, alias: String, icon: String) {
        if (id <= 0) return
        val cleanAlias = ChannelOrganizationRules.cleanLabel(alias)
        val cleanIcon = ChannelOrganizationRules.cleanEmoji(icon)
        prefs.edit().putString("label_$id", cleanAlias)
            .putString("emoji_$id", cleanIcon).apply()
    }

    fun folders(): List<String> =
        prefs.getStringSet("folders", emptySet()).orEmpty()
            .filter { ChannelOrganizationRules.validFolder(it) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun addFolder(raw: String): Boolean {
        val name = ChannelOrganizationRules.cleanFolder(raw)
        val existing = folders()
        if (!ChannelOrganizationRules.validFolder(name) ||
            existing.any { it.equals(name, ignoreCase = true) } ||
            existing.size >= 30) return false
        prefs.edit().putStringSet("folders", (existing + name).toSet()).apply()
        return true
    }

    fun renameFolder(from: String, rawTo: String): Boolean {
        val target = ChannelOrganizationRules.cleanFolder(rawTo)
        val existing = folders()
        if (from !in existing || !ChannelOrganizationRules.validFolder(target) ||
            existing.any { it != from && it.equals(target, ignoreCase = true) }) return false
        val editor = prefs.edit()
            .putStringSet("folders", (existing - from + target).toSet())
        prefs.all.filter { it.key.startsWith("folder_") && it.value == from }
            .forEach { editor.putString(it.key, target) }
        editor.apply()
        return true
    }

    fun deleteFolder(folder: String) {
        if (folder !in folders()) return
        val editor = prefs.edit().putStringSet("folders", (folders() - folder).toSet())
        prefs.all.filter { it.key.startsWith("folder_") && it.value == folder }
            .forEach { editor.remove(it.key) }
        editor.apply()
    }

    /** Refuse unknown folders, keeping membership consistent with the folder catalog. */
    fun assign(id: Long, folder: String): Boolean {
        if (id <= 0 || (folder.isNotBlank() && folder !in folders())) return false
        prefs.edit().putString("folder_$id", folder).apply()
        return true
    }

    fun clearChannel(id: Long) {
        if (id <= 0) return
        prefs.edit().remove("label_$id").remove("emoji_$id").remove("folder_$id").apply()
    }
}

object ChannelOrganizationRules {
    fun cleanLabel(raw: String): String = raw.trim()
        .filterNot { it == '\n' || it == '\r' || it == '\u0000' }.take(60)

    fun cleanEmoji(raw: String): String = raw.trim()
        .filterNot { it == '\n' || it == '\r' || it == '\u0000' }
        .take(12)

    fun cleanFolder(raw: String): String = raw.trim()
        .filterNot { it == '\n' || it == '\r' || it == '\u0000' }.take(32)

    fun validFolder(value: String): Boolean =
        value.isNotBlank() && value.length <= 32 && value.trim() == value
}

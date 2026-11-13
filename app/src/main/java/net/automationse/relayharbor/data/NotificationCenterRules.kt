package net.automationse.relayharbor.data

enum class NotificationAction {
    PIN, UNPIN, SAVE_FOR_LATER, REMOVE_FROM_LATER,
    COMPLETE, REOPEN, MARK_READ, MARK_UNREAD
}

/** Local-only statuses; incoming webhook events never execute actions automatically. */
object NotificationCenterRules {
    const val PRIORITY_SCORE = 85
    val filters = setOf("all", "important", "unread", "later", "done", "read")

    fun filterId(value: String?): String = value?.takeIf { it in filters } ?: "all"

    fun important(message: MessageEntity): Boolean =
        message.isPinned ||
            (message.kind == "card" && message.cardJson?.let {
                CardCodec.decode(it)?.score
            }?.let { it >= PRIORITY_SCORE } == true)

    fun matches(message: MessageEntity, filter: String, query: String = "",
        channelName: String = ""): Boolean {
        val normalized = filterId(filter)
        val details = if (message.kind == "card") {
            message.cardJson?.let { CardCodec.decode(it) }
        } else null
        val term = query.trim()
        if (term.isNotEmpty() && listOf(
                message.text, message.sender, channelName,
                details?.title.orEmpty(), details?.description.orEmpty()
            ).none { it.contains(term, ignoreCase = true) }) return false

        return when (normalized) {
            "important" -> !message.isDone && important(message)
            "unread" -> !message.isDone && !message.isRead
            "later" -> !message.isDone && message.savedForLater
            "done" -> message.isDone
            "read" -> !message.isDone && message.isRead
            else -> !message.isDone
        }
    }

    fun sort(messages: List<MessageEntity>, filter: String): List<MessageEntity> =
        if (filterId(filter) == "important") {
            messages.sortedWith(compareByDescending<MessageEntity> { it.isPinned }
                .thenByDescending { it.timestamp }.thenByDescending { it.id })
        } else {
            messages.sortedWith(compareByDescending<MessageEntity> { it.timestamp }
                .thenByDescending { it.id })
        }
}

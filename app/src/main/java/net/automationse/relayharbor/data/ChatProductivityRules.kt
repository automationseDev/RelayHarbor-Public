package net.automationse.relayharbor.data

/** Pure on-device search and drafting rules: no API calls or uploads. */
object ChatProductivityRules {
    val filters = setOf("all", "unread", "pinned", "later")
    val presets = listOf(
        "summary" to "要約",
        "tasks" to "タスク抽出",
        "reply" to "返信案",
        "translate" to "英語に翻訳"
    )

    fun matches(message: MessageEntity, term: String): Boolean =
        listOf(message.text, message.sender, message.cardJson.orEmpty())
            .any { it.contains(term, ignoreCase = true) }

    fun visible(
        messages: List<MessageEntity>,
        parentId: Long?,
        term: String,
        filter: String
    ): List<MessageEntity> {
        if (parentId != null) {
            val root = messages.firstOrNull {
                it.id == parentId && it.parentMessageId == null
            } ?: return emptyList()
            return listOf(root) + messages.filter { it.parentMessageId == parentId }
        }
        val search = term.trim().take(100)
        val replyIds = if (search.isBlank()) emptySet() else messages.asSequence()
            .filter { it.parentMessageId != null && matches(it, search) }
            .mapNotNull { it.parentMessageId }.toSet()

        return messages.filter { message ->
            message.parentMessageId == null &&
                (search.isBlank() || matches(message, search) || message.id in replyIds) &&
                when (filter) {
                    "unread" -> !message.isRead && !message.fromMe
                    "pinned" -> message.isPinned
                    "later" -> message.savedForLater
                    else -> true
                }
        }
    }

    fun preparedPrompt(id: String, source: String): String {
        val request = when (id) {
            "summary" -> "以下の内容を短く要約し、重要な点を3つ挙げてください。"
            "tasks" -> "以下の内容から必要な作業を抽出し、優先度付きのチェックリストにしてください。"
            "reply" -> "以下の内容に対する、丁寧で簡潔な返信案を作成してください。"
            "translate" -> "以下の内容を自然な英語に翻訳してください。"
            else -> return source.take(8000)
        }
        return (request + "\n\n" + source.take(7000)).take(8000)
    }
}

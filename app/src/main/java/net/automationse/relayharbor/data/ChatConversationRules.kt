package net.automationse.relayharbor.data

/** A reply belongs to exactly one root message and never creates nested threads. */
object ChatConversationRules {
    val emojiChoices = listOf("👍", "❤️", "🎉", "👀", "✅", "🔥")

    fun roots(messages: List<MessageEntity>): List<MessageEntity> =
        messages.filter { it.parentMessageId == null }

    fun thread(messages: List<MessageEntity>, parentId: Long): List<MessageEntity> =
        messages.filter { it.parentMessageId == parentId }

    fun cleanBody(body: String): String = body.trim().take(8000)

    fun validReaction(emoji: String): Boolean = emoji in emojiChoices

    fun canEdit(message: MessageEntity): Boolean = message.fromMe && message.kind == "text"
}

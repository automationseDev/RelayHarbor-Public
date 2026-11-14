package net.automationse.relayharbor.data

import kotlinx.coroutines.flow.Flow
import net.automationse.relayharbor.integration.IncomingRelayEvent
import net.automationse.relayharbor.integration.RelayChatTurn

data class ImportedRelayMessage(
    val source: String,
    val sequence: Long,
    val channelId: Long,
    val channelName: String,
    val sender: String,
    val text: String,
    val timestamp: Long
)

class HubRepository(private val dao: HubDao) {
    val channels: Flow<List<ChannelEntity>> = dao.observeChannels()
    val channelSummaries: Flow<List<ChannelSummary>> = dao.observeChannelSummaries()
    val activity: Flow<List<MessageEntity>> = dao.observeActivity()
    fun messages(channelId: Long): Flow<List<MessageEntity>> = dao.observeMessages(channelId)

    suspend fun bootstrap() {
        if (dao.channelCount() != 0) return
        val ai = dao.insertChannel(ChannelEntity(name = "お知らせ", emoji = "🔔", position = 10))
        val nas = dao.insertChannel(ChannelEntity(name = "システム監視", emoji = "🖥️", position = 20))
        val github = dao.insertChannel(ChannelEntity(name = "GitHub", emoji = "🧩", position = 30))
        val assistant = dao.insertChannel(ChannelEntity(name = "AIチャット", emoji = "✨", position = 40))
        val now = System.currentTimeMillis()
        dao.insertMessage(MessageEntity(
            channelId = ai, sender = "Demo Bot",
            text = "サンプル通知です（デモデータ）。",
            kind = "card", timestamp = now - 120000L,
            cardJson = CardCodec.encode(RichCard(
                title = "サービス通知のサンプル",
                description = "リッチカードのサンプルです。外部サービスとは接続していません。",
                score = 92, badge = "高適合",
                metrics = listOf(CardMetric("稼働率", 95), CardMetric("品質", 88), CardMetric("安定性", 80))
            ))
        ))
        dao.insertMessage(MessageEntity(
            channelId = nas, sender = "NAS Monitor", timestamp = now - 7200000L,
            text = "サーバー監視のサンプル通知です。実機の状態ではありません。"
        ))
        dao.insertMessage(MessageEntity(
            channelId = github, sender = "GitHub Bot", timestamp = now - 15000000L,
            text = "ビルド通知のサンプルです。GitHub連携は未設定です。"
        ))
        dao.insertMessage(MessageEntity(
            channelId = assistant, sender = "RelayHarbor", timestamp = now - 18000000L,
            text = "ここには将来DifyやAI APIの応答を表示できます。現在はローカルモードです。"
        ))
    }

    suspend fun lastInboundSequence(source: String): Long =
        dao.lastInboundSequence(source)

    suspend fun importRelayEvents(
        source: String,
        events: List<IncomingRelayEvent>
    ): List<ImportedRelayMessage> {
        val newlyInserted = mutableListOf<ImportedRelayMessage>()
        events.sortedBy { it.sequence }.forEach { event ->
            val name = event.channel.ifBlank { "Webhook" }.take(60)
            val channel = dao.findChannelByName(name)
                ?: ChannelEntity(name = name, emoji = "🔔", position = 80).let {
                    it.copy(id = dao.insertChannel(it))
                }
            val message = MessageEntity(
                channelId = channel.id,
                sender = event.sender,
                text = event.text,
                kind = if (event.cardJson != null) "card" else "text",
                cardJson = event.cardJson,
                timestamp = event.timestamp,
                isRead = false
            )
            if (dao.saveInboundMessage(
                    InboundEventEntity(source, event.sequence), message
                )
            ) {
                newlyInserted.add(ImportedRelayMessage(
                    source = source,
                    sequence = event.sequence,
                    channelId = channel.id,
                    channelName = channel.name,
                    sender = event.sender,
                    text = event.text,
                    timestamp = event.timestamp
                ))
            }
        }
        return newlyInserted
    }

    /**
     * Device-local only: OS notification content must never enter the Relay upload path.
     * Uses the same transactional dedup ledger as inbound events, but its own source.
     */
    suspend fun importAndroidNotification(
        sourcePackage: String,
        notificationKey: String,
        appName: String,
        title: String,
        body: String,
        channelId: Long,
        timestamp: Long
    ): Boolean {
        val channel = dao.findChannelById(channelId) ?: return false
        if (channel.id <= 0L) return false
        val source = net.automationse.relayharbor.notification.AndroidNotificationRules
            .source(sourcePackage, notificationKey)
        val sequence = net.automationse.relayharbor.notification.AndroidNotificationRules
            .revision(title, body)
        val displayedText = listOf(title, body)
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .take(net.automationse.relayharbor.notification.AndroidNotificationRules.MAX_TEXT_LENGTH)
        return dao.saveInboundMessage(
            InboundEventEntity(source, sequence),
            MessageEntity(
                channelId = channelId,
                sender = appName.take(80),
                text = displayedText,
                kind = "app_notification",
                timestamp = timestamp,
                isRead = false,
                sourcePackage = sourcePackage
            )
        )
    }

    suspend fun chatHistory(channelId: Long): List<RelayChatTurn> =
        dao.recentMessages(channelId).asReversed()
            .filter { it.kind == "text" && (it.fromMe || it.sender in setOf("Dify", "OpenAI")) }
            .takeLast(12)
            .map { RelayChatTurn(if (it.fromMe) "user" else "assistant", it.text) }

    suspend fun addAiReply(channelId: Long, provider: String, message: String): Long {
        return dao.insertMessage(MessageEntity(
            channelId = channelId,
            sender = if (provider == "dify") "Dify" else "OpenAI",
            text = message.take(12000),
            isRead = false
        ))
    }

    suspend fun createChannel(name: String) {
        val trimmed = name.trim().take(60)
        if (trimmed.isNotEmpty()) {
            dao.insertChannel(ChannelEntity(name = trimmed))
        }
    }

    suspend fun deleteChannel(id: Long) = dao.deleteChannel(id)
    suspend fun markRead(id: Long) = dao.markRead(id)

    suspend fun updateNotification(messageId: Long, action: NotificationAction) {
        if (messageId <= 0) return
        when (action) {
            NotificationAction.PIN -> dao.setMessagePinned(messageId, true)
            NotificationAction.UNPIN -> dao.setMessagePinned(messageId, false)
            NotificationAction.SAVE_FOR_LATER -> dao.setMessageSaved(messageId, true)
            NotificationAction.REMOVE_FROM_LATER -> dao.setMessageSaved(messageId, false)
            NotificationAction.COMPLETE -> dao.setMessageDone(messageId, true)
            NotificationAction.REOPEN -> dao.setMessageDone(messageId, false)
            NotificationAction.MARK_READ -> dao.setMessageRead(messageId, true)
            NotificationAction.MARK_UNREAD -> dao.setMessageRead(messageId, false)
        }
    }

    suspend fun sendMessage(channelId: Long, body: String) {
        val cleaned = body.trim()
        if (cleaned.isNotEmpty()) {
            dao.insertMessage(MessageEntity(
                channelId = channelId, sender = "自分", text = cleaned, fromMe = true
            ))
        }
    }

    suspend fun addDemoCard(channelId: Long) {
        dao.insertMessage(MessageEntity(
            channelId = channelId,
            sender = "Demo Bot",
            kind = "card",
            text = "これはサンプル通知です。WebhookやAI接続はまだ有効になっていません。",
            cardJson = CardCodec.encode(RichCard(
                title = "システム評価デモ",
                description = "カード内にはメトリクス・評価スコア・説明を表示できます。",
                score = 86,
                badge = "サンプル",
                metrics = listOf(
                    CardMetric("品質", 93),
                    CardMetric("進捗", 76),
                    CardMetric("優先度", 89)
                )
            ))
        ))
    }
}

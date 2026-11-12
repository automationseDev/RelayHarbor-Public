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
        val ai = dao.insertChannel(ChannelEntity(name = "AI案件スカウト", emoji = "🤖", position = 10))
        val nas = dao.insertChannel(ChannelEntity(name = "NAS監視", emoji = "🖥️", position = 20))
        val github = dao.insertChannel(ChannelEntity(name = "GitHub", emoji = "🧩", position = 30))
        val assistant = dao.insertChannel(ChannelEntity(name = "AIチャット", emoji = "✨", position = 40))
        val now = System.currentTimeMillis()
        dao.insertMessage(MessageEntity(
            channelId = ai, sender = "Scout Bot",
            text = "新しい案件を検出しました（デモデータ）。",
            kind = "card", timestamp = now - 120000L,
            cardJson = CardCodec.encode(RichCard(
                title = "Python業務自動化ツール開発",
                description = "案件評価カードのサンプルです。外部サービスとは接続していません。",
                score = 92, badge = "高適合",
                metrics = listOf(CardMetric("技術適合", 95), CardMetric("収益性", 88), CardMetric("納期余裕", 80))
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

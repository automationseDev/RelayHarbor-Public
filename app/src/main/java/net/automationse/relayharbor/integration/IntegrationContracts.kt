package net.automationse.relayharbor.integration

/**
 * Extension point for future cloud or self-hosted gateways.
 * v0.1 does not connect to external endpoints or collect provider credentials.
 */
interface HubGateway {
    suspend fun ping(): GatewayStatus
    suspend fun fetchEvents(afterCursor: String?): EventPage
}

data class GatewayStatus(val connected: Boolean, val message: String)
data class GatewayEvent(
    val remoteId: String,
    val channel: String,
    val sender: String,
    val body: String,
    val cardJson: String? = null,
    val timestamp: Long
)
data class EventPage(val events: List<GatewayEvent>, val nextCursor: String?)

/**
 * Provider adapters can be implemented independently; no hard-coded NAS URL or Dify API key.
 */
interface AiProvider {
    suspend fun ask(prompt: String, conversationId: String? = null): AiReply
}
data class AiReply(val text: String, val conversationId: String? = null)

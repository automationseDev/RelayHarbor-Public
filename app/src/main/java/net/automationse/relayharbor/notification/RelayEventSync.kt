package net.automationse.relayharbor.notification

import android.content.Context
import net.automationse.relayharbor.data.ConnectionStore
import net.automationse.relayharbor.data.HubDatabase
import net.automationse.relayharbor.data.HubRepository
import net.automationse.relayharbor.data.RelaySecretStore
import net.automationse.relayharbor.integration.RelayClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes foreground and periodic synchronization in the same app process. */
object RelayEventSync {
    private val mutex = Mutex()

    suspend fun perform(context: Context): Int = mutex.withLock {
        val app = context.applicationContext
        val settings = ConnectionStore(app).settings.first()
        require(settings.mode != "local" && RelayClient.validEndpoint(settings.endpoint)) {
            "RelayのHTTPS接続先を登録してください"
        }
        val token = RelaySecretStore(app).getToken()
            ?: error("Relayの接続トークンを登録してください")

        val source = settings.endpoint.trim().trimEnd('/')
        val repository = HubRepository(HubDatabase.get(app).dao())
        val client = RelayClient()
        var count = 0
        repeat(5) {
            val cursor = repository.lastInboundSequence(source)
            val page = client.loadEvents(source, token, cursor)
            if (page.isEmpty()) return@withLock count
            val committed = repository.importRelayEvents(source, page)
            count += committed.size
            // The message table's inbound-event key is the deduplication authority.
            // Do not notify before the transaction commits.
            MessageNotifications.showImported(app, committed)
            if (page.size < 100) return@withLock count
        }
        count
    }
}

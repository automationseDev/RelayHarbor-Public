package net.automationse.relayharbor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.data.ChannelSummary
import net.automationse.relayharbor.data.MessageEntity
import net.automationse.relayharbor.data.ConnectionSettings
import net.automationse.relayharbor.data.ConnectionStore
import net.automationse.relayharbor.data.RelaySecretStore
import net.automationse.relayharbor.data.RelayConversationStore
import net.automationse.relayharbor.data.HubDatabase
import net.automationse.relayharbor.data.HubRepository
import net.automationse.relayharbor.integration.RelayClient
import net.automationse.relayharbor.integration.IntegrationClient
import net.automationse.relayharbor.integration.IntegrationConnection
import net.automationse.relayharbor.integration.IntegrationCredential
import net.automationse.relayharbor.integration.AppRelease
import net.automationse.relayharbor.integration.AppUpdater
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AppUpdateState(
    val message: String = "GitHub Releasesで更新を確認できます",
    val release: AppRelease? = null,
    val progress: Int? = null,
    val busy: Boolean = false,
    val downloadedApk: File? = null
)

class HubViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = HubRepository(HubDatabase.get(app).dao())
    private val connectionStore = ConnectionStore(app)
    private val secretStore = RelaySecretStore(app)
    private val conversationStore = RelayConversationStore(app)
    private val relayClient = RelayClient()
    private val integrationClient = IntegrationClient(relayClient)
    private val syncMutex = Mutex()
    private val currentChannel = MutableStateFlow<Long?>(null)

    val channels: StateFlow<List<ChannelEntity>> = repository.channels.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val channelSummaries: StateFlow<List<ChannelSummary>> = repository.channelSummaries.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val activity: StateFlow<List<MessageEntity>> = repository.activity.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val selectedChannelId: StateFlow<Long?> = currentChannel
    val messages = currentChannel.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.messages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionSettings = connectionStore.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionSettings()
    )
    val tokenConfigured = MutableStateFlow(secretStore.hasToken())
    val saveError = MutableStateFlow<String?>(null)
    val aiError = MutableStateFlow<String?>(null)
    val syncStatus = MutableStateFlow("未同期")
    val integrations = MutableStateFlow<List<IntegrationConnection>>(emptyList())
    val integrationStatus = MutableStateFlow("接続先を設定してください")
    val newIntegrationCredential = MutableStateFlow<IntegrationCredential?>(null)
    val busyChannelId = MutableStateFlow<Long?>(null)
    val appUpdateState = MutableStateFlow(AppUpdateState())

    init {
        viewModelScope.launch { repository.bootstrap() }
    }

    fun selectChannel(id: Long) {
        currentChannel.value = id
        aiError.value = null
        viewModelScope.launch { repository.markRead(id) }
    }

    fun leaveChannel() {
        currentChannel.value = null
    }

    fun createChannel(name: String) {
        viewModelScope.launch { repository.createChannel(name) }
    }

    fun deleteChannel(id: Long) {
        if (currentChannel.value == id) leaveChannel()
        viewModelScope.launch { repository.deleteChannel(id) }
    }

    fun sendMessage(body: String) {
        val id = currentChannel.value ?: return
        if (busyChannelId.value == id) return
        val cleaned = body.trim()
        if (cleaned.isEmpty()) return
        aiError.value = null
        viewModelScope.launch {
            val config = connectionStore.settings.first()
            val history = repository.chatHistory(id)
            repository.sendMessage(id, cleaned)
            if (config.aiProvider == "none" || config.mode == "local") return@launch

            val token = secretStore.getToken()
            if (!RelayClient.validEndpoint(config.endpoint) || token == null) {
                aiError.value = "接続先とアクセストークンを設定してください"
                return@launch
            }
            busyChannelId.value = id
            try {
                val conversationId = if (config.aiProvider == "dify")
                    conversationStore.get(config.endpoint, config.aiProvider, id) else ""
                val result = relayClient.sendChat(
                    config.endpoint, token, config.aiProvider,
                    cleaned, conversationId, history
                )
                repository.addAiReply(id, config.aiProvider, result.answer)
                if (config.aiProvider == "dify" && result.conversationId.isNotBlank()) {
                    conversationStore.save(
                        config.endpoint, config.aiProvider, id, result.conversationId
                    )
                }
                if (currentChannel.value == id) repository.markRead(id)
            } catch (error: Exception) {
                if (currentChannel.value == id) {
                    aiError.value = error.message ?: "AI接続に失敗しました"
                }
            } finally {
                busyChannelId.value = null
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            if (syncMutex.isLocked) return@launch
            syncMutex.withLock {
                val config = connectionStore.settings.first()
                val token = secretStore.getToken()
                if (config.mode == "local" || token == null ||
                    !RelayClient.validEndpoint(config.endpoint)) {
                    syncStatus.value = "サーバー接続先・トークン未設定"
                    return@withLock
                }
                syncStatus.value = "同期中…"
                try {
                    val source = config.endpoint.trim().trimEnd('/')
                    var newCount = 0
                    for (page in 0 until 5) {
                        val after = repository.lastInboundSequence(source)
                        val events = relayClient.loadEvents(source, token, after)
                        if (events.isEmpty()) break
                        newCount += repository.importRelayEvents(source, events)
                        if (events.size < 100) break
                    }
                    syncStatus.value = "同期完了：新着 $newCount 件"
                } catch (error: Exception) {
                    syncStatus.value = "同期エラー：" + (error.message ?: "通信に失敗しました")
                }
            }
        }
    }

    fun addDemoCard() {
        val id = currentChannel.value ?: return
        viewModelScope.launch { repository.addDemoCard(id) }
    }

    fun saveSettings(mode: String, endpoint: String, ai: String, newToken: String) {
        viewModelScope.launch {
            saveError.value = null
            try {
                val trimmed = newToken.trim()
                if (trimmed.isNotEmpty()) secretStore.setToken(trimmed)
                connectionStore.save(mode, endpoint, ai)
                tokenConfigured.value = secretStore.hasToken()
            } catch (error: Exception) {
                saveError.value = error.message ?: "保存に失敗しました"
            }
        }
    }

    fun clearToken() {
        secretStore.clearToken()
        tokenConfigured.value = false
        syncStatus.value = "接続トークンを削除しました"
    }
    private suspend fun integrationAuth(): Pair<String, String> {
        val config = connectionStore.settings.first()
        require(config.mode != "local" && RelayClient.validEndpoint(config.endpoint)) {
            "設定でRelayのHTTPS接続先を登録してください"
        }
        val token = secretStore.getToken() ?: error("設定でRelayアクセストークンを登録してください")
        return config.endpoint.trim().trimEnd('/') to token
    }

    fun refreshIntegrations() {
        viewModelScope.launch {
            try {
                val (endpoint, token) = integrationAuth()
                integrations.value = integrationClient.list(endpoint, token)
                integrationStatus.value = "連携先 ${integrations.value.size} 件"
            } catch (error: Exception) {
                integrationStatus.value = "取得失敗：" + (error.message ?: "通信エラー")
            }
        }
    }

    fun createIntegration(
        name: String, provider: String, channelName: String, minScore: Int,
        keyword: String, feedUrl: String, pollIntervalMinutes: Int
    ) {
        viewModelScope.launch {
            integrationStatus.value = "連携先を登録しています…"
            try {
                val (endpoint, token) = integrationAuth()
                newIntegrationCredential.value = integrationClient.create(
                    endpoint, token, name, provider, channelName, minScore, keyword,
                    feedUrl, pollIntervalMinutes
                )
                integrations.value = integrationClient.list(endpoint, token)
                integrationStatus.value = if (provider == "rss") "RSS連携を作成しました。「今すぐ取得」で記事を確認できます" else "連携先を作成しました。専用トークンは今回だけ表示されます"
            } catch (error: Exception) {
                integrationStatus.value = "登録失敗：" + (error.message ?: "通信エラー")
            }
        }
    }

    fun updateIntegration(connection: IntegrationConnection) {
        viewModelScope.launch {
            try {
                val (endpoint, token) = integrationAuth()
                integrationClient.update(endpoint, token, connection)
                integrations.value = integrationClient.list(endpoint, token)
                integrationStatus.value = "設定を更新しました"
            } catch (error: Exception) {
                integrationStatus.value = "更新失敗：" + (error.message ?: "通信エラー")
            }
        }
    }

    fun refreshRssIntegration(id: String) {
        viewModelScope.launch {
            integrationStatus.value = "RSSの新着記事を取得しています…"
            try {
                val (endpoint, token) = integrationAuth()
                val count = integrationClient.refreshRss(endpoint, token, id)
                integrations.value = integrationClient.list(endpoint, token)
                integrationStatus.value = "RSS取得完了：新着 $count 件"
                if (count > 0) syncNow()
            } catch (error: Exception) {
                integrationStatus.value = "RSS取得失敗：" + (error.message ?: "通信エラー")
                // refresh the last_error field on the connection card
                runCatching {
                    val (endpoint, token) = integrationAuth()
                    integrations.value = integrationClient.list(endpoint, token)
                }
            }
        }
    }

    fun testIntegration(id: String) {
        viewModelScope.launch {
            integrationStatus.value = "テスト通知を送っています…"
            try {
                val (endpoint, token) = integrationAuth()
                integrationClient.sendTest(endpoint, token, id)
                integrationStatus.value = "テスト通知を保存しました。通知を同期します"
                syncNow()
            } catch (error: Exception) {
                integrationStatus.value = "テスト失敗：" + (error.message ?: "通信エラー")
            }
        }
    }

    fun rotateIntegration(id: String) {
        viewModelScope.launch {
            try {
                val (endpoint, token) = integrationAuth()
                newIntegrationCredential.value = integrationClient.rotate(endpoint, token, id)
                integrationStatus.value = "トークンを更新しました。旧トークンは無効です"
            } catch (error: Exception) {
                integrationStatus.value = "トークン更新失敗：" + (error.message ?: "通信エラー")
            }
        }
    }

    fun deleteIntegration(id: String) {
        viewModelScope.launch {
            try {
                val (endpoint, token) = integrationAuth()
                integrationClient.delete(endpoint, token, id)
                integrations.value = integrationClient.list(endpoint, token)
                integrationStatus.value = "連携を削除しました（受信済みの通知は残ります）"
            } catch (error: Exception) {
                integrationStatus.value = "削除失敗：" + (error.message ?: "通信エラー")
            }
        }
    }

    fun checkAppUpdate() {
        if (appUpdateState.value.busy) return
        viewModelScope.launch {
            appUpdateState.value = AppUpdateState(message = "GitHub Releasesを確認中…", busy = true)
            try {
                val release = AppUpdater.checkLatest(getApplication())
                appUpdateState.value = if (release == null) {
                    AppUpdateState(message = "最新版を利用しています")
                } else {
                    AppUpdateState(
                        message = "新しいバージョン ${release.version} が利用できます",
                        release = release
                    )
                }
            } catch (error: Exception) {
                appUpdateState.value = AppUpdateState(
                    message = "更新確認に失敗：" + (error.message ?: "通信エラー")
                )
            }
        }
    }

    fun installAppUpdate() {
        val state = appUpdateState.value
        if (state.busy) return
        val release = state.release ?: return
        viewModelScope.launch {
            appUpdateState.value = state.copy(busy = true, message = "更新APKを準備中…")
            try {
                val apk = state.downloadedApk?.takeIf { it.exists() }
                    ?: AppUpdater.download(getApplication(), release) { progress ->
                        appUpdateState.value = appUpdateState.value.copy(
                            progress = progress,
                            message = "署名済みAPKを取得中：$progress%"
                        )
                    }
                appUpdateState.value = appUpdateState.value.copy(
                    downloadedApk = apk, busy = false, progress = 100,
                    message = "ダウンロード完了。Androidのインストール画面を開きます"
                )
                val installerOpened = AppUpdater.install(getApplication(), apk)
                if (!installerOpened) {
                    appUpdateState.value = appUpdateState.value.copy(
                        message = "このアプリからのインストールを許可し、設定に戻って「インストール」をもう一度押してください"
                    )
                }
            } catch (error: Exception) {
                appUpdateState.value = appUpdateState.value.copy(
                    busy = false,
                    message = "アプリ更新に失敗：" + (error.message ?: "処理エラー")
                )
            }
        }
    }

    fun closeCredential() {
        newIntegrationCredential.value = null
    }

}

package net.automationse.relayharbor.ui

import android.app.Application
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.data.ChannelSummary
import net.automationse.relayharbor.data.MessageEntity
import net.automationse.relayharbor.data.MessageReactionEntity
import net.automationse.relayharbor.data.NotificationAction
import net.automationse.relayharbor.data.ConnectionSettings
import net.automationse.relayharbor.data.ConnectionStore
import net.automationse.relayharbor.data.AiChannelSelectionStore
import net.automationse.relayharbor.data.RelaySecretStore
import net.automationse.relayharbor.data.RelayConversationStore
import net.automationse.relayharbor.data.HubDatabase
import net.automationse.relayharbor.data.HubRepository
import net.automationse.relayharbor.integration.RelayClient
import net.automationse.relayharbor.integration.AiProfile
import net.automationse.relayharbor.integration.AiProfileClient
import net.automationse.relayharbor.integration.AiProfileDraft
import net.automationse.relayharbor.integration.IntegrationClient
import net.automationse.relayharbor.integration.IntegrationConnection
import net.automationse.relayharbor.integration.IntegrationCredential
import net.automationse.relayharbor.integration.AppRelease
import net.automationse.relayharbor.integration.AppUpdater
import net.automationse.relayharbor.notification.RelayEventSync
import net.automationse.relayharbor.notification.MessageNotifications
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
    val busy: Boolean = false
)

class HubViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = HubRepository(HubDatabase.get(app).dao())
    private val connectionStore = ConnectionStore(app)
    private val secretStore = RelaySecretStore(app)
    private val conversationStore = RelayConversationStore(app)
    private val relayClient = RelayClient()
    private val aiProfileClient = AiProfileClient(relayClient)
    private val aiChannelStore = AiChannelSelectionStore(app)
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
    val messageSearchResults = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messageSearchBusy = MutableStateFlow(false)
    private var messageSearchJob: Job? = null

    /** Debounced local Room query; no message text is sent to Relay or an AI API. */
    fun searchAllMessages(query: String) {
        messageSearchJob?.cancel()
        messageSearchResults.value = emptyList()
        messageSearchBusy.value = query.trim().length >= 2
        if (!messageSearchBusy.value) return
        val term = query.trim().take(100)
        messageSearchJob = viewModelScope.launch {
            delay(250L)
            try {
                messageSearchResults.value = withContext(Dispatchers.IO) {
                    repository.searchAllMessages(term)
                }
            } finally {
                messageSearchBusy.value = false
            }
        }
    }

    val selectedChannelId: StateFlow<Long?> = currentChannel
    val messages = currentChannel.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.messages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reactions = currentChannel.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.reactions(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<MessageReactionEntity>())

    fun sendThreadReply(parentId: Long, body: String) {
        val channelId = currentChannel.value ?: return
        viewModelScope.launch {
            if (!repository.sendThreadReply(channelId, parentId, body)) {
                aiError.value = "返信先が見つかりません。チャンネルを開き直してください"
            }
        }
    }

    fun toggleReaction(messageId: Long, emoji: String) {
        viewModelScope.launch { repository.toggleReaction(messageId, emoji) }
    }

    fun editOwnMessage(messageId: Long, body: String) {
        viewModelScope.launch {
            if (!repository.editOwnMessage(messageId, body)) {
                aiError.value = "メッセージを編集できませんでした"
            }
        }
    }

    fun deleteOwnMessage(messageId: Long) {
        viewModelScope.launch {
            if (!repository.deleteOwnMessage(messageId)) {
                aiError.value = "返信があるメッセージは削除できません"
            }
        }
    }

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
    val integrationTransferBusy = MutableStateFlow(false)
    private val pendingImportedCredentials = ArrayDeque<IntegrationCredential>()
    /** Independent in-flight state per channel prevents rapid duplicate sends. */
    val busyChannelIds = MutableStateFlow<Set<Long>>(emptySet())
    val appUpdateState = MutableStateFlow(AppUpdateState())
    val aiProfiles = MutableStateFlow<List<AiProfile>>(emptyList())
    val aiProfileStatus = MutableStateFlow("接続先を保存するとAI一覧を取得できます")
    val aiProfileCanManage = MutableStateFlow(false)
    val aiProfileBusy = MutableStateFlow(false)
    val selectedAiChoice = MutableStateFlow("default")

    fun chooseAiForChannel(choice: String) {
        val id = currentChannel.value ?: return
        val config = connectionSettings.value
        aiChannelStore.set(config.endpoint, id, choice)
        selectedAiChoice.value = choice
    }

    fun refreshAiProfiles() {
        viewModelScope.launch {
            try {
                val (endpoint, token) = integrationAuth()
                val result = aiProfileClient.list(endpoint, token)
                // Do not display profiles fetched from a previous Relay endpoint.
                if (connectionStore.settings.first().endpoint.trimEnd('/') != endpoint) return@launch
                aiProfiles.value = result.profiles
                aiProfileCanManage.value = result.canManage
                aiProfileStatus.value = if (result.canManage)
                    "AIプロバイダー ${result.profiles.size} 件"
                else "登録機能を利用するにはRelayの暗号化キーを設定してください"
            } catch (error: Exception) {
                aiProfiles.value = emptyList()
                aiProfileCanManage.value = false
                aiProfileStatus.value = "AI一覧の取得に失敗：" +
                    (error.message ?: "Relayを更新して接続設定を確認してください")
            }
        }
    }

    fun createAiProfile(draft: AiProfileDraft) {
        viewModelScope.launch {
            aiProfileBusy.value = true
            try {
                val (endpoint, token) = integrationAuth()
                aiProfileClient.create(endpoint, token, draft)
                aiProfileStatus.value = "AI接続先を追加しました"
                refreshAiProfiles()
            } catch (error: Exception) {
                aiProfileStatus.value = "追加できませんでした：" + (error.message ?: "エラー")
            } finally {
                aiProfileBusy.value = false
            }
        }
    }

    fun updateAiProfile(id: String, draft: AiProfileDraft) {
        viewModelScope.launch {
            aiProfileBusy.value = true
            try {
                val (endpoint, token) = integrationAuth()
                aiProfileClient.update(endpoint, token, id, draft)
                aiProfileStatus.value = "AI接続先を更新しました"
                refreshAiProfiles()
            } catch (error: Exception) {
                aiProfileStatus.value = "更新できませんでした：" + (error.message ?: "エラー")
            } finally {
                aiProfileBusy.value = false
            }
        }
    }

    fun deleteAiProfile(id: String) {
        viewModelScope.launch {
            aiProfileBusy.value = true
            try {
                val (endpoint, token) = integrationAuth()
                aiProfileClient.delete(endpoint, token, id)
                aiProfileStatus.value = "AI接続先を削除しました"
                val channel = currentChannel.value
                if (channel != null && aiChannelStore.get(endpoint, channel) == id) {
                    aiChannelStore.set(endpoint, channel, "off")
                    selectedAiChoice.value = "off"
                }
                refreshAiProfiles()
            } catch (error: Exception) {
                aiProfileStatus.value = "削除できませんでした：" + (error.message ?: "エラー")
            } finally {
                aiProfileBusy.value = false
            }
        }
    }

    init {
        viewModelScope.launch { repository.bootstrap() }
    }

    fun selectChannel(id: Long) {
        currentChannel.value = id
        aiError.value = null
        selectedAiChoice.value = aiChannelStore.get(connectionSettings.value.endpoint, id)
        viewModelScope.launch {
            repository.markRead(id)
            val cfg = connectionStore.settings.first()
            if (currentChannel.value == id) {
                selectedAiChoice.value = aiChannelStore.get(cfg.endpoint, id)
            }
        }
        refreshAiProfiles()
    }

    fun leaveChannel() {
        currentChannel.value = null
    }

    fun updateNotification(messageId: Long, action: NotificationAction) {
        viewModelScope.launch { repository.updateNotification(messageId, action) }
    }

    fun bulkSetNotificationsRead(ids: List<Long>, read: Boolean) {
        if (ids.isEmpty()) return
        viewModelScope.launch { repository.bulkSetRead(ids, read) }
    }

    fun createChannel(name: String) {
        viewModelScope.launch { repository.createChannel(name) }
    }

    fun deleteChannel(id: Long) {
        if (currentChannel.value == id) leaveChannel()
        viewModelScope.launch { repository.deleteChannel(id) }
    }

    /**
     * Preserve the user's outgoing message even if a saved AI profile was
     * deleted or the gateway is offline. Network failures remain visible.
     */
    fun sendMessage(body: String) {
        val id = currentChannel.value ?: return
        if (id in busyChannelIds.value) return
        val cleaned = body.trim().take(8000)
        if (cleaned.isEmpty()) return
        busyChannelIds.value = busyChannelIds.value + id
        aiError.value = null
        viewModelScope.launch {
            var storedLocally = false
            try {
                val config = connectionStore.settings.first()
                val choice = aiChannelStore.get(config.endpoint, id)
                val profile = aiProfiles.value.firstOrNull { it.id == choice }
                val provider = when (choice) {
                    "off" -> "none"
                    "default" -> config.aiProvider
                    "legacy:openai" -> "openai"
                    "legacy:dify" -> "dify"
                    else -> profile?.kind ?: "missing"
                }
                val remoteEnabled = config.mode != "local" && provider !in setOf("none", "missing")
                val profileKey = if (choice == "default") "legacy:" + provider else choice
                val history = if (remoteEnabled) repository.chatHistory(id, profileKey)
                    else emptyList()
                // Never silently discard text when a remote profile cannot be reached.
                repository.sendMessage(id, cleaned, if (remoteEnabled) profileKey else "")
                storedLocally = true
                if (config.mode == "local" || provider == "none") return@launch
                if (provider == "missing") {
                    if (currentChannel.value == id) {
                        aiError.value = "文章は端末に保存しました。AI接続先を取得・選択し直してください"
                    }
                    return@launch
                }

                val token = secretStore.getToken()
                if (!RelayClient.validEndpoint(config.endpoint) || token == null) {
                    if (currentChannel.value == id) {
                        aiError.value = "文章は保存しました。接続先とアクセストークンを確認してください"
                    }
                    return@launch
                }
                val conversationKey = if (choice == "default") provider else choice
                val conversationId = if (provider == "dify")
                    conversationStore.get(config.endpoint, conversationKey, id) else ""
                val result = relayClient.sendChat(
                    config.endpoint, token, provider, cleaned, conversationId, history,
                    if (choice == "default") null else choice
                )
                val label = profile?.name ?: when (provider) {
                    "dify" -> "Dify"
                    "compatible" -> "OpenAI互換AI"
                    else -> "OpenAI"
                }
                val replyId = repository.addAiReply(id, label, result.answer, profileKey)
                if (currentChannel.value != id) {
                    val name = channels.value.firstOrNull { it.id == id }?.name ?: "AIチャット"
                    MessageNotifications.showAiReply(getApplication(), replyId, id, name, result.answer)
                }
                if (provider == "dify" && result.conversationId.isNotBlank()) {
                    conversationStore.save(config.endpoint, conversationKey, id, result.conversationId)
                }
                if (currentChannel.value == id) repository.markRead(id)
            } catch (error: Exception) {
                if (currentChannel.value == id) {
                    aiError.value = (if (storedLocally) "文章は保存済みです。AI応答の取得に失敗しました："
                        else "文章を端末に保存できませんでした：") +
                        (error.message ?: "エラー")
                }
            } finally {
                busyChannelIds.value = busyChannelIds.value - id
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            if (syncMutex.isLocked) return@launch
            syncMutex.withLock {
                syncStatus.value = "同期中…"
                try {
                    val newCount = RelayEventSync.perform(getApplication())
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
                refreshAiProfiles()
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
        keyword: String, feedUrl: String, pollIntervalMinutes: Int,
        cssSelector: String = ""
    ) {
        viewModelScope.launch {
            integrationStatus.value = "連携先を登録しています…"
            try {
                val (endpoint, token) = integrationAuth()
                newIntegrationCredential.value = integrationClient.create(
                    endpoint, token, name, provider, channelName, minScore, keyword,
                    feedUrl, pollIntervalMinutes, cssSelector
                )
                integrations.value = integrationClient.list(endpoint, token)
                integrationStatus.value = when (provider) {
                    "rss" -> "RSS連携を作成しました。「今すぐ取得」で記事を確認できます"
                    "website" -> "Web監視を作成しました。「今すぐ確認」で初回の基準を保存してください"
                    else -> "連携先を作成しました。専用トークンは今回だけ表示されます"
                }
            } catch (error: Exception) {
                integrationStatus.value = "登録失敗：" + (error.message ?: "通信エラー")
            }
        }
    }

    fun exportIntegrationSettings(uri: Uri) {
        if (integrationTransferBusy.value) return
        integrationTransferBusy.value = true
        viewModelScope.launch {
            try {
                val (endpoint, token) = integrationAuth()
                val data = integrationClient.exportJson(endpoint, token)
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val output = resolver.openOutputStream(uri, "w")
                        ?: error("保存先のファイルを開けません")
                    output.buffered().use { it.write(data.toByteArray(Charsets.UTF_8)) }
                }
                integrationStatus.value = "連携設定をJSONファイルに保存しました（トークンは含まれません）"
            } catch (error: Exception) {
                integrationStatus.value = "エクスポート失敗：" + (error.message ?: "ファイル保存エラー")
            } finally {
                integrationTransferBusy.value = false
            }
        }
    }

    fun importIntegrationSettings(uri: Uri) {
        if (integrationTransferBusy.value) return
        integrationTransferBusy.value = true
        viewModelScope.launch {
            try {
                val document = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val input = resolver.openInputStream(uri)
                        ?: error("設定ファイルを開けません")
                    val buffer = ByteArray(8192)
                    val result = ByteArrayOutputStream()
                    input.use { source ->
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            require(result.size() + count <= 256_000) {
                                "ファイルが大きすぎます（上限256KB）"
                            }
                            result.write(buffer, 0, count)
                        }
                    }
                    result.toString(Charsets.UTF_8.name())
                }
                val (endpoint, token) = integrationAuth()
                val summary = integrationClient.importJson(endpoint, token, document)
                // The server returns inbound tokens ONCE. Display them before another
                // network operation so a failed refresh cannot lose credentials.
                pendingImportedCredentials.addAll(summary.credentials)
                if (newIntegrationCredential.value == null && pendingImportedCredentials.isNotEmpty()) {
                    newIntegrationCredential.value = pendingImportedCredentials.removeFirst()
                }
                try {
                    integrations.value = integrationClient.list(endpoint, token)
                } catch (_: Exception) {
                    // Credentials remain visible; the list can be refreshed later.
                }
                integrationStatus.value = "インポート完了：新規${summary.createdCount}件、重複スキップ${summary.skippedCount}件。" +
                    if (summary.credentials.isNotEmpty()) "Webhookトークンをすべてコピーしてください。" else ""
            } catch (error: Exception) {
                integrationStatus.value = "インポート失敗：" + (error.message ?: "設定ファイルを確認してください")
            } finally {
                integrationTransferBusy.value = false
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
            integrationStatus.value = "Web/RSSの更新を確認しています…"
            try {
                val (endpoint, token) = integrationAuth()
                val count = integrationClient.refreshRss(endpoint, token, id)
                integrations.value = integrationClient.list(endpoint, token)
                integrationStatus.value = "確認完了：新規通知 $count 件（初回は基準の保存のみ）"
                if (count > 0) syncNow()
            } catch (error: Exception) {
                integrationStatus.value = "更新確認失敗：" + (error.message ?: "通信エラー")
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

    fun openAppUpdatePage() {
        val release = appUpdateState.value.release ?: return
        try {
            AppUpdater.openReleasePage(getApplication(), release)
            appUpdateState.value = appUpdateState.value.copy(
                message = "公式GitHub Releasesを開きました。APKの取得とインストールはAndroidの警告を確認して判断してください。"
            )
        } catch (error: Exception) {
            appUpdateState.value = appUpdateState.value.copy(
                message = "更新ページを開けません：" + (error.message ?: "ブラウザが利用できません")
            )
        }
    }

    fun closeCredential() {
        newIntegrationCredential.value = if (pendingImportedCredentials.isNotEmpty()) {
            pendingImportedCredentials.removeFirst()
        } else null
    }

}

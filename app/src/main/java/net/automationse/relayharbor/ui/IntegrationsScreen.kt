package net.automationse.relayharbor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.integration.IntegrationConnection
import net.automationse.relayharbor.integration.IntegrationCredential
import net.automationse.relayharbor.integration.LocalWebsiteChecker
import net.automationse.relayharbor.integration.LocalWebsiteRules
import net.automationse.relayharbor.integration.LocalWebsiteScheduler
import net.automationse.relayharbor.integration.LocalWebsiteStore
import net.automationse.relayharbor.integration.LocalWebsiteWatch
import java.text.DateFormat
import java.util.Date
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsScreen(
    endpoint: String,
    configured: Boolean,
    localChannels: List<ChannelEntity>,
    onCreateLocalChannel: (String) -> Unit,
    connections: List<IntegrationConnection>,
    status: String,
    credential: IntegrationCredential?,
    transferBusy: Boolean,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onRefresh: () -> Unit,
    onAdd: (String, String, String, Int, String, String, Int, String) -> Unit,
    onEdit: (IntegrationConnection) -> Unit,
    onTest: (String) -> Unit,
    onRefreshFeed: (String) -> Unit,
    onRotate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismissCredential: () -> Unit
) {
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExport) }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImport) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var createOpen by rememberSaveable { mutableStateOf(false) }
    var openRelayWebsiteWizard by remember { mutableStateOf(false) }
    // Credentials are never stored in the editor draft.
    LaunchedEffect(credential?.connection?.id) {
        if (credential != null) {
            IntegrationDraftStore.clear(context)
            createOpen = false
            openRelayWebsiteWizard = false
        }
    }
    var editing by remember { mutableStateOf<IntegrationConnection?>(null) }
    var deleting by remember { mutableStateOf<IntegrationConnection?>(null) }
    var rotating by remember { mutableStateOf<IntegrationConnection?>(null) }
    val uiStore = remember(context) { WorkspaceUiStore(context) }
    var connectionQuery by rememberSaveable {
        mutableStateOf(uiStore.integrationSearch())
    }
    var connectionType by rememberSaveable {
        mutableStateOf(uiStore.integrationListFilter())
    }
    var cardMode by rememberSaveable { mutableStateOf(uiStore.integrationCardLayout()) }
    val localStore = remember(context) { LocalWebsiteStore(context) }
    var localWatches by remember(context) { mutableStateOf(localStore.list()) }
    var localCreateOpen by remember { mutableStateOf(false) }
    var localEditing by remember { mutableStateOf<LocalWebsiteWatch?>(null) }
    var localCheckingId by remember { mutableStateOf<String?>(null) }
    var localFeedback by remember { mutableStateOf("") }
    val localScope = rememberCoroutineScope()
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("local_website_watch_v1", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key.startsWith("watch:")) {
                localWatches = localStore.list()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun saveLocal(watch: LocalWebsiteWatch?, form: LocalWebsiteForm): String? =
        try {
            require(localChannels.any { it.id == form.channelId }) {
                "通知先チャンネルを選び直してください"
            }
            val saved = localStore.save(
                watch?.id, form.name, form.url, form.channelId,
                form.interval, form.selector, form.keyword, watch?.enabled ?: true
            )
            LocalWebsiteScheduler.schedule(context, saved)
            localWatches = localStore.list()
            localFeedback = if (watch == null) {
                "端末内監視を登録しました。初回の「今すぐ確認」で監視基準を保存できます。"
            } else "端末内監視を更新しました"
            null
        } catch (error: Exception) {
            error.message ?: "監視設定を保存できませんでした"
        }

    fun toggleConnection(connection: IntegrationConnection, enabled: Boolean) {
        val localId = IntegrationListPolicy.localWatchId(connection)
        if (localId == null) {
            onEdit(connection.copy(enabled = enabled))
        } else {
            val watch = localStore.get(localId) ?: return
            try {
                val saved = localStore.save(
                    watch.id, watch.name, watch.url, watch.channelId,
                    watch.intervalMinutes, watch.selector, watch.keyword, enabled
                )
                LocalWebsiteScheduler.schedule(context, saved)
                localWatches = localStore.list()
            } catch (error: Exception) {
                localFeedback = "切替失敗: " + (error.message ?: "不明なエラー")
            }
        }
    }

    fun editConnection(connection: IntegrationConnection) {
        val localId = IntegrationListPolicy.localWatchId(connection)
        if (localId == null) editing = connection
        else {
            localEditing = localStore.get(localId)
            if (localEditing == null) localFeedback = "監視設定が見つかりません"
        }
    }

    fun checkConnection(connection: IntegrationConnection) {
        val localId = IntegrationListPolicy.localWatchId(connection)
        if (localId == null) {
            if (IntegrationListPolicy.isPullSource(connection)) onRefreshFeed(connection.id)
            else onTest(connection.id)
        } else if (localCheckingId == null) {
            localCheckingId = localId
            localScope.launch {
                try {
                    localFeedback = LocalWebsiteChecker.check(
                        context, localId, force = true
                    ).detail
                } catch (error: Exception) {
                    localFeedback = "確認失敗: " + (error.message ?: "通信エラー")
                } finally {
                    localWatches = localStore.list()
                    localCheckingId = null
                }
            }
        }
    }

    val allConnections = connections + localWatches.map {
        IntegrationListPolicy.fromLocal(it, localChannels)
    }
    val visibleConnections = allConnections.filter {
        IntegrationListPolicy.matches(it, connectionType, connectionQuery)
    }.sortedBy { it.name.lowercase() }
    val baseUrl = endpoint.trim().trimEnd('/')

    Scaffold(topBar = { TopAppBar(title = { Text("サービス連携") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("連携先をまとめて管理", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(
                "Android通知と端末内Web監視はサーバー不要。Webhook・RSS・Relay監視は接続先を設定すると利用できます。",
                style = MaterialTheme.typography.bodyMedium
            )
            LocalWebsiteMonitorSection(
                relayAvailable = configured,
                localAvailable = localChannels.isNotEmpty() &&
                    localWatches.size < LocalWebsiteRules.MAX_WATCHES,
                onAddLocal = { localCreateOpen = true },
                onOpenRelayWebsite = {
                    openRelayWebsiteWizard = true
                    createOpen = true
                }
            )
            AndroidAppNotificationSettingsCard(
                channels = localChannels,
                onCreateChannel = onCreateLocalChannel
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(status, color = MaterialTheme.colorScheme.secondary)
                    if (!configured) Text(
                        "設定タブでHTTPS接続先とRelayアクセストークンを登録してください。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { createOpen = true }, enabled = configured && !transferBusy) {
                            Text("＋ ガイド付きで追加")
                        }
                        TextButton(onClick = onRefresh, enabled = configured && !transferBusy) {
                            Text("更新")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                exportPicker.launch("relayharbor-integrations.json")
                            },
                            enabled = configured && !transferBusy
                        ) { Text("設定をエクスポート") }
                        TextButton(
                            onClick = {
                                importPicker.launch(arrayOf("application/json", "text/plain"))
                            },
                            enabled = configured && !transferBusy
                        ) { Text("設定をインポート") }
                    }
                    Text(
                        if (transferBusy) "連携設定の転送中です…"
                        else "連携名・通知先・RSS/Web監視設定をJSONで移行。認証トークンは含めません。Webhookは新しいトークンを発行します。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedTextField(
                value = connectionQuery,
                onValueChange = {
                    connectionQuery = it
                    uiStore.saveIntegrationSearch(it)
                },
                label = { Text("連携先を検索") },
                trailingIcon = {
                    if (connectionQuery.isNotEmpty()) {
                        TextButton(onClick = {
                            connectionQuery = ""
                            uiStore.saveIntegrationSearch("")
                        }) { Text("消去") }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("all" to "すべて", "rss" to "RSS",
                    "website" to "Web監視", "webhook" to "Webhook",
                    "active" to "有効").forEach { (type, label) ->
                    FilterChip(
                        selected = connectionType == type,
                        onClick = {
                            connectionType = type
                            uiStore.saveIntegrationListFilter(type)
                        },
                        label = { Text(label) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "カードの大きさ",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = cardMode == "normal",
                    onClick = {
                        cardMode = "normal"
                        uiStore.saveIntegrationCardLayout("normal")
                    },
                    label = { Text("通常") }
                )
                FilterChip(
                    selected = cardMode == "compact",
                    onClick = {
                        cardMode = "compact"
                        uiStore.saveIntegrationCardLayout("compact")
                    },
                    label = { Text("小さく") }
                )
            }
            Text(
                "表示 ${visibleConnections.size} / ${connections.size} 件",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (visibleConnections.isEmpty()) Text(
                if (connections.isEmpty()) "連携先はまだ登録されていません。"
                else "条件に合う連携先がありません。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            visibleConnections.forEach { connection ->
                if (cardMode == "compact") {
                    var menuOpen by remember(connection.id) { mutableStateOf(false) }
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(
                                horizontal = 12.dp, vertical = 6.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    connection.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${when (connection.provider) { "rss" -> "RSS"; "website" -> "Web監視"; "github_actions" -> "GitHub"; else -> "Webhook" }} · #${connection.channelName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (connection.lastError.isNotBlank()) {
                                    Text(
                                        "取得エラーあり",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Switch(
                                checked = connection.enabled,
                                onCheckedChange = { onEdit(connection.copy(enabled = it)) }
                            )
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "${connection.name} の操作"
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("編集・詳細") },
                                        onClick = { menuOpen = false; editing = connection }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("URLをコピー") },
                                        onClick = {
                                            menuOpen = false
                                            val url = if (connection.provider in setOf("rss", "website"))
                                                connection.feedUrl
                                            else baseUrl + connection.path
                                            clipboard.setText(AnnotatedString(url))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (connection.provider in setOf("rss", "website")) "今すぐ確認"
                                                else "テスト通知"
                                            )
                                        },
                                        enabled = connection.enabled,
                                        onClick = {
                                            menuOpen = false
                                            if (connection.provider in setOf("rss", "website")) onRefreshFeed(connection.id)
                                            else onTest(connection.id)
                                        }
                                    )
                                    if (connection.provider !in setOf("rss", "website")) {
                                        DropdownMenuItem(
                                            text = { Text("鍵更新") },
                                            onClick = { menuOpen = false; rotating = connection }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("削除") },
                                        onClick = { menuOpen = false; deleting = connection }
                                    )
                                }
                            }
                        }
                    }
                } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(connection.name, fontWeight = FontWeight.Bold)
                                Text(when (connection.provider) {
                                    "rss" -> "RSS / Atom フィード"
                                    "website" -> "RSS不要・Webページ更新監視"
                                    "github_actions" -> "GitHub Actions JSON Webhook"
                                    else -> "カスタムWebhook"
                                }, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = connection.enabled,
                                onCheckedChange = { onEdit(connection.copy(enabled = it)) }
                            )
                        }
                        Text("チャンネル: #${connection.channelName}")
                        if (connection.provider in setOf("rss", "website")) {
                            Text(connection.feedUrl, style = MaterialTheme.typography.bodySmall)
                            if (connection.provider == "website") Text(
                                "監視範囲: " + connection.cssSelector.ifBlank { "ページの本文全体" },
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("取得間隔: ${connection.pollIntervalMinutes}分 / キーワード: ${connection.keyword.ifBlank { "なし" }}",
                                style = MaterialTheme.typography.bodySmall)
                            if (connection.lastSuccess > 0) Text(
                                "最終取得: ${DateFormat.getDateTimeInstance().format(Date(connection.lastSuccess))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (connection.lastError.isNotBlank()) Text(
                                "取得エラー: ${connection.lastError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(connection.feedUrl))
                                }) { Text("URLコピー") }
                                TextButton(onClick = { onRefreshFeed(connection.id) },
                                    enabled = connection.enabled) { Text(if (connection.provider == "website") "今すぐ確認" else "今すぐ取得") }
                            }
                        } else {
                            Text("最低評価スコア: ${connection.minScore} / キーワード: ${connection.keyword.ifBlank { "なし" }}",
                                style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(baseUrl + connection.path))
                                }) { Text("URLコピー") }
                                TextButton(onClick = { onTest(connection.id) },
                                    enabled = connection.enabled) { Text("テスト通知") }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editing = connection }) { Text("編集") }
                            if (connection.provider !in setOf("rss", "website")) {
                                TextButton(onClick = { rotating = connection }) { Text("鍵更新") }
                            }
                            TextButton(onClick = { deleting = connection }) { Text("削除") }
                        }
                    }
                }
                }
            }
            HorizontalDivider()
            Text("RSSとWeb更新監視は公開HTTPSサイトに対応（15分〜24時間間隔）。Web監視の初回は基準保存のみ。JavaScript生成やログイン必須のページは対象外です。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (createOpen) {
        IntegrationSetupWizard(
            status = status,
            initialTemplateKey = if (openRelayWebsiteWizard) "website" else null,
            onDismiss = { createOpen = false; openRelayWebsiteWizard = false },
            onDiscard = {
                IntegrationDraftStore.clear(context)
                createOpen = false
                openRelayWebsiteWizard = false
            },
            onCreate = { name, provider, channel, score, keyword, feedUrl, interval, selector ->
                onAdd(name, provider, channel, score, keyword, feedUrl, interval, selector)
            }
        )
    }
    editing?.let { original ->
        ConnectionEditorDialog(
            initial = original,
            onDismiss = { editing = null },
            onSave = { name, _, channel, score, keyword, feedUrl, interval, selector ->
                onEdit(original.copy(
                    name = name, channelName = channel,
                    minScore = score, keyword = keyword,
                    feedUrl = feedUrl, pollIntervalMinutes = interval, cssSelector = selector
                ))
                editing = null
            }
        )
    }
    deleting?.let { connection ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("連携先を削除しますか？") },
            text = { Text("「${connection.name}」の連携を削除します。受信済み通知は残ります。") },
            confirmButton = {
                Button(onClick = { onDelete(connection.id); deleting = null }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("キャンセル") }
            }
        )
    }
    rotating?.let { connection ->
        AlertDialog(
            onDismissRequest = { rotating = null },
            title = { Text("トークンを再発行しますか？") },
            text = { Text("旧トークンは直ちに無効になります。外部の送信元も再設定してください。") },
            confirmButton = {
                Button(onClick = { onRotate(connection.id); rotating = null }) { Text("再発行") }
            },
            dismissButton = {
                TextButton(onClick = { rotating = null }) { Text("キャンセル") }
            }
        )
    }
    credential?.let { created ->
        var tokenCopied by remember(created.connection.id, created.token) {
            mutableStateOf(false)
        }
        var testRequested by remember(created.connection.id) { mutableStateOf(false) }
        AlertDialog(
            // Do not silently discard a freshly created one-time token.
            onDismissRequest = {},
            title = { Text(when (created.connection.provider) {
                "rss" -> "RSSを登録しました"
                "website" -> "Web監視を登録しました"
                else -> "連携を作成しました"
            }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (created.connection.provider in setOf("rss", "website")) {
                        Text(if (created.connection.provider == "website")
                            "登録完了。初回確認は現在のページを基準として保存し、通知しません。"
                            else "登録が完了しました。以下のボタンから記事取得をテストできます。")
                        Text(created.connection.feedUrl, style = MaterialTheme.typography.bodySmall)
                        Text("間隔: ${created.connection.pollIntervalMinutes}分",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        val url = baseUrl + created.connection.path
                        Text("トークンは今回だけ表示されます。必ず安全に保管してください。")
                        Text("Webhook URL", fontWeight = FontWeight.Bold)
                        Text(url, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(url))
                        }) { Text("URLをコピー") }
                        Text("Bearer Token", fontWeight = FontWeight.Bold)
                        Text("••••••••••••••（再表示できません）",
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(created.token))
                            tokenCopied = true
                        }) { Text(if (tokenCopied) "コピー済み" else "専用トークンをコピー") }
                        Text(
                            "URLとトークンを送信元のHTTP POST設定に登録してください。トークンは再表示できません。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(
                        enabled = created.token.isBlank() || tokenCopied,
                        onClick = {
                            testRequested = true
                            if (created.connection.provider in setOf("rss", "website"))
                                onRefreshFeed(created.connection.id)
                            else onTest(created.connection.id)
                        }
                    ) {
                        Text(when (created.connection.provider) {
                            "rss" -> "今すぐ記事を取得"
                            "website" -> "今すぐ確認・基準を保存"
                            else -> "テスト通知を送信"
                        })
                    }
                    if (testRequested) {
                        Text(
                            "テストを依頼しました。結果は連携先の状態やチャット画面で確認してください。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismissCredential,
                    enabled = created.token.isBlank() || tokenCopied
                ) {
                    Text(if (created.token.isBlank()) "完了" else "コピー済み・次へ")
                }
            }
        )
    }
}

@Composable
private fun ConnectionEditorDialog(
    initial: IntegrationConnection?,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit = onDismiss,
    onSave: (String, String, String, Int, String, String, Int, String) -> Unit
) {
    val context = LocalContext.current
    val savedDraft = remember(initial?.id) {
        if (initial == null) IntegrationDraftStore.load(context) else null
    }
    var provider by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.provider ?: savedDraft?.optString("provider", "webhook") ?: "webhook")
    }
    var name by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.name ?: savedDraft?.optString("name", "新しい連携") ?: "新しい連携")
    }
    var channel by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.channelName ?: savedDraft?.optString("channel", "Webhook") ?: "Webhook")
    }
    var minScore by rememberSaveable(initial?.id) {
        mutableStateOf((initial?.minScore ?: savedDraft?.optInt("minScore", 0) ?: 0).toFloat())
    }
    var keyword by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.keyword ?: savedDraft?.optString("keyword", "") ?: "")
    }
    var feedUrl by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.feedUrl ?: savedDraft?.optString("feedUrl", "") ?: "")
    }
    var interval by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.pollIntervalMinutes ?: savedDraft?.optInt("interval", 30) ?: 30)
    }
    var cssSelector by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.cssSelector ?: savedDraft?.optString("cssSelector", "") ?: "")
    }
    LaunchedEffect(initial?.id, provider, name, channel, minScore, keyword, feedUrl, interval, cssSelector) {
        if (initial == null) {
            IntegrationDraftStore.save(context, provider, name, channel,
                minScore.toInt(), keyword, feedUrl, interval, cssSelector = cssSelector)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "連携先を追加" else "連携先を編集") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial == null) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(selected = provider == "webhook",
                            onClick = {
                                provider = "webhook"; name = "新しい連携"; channel = "Webhook"
                            }, label = { Text("Webhook") })
                        FilterChip(selected = provider == "github_actions",
                            onClick = {
                                provider = "github_actions"; name = "GitHub Actions"; channel = "GitHub"
                            }, label = { Text("GitHub") })
                        FilterChip(selected = provider == "rss",
                            onClick = {
                                provider = "rss"; name = "RSSニュース"; channel = "RSS"
                            }, label = { Text("RSS") })
                        FilterChip(selected = provider == "website",
                            onClick = {
                                provider = "website"; name = "サイト更新"; channel = "Web更新"
                            }, label = { Text("Web監視") })
                    }
                }
                if (initial == null) Text("入力内容は自動保存されます。閉じても次回再開できます。",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) },
                    label = { Text("連携名") }, singleLine = true
                )
                OutlinedTextField(
                    value = channel, onValueChange = { channel = it.take(60) },
                    label = { Text("通知チャンネル") }, singleLine = true
                )
                if (provider in setOf("rss", "website")) {
                    OutlinedTextField(
                        value = feedUrl, onValueChange = { feedUrl = it.take(2048) },
                        label = { Text(if (provider == "rss") "RSS / Atom URL（HTTPS）"
                            else "監視するWebページURL（HTTPS）") },
                        placeholder = { Text(if (provider == "rss")
                            "https://example.com/feed.xml" else "https://example.com/news") },
                        singleLine = true
                    )
                    if (provider == "website") {
                        OutlinedTextField(
                            value = cssSelector,
                            onValueChange = { cssSelector = it.take(200) },
                            label = { Text("監視する範囲（CSSセレクター・任意）") },
                            placeholder = { Text("例: main, #news, .updates") },
                            singleLine = true
                        )
                        Text("省略するとページ本文を監視。広告・時計などで誤通知が出る場合は、更新欄を指定してください。初回は通知しません。",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("取得間隔: ${interval}分")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 60).forEach { minutes ->
                            FilterChip(selected = interval == minutes,
                                onClick = { interval = minutes },
                                label = { Text("${minutes}分") })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(180, 360, 720).forEach { minutes ->
                            FilterChip(selected = interval == minutes,
                                onClick = { interval = minutes },
                                label = { Text("${minutes / 60}時間") })
                        }
                    }
                    if (provider == "rss") Text("初回は最新3件、以降は新着記事のみ通知します。",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("最低評価スコア: ${minScore.toInt()}（0 = 無制限）")
                    Slider(value = minScore, onValueChange = { minScore = it },
                        valueRange = 0f..100f)
                }
                OutlinedTextField(
                    value = keyword, onValueChange = { keyword = it.take(100) },
                    label = { Text("キーワード（任意）") }, singleLine = true
                )
                if (provider == "rss") Text(
                    "キーワードがある場合、記事のタイトルまたは概要に一致するものだけ通知します。",
                    style = MaterialTheme.typography.bodySmall
                )
                if (provider == "website") Text(
                    "キーワードを指定すると、更新後の監視範囲にその文字列がある場合のみ通知します。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && channel.isNotBlank() &&
                    (provider !in setOf("rss", "website") || feedUrl.trim().startsWith("https://")),
                onClick = {
                    onSave(name, provider, channel,
                        if (provider in setOf("rss", "website")) 0 else minScore.toInt(),
                        keyword, feedUrl, interval, cssSelector)
                }
            ) { Text(if (initial == null) "作成" else "保存") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (initial == null) TextButton(onClick = onDiscard) { Text("下書きを破棄") }
                TextButton(onClick = onDismiss) {
                    Text(if (initial == null) "閉じる" else "キャンセル")
                }
            }
        }
    )
}

/** Stores only non-secret connection editor fields; never credentials or access tokens. */
internal object IntegrationDraftStore {
    private const val PREFS = "integration_editor_draft"
    private const val KEY = "create_draft_json"

    fun load(context: Context): JSONObject? = runCatching {
        val data = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        JSONObject(data)
    }.getOrNull()

    fun save(context: Context, provider: String, name: String, channel: String,
             minScore: Int, keyword: String, feedUrl: String, interval: Int,
             cssSelector: String = "",
             templateKey: String = "webhook", wizardStep: Int = 0) {
        val json = JSONObject()
            .put("provider", provider)
            .put("name", name)
            .put("channel", channel)
            .put("minScore", minScore)
            .put("keyword", keyword)
            .put("feedUrl", feedUrl)
            .put("cssSelector", cssSelector)
            .put("interval", interval)
            .put("templateKey", templateKey)
            .put("wizardStep", wizardStep)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

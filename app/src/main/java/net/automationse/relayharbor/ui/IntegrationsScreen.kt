package net.automationse.relayharbor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.integration.IntegrationConnection
import net.automationse.relayharbor.integration.IntegrationCredential
import java.text.DateFormat
import java.util.Date
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsScreen(
    endpoint: String,
    configured: Boolean,
    connections: List<IntegrationConnection>,
    status: String,
    credential: IntegrationCredential?,
    transferBusy: Boolean,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onRefresh: () -> Unit,
    onAdd: (String, String, String, Int, String, String, Int) -> Unit,
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
    // Credentials are never stored in the editor draft.
    LaunchedEffect(credential?.connection?.id) {
        if (credential != null) IntegrationDraftStore.clear(context)
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
    val visibleConnections = connections.filter { connection ->
        (connectionQuery.isBlank() ||
            connection.name.contains(connectionQuery, ignoreCase = true) ||
            connection.channelName.contains(connectionQuery, ignoreCase = true) ||
            connection.keyword.contains(connectionQuery, ignoreCase = true) ||
            connection.feedUrl.contains(connectionQuery, ignoreCase = true)) &&
            when (connectionType) {
                "rss" -> connection.provider == "rss"
                "webhook" -> connection.provider != "rss"
                "active" -> connection.enabled
                else -> true
            }
    }
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
                "Webhookは専用URLで受信し、RSSはフィードURLから記事を定期取得します。",
                style = MaterialTheme.typography.bodyMedium
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
                            Text("＋ 連携先を追加")
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
                        else "JSONで連携名・通知先・フィルター・RSS設定を移行。認証トークンは含めません。インポートでは新規Webhookのトークンを再発行します。",
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
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "すべて", "rss" to "RSS",
                    "webhook" to "Webhook", "active" to "有効").forEach { (type, label) ->
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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(connection.name, fontWeight = FontWeight.Bold)
                                Text(when (connection.provider) {
                                    "rss" -> "RSS / Atom フィード"
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
                        if (connection.provider == "rss") {
                            Text(connection.feedUrl, style = MaterialTheme.typography.bodySmall)
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
                                    enabled = connection.enabled) { Text("今すぐ取得") }
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
                            if (connection.provider != "rss") {
                                TextButton(onClick = { rotating = connection }) { Text("鍵更新") }
                            }
                            TextButton(onClick = { deleting = connection }) { Text("削除") }
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("RSSは公開HTTPSフィードに対応（15分〜24時間間隔）。新着記事のタイトル・概要・元記事リンクを表示します。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (createOpen) {
        ConnectionEditorDialog(
            initial = null,
            onDismiss = { createOpen = false },
            onDiscard = { IntegrationDraftStore.clear(context); createOpen = false },
            onSave = { name, provider, channel, score, keyword, feedUrl, interval ->
                onAdd(name, provider, channel, score, keyword, feedUrl, interval)
                createOpen = false
            }
        )
    }
    editing?.let { original ->
        ConnectionEditorDialog(
            initial = original,
            onDismiss = { editing = null },
            onSave = { name, _, channel, score, keyword, feedUrl, interval ->
                onEdit(original.copy(
                    name = name, channelName = channel,
                    minScore = score, keyword = keyword,
                    feedUrl = feedUrl, pollIntervalMinutes = interval
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
        AlertDialog(
            // Do not silently discard a freshly created one-time token.
            onDismissRequest = {},
            title = { Text(if (created.connection.provider == "rss") "RSSを登録しました" else "連携を作成しました") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (created.connection.provider == "rss") {
                        Text("登録が完了しました。「今すぐ取得」で最新記事を確認できます。")
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
    onSave: (String, String, String, Int, String, String, Int) -> Unit
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
    LaunchedEffect(initial?.id, provider, name, channel, minScore, keyword, feedUrl, interval) {
        if (initial == null) {
            IntegrationDraftStore.save(context, provider, name, channel,
                minScore.toInt(), keyword, feedUrl, interval)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "連携先を追加" else "連携先を編集") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                if (provider == "rss") {
                    OutlinedTextField(
                        value = feedUrl, onValueChange = { feedUrl = it.take(2048) },
                        label = { Text("RSS / Atom URL（HTTPS）") },
                        placeholder = { Text("https://example.com/feed.xml") },
                        singleLine = true
                    )
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
                    Text("初回は最新3件、以降は新着記事のみ通知します。",
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
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && channel.isNotBlank() &&
                    (provider != "rss" || feedUrl.trim().startsWith("https://")),
                onClick = {
                    onSave(name, provider, channel,
                        if (provider == "rss") 0 else minScore.toInt(),
                        keyword, feedUrl, interval)
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
private object IntegrationDraftStore {
    private const val PREFS = "integration_editor_draft"
    private const val KEY = "create_draft_json"

    fun load(context: Context): JSONObject? = runCatching {
        val data = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        JSONObject(data)
    }.getOrNull()

    fun save(context: Context, provider: String, name: String, channel: String,
             minScore: Int, keyword: String, feedUrl: String, interval: Int) {
        val json = JSONObject()
            .put("provider", provider)
            .put("name", name)
            .put("channel", channel)
            .put("minScore", minScore)
            .put("keyword", keyword)
            .put("feedUrl", feedUrl)
            .put("interval", interval)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}

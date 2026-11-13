package net.automationse.relayharbor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.net.URI

/** These are guides/templates, not new backend provider types or OAuth integrations. */
internal data class IntegrationWizardPreset(
    val key: String,
    val title: String,
    val description: String,
    val provider: String,
    val suggestedName: String,
    val suggestedChannel: String,
    val setupGuide: String
)

internal val integrationWizardPresets = listOf(
    IntegrationWizardPreset(
        "webhook", "汎用Webhook", "監視ツールや自作スクリプトから通知する",
        "webhook", "新しい連携", "Webhook",
        "登録後の専用URLに、送信元からAuthorization: Bearer とJSON本文をPOSTします。"
    ),
    IntegrationWizardPreset(
        "rss", "RSS / Atom", "記事の新着を自動取得する（送信元の設定不要）",
        "rss", "RSSニュース", "RSS",
        "公開HTTPSフィードのURLを設定します。初回は最新3件、以降は新着記事を取り込みます。"
    ),
    IntegrationWizardPreset(
        "github", "GitHub Actions", "Actionsのワークフローから通知する",
        "github_actions", "GitHub Actions", "GitHub",
        "ネイティブのGitHub App連携ではありません。Actions側のHTTP POST処理に発行された専用URLとBearerトークンを設定します。"
    ),
    IntegrationWizardPreset(
        "dify", "Dify ワークフロー", "HTTPリクエストノードから結果を送る",
        "webhook", "Dify通知", "AI",
        "DifyのHTTPリクエストノードで、発行されたURLにJSONをPOSTし、AuthorizationヘッダーにBearerトークンを設定します。DifyのAPIキーはRelayHarborに渡しません。"
    ),
    IntegrationWizardPreset(
        "n8n", "n8n ワークフロー", "HTTP Requestノードから結果を送る",
        "webhook", "n8n通知", "自動化",
        "n8nのHTTP RequestノードでPOST / JSON / Authorization: Bearerを設定します。これはWebhook送信の設定ガイドで、n8nアカウントとのOAuth接続ではありません。"
    ),
    IntegrationWizardPreset(
        "scout", "Lancers AI Scout", "案件評価の結果をカードとして受け取る",
        "webhook", "Lancers AI Scout", "AI案件スカウト",
        "ScoutのRELAYHARBOR_INBOUND_URL / RELAYHARBOR_INBOUND_TOKENに発行値を設定し、ALERT_MODE=slack,relayで並行通知できます。既存Slackの通知先は無効化しないでください。"
    )
)

/** Local form validation. Network reachability and RSS redirects are validated on the Relay. */
internal object IntegrationWizardRules {
    fun validBasics(name: String, channel: String): Boolean =
        name.trim().length in 1..60 && channel.trim().length in 1..60

    fun validRssUrl(raw: String): Boolean {
        if (raw.isBlank() || raw.length > 2048 || raw.any { it.code < 32 || it.code == 127 }) return false
        return runCatching {
            val uri = URI(raw.trim())
            val host = uri.host?.lowercase()?.trimEnd('.') ?: return@runCatching false
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.rawUserInfo == null && uri.rawFragment == null &&
                uri.port in listOf(-1, 443) &&
                host.contains('.') && !host.startsWith('.') && !host.contains("..") &&
                !host.all { it.isDigit() || it == '.' } &&
                !listOf(".local", ".localhost", ".internal", ".home.arpa",
                    ".test", ".invalid").any { host.endsWith(it) } &&
                host != "localhost"
        }.getOrDefault(false)
    }

    fun validForReview(
        name: String, channel: String, provider: String, feedUrl: String
    ): Boolean = validBasics(name, channel) &&
        (provider != "rss" || validRssUrl(feedUrl))
}

/** Four-step guided setup; saved drafts contain no access credentials. */
@Composable
internal fun IntegrationSetupWizard(
    status: String,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onCreate: (String, String, String, Int, String, String, Int) -> Unit
) {
    val context = LocalContext.current
    val draft = remember { IntegrationDraftStore.load(context) }
    val savedKey = draft?.optString("templateKey").orEmpty()
    val fallbackKey = when (draft?.optString("provider")) {
        "rss" -> "rss"
        "github_actions" -> "github"
        else -> "webhook"
    }
    var templateKey by rememberSaveable {
        mutableStateOf(
            savedKey.takeIf { key -> integrationWizardPresets.any { it.key == key } }
                ?: fallbackKey
        )
    }
    var step by rememberSaveable {
        mutableStateOf((draft?.optInt("wizardStep") ?: 0).coerceIn(0, 3))
    }
    var name by rememberSaveable {
        mutableStateOf(draft?.optString("name", "").orEmpty())
    }
    var channel by rememberSaveable {
        mutableStateOf(draft?.optString("channel", "").orEmpty())
    }
    var minScore by rememberSaveable {
        mutableStateOf((draft?.optInt("minScore") ?: 0).coerceIn(0, 100))
    }
    var keyword by rememberSaveable {
        mutableStateOf(draft?.optString("keyword", "").orEmpty())
    }
    var feedUrl by rememberSaveable {
        mutableStateOf(draft?.optString("feedUrl", "").orEmpty())
    }
    var interval by rememberSaveable {
        mutableStateOf((draft?.optInt("interval") ?: 30).coerceIn(15, 1440))
    }
    var submitting by rememberSaveable { mutableStateOf(false) }
    val preset = integrationWizardPresets.first { it.key == templateKey }
    val canAdvance = IntegrationWizardRules.validForReview(
        name, channel, preset.provider, feedUrl
    )

    LaunchedEffect(templateKey, step, name, channel, minScore, keyword, feedUrl, interval) {
        IntegrationDraftStore.save(
            context, preset.provider, name, channel, minScore, keyword, feedUrl, interval,
            templateKey = templateKey, wizardStep = step
        )
    }
    LaunchedEffect(status) {
        if (status.startsWith("登録失敗")) submitting = false
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("連携先を登録")
                Text(
                    "ステップ " + (step + 1) + " / 4  ·  " +
                        listOf("サービスを選択", "基本情報", "通知設定", "内容を確認")[step],
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    0 -> {
                        Text("何から通知を受け取りますか？", fontWeight = FontWeight.SemiBold)
                        Text(
                            "サービス名を選ぶだけで設定項目と登録後の手順を案内します。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        integrationWizardPresets.forEach { option ->
                            Card(
                                Modifier.fillMaxWidth().clickable {
                                    templateKey = option.key
                                    name = option.suggestedName
                                    channel = option.suggestedChannel
                                    if (option.provider == "rss") minScore = 0
                                }
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(9.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RadioButton(
                                        selected = templateKey == option.key,
                                        onClick = {
                                            templateKey = option.key
                                            name = option.suggestedName
                                            channel = option.suggestedChannel
                                            if (option.provider == "rss") minScore = 0
                                        }
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(option.title, fontWeight = FontWeight.SemiBold)
                                        Text(option.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        Text(preset.title, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(60) },
                            label = { Text("連携名（必須）") },
                            supportingText = { Text("連携先一覧に表示される名前です") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = channel,
                            onValueChange = { channel = it.take(60) },
                            label = { Text("通知先チャンネル（必須）") },
                            supportingText = { Text("例：AI案件スカウト、GitHub、RSS") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (preset.provider == "rss") {
                            OutlinedTextField(
                                value = feedUrl,
                                onValueChange = { feedUrl = it.take(2048) },
                                label = { Text("RSS / Atom URL（HTTPS）") },
                                placeholder = { Text("https://example.com/feed.xml") },
                                isError = feedUrl.isNotBlank() &&
                                    !IntegrationWizardRules.validRssUrl(feedUrl),
                                supportingText = {
                                    Text("公開HTTPSフィードのURLを入力してください。LAN内のURLやHTTPは利用できません。")
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                "専用Webhook URLとトークンは登録後に発行されます。ここでトークンを入力する必要はありません。",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!canAdvance) Text(
                            "連携名・チャンネル名と、必要に応じて有効なRSS URLを入力してください。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    2 -> {
                        Text("通知の条件を指定", fontWeight = FontWeight.SemiBold)
                        if (preset.provider == "rss") {
                            Text("RSSの取得間隔: " + interval + "分")
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                listOf(listOf(15, 30, 60), listOf(180, 360, 720), listOf(1440))
                                    .forEach { choices ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            choices.forEach { minutes ->
                                                FilterChip(
                                                    selected = interval == minutes,
                                                    onClick = { interval = minutes },
                                                    label = {
                                                        Text(if (minutes < 60) {
                                                            minutes.toString() + "分"
                                                        } else {
                                                            (minutes / 60).toString() + "時間"
                                                        })
                                                    }
                                                )
                                            }
                                        }
                                    }
                            }
                        } else {
                            Text("最低評価スコア: " + minScore + "（0なら制限なし）")
                            Slider(
                                value = minScore.toFloat(),
                                onValueChange = { minScore = it.toInt() },
                                valueRange = 0f..100f
                            )
                            Text(
                                "スコアのない通知も受け取る場合は0を選択します。",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = { keyword = it.take(100) },
                            label = { Text("キーワード（任意）") },
                            supportingText = {
                                Text("未入力なら全件受信。入力すると該当する通知のみ表示します。")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {
                        Text("登録内容を確認してください", fontWeight = FontWeight.SemiBold)
                        Text("種類: " + preset.title)
                        Text("連携名: " + name.trim())
                        Text("通知先: #" + channel.trim())
                        if (preset.provider == "rss") {
                            Text("フィード: " + feedUrl.trim())
                            Text("取得間隔: " + interval + "分")
                        } else {
                            Text("最低評価スコア: " + minScore)
                        }
                        Text("キーワード: " + keyword.trim().ifBlank { "指定なし" })
                        Text("登録後の設定", fontWeight = FontWeight.SemiBold)
                        Text(preset.setupGuide, style = MaterialTheme.typography.bodySmall)
                        if (preset.provider != "rss") {
                            Text(
                                "URLとトークンは作成後に一度だけ表示されます。両方を送信側に設定し、テスト通知で確認します。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (status.startsWith("登録失敗")) {
                            Text(status, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (step < 3) Text(
                    "入力内容は端末内に自動保存します。あとから続きの設定が可能です。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting && (step != 1 && step != 3 || canAdvance),
                onClick = {
                    if (step < 3) {
                        step += 1
                    } else {
                        submitting = true
                        onCreate(
                            name.trim(), preset.provider, channel.trim(),
                            if (preset.provider == "rss") 0 else minScore,
                            keyword.trim(), if (preset.provider == "rss") feedUrl.trim() else "",
                            interval
                        )
                    }
                }
            ) { Text(if (submitting) "登録中…" else if (step == 3) "連携先を作成" else "次へ") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                if (step == 0) {
                    TextButton(onClick = onDiscard) { Text("下書きを破棄") }
                } else {
                    TextButton(
                        onClick = { if (!submitting) step -= 1 },
                        enabled = !submitting
                    ) { Text("戻る") }
                }
                TextButton(onClick = onDismiss, enabled = !submitting) {
                    Text("閉じる")
                }
            }
        }
    )
}

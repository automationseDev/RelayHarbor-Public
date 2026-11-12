package net.automationse.relayharbor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.data.ConnectionSettings
import net.automationse.relayharbor.integration.RelayClient
import net.automationse.relayharbor.integration.AppUpdater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: ConnectionSettings,
    error: String?,
    tokenConfigured: Boolean,
    syncStatus: String,
    onSave: (String, String, String, String) -> Unit,
    onClearToken: () -> Unit,
    onSync: () -> Unit,
    updateState: AppUpdateState,
    onCheckUpdate: () -> Unit,
    onOpenUpdatePage: () -> Unit
) {
    var mode by remember(settings.mode) { mutableStateOf(settings.mode) }
    var endpoint by remember(settings.endpoint) { mutableStateOf(settings.endpoint) }
    var ai by remember(settings.aiProvider) { mutableStateOf(settings.aiProvider) }
    var token by remember { mutableStateOf("") }
    val valid = mode == "local" || RelayClient.validEndpoint(endpoint)
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("接続と設定") }) }) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("接続モード", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("local" to "端末内", "cloud" to "クラウド", "selfhost" to "自前サーバー").forEach { (value, label) ->
                    FilterChip(
                        selected = mode == value,
                        onClick = { mode = value },
                        label = { Text(label) }
                    )
                }
            }
            Text(
                "端末内ではチャットをローカル保存します。外部連携にはクラウドまたは自前サーバーを選び、RelayHarbor RelayをHTTPSで公開してください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (mode != "local") {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("RelayサーバーのHTTPS URL") },
                    placeholder = { Text("https://relay.example.com") },
                    singleLine = true,
                    isError = !valid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!valid) Text("https:// の接続先を入力してください",
                    color = MaterialTheme.colorScheme.error)

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Relayアクセストークン") },
                    placeholder = { Text(if (tokenConfigured) "登録済み（変更するときだけ入力）" else "32文字以上") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (tokenConfigured) "アクセストークンはAndroid Keystoreで暗号化保存されています。"
                    else "アクセストークンが未登録です。サーバーのRELAY_CLIENT_TOKENを入力してください。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HorizontalDivider()
            Text("AIチャット", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Column {
                listOf("none" to "利用しない", "dify" to "Dify", "openai" to "OpenAI").forEach { (value, label) ->
                    FilterChip(
                        selected = ai == value,
                        onClick = { ai = value },
                        label = { Text(label) }
                    )
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RelayHarbor v" + AppUpdater.installedVersion(context), fontWeight = FontWeight.SemiBold)
                    Text("AIのAPIキーはアプリではなく中継サーバーの環境変数で管理します。チャットでの送信時に選択したAIへ接続します。",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Webhook通知はサーバーに保存され、アプリ使用中に約30秒間隔で取得します。バックグラウンドの即時Push通知には未対応です。",
                        style = MaterialTheme.typography.bodySmall)
                    Text(syncStatus, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("アプリのアップデート",
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("現在のバージョン：v" + AppUpdater.installedVersion(context))
                    Text(updateState.message, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary)
                    Button(
                        onClick = onCheckUpdate,
                        enabled = !updateState.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("最新バージョンを確認") }
                    updateState.release?.let { release ->
                        Text("利用可能：${release.version}",
                            style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = onOpenUpdatePage,
                            enabled = !updateState.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("公式リリースページを開く")
                        }
                    }
                    Text(
                        "GitHub Releasesで最新版を確認できます。アプリはAPKのダウンロードやインストールを実行しません。有害アプリ警告が出た場合はインストールを中止してください。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            Button(
                enabled = valid,
                onClick = {
                    onSave(mode, endpoint, ai, token)
                    token = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("接続設定を保存") }
            if (mode != "local") {
                Button(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
                    Text("Webhook通知を今すぐ同期")
                }
                if (tokenConfigured) {
                    TextButton(onClick = onClearToken) { Text("アクセストークンを削除") }
                }
            }
        }
    }
}

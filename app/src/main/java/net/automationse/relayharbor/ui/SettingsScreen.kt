package net.automationse.relayharbor.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.data.ConnectionSettings
import net.automationse.relayharbor.integration.RelayClient
import net.automationse.relayharbor.integration.AppUpdater
import net.automationse.relayharbor.notification.MessageNotifications
import net.automationse.relayharbor.notification.NotificationPreferences
import net.automationse.relayharbor.notification.NotificationSyncScheduler

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
    foregroundSyncSeconds: Int,
    onForegroundSyncSecondsChange: (Int) -> Unit,
    updateState: AppUpdateState,
    onCheckUpdate: () -> Unit,
    onOpenUpdatePage: () -> Unit,
    resumeLastPage: Boolean,
    onResumeLastPageChange: (Boolean) -> Unit,
    showHomeActivity: Boolean,
    onShowHomeActivityChange: (Boolean) -> Unit,
    showHomeChannels: Boolean,
    onShowHomeChannelsChange: (Boolean) -> Unit
) {
    var mode by remember(settings.mode) { mutableStateOf(settings.mode) }
    var endpoint by remember(settings.endpoint) { mutableStateOf(settings.endpoint) }
    var ai by remember(settings.aiProvider) { mutableStateOf(settings.aiProvider) }
    var token by remember { mutableStateOf("") }
    val valid = mode == "local" || RelayClient.validEndpoint(endpoint)
    val context = LocalContext.current
    val notificationPrefs = remember(context) { NotificationPreferences(context) }
    var notificationsEnabled by remember { mutableStateOf(notificationPrefs.enabled()) }
    var backgroundEnabled by remember { mutableStateOf(notificationPrefs.backgroundEnabled()) }
    var notificationPermission by remember {
        mutableStateOf(MessageNotifications.hasPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationPermission = MessageNotifications.hasPermission(context)
        NotificationSyncScheduler.refresh(context)
    }

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
            HorizontalDivider()
            Text(
                "新着メッセージ通知",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Androidの通知バーに表示")
                            Text("Webhook・Botの新着通知とチャット返信に対応",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = notificationsEnabled, onCheckedChange = { enabled ->
                            notificationsEnabled = enabled
                            notificationPrefs.setEnabled(enabled)
                            NotificationSyncScheduler.refresh(context)
                            if (enabled && !MessageNotifications.hasPermission(context) &&
                                Build.VERSION.SDK_INT >= 33
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        })
                    }
                    if (notificationsEnabled && !notificationPermission) {
                        Text(
                            "Android側の通知権限が未許可です。通知を有効にしてください。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (Build.VERSION.SDK_INT >= 33) {
                            TextButton(onClick = {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) { Text("通知の許可をリクエスト") }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("アプリを閉じている間も定期的に確認")
                            Text("Android WorkManagerを使用。約15分以上の間隔で、バッテリー最適化などで遅れることがあります。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = backgroundEnabled,
                            onCheckedChange = {
                                backgroundEnabled = it
                                notificationPrefs.setBackgroundEnabled(it)
                                NotificationSyncScheduler.refresh(context)
                            }
                        )
                    }
                    Text(
                        "アプリ画面を表示している間の同期間隔",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            30 to "30秒",
                            60 to "1分",
                            120 to "2分",
                            300 to "5分"
                        ).forEach { (seconds, label) ->
                            FilterChip(
                                selected = foregroundSyncSeconds == seconds,
                                onClick = { onForegroundSyncSecondsChange(seconds) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text(
                        "ホームアプリとして使う場合は1〜2分がおすすめです。画面非表示中は上記の短い間隔で同期せず、バックグラウンド定期確認へ切り替わります。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "即時プッシュ（FCM）は未設定です。アプリ表示中は指定間隔で、非表示時はAndroidによる15分以上の定期確認を利用します（遅延する場合があります）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { MessageNotifications.openAppSettings(context) }) {
                        Text("Androidの通知音・振動などを設定")
                    }
                    Text(
                        "個別チャンネルの通知ON/OFFと通知音・振動は「チャット」→各チャンネルの︙から設定できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider()
            DefaultHomeSettings()
            Text(
                "表示設定",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("前回開いた画面から再開")
                            Text("次回起動時に最後のタブやチャットを復元します。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = resumeLastPage, onCheckedChange = onResumeLastPageChange)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ホームに最近の通知を表示", modifier = Modifier.weight(1f))
                        Switch(
                            checked = showHomeActivity,
                            onCheckedChange = onShowHomeActivityChange
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ホームにチャンネル一覧を表示", modifier = Modifier.weight(1f))
                        Switch(
                            checked = showHomeChannels,
                            onCheckedChange = onShowHomeChannelsChange
                        )
                    }
                    Text(
                        "アプリの配置・検索条件・表示切替も端末内に保存されます。アカウントや認証情報には影響しません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RelayHarbor v" + AppUpdater.installedVersion(context), fontWeight = FontWeight.SemiBold)
                    Text("AIのAPIキーはアプリではなく中継サーバーの環境変数で管理します。チャットでの送信時に選択したAIへ接続します。",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Webhook通知はサーバーに保存され、アプリ使用中は約30秒ごとに取得します。バックグラウンドは15分以上の定期同期を利用できます。",
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

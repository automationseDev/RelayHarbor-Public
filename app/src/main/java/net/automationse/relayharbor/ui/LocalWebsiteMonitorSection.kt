package net.automationse.relayharbor.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.integration.LocalWebsiteChecker
import net.automationse.relayharbor.integration.LocalWebsiteRules
import net.automationse.relayharbor.integration.LocalWebsiteScheduler
import net.automationse.relayharbor.integration.LocalWebsiteStore
import net.automationse.relayharbor.integration.LocalWebsiteWatch
import net.automationse.relayharbor.notification.MessageNotifications
import java.text.DateFormat
import java.util.Date

/** Usable in local mode without a Relay endpoint or credentials. */
@Composable
internal fun LocalWebsiteMonitorSection(
    channels: List<ChannelEntity>,
    onCreateChannel: (String) -> Unit,
    relayAvailable: Boolean,
    onOpenRelayWebsite: () -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { LocalWebsiteStore(context) }
    val scope = rememberCoroutineScope()
    var watches by remember(context) { mutableStateOf(store.list()) }
    var createOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LocalWebsiteWatch?>(null) }
    var deleting by remember { mutableStateOf<LocalWebsiteWatch?>(null) }
    var checkingId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("local_website_watch_v1", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key.startsWith("watch:")) watches = store.list()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun save(watch: LocalWebsiteWatch?, input: LocalWebsiteForm): String? {
        return try {
            require(channels.any { it.id == input.channelId }) {
                "通知先チャンネルを選び直してください"
            }
            val item = store.save(
                watch?.id, input.name, input.url,
                input.channelId, input.interval, input.selector, input.keyword,
                watch?.enabled ?: true
            )
            LocalWebsiteScheduler.schedule(context, item)
            watches = store.list()
            status = "端末内監視を保存しました。初回チェックで現在のページを基準にします。"
            null
        } catch (e: Exception) { e.message ?: "保存できませんでした" }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("🌐 Webサイト更新監視", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text("RSSがないサイトも監視。用途に応じて監視方式を選べます。",
                style = MaterialTheme.typography.bodySmall)
            Card {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📱 端末内監視 — サーバー不要", fontWeight = FontWeight.SemiBold)
                    Text("AndroidのWorkManagerで定期チェック。最短15分、スリープ・省電力時は遅延する場合があります。",
                        style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { createOpen = true },
                        enabled = channels.isNotEmpty() && watches.size < LocalWebsiteRules.MAX_WATCHES
                    ) { Text("＋ 端末内監視を追加") }
                    if (channels.isEmpty()) {
                        Text("通知先チャンネルを先に作成してください。",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Card {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🖥️ Relay監視 — サーバーで継続", fontWeight = FontWeight.SemiBold)
                    Text("NAS・VPSで監視し、スマホの電池を使いません。Relay v0.6.0以上が必要です。",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onOpenRelayWebsite, enabled = relayAvailable) {
                        Text("Relay側のWeb監視を追加")
                    }
                    if (!relayAvailable) Text(
                        "Relay側を利用するには設定タブでサーバー接続を登録してください。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text("端末内監視 " + watches.size + "/" + LocalWebsiteRules.MAX_WATCHES + " 件",
                style = MaterialTheme.typography.labelMedium)
            if (!MessageNotifications.hasPermission(context)) Text(
                "Androidの通知権限がないため、更新はチャットに保存されますがOS通知は表示されません。",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
            )
            if (status.isNotBlank()) Text(status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary)
            watches.forEach { watch ->
                val channelName = channels.firstOrNull { it.id == watch.channelId }?.name
                    ?: "削除済みチャンネル"
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(watch.name, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("📱 端末内 · #" + channelName,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(checked = watch.enabled, onCheckedChange = { enabled ->
                                try {
                                    val saved = store.save(
                                        watch.id, watch.name, watch.url, watch.channelId,
                                        watch.intervalMinutes, watch.selector, watch.keyword, enabled
                                    )
                                    LocalWebsiteScheduler.schedule(context, saved)
                                    watches = store.list()
                                } catch (e: Exception) {
                                    status = "変更失敗: " + (e.message ?: "不明なエラー")
                                }
                            })
                        }
                        Text(watch.url, style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("間隔: " + watch.intervalMinutes + "分 · 監視範囲: " +
                            watch.selector.ifBlank { "本文全体" },
                            style = MaterialTheme.typography.bodySmall)
                        if (watch.keyword.isNotBlank()) Text("キーワード: " + watch.keyword,
                            style = MaterialTheme.typography.bodySmall)
                        Text(
                            when {
                                watch.lastError.isNotBlank() -> "取得エラー: " + watch.lastError
                                watch.initialized -> "基準を保存済み"
                                else -> "初回の基準取得待ち"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (watch.lastError.isNotBlank()) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (watch.lastSuccess > 0) Text(
                            "最終成功: " + DateFormat.getDateTimeInstance()
                                .format(Date(watch.lastSuccess)),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                enabled = watch.enabled && checkingId == null,
                                onClick = {
                                    checkingId = watch.id
                                    scope.launch {
                                        try {
                                            val result = LocalWebsiteChecker.check(
                                                context, watch.id, force = true
                                            )
                                            status = result.detail
                                        } catch (e: Exception) {
                                            status = e.message ?: "確認できませんでした"
                                        } finally {
                                            watches = store.list()
                                            checkingId = null
                                        }
                                    }
                                }
                            ) { Text(if (checkingId == watch.id) "確認中…" else "今すぐ確認") }
                            TextButton(onClick = { editing = watch }) { Text("編集") }
                            TextButton(onClick = { deleting = watch }) { Text("削除") }
                        }
                    }
                }
            }
            Text("初回は基準保存のみ。HTML本文（最大1MB）を比較します。ログイン・JavaScript描画には未対応。監視内容は端末内に保存し、Relayへ送信しません。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (createOpen || editing != null) {
        LocalWebsiteEditor(
            watch = editing,
            channels = channels,
            onCreateChannel = onCreateChannel,
            onSave = { form ->
                save(editing, form).also { error ->
                    if (error == null) {
                        createOpen = false
                        editing = null
                    }
                }
            },
            onDismiss = { createOpen = false; editing = null }
        )
    }
    deleting?.let { watch ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("端末内監視を削除") },
            text = { Text("「" + watch.name + "」を削除します。過去のチャット履歴は残ります。") },
            confirmButton = {
                Button(onClick = {
                    store.remove(watch.id)
                    LocalWebsiteScheduler.cancel(context, watch.id)
                    watches = store.list()
                    deleting = null
                    status = "監視設定を削除しました"
                }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル") } }
        )
    }
}

internal data class LocalWebsiteForm(
    val name: String,
    val url: String,
    val channelId: Long,
    val interval: Int,
    val selector: String,
    val keyword: String
)

@Composable
internal fun LocalWebsiteEditor(
    watch: LocalWebsiteWatch?,
    channels: List<ChannelEntity>,
    onCreateChannel: (String) -> Unit,
    onSave: (LocalWebsiteForm) -> String?,
    onDismiss: () -> Unit
) {
    var name by remember(watch?.id) { mutableStateOf(watch?.name ?: "サイト更新監視") }
    var url by remember(watch?.id) { mutableStateOf(watch?.url.orEmpty()) }
    var selector by remember(watch?.id) { mutableStateOf(watch?.selector.orEmpty()) }
    var keyword by remember(watch?.id) { mutableStateOf(watch?.keyword.orEmpty()) }
    var interval by remember(watch?.id) { mutableStateOf(watch?.intervalMinutes ?: 30) }
    var channelId by remember(watch?.id) {
        mutableStateOf(watch?.channelId ?: channels.firstOrNull()?.id ?: 0L)
    }
    var channelMenu by remember { mutableStateOf(false) }
    var newChannel by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (watch == null) "端末内のWeb監視を追加" else "端末内のWeb監視を編集") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 490.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("サーバー不要。省電力中は指定間隔より遅れることがあります。",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(), value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("監視名") }, singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(), value = url,
                    onValueChange = { url = it.take(2048) },
                    label = { Text("公開WebページURL（HTTPS）") },
                    placeholder = { Text("https://example.com/news") },
                    singleLine = true,
                    isError = url.isNotBlank() && !LocalWebsiteRules.validUrl(url)
                )
                Text("通知先チャンネル", style = MaterialTheme.typography.labelMedium)
                Box {
                    TextButton(onClick = { channelMenu = true }) {
                        Text(channels.firstOrNull { it.id == channelId }
                            ?.let { it.emoji + " #" + it.name + " ▾" } ?: "選択してください ▾")
                    }
                    DropdownMenu(expanded = channelMenu, onDismissRequest = { channelMenu = false }) {
                        channels.forEach { channel ->
                            DropdownMenuItem(
                                text = { Text(channel.emoji + " #" + channel.name) },
                                onClick = { channelId = channel.id; channelMenu = false }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f), value = newChannel,
                        onValueChange = { newChannel = it.take(60) },
                        label = { Text("新しいチャンネル") }, singleLine = true
                    )
                    TextButton(enabled = newChannel.isNotBlank(), onClick = {
                        onCreateChannel(newChannel)
                        newChannel = ""
                        error = "チャンネル作成後、上の通知先一覧から選んでください"
                    }) { Text("追加") }
                }
                Text("取得間隔: " + interval + "分")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    LocalWebsiteRules.intervals.forEach { mins ->
                        FilterChip(
                            selected = interval == mins,
                            onClick = { interval = mins },
                            label = { Text(if (mins < 60) mins.toString() + "分"
                                else (mins / 60).toString() + "時間") }
                        )
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(), value = selector,
                    onValueChange = { selector = it.take(200) },
                    label = { Text("監視範囲（CSSセレクター・任意）") },
                    placeholder = { Text("main, #news, .updates") }, singleLine = true
                )
                Text("広告・時計による誤通知を避けるため、変更欄のみの指定を推奨します。",
                    style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(), value = keyword,
                    onValueChange = { keyword = it.take(100) },
                    label = { Text("通知キーワード（任意）") }, singleLine = true
                )
                if (error.isNotBlank()) Text(error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && LocalWebsiteRules.validUrl(url) &&
                    channelId > 0L,
                onClick = {
                    error = onSave(
                        LocalWebsiteForm(name, url.trim(), channelId, interval, selector, keyword)
                    ).orEmpty()
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

package net.automationse.relayharbor.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.integration.LocalWebsiteRules
import net.automationse.relayharbor.integration.LocalWebsiteWatch

/** Registration choices only. Saved watches appear in the shared integration list. */
@Composable
internal fun LocalWebsiteMonitorSection(
    relayAvailable: Boolean,
    localAvailable: Boolean,
    onAddLocal: () -> Unit,
    onOpenRelayWebsite: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🌐 Webサイト更新監視を追加",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text(
                "RSSのないサイトも監視できます。登録した監視は、下の連携一覧で他の通知とまとめて管理できます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAddLocal,
                    enabled = localAvailable
                ) { Text("📱 端末内") }
                TextButton(
                    onClick = onOpenRelayWebsite,
                    enabled = relayAvailable
                ) { Text("🖥️ Relayサーバー") }
            }
            Text(
                "端末内はサーバー不要・15分以上（省電力時は遅延あり）。RelayはNAS/VPSで継続監視します。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

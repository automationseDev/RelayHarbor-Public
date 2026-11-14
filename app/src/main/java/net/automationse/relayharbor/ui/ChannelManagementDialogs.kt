package net.automationse.relayharbor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.data.ChannelOrganizationRules
import net.automationse.relayharbor.notification.NotificationPriorityPolicy

private val availableEmojis = listOf("💬", "🔔", "🤖", "🧠", "📰", "🛠️", "🖥️", "📅", "📌", "📊", "⭐", "🎮", "💼", "🚀", "🧩")

@Composable
internal fun ChannelAppearanceDialog(
    channel: ChannelEntity,
    onSave: (alias: String, emoji: String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember(channel.id) { mutableStateOf(channel.name) }
    var emoji by remember(channel.id) { mutableStateOf(channel.emoji) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("チャンネルの表示設定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Webhook・RSSの受信先名は変えず、アプリに表示する名前とアイコンだけを変更します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(60) },
                    label = { Text("表示名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = emoji, onValueChange = { emoji = it.take(12) },
                    label = { Text("アイコン（絵文字）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("アイコンの候補", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableEmojis.forEach { choice ->
                        FilterChip(
                            selected = emoji == choice,
                            onClick = { emoji = choice },
                            label = { Text(choice) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(ChannelOrganizationRules.cleanLabel(label),
                        ChannelOrganizationRules.cleanEmoji(emoji))
                },
                enabled = label.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave("", ChannelOrganizationRules.cleanEmoji(emoji)) }) {
                    Text("表示名を元に戻す")
                }
                TextButton(onClick = onDismiss) { Text("キャンセル") }
            }
        }
    )
}

@Composable
internal fun ChannelFolderAssignDialog(
    channelName: String,
    current: String,
    folders: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("フォルダーに分類") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(channelName, style = MaterialTheme.typography.bodySmall)
                (listOf("" to "未分類") + folders.map { it to "📁 $it" }).forEach { (id, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(id) }.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label)
                        if (current == id) Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (folders.isEmpty()) {
                    Text("先に「フォルダー管理」で新しいフォルダーを作成してください。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

@Composable
internal fun ChannelFolderManagerDialog(
    folders: List<String>,
    onCreate: (String) -> Boolean,
    onRename: (String, String) -> Boolean,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var changedName by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("フォルダー管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("フォルダーを作成・名前変更・削除できます。削除してもチャンネルと履歴は残ります。",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = newName, onValueChange = { newName = it.take(32) },
                        singleLine = true, label = { Text("新しいフォルダー") }
                    )
                    TextButton(
                        onClick = {
                            if (onCreate(newName)) { newName = ""; error = "" }
                            else error = "同じ名前があるか、作成上限に達しました"
                        },
                        enabled = newName.isNotBlank()
                    ) { Text("追加") }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 270.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    folders.forEach { folder ->
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("📁 $folder", modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                            TextButton(onClick = {
                                editing = folder; changedName = folder
                            }) { Text("変更") }
                            TextButton(onClick = { confirmRemove = folder }) { Text("削除") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完了") } }
    )
    editing?.let { current ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("フォルダー名を変更") },
            text = {
                OutlinedTextField(
                    value = changedName, onValueChange = { changedName = it.take(32) },
                    singleLine = true, label = { Text("フォルダー名") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onRename(current, changedName)) editing = null
                    else error = "同じ名前を使用できません"
                }, enabled = changedName.isNotBlank()) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("キャンセル") } }
        )
    }
    confirmRemove?.let { folder ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("フォルダーを削除") },
            text = { Text("「$folder」を削除します。チャンネルと履歴は削除せず、未分類に戻します。") },
            confirmButton = {
                TextButton(onClick = { onRemove(folder); confirmRemove = null }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("キャンセル") } }
        )
    }
}

@Composable
internal fun ChannelPriorityDialog(
    channelName: String,
    current: String,
    onSet: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通知の優先度") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(channelName, style = MaterialTheme.typography.titleSmall)
                Text("変更後の通知に適用します。音・振動はAndroidの通知設定でも変更できます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    Triple(NotificationPriorityPolicy.HIGH, "高", "重要な通知を強調"),
                    Triple(NotificationPriorityPolicy.NORMAL, "通常", "標準の通知"),
                    Triple(NotificationPriorityPolicy.SILENT, "サイレント", "音を鳴らさず静かに通知")
                ).forEach { (id, label, description) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSet(id) }.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text(description, style = MaterialTheme.typography.labelSmall)
                        }
                        if (current == id) Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

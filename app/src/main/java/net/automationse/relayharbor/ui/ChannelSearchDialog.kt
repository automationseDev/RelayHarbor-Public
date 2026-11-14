package net.automationse.relayharbor.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.data.MessageEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelSearchDialog(
    channels: List<ChannelEntity>,
    results: List<MessageEntity>,
    busy: Boolean,
    onQuery: (String) -> Unit,
    onOpenChannel: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(query) { onQuery(query) }
    DisposableEffect(Unit) { onDispose { onQuery("") } }
    val names = remember(channels) { channels.associateBy { it.id } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("メッセージを横断検索") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "検索を閉じる")
                        }
                    }
                )
            }
        ) { inner ->
            Column(
                modifier = Modifier.fillMaxSize().padding(inner),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    value = query,
                    onValueChange = { query = it.take(100) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("すべてのチャンネルの本文・送信者・カードを検索") }
                )
                Text(
                    when {
                        query.trim().length < 2 -> "2文字以上で検索できます。検索は端末内だけで実行されます。"
                        busy -> "検索しています…"
                        else -> "検索結果 " + results.size + " 件（最大100件）"
                    },
                    modifier = Modifier.padding(horizontal = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                ) {
                    if (query.trim().length >= 2 && !busy && results.isEmpty()) {
                        item {
                            Text("一致するメッセージはありません。",
                                modifier = Modifier.padding(20.dp),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (query.trim().length >= 2 && !busy) {
                        items(results, key = { it.id }) { message ->
                            val channel = names[message.channelId]
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        onOpenChannel(message.channelId)
                                        onDismiss()
                                    }.padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text((channel?.emoji ?: "💬") + "  # " +
                                        (channel?.name ?: "削除済みチャンネル"),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(DateFormat.format("MM/dd HH:mm", message.timestamp).toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(message.sender, style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium)
                                Text(
                                    message.text.ifBlank {
                                        if (message.cardJson != null) "リッチカードの通知"
                                        else "テキストなし"
                                    }.replace('\n', ' '),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

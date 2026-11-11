package net.automationse.relayharbor.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.data.CardCodec
import net.automationse.relayharbor.data.MessageEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    name: String,
    messages: List<MessageEntity>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onDemo: () -> Unit,
    sending: Boolean = false,
    error: String? = null
) {
    var draft by remember { mutableStateOf("") }
    var openAddMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("# " + name, maxLines = 1, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        Text(if (sending) "AIが応答を作成中…" else "端末内の会話・通知", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box {
                            FilledTonalIconButton(onClick = { openAddMenu = true }) {
                                Icon(Icons.Default.Add, contentDescription = "追加メニュー")
                            }
                            DropdownMenu(expanded = openAddMenu, onDismissRequest = { openAddMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("デモ評価カードを追加") },
                                    onClick = {
                                        openAddMenu = false
                                        onDemo()
                                    }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            placeholder = { Text("メッセージを入力…") },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp)
                        )
                        FilledIconButton(
                            enabled = draft.isNotBlank() && !sending,
                            onClick = {
                                onSend(draft)
                                draft = ""
                            }
                        ) {
                            if (sending) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Send, contentDescription = "送信")
                            }
                        }
                    }
                }
            }
        }
    ) { inner ->
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text("ここから会話を始めましょう",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp, end = 14.dp, top = 14.dp, bottom = 22.dp
                ),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxSize().padding(inner)
            ) {
                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                    val previous = messages.getOrNull(index - 1)
                    val currentDate = DateFormat.format("yyyy/MM/dd", message.timestamp).toString()
                    val previousDate = previous?.let {
                        DateFormat.format("yyyy/MM/dd", it.timestamp).toString()
                    }
                    if (currentDate != previousDate) {
                        DateSeparator(currentDate)
                    }
                    val grouped = previous != null &&
                        previous.sender == message.sender &&
                        previous.fromMe == message.fromMe &&
                        previousDate == currentDate &&
                        (message.timestamp - previous.timestamp) in 0L..180000L
                    MessageTimelineItem(message, grouped)
                }
            }
        }
    }
}

@Composable
private fun DateSeparator(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MessageTimelineItem(message: MessageEntity, grouped: Boolean) {
    if (message.fromMe) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp,
                    bottomEnd = if (grouped) 18.dp else 5.dp
                ),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!grouped) {
                        Text("自分  ·  " + DateFormat.format("HH:mm", message.timestamp).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(message.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (grouped) {
                Spacer(Modifier.width(36.dp))
            } else {
                Surface(shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Text(message.sender.take(1).uppercase(), fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!grouped) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message.sender, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text(DateFormat.format("HH:mm", message.timestamp).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (message.text.isNotBlank()) {
                    Text(message.text, style = MaterialTheme.typography.bodyMedium)
                }
                if (message.kind == "card" && message.cardJson != null) {
                    val card = remember(message.cardJson) { CardCodec.decode(message.cardJson) }
                    if (card == null) {
                        Text("カードデータを表示できません",
                            style = MaterialTheme.typography.labelSmall)
                    } else {
                        RichCardView(card)
                    }
                }
            }
        }
    }
}

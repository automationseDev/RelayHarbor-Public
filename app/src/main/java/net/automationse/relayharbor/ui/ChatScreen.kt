package net.automationse.relayharbor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.data.ChatConversationRules
import net.automationse.relayharbor.data.ChatProductivityRules
import net.automationse.relayharbor.data.ChatDraftStore
import net.automationse.relayharbor.data.CardCodec
import net.automationse.relayharbor.data.MessageEntity
import net.automationse.relayharbor.integration.AiProfile
import net.automationse.relayharbor.data.MessageReactionEntity
import net.automationse.relayharbor.data.NotificationAction

/**
 * Slack-like local conversation UX. Thread replies and reactions are device-only.
 * They never trigger an external Webhook or AI API implicitly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    channelId: Long,
    name: String,
    messages: List<MessageEntity>,
    reactions: List<MessageReactionEntity>,
    aiProfiles: List<AiProfile>,
    aiChoice: String,
    aiDefault: String,
    aiOnline: Boolean,
    onChooseAi: (String) -> Unit,
    channelTopic: String,
    channelDescription: String,
    onSaveChannelDetails: (String, String) -> Unit,
    onSendThreadReply: (Long, String) -> Unit,
    onToggleReaction: (Long, String) -> Unit,
    onEditMessage: (Long, String) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onDemo: () -> Unit,
    onNotificationAction: (Long, NotificationAction) -> Unit,
    sending: Boolean = false,
    error: String? = null
) {
    val context = LocalContext.current
    val draftStore = remember(context) { ChatDraftStore(context) }
    var activeThreadId by rememberSaveable(channelId) { mutableStateOf<Long?>(null) }
    BackHandler(enabled = activeThreadId != null) { activeThreadId = null }
    val threadRoot = activeThreadId?.let { id ->
        messages.firstOrNull { it.id == id && it.parentMessageId == null }
    }
    LaunchedEffect(activeThreadId, messages.size) {
        if (activeThreadId != null && threadRoot == null && messages.isNotEmpty()) {
            activeThreadId = null
        }
    }
    val draftKey = threadRoot?.let { -it.id } ?: channelId
    var draft by remember(draftKey) { mutableStateOf(draftStore.load(draftKey)) }
    var openAddMenu by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable(channelId) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(channelId) { mutableStateOf("") }
    var searchFilter by rememberSaveable(channelId) { mutableStateOf("all") }
    BackHandler(enabled = activeThreadId == null && searchOpen) { searchOpen = false }
    var aiPickerOpen by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var editingMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var deletingMessage by remember { mutableStateOf<MessageEntity?>(null) }

    val visibleMessages = remember(messages, threadRoot?.id, searchOpen, searchQuery, searchFilter) {
        ChatProductivityRules.visible(
            messages, threadRoot?.id, if (searchOpen) searchQuery else "",
            if (searchOpen) searchFilter else "all"
        )
    }
    // Pre-computed once per Room snapshot, not for every rendered chat bubble.
    val replyCounts = remember(messages) {
        messages.mapNotNull { it.parentMessageId }.groupingBy { it }.eachCount()
    }
    val reactionGroups = remember(reactions) {
        reactions.groupBy({ it.messageId }, { it.emoji })
    }
    val replySearchMatches = remember(messages, searchQuery, searchOpen) {
        if (!searchOpen || searchQuery.isBlank()) emptySet<Long>()
        else messages.asSequence().filter {
            it.parentMessageId != null &&
                ChatProductivityRules.matches(it, searchQuery.trim())
        }.mapNotNull { it.parentMessageId }.toSet()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(visibleMessages.lastOrNull()?.id, activeThreadId, searchOpen, searchQuery, searchFilter) {
        if (visibleMessages.isNotEmpty()) {
            val filtered = searchOpen && threadRoot == null &&
                (searchQuery.isNotBlank() || searchFilter != "all")
            listState.scrollToItem(if (filtered) 0 else visibleMessages.lastIndex)
        }
    }
    fun putDraft(value: String) {
        draft = value.take(8000)
        draftStore.save(draftKey, draft)
    }

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Column {
                        if (threadRoot == null) {
                            TextButton(onClick = { showDetails = true }) {
                                Text("# " + name, maxLines = 1, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium)
                            }
                        } else {
                            Text("スレッド", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                        }
                        Text(if (threadRoot != null) "元メッセージと返信"
                            else if (sending) "AIが応答を作成中…"
                            else "端末内の会話・通知",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (threadRoot != null) activeThreadId = null
                        else if (searchOpen) searchOpen = false else onBack()
                    }) { Icon(Icons.Default.ArrowBack, contentDescription = "戻る") }
                },
                actions = {
                    if (threadRoot == null) {
                        IconButton(onClick = { searchOpen = !searchOpen }) {
                            Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (searchOpen) "検索を閉じる" else "チャンネル内を検索")
                        }
                        IconButton(onClick = { showDetails = true }) {
                            Icon(Icons.Default.Info, contentDescription = "チャンネル詳細")
                        }
                    }
                }
            )
            if (threadRoot == null && searchOpen) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.take(100) },
                    label = { Text("このチャンネルのメッセージ・返信を検索") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "検索語をクリア")
                            }
                        }
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "all" to "すべて",
                        "unread" to "未読",
                        "pinned" to "固定",
                        "later" to "あとで"
                    ).forEach { (id, label) ->
                        FilterChip(selected = searchFilter == id,
                            onClick = { searchFilter = id },
                            label = { Text(label) })
                    }
                }
                Text("表示 ${visibleMessages.size} 件 · 検索はこの端末内だけで実行",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (threadRoot == null) {
                        val displayLabel = when (aiChoice) {
                            "off" -> "AIなし"
                            "default" -> when (aiDefault) {
                                "openai" -> "従来設定 · OpenAI"
                                "dify" -> "従来設定 · Dify"
                                else -> "AIなし（従来設定）"
                            }
                            "legacy:openai" -> "OpenAI（従来設定）"
                            "legacy:dify" -> "Dify（従来設定）"
                            else -> aiProfiles.firstOrNull { it.id == aiChoice }?.name
                                ?: "未登録のAI（選び直してください）"
                        }
                        Box(Modifier.padding(horizontal = 10.dp)) {
                            TextButton(onClick = { aiPickerOpen = true }) {
                                Text("🤖 " + (if (aiOnline) displayLabel else "端末内モード · " + displayLabel) + " ▾",
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            DropdownMenu(expanded = aiPickerOpen,
                                onDismissRequest = { aiPickerOpen = false }) {
                                listOf(
                                    "default" to "従来のAI設定に従う",
                                    "off" to "このチャンネルではAIを使わない"
                                ).forEach { (id, name) ->
                                    DropdownMenuItem(text = { Text(name) },
                                        onClick = { onChooseAi(id); aiPickerOpen = false })
                                }
                                aiProfiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(profile.name + " · " + profile.kind +
                                                (if (profile.model.isNotBlank())
                                                    " · " + profile.model else ""),
                                                maxLines = 2)
                                        },
                                        onClick = {
                                            onChooseAi(profile.id)
                                            aiPickerOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (threadRoot != null) {
                        Text("スレッドへの返信はこの端末内に保存されます",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
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
                        if (threadRoot == null) {
                            Box {
                                FilledTonalIconButton(onClick = { openAddMenu = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "追加メニュー")
                                }
                                DropdownMenu(
                                    expanded = openAddMenu,
                                    onDismissRequest = { openAddMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("デモ評価カードを追加") },
                                        onClick = { openAddMenu = false; onDemo() }
                                    )
                                    ChatProductivityRules.presets.forEach { (id, label) ->
                                        DropdownMenuItem(
                                            text = { Text("🤖 " + label + "の依頼文を作る") },
                                            onClick = {
                                                putDraft(ChatProductivityRules.preparedPrompt(id, draft))
                                                openAddMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = ::putDraft,
                            placeholder = { Text(if (threadRoot == null) "メッセージを入力…"
                                else "スレッドに返信…") },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp)
                        )
                        FilledIconButton(
                            enabled = draft.isNotBlank() && (threadRoot != null || !sending),
                            onClick = {
                                if (threadRoot == null) onSend(draft)
                                else onSendThreadReply(threadRoot.id, draft)
                                draft = ""
                                draftStore.clear(draftKey)
                            }
                        ) {
                            if (sending && threadRoot == null) {
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
        if (visibleMessages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text(if (searchOpen && threadRoot == null) "検索条件に一致するメッセージがありません"
                    else "ここから会話を始めましょう",
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
                itemsIndexed(visibleMessages, key = { _, message -> message.id }) { index, message ->
                    val previous = visibleMessages.getOrNull(index - 1)
                    val currentDate = DateFormat.format("yyyy/MM/dd", message.timestamp).toString()
                    val previousDate = previous?.let {
                        DateFormat.format("yyyy/MM/dd", it.timestamp).toString()
                    }
                    if (currentDate != previousDate) DateSeparator(currentDate)
                    val grouped = previous != null &&
                        previous.sender == message.sender &&
                        previous.fromMe == message.fromMe &&
                        previousDate == currentDate &&
                        (message.timestamp - previous.timestamp) in 0L..180000L
                    MessageTimelineItem(
                        message = message,
                        grouped = grouped,
                        onNotificationAction = onNotificationAction,
                        onOpenThread = { if (message.parentMessageId == null) activeThreadId = message.id },
                        onLongPress = { selectedMessage = message }
                    )
                    if (searchOpen && threadRoot == null && searchQuery.isNotBlank() &&
                        message.id in replySearchMatches &&
                        !ChatProductivityRules.matches(message, searchQuery.trim())) {
                        Text("💬 返信内に一致 · 「返信」をタップして確認",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    MessageEngagementBar(
                        message = message,
                        emojis = reactionGroups[message.id].orEmpty(),
                        replyCount = if (message.parentMessageId == null)
                            replyCounts[message.id] ?: 0 else 0,
                        showReplyButton = threadRoot == null && message.parentMessageId == null,
                        onOpenThread = { activeThreadId = message.id },
                        onToggleReaction = { emoji -> onToggleReaction(message.id, emoji) }
                    )
                }
            }
        }
    }

    if (showDetails) {
        ChannelDetailsDialog(
            channelName = name, channelId = channelId,
            messages = messages, topic = channelTopic, description = channelDescription,
            onSave = { topic, description ->
                onSaveChannelDetails(topic, description)
                showDetails = false
            },
            onDismiss = { showDetails = false }
        )
    }

    selectedMessage?.let { message ->
        ChatMessageOptions(
            message = message,
            onDismiss = { selectedMessage = null },
            onReply = {
                if (message.parentMessageId == null) activeThreadId = message.id
                else activeThreadId = message.parentMessageId
                selectedMessage = null
            },
            onReact = { emoji ->
                onToggleReaction(message.id, emoji)
                selectedMessage = null
            },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("RelayHarbor", message.text))
                selectedMessage = null
            },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message.text)
                }
                context.startActivity(Intent.createChooser(send, "メッセージを共有"))
                selectedMessage = null
            },
            onQuickSummary = if (threadRoot == null && message.text.isNotBlank()) {
                {
                    putDraft(ChatProductivityRules.preparedPrompt("summary", message.text))
                    selectedMessage = null
                }
            } else null,
            onEdit = { editingMessage = message; selectedMessage = null },
            onDelete = { deletingMessage = message; selectedMessage = null },
            onTogglePin = {
                onNotificationAction(message.id,
                    if (message.isPinned) NotificationAction.UNPIN
                    else NotificationAction.PIN)
                selectedMessage = null
            }
        )
    }

    editingMessage?.let { message ->
        var edited by remember(message.id) { mutableStateOf(message.text) }
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("メッセージを編集") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("端末内に保存されたテキストを変更します。外部サービスには反映されません。",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = edited, onValueChange = { edited = it.take(8000) },
                        modifier = Modifier.fillMaxWidth(), maxLines = 6)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onEditMessage(message.id, edited)
                    editingMessage = null
                }, enabled = edited.isNotBlank()) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("キャンセル") }
            }
        )
    }

    deletingMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { deletingMessage = null },
            title = { Text("メッセージを削除しますか？") },
            text = {
                Text("この端末の履歴から削除します。返信が存在する元メッセージは削除できません。")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMessage(message.id)
                    deletingMessage = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingMessage = null }) { Text("キャンセル") }
            }
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageTimelineItem(
    message: MessageEntity,
    grouped: Boolean,
    onNotificationAction: (Long, NotificationAction) -> Unit,
    onOpenThread: () -> Unit,
    onLongPress: () -> Unit
) {
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
                    .combinedClickable(onClick = onOpenThread, onLongClick = onLongPress)
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
            modifier = Modifier.fillMaxWidth()
                .combinedClickable(onClick = onOpenThread, onLongClick = onLongPress),
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
                if (message.sourcePackage.isNotBlank()) {
                    SourceAppOpenButton(message.sourcePackage)
                }
                NotificationActionBar(message, onNotificationAction)
            }
        }
    }
}

@Composable
private fun MessageEngagementBar(
    message: MessageEntity,
    emojis: List<String>,
    replyCount: Int,
    showReplyButton: Boolean,
    onOpenThread: () -> Unit,
    onToggleReaction: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        emojis.forEach { emoji ->
            TextButton(onClick = { onToggleReaction(emoji) }) {
                Text(emoji, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (showReplyButton) {
            TextButton(onClick = onOpenThread) {
                Text(if (replyCount == 0) "返信" else "💬 " + replyCount + " 件の返信",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ChatMessageOptions(
    message: MessageEntity,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onQuickSummary: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("メッセージの操作") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(message.text.take(140), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ChatConversationRules.emojiChoices.forEach { emoji ->
                        TextButton(onClick = { onReact(emoji) }) {
                            Text(emoji)
                        }
                    }
                }
                TextButton(onClick = onReply) { Text("💬 スレッドで返信") }
                TextButton(onClick = onCopy) { Text("メッセージをコピー") }
                TextButton(onClick = onShare) { Text("共有") }
                if (onQuickSummary != null) {
                    TextButton(onClick = onQuickSummary) {
                        Text("🤖 この内容を要約する下書きを作成")
                    }
                }
                if (!message.fromMe) {
                    TextButton(onClick = onTogglePin) {
                        Text(if (message.isPinned) "重要ピンを解除" else "重要に固定")
                    }
                }
                if (ChatConversationRules.canEdit(message)) {
                    TextButton(onClick = onEdit) { Text("編集（端末内）") }
                    TextButton(onClick = onDelete) { Text("削除（端末内）") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

@Composable
private fun ChannelDetailsDialog(
    channelName: String,
    channelId: Long,
    messages: List<MessageEntity>,
    topic: String,
    description: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var editedTopic by remember(channelId) { mutableStateOf(topic) }
    var editedDescription by remember(channelId) { mutableStateOf(description) }
    val pinned = messages.filter { it.isPinned }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("# " + channelName + " · 詳細") },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("チャンネルID: " + channelId + " · " + messages.size + " 件のメッセージ",
                    style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = editedTopic,
                    onValueChange = { editedTopic = it.take(100) },
                    label = { Text("トピック") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = editedDescription,
                    onValueChange = { editedDescription = it.take(500) },
                    label = { Text("説明") }, modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
                Text("固定したメッセージ (" + pinned.size + ")",
                    style = MaterialTheme.typography.titleSmall)
                if (pinned.isEmpty()) {
                    Text("まだ固定されたメッセージはありません",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    pinned.take(12).forEach { item ->
                        Text("📌 " + item.sender + ": " + item.text.take(100),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("トピックと説明はこの端末に保存され、受信先の名前は変更しません。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(editedTopic, editedDescription) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

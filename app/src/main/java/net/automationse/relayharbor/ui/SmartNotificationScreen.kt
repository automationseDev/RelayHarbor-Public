package net.automationse.relayharbor.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.automationse.relayharbor.data.CardCodec
import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.data.MessageEntity
import net.automationse.relayharbor.data.NotificationAction
import net.automationse.relayharbor.data.NotificationCenterRules

internal object NotificationSwipePolicy {
    fun right(message: MessageEntity): NotificationAction =
        if (message.isDone) NotificationAction.REOPEN else NotificationAction.COMPLETE
    fun left(message: MessageEntity): NotificationAction =
        if (message.savedForLater) NotificationAction.REMOVE_FROM_LATER
        else NotificationAction.SAVE_FOR_LATER
    fun inverse(action: NotificationAction): NotificationAction = when (action) {
        NotificationAction.COMPLETE -> NotificationAction.REOPEN
        NotificationAction.REOPEN -> NotificationAction.COMPLETE
        NotificationAction.SAVE_FOR_LATER -> NotificationAction.REMOVE_FROM_LATER
        NotificationAction.REMOVE_FROM_LATER -> NotificationAction.SAVE_FOR_LATER
        NotificationAction.PIN -> NotificationAction.UNPIN
        NotificationAction.UNPIN -> NotificationAction.PIN
        NotificationAction.MARK_READ -> NotificationAction.MARK_UNREAD
        NotificationAction.MARK_UNREAD -> NotificationAction.MARK_READ
    }
}

/** Swipe right = complete/reopen, swipe left = save/unsave. Never deletes a message. */
@Composable
private fun SwipeableNotification(
    message: MessageEntity,
    onSwipe: (NotificationAction) -> Unit,
    content: @Composable () -> Unit
) {
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            when (target) {
                SwipeToDismissBoxValue.StartToEnd ->
                    onSwipe(NotificationSwipePolicy.right(message))
                SwipeToDismissBoxValue.EndToStart ->
                    onSwipe(NotificationSwipePolicy.left(message))
                else -> Unit
            }
            // Return to idle after the action; avoid removing the composable by gesture alone.
            false
        },
        positionalThreshold = { total -> total * 0.35f }
    )
    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            val fromLeft = swipeState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val action = if (fromLeft) NotificationSwipePolicy.right(message)
                else NotificationSwipePolicy.left(message)
            val tint = if (fromLeft) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            Row(
                modifier = Modifier.fillMaxSize().background(tint, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                horizontalArrangement = if (fromLeft) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (fromLeft) Icons.Default.CheckCircle else Icons.Default.Bookmark,
                    contentDescription = null
                )
                Text(
                    when (action) {
                        NotificationAction.COMPLETE -> "対応済み"
                        NotificationAction.REOPEN -> "再開"
                        NotificationAction.REMOVE_FROM_LATER -> "保留解除"
                        else -> "あとで"
                    },
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        content = { content() }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ActivityItem(
    message: MessageEntity,
    channel: ChannelEntity?,
    onClick: () -> Unit,
    onNotificationAction: (Long, NotificationAction) -> Unit,
    compact: Boolean = false
) {
    val card = remember(message.cardJson) {
        message.cardJson?.let(CardCodec::decode)
    }
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!message.isRead) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (!compact) menuOpen = true }
                ).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(channel?.emoji ?: "🔔", style = MaterialTheme.typography.titleMedium)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "# " + (channel?.name ?: "通知"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            DateFormat.format("MM/dd HH:mm", message.timestamp).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        message.sender,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (card != null) {
                        RichCardView(card, CardDensity.COMPACT)
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            message.text, maxLines = if (compact) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (message.isPinned || message.savedForLater || message.isDone) {
                        Text(
                            listOfNotNull(
                                if (message.isPinned) "📌 重要" else null,
                                if (message.savedForLater) "あとで" else null,
                                if (message.isDone) "対応済み" else null
                            ).joinToString(" ・ "),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                if (!compact) {
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "通知の操作")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            fun invoke(action: NotificationAction) {
                                onNotificationAction(message.id, action)
                                menuOpen = false
                            }
                            DropdownMenuItem(
                                text = { Text(if (message.isPinned) "重要固定を解除" else "重要に固定") },
                                onClick = {
                                    invoke(if (message.isPinned)
                                        NotificationAction.UNPIN else NotificationAction.PIN)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (message.savedForLater) "保留解除" else "あとで確認")
                                },
                                onClick = { invoke(NotificationSwipePolicy.left(message)) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (message.isDone) "対応を再開" else "対応済み") },
                                onClick = { invoke(NotificationSwipePolicy.right(message)) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (message.isRead) "未読に戻す" else "既読にする") },
                                onClick = {
                                    invoke(if (message.isRead)
                                        NotificationAction.MARK_UNREAD else NotificationAction.MARK_READ)
                                }
                            )
                        }
                    }
                }
            }
            if (!compact) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "タップして詳細 ・ 右へスワイプで対応済み ・ 左へスワイプであとで",
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NotificationInspector(
    message: MessageEntity?,
    channel: ChannelEntity?,
    onOpenChat: (Long) -> Unit,
    onAction: (Long, NotificationAction) -> Unit
) {
    if (message == null) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Text(
                "通知を選択すると詳細と操作を表示します",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val card = remember(message.cardJson) { message.cardJson?.let(CardCodec::decode) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "# " + (channel?.name ?: "通知"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            message.sender + "  ·  " +
                DateFormat.format("yyyy/MM/dd HH:mm", message.timestamp).toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (message.text.isNotBlank()) Text(
                message.text, style = MaterialTheme.typography.bodyMedium
            )
            if (card != null) RichCardView(card, density = CardDensity.DETAILED)
            NotificationActionBar(message, onAction)
        }
        TextButton(onClick = { onOpenChat(message.channelId) }) {
            Text("この通知のチャットを開く")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityScreen(
    activity: List<MessageEntity>,
    channels: Map<Long, ChannelEntity>,
    onOpen: (Long) -> Unit,
    onNotificationAction: (Long, NotificationAction) -> Unit,
    twoPane: Boolean = false
) {
    val context = LocalContext.current
    val uiStore = remember(context) { WorkspaceUiStore(context) }
    var query by rememberSaveable { mutableStateOf(uiStore.activitySearch()) }
    var filter by rememberSaveable { mutableStateOf(uiStore.activityListFilter()) }
    var selectedMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val visible = NotificationCenterRules.sort(
        activity.filter { message ->
            NotificationCenterRules.matches(
                message, filter, query, channels[message.channelId]?.name.orEmpty()
            )
        }, filter
    )
    val selected = if (twoPane) {
        visible.firstOrNull { it.id == selectedMessageId } ?: visible.firstOrNull()
    } else activity.firstOrNull { it.id == selectedMessageId }

    fun swipeAction(message: MessageEntity, action: NotificationAction) {
        onNotificationAction(message.id, action)
        scope.launch {
            val label = when (action) {
                NotificationAction.COMPLETE -> "対応済みにしました"
                NotificationAction.REOPEN -> "対応を再開しました"
                NotificationAction.SAVE_FOR_LATER -> "あとで確認に保存しました"
                else -> "保留を解除しました"
            }
            if (snackbarHostState.showSnackbar(
                    label, actionLabel = "元に戻す", duration = SnackbarDuration.Short
                ) == SnackbarResult.ActionPerformed
            ) {
                onNotificationAction(message.id, NotificationSwipePolicy.inverse(action))
            }
        }
    }

    val counts = mapOf(
        "important" to activity.count { NotificationCenterRules.matches(it, "important") },
        "unread" to activity.count { NotificationCenterRules.matches(it, "unread") },
        "later" to activity.count { NotificationCenterRules.matches(it, "later") },
        "done" to activity.count { NotificationCenterRules.matches(it, "done") }
    )

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("通知センター", fontWeight = FontWeight.Bold)
                    Text(
                        "スワイプで整理 ・ 長押しで操作",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { inner ->
        Row(Modifier.fillMaxSize().padding(inner)) {
            Column(
                modifier = Modifier.weight(if (twoPane) 0.44f else 1f).fillMaxSize()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        uiStore.saveActivitySearch(it)
                    },
                    placeholder = { Text("通知・送信元を検索") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                uiStore.saveActivitySearch("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "検索をクリア")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    listOf(
                        "all" to "すべて",
                        "important" to "重要 ${counts["important"]}",
                        "unread" to "未読 ${counts["unread"]}",
                        "later" to "あとで ${counts["later"]}",
                        "done" to "対応済み ${counts["done"]}",
                        "read" to "既読"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = filter == value,
                            onClick = {
                                filter = value
                                uiStore.saveActivityListFilter(value)
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Text(
                    "表示 ${visible.size} 件  ·  重要：固定または85点以上",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (visible.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "条件に合う通知がありません",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 9.dp, end = 9.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visible, key = { it.id }) { message ->
                            SwipeableNotification(
                                message = message,
                                onSwipe = { action -> swipeAction(message, action) }
                            ) {
                                ActivityItem(
                                    message = message,
                                    channel = channels[message.channelId],
                                    onClick = { selectedMessageId = message.id },
                                    onNotificationAction = onNotificationAction
                                )
                            }
                        }
                    }
                }
            }
            if (twoPane) {
                VerticalDivider()
                Box(Modifier.weight(0.56f).fillMaxSize()) {
                    NotificationInspector(
                        selected, selected?.let { channels[it.channelId] },
                        onOpen, onNotificationAction
                    )
                }
            }
        }
    }
    if (!twoPane && selected != null) {
        ModalBottomSheet(onDismissRequest = { selectedMessageId = null }) {
            Box(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
                NotificationInspector(
                    selected, channels[selected.channelId],
                    onOpenChat = {
                        selectedMessageId = null
                        onOpen(it)
                    },
                    onAction = onNotificationAction
                )
            }
        }
    }
}

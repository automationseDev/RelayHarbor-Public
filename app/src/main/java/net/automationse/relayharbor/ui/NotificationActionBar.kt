package net.automationse.relayharbor.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.data.MessageEntity
import net.automationse.relayharbor.data.NotificationAction

/** User-only local state changes. No network calls or sender-defined arbitrary actions. */
@Composable
internal fun NotificationActionBar(
    message: MessageEntity,
    onAction: (Long, NotificationAction) -> Unit
) {
    if (message.fromMe) return
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = message.isPinned,
            onClick = {
                onAction(message.id, if (message.isPinned)
                    NotificationAction.UNPIN else NotificationAction.PIN)
            },
            label = { Text(if (message.isPinned) "重要を解除" else "重要に固定") },
            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) }
        )
        FilterChip(
            selected = message.savedForLater,
            onClick = {
                onAction(message.id, if (message.savedForLater)
                    NotificationAction.REMOVE_FROM_LATER else NotificationAction.SAVE_FOR_LATER)
            },
            label = { Text(if (message.savedForLater) "保留を解除" else "あとで確認") },
            leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) }
        )
        FilterChip(
            selected = message.isDone,
            onClick = {
                onAction(message.id, if (message.isDone)
                    NotificationAction.REOPEN else NotificationAction.COMPLETE)
            },
            label = { Text(if (message.isDone) "対応を再開" else "対応済みにする") },
            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
        )
        FilterChip(
            selected = message.isRead,
            onClick = {
                onAction(message.id, if (message.isRead)
                    NotificationAction.MARK_UNREAD else NotificationAction.MARK_READ)
            },
            label = { Text(if (message.isRead) "未読に戻す" else "既読にする") },
            leadingIcon = { Icon(Icons.Default.MarkEmailRead, contentDescription = null) }
        )
    }
}

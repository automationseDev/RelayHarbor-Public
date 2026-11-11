package net.automationse.relayharbor.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.data.ChannelSummary
import net.automationse.relayharbor.data.MessageEntity

@Composable
fun RelayHarborApp(vm: HubViewModel = viewModel()) {
    var page by rememberSaveable { mutableStateOf("home") }
    var previousPage by rememberSaveable { mutableStateOf("channels") }
    var showCreate by remember { mutableStateOf(false) }
    val channels by vm.channels.collectAsState()
    val summaries by vm.channelSummaries.collectAsState()
    val activity by vm.activity.collectAsState()
    val selectedId by vm.selectedChannelId.collectAsState()
    val messages by vm.messages.collectAsState()
    val settings by vm.connectionSettings.collectAsState()
    val saveError by vm.saveError.collectAsState()
    val tokenConfigured by vm.tokenConfigured.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val integrations by vm.integrations.collectAsState()
    val integrationStatus by vm.integrationStatus.collectAsState()
    val integrationCredential by vm.newIntegrationCredential.collectAsState()
    val appUpdateState by vm.appUpdateState.collectAsState()
    val aiError by vm.aiError.collectAsState()
    val busyChannelId by vm.busyChannelId.collectAsState()

    LaunchedEffect(settings.mode, settings.endpoint, tokenConfigured) {
        if (settings.mode != "local" && tokenConfigured &&
            net.automationse.relayharbor.integration.RelayClient.validEndpoint(settings.endpoint)) {
            while (true) {
                vm.syncNow()
                delay(30_000)
            }
        }
    }
    LaunchedEffect(page, settings.endpoint, tokenConfigured) {
        if (page == "integrations") vm.refreshIntegrations()
    }
    val summaryById = remember(summaries) { summaries.associateBy { it.channelId } }
    val unreadCount = summaries.sumOf { it.unreadCount }

    fun openChannel(id: Long) {
        previousPage = page
        vm.selectChannel(id)
        page = "chat"
    }

    fun closeChat() {
        vm.leaveChannel()
        page = previousPage
    }

    BackHandler(enabled = page == "chat") { closeChat() }

    Scaffold(
        bottomBar = {
            if (page != "chat") {
                NavigationBar {
                    NavigationBarItem(
                        selected = page == "home",
                        onClick = { page = "home" },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("ホーム") }
                    )
                    NavigationBarItem(
                        selected = page == "channels",
                        onClick = { page = "channels" },
                        icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = null) },
                        label = { Text("チャット") }
                    )
                    NavigationBarItem(
                        selected = page == "activity",
                        onClick = { page = "activity" },
                        icon = { Icon(Icons.Default.NotificationsNone, contentDescription = null) },
                        label = { Text(if (unreadCount > 0) "通知 " + unreadCount else "通知") }
                    )
                    NavigationBarItem(
                        selected = page == "integrations",
                        onClick = { page = "integrations" },
                        icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                        label = { Text("連携") }
                    )
                    NavigationBarItem(
                        selected = page == "settings",
                        onClick = { page = "settings" },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("設定") }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (page) {
                "home" -> HomeScreen(
                    channels = channels,
                    summaries = summaryById,
                    activity = activity,
                    onOpen = ::openChannel,
                    onAllChannels = { page = "channels" },
                    onAllActivity = { page = "activity" }
                )
                "channels" -> ChannelListScreen(
                    channels = channels,
                    summaries = summaryById,
                    onSelect = ::openChannel,
                    onAdd = { showCreate = true },
                    onDelete = vm::deleteChannel
                )
                "activity" -> ActivityScreen(
                    activity = activity,
                    channels = channels.associateBy { it.id },
                    onOpen = ::openChannel
                )
                "chat" -> {
                    val channel = channels.firstOrNull { it.id == selectedId }
                    ChatScreen(
                        name = channel?.name ?: "チャット",
                        messages = messages,
                        onBack = ::closeChat,
                        onSend = vm::sendMessage,
                        onDemo = vm::addDemoCard,
                        sending = busyChannelId == selectedId,
                        error = aiError
                    )
                }
                "integrations" -> IntegrationsScreen(
                    endpoint = settings.endpoint,
                    configured = settings.mode != "local" && tokenConfigured &&
                        net.automationse.relayharbor.integration.RelayClient.validEndpoint(settings.endpoint),
                    connections = integrations,
                    status = integrationStatus,
                    credential = integrationCredential,
                    onRefresh = vm::refreshIntegrations,
                    onAdd = vm::createIntegration,
                    onEdit = vm::updateIntegration,
                    onTest = vm::testIntegration,
                    onRefreshFeed = vm::refreshRssIntegration,
                    onRotate = vm::rotateIntegration,
                    onDelete = vm::deleteIntegration,
                    onDismissCredential = vm::closeCredential
                )
                "settings" -> SettingsScreen(
                    settings = settings,
                    error = saveError,
                    tokenConfigured = tokenConfigured,
                    syncStatus = syncStatus,
                    onSave = vm::saveSettings,
                    onClearToken = vm::clearToken,
                    onSync = vm::syncNow,
                    updateState = appUpdateState,
                    onCheckUpdate = vm::checkAppUpdate,
                    onInstallUpdate = vm::installAppUpdate
                )
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("チャンネルを作成") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("チャンネル名") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.createChannel(name)
                        showCreate = false
                    }
                ) { Text("作成") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("キャンセル") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceBar(title: String, subtitle: String) {
    TopAppBar(title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text("RH", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                }
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    channels: List<ChannelEntity>,
    summaries: Map<Long, ChannelSummary>,
    activity: List<MessageEntity>,
    onOpen: (Long) -> Unit,
    onAllChannels: () -> Unit,
    onAllActivity: () -> Unit
) {
    val unreadCount = summaries.values.sumOf { it.unreadCount }
    val recentChannels = channels.sortedWith(
        compareByDescending<ChannelEntity> { summaries[it.id]?.lastTimestamp ?: 0L }.thenBy { it.position }
    ).take(5)
    Scaffold(topBar = { WorkspaceBar("RelayHarbor", "あなたのワークスペース") }) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("すべての情報を、ひとつの場所に。",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("AI・Bot・自分のメモをチャンネルごとに整理",
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("＃ " + channels.size + " チャンネル", fontWeight = FontWeight.Medium)
                        Text("● " + unreadCount + " 未読", fontWeight = FontWeight.Medium)
                    }
                    Text("Webhook・AI連携は設定または連携タブから利用できます。",
                        style = MaterialTheme.typography.labelSmall)
                }
            }

            SectionHeading("最近のアクティビティ", "すべて見る", onAllActivity)
            if (activity.isEmpty()) {
                EmptyHint("Botからの通知は、ここにまとまります。")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activity.take(3).forEach { message ->
                        val channel = channels.firstOrNull { it.id == message.channelId }
                        ActivityItem(message, channel, { onOpen(message.channelId) })
                    }
                }
            }

            SectionHeading("チャンネル", "一覧へ", onAllChannels)
            if (recentChannels.isEmpty()) {
                EmptyHint("チャンネルを作成して、通知や会話を整理しましょう。")
            } else {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    recentChannels.forEachIndexed { index, channel ->
                        ChannelItem(channel, summaries[channel.id], onClick = { onOpen(channel.id) })
                        if (index != recentChannels.lastIndex) HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextButton(onClick = onClick) {
            Text(action)
            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyHint(message: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelListScreen(
    channels: List<ChannelEntity>,
    summaries: Map<Long, ChannelSummary>,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var pendingDelete by remember { mutableStateOf<ChannelEntity?>(null) }

    val filtered = channels.filter { channel ->
        channel.name.contains(query, ignoreCase = true) &&
            (filter != "unread" || (summaries[channel.id]?.unreadCount ?: 0) > 0)
    }
    val visible = if (filter == "recent") {
        filtered.sortedByDescending { summaries[it.id]?.lastTimestamp ?: 0L }
    } else filtered

    Scaffold(
        topBar = { WorkspaceBar("チャンネル", "会話・AI・Botをまとめて管理") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新規") }
            )
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("チャンネルを検索") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                listOf("all" to "すべて", "recent" to "最近", "unread" to "未読").forEach { (value, label) ->
                    FilterChip(selected = filter == value, onClick = { filter = value },
                        label = { Text(label) })
                }
            }
            Text(
                "チャンネル  " + visible.size,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
            if (visible.isEmpty()) {
                EmptyHint(if (filter == "unread") "未読メッセージはありません。"
                    else "該当するチャンネルはありません。")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 92.dp)
                ) {
                    items(visible, key = { it.id }) { channel ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                ChannelItem(channel, summaries[channel.id],
                                    onClick = { onSelect(channel.id) })
                            }
                            Box {
                                var showMenu by remember { mutableStateOf(false) }
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "チャンネルの操作")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("削除") },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            pendingDelete = channel
                                        }
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp),
                            color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
    pendingDelete?.let { channel ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("チャンネルを削除しますか？") },
            text = { Text("「" + channel.name + "」のメッセージ履歴も削除されます。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(channel.id)
                    pendingDelete = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("キャンセル") }
            }
        )
    }
}

@Composable
private fun ChannelItem(
    channel: ChannelEntity,
    summary: ChannelSummary?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Text(channel.emoji, style = MaterialTheme.typography.titleLarge)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("# " + channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold)
            val preview = when {
                summary?.lastTimestamp == null -> "まだメッセージがありません"
                summary.lastText.isNullOrBlank() && summary.lastKind == "card" -> "評価カード"
                else -> summary.lastText.orEmpty().replace('\n', ' ')
            }
            Text(preview, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            summary?.lastTimestamp?.let {
                Text(DateFormat.format("HH:mm", it).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if ((summary?.unreadCount ?: 0) > 0) {
                Surface(color = MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(20.dp)) {
                    Text(summary!!.unreadCount.toString(), color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityScreen(
    activity: List<MessageEntity>,
    channels: Map<Long, ChannelEntity>,
    onOpen: (Long) -> Unit
) {
    Scaffold(topBar = { WorkspaceBar("アクティビティ", "Bot・AIから届いたメッセージ") }) { inner ->
        if (activity.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner).padding(16.dp)) {
                EmptyHint("届いた通知は、ここに時系列で表示されます。")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(activity, key = { it.id }) { message ->
                    ActivityItem(message, channels[message.channelId], onClick = { onOpen(message.channelId) })
                }
            }
        }
    }
}

@Composable
private fun ActivityItem(message: MessageEntity, channel: ChannelEntity?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!message.isRead) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top) {
            Text(channel?.emoji ?: "💬", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("# " + (channel?.name ?: "チャンネル"),
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f), maxLines = 1)
                    Text(DateFormat.format("MM/dd HH:mm", message.timestamp).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(message.sender, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary)
                Text(if (message.text.isBlank() && message.kind == "card") "評価カード" else message.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

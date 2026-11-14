package net.automationse.relayharbor.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.ScaffoldDefaults
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.data.ChannelSummary
import net.automationse.relayharbor.data.MessageEntity
import net.automationse.relayharbor.data.NotificationAction
import net.automationse.relayharbor.data.NotificationCenterRules
import net.automationse.relayharbor.data.CardCodec
import net.automationse.relayharbor.notification.NotificationOpenRequests
import net.automationse.relayharbor.notification.NotificationPreferences
import net.automationse.relayharbor.notification.MessageNotifications
import net.automationse.relayharbor.notification.VisibleChat

@Composable
fun RelayHarborApp(vm: HubViewModel = viewModel()) {
    val context = LocalContext.current
    val uiStore = remember(context) { WorkspaceUiStore(context) }
    val twoPane = ResponsiveLayoutPolicy.useTwoPanes(LocalConfiguration.current.screenWidthDp)
    val defaultHome by DefaultHomeRole.active.collectAsState()
    val homeRequested by HomeLauncherRequests.requested.collectAsState()
    var page by rememberSaveable {
        mutableStateOf(
            if (HomeLauncherRequests.requested.value) "home"
            else if (uiStore.resumeLastPage()) uiStore.lastPage() else "home"
        )
    }
    var previousPage by rememberSaveable { mutableStateOf(uiStore.previousPage()) }
    var navigationRestored by remember { mutableStateOf(false) }
    var resumeLastPage by remember { mutableStateOf(uiStore.resumeLastPage()) }
    var showHomeActivity by remember { mutableStateOf(uiStore.showHomeActivity()) }
    var showHomeChannels by remember { mutableStateOf(uiStore.showHomeChannels()) }
    var showHomeHeader by remember { mutableStateOf(uiStore.showHomeHeader()) }
    var foregroundActive by remember { mutableStateOf(false) }
    var foregroundSyncSeconds by remember {
        mutableStateOf(uiStore.foregroundSyncSeconds())
    }
    LifecycleStartEffect(Unit) {
        foregroundActive = true
        onStopOrDispose { foregroundActive = false }
    }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var channelNameDraft by rememberSaveable { mutableStateOf("") }
    val pageStates = rememberSaveableStateHolder()
    var channelQuery by rememberSaveable { mutableStateOf(uiStore.channelSearch()) }
    var channelFilter by rememberSaveable { mutableStateOf(uiStore.channelListFilter()) }
    val channels by vm.channels.collectAsState()
    val summaries by vm.channelSummaries.collectAsState()
    val activity by vm.activity.collectAsState()
    val selectedId by vm.selectedChannelId.collectAsState()
    val notificationTarget by NotificationOpenRequests.channelId.collectAsState()
    val messages by vm.messages.collectAsState()
    val settings by vm.connectionSettings.collectAsState()
    val saveError by vm.saveError.collectAsState()
    val tokenConfigured by vm.tokenConfigured.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val integrations by vm.integrations.collectAsState()
    val integrationStatus by vm.integrationStatus.collectAsState()
    val integrationCredential by vm.newIntegrationCredential.collectAsState()
    val integrationTransferBusy by vm.integrationTransferBusy.collectAsState()
    val appUpdateState by vm.appUpdateState.collectAsState()
    val aiError by vm.aiError.collectAsState()
    val busyChannelId by vm.busyChannelId.collectAsState()

    // Poll only when the activity is visible. As a default HOME app the Compose
    // composition can survive after opening another app or turning off the screen.
    // WorkManager handles the optional, slower background checks.
    LaunchedEffect(
        foregroundActive, foregroundSyncSeconds,
        settings.mode, settings.endpoint, tokenConfigured
    ) {
        if (foregroundActive && settings.mode != "local" && tokenConfigured &&
            net.automationse.relayharbor.integration.RelayClient.validEndpoint(settings.endpoint)) {
            while (true) {
                vm.syncNow()
                delay(foregroundSyncSeconds * 1_000L)
            }
        }
    }
    val showAllNavLabels = BottomNavigationPolicy.showAllLabels(
        LocalConfiguration.current.screenWidthDp, LocalDensity.current.fontScale
    )

    // A new process has no saved ViewModel channel; restore it before applying
    // the usual "chat without channel" fallback.
    LaunchedEffect(Unit) {
        if (NotificationOpenRequests.channelId.value == null &&
            page == "chat" && selectedId == null) {
            val savedId = uiStore.lastChatId()
            if (savedId > 0) vm.selectChannel(savedId)
            else page = previousPage
        }
        navigationRestored = true
    }
    // Every Android HOME invocation returns to the launcher's root screen;
    // opening RelayHarbor from its normal icon keeps the user's last screen.
    LaunchedEffect(homeRequested) {
        if (homeRequested) {
            vm.leaveChannel()
            page = "home"
            HomeLauncherRequests.consume()
        }
    }
    LaunchedEffect(page) { uiStore.savePage(page) }
    LaunchedEffect(previousPage) { uiStore.savePreviousPage(previousPage) }
    LaunchedEffect(page, selectedId, navigationRestored) {
        if (navigationRestored && page == "chat" && vm.selectedChannelId.value == null) {
            page = previousPage
        }
    }
    // A channel may have been deleted since the previous launch.
    // Wait for the first real non-empty Room channel snapshot, then recover.
    LaunchedEffect(navigationRestored, page, selectedId, channels) {
        if (navigationRestored && page == "chat" && selectedId != null &&
            channels.isNotEmpty() && channels.none { it.id == selectedId }) {
            vm.leaveChannel()
            page = previousPage
        }
    }
    LaunchedEffect(page, settings.endpoint, tokenConfigured) {
        if (page == "integrations") vm.refreshIntegrations()
    }
    val summaryById = remember(summaries) { summaries.associateBy { it.channelId } }
    val unreadCount = summaries.sumOf { it.unreadCount }

    fun openChannel(id: Long) {
        if (page != "chat") previousPage = page
        uiStore.saveLastChatId(id)
        vm.selectChannel(id)
        page = if (twoPane) "channels" else "chat"
    }

    fun closeChat() {
        vm.leaveChannel()
        page = previousPage
    }

    LaunchedEffect(notificationTarget) {
        val requestedId = notificationTarget
        if (requestedId != null && requestedId > 0) {
            openChannel(requestedId)
            NotificationOpenRequests.consume(requestedId)
        }
    }
    LaunchedEffect(page, selectedId, twoPane) {
        VisibleChat.channelId = if (page == "chat" || (twoPane && page == "channels"))
            selectedId ?: 0L else 0L
    }
    // Keep the selected conversation visible across Fold open/close transitions.
    LaunchedEffect(twoPane) {
        if (twoPane && page == "chat" && selectedId != null) {
            page = "channels"
        } else if (!twoPane && page == "channels" && selectedId != null) {
            page = "chat"
        }
    }

    BackHandler(enabled = page == "chat") { closeChat() }
    // As system HOME, Back navigates to the root rather than exiting the launcher.
    BackHandler(enabled = defaultHome && page != "home" && page != "chat") {
        page = "home"
    }
    BackHandler(enabled = defaultHome && page == "home") {
        // A HOME activity has no parent screen.
    }

    Scaffold(
        bottomBar = {
            if (page != "chat") {
                NavigationBar {
                    NavigationBarItem(
                        selected = page == "home",
                        onClick = { page = "home" },
                        icon = { Icon(Icons.Default.Home, contentDescription = "ホーム") },
                        label = { Text("ホーム", style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        alwaysShowLabel = showAllNavLabels
                    )
                    NavigationBarItem(
                        selected = page == "calendar_weather",
                        onClick = { page = "calendar_weather" },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "予定・天気") },
                        label = { Text("予定・天気", style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        alwaysShowLabel = showAllNavLabels
                    )
                    NavigationBarItem(
                        selected = page == "channels",
                        onClick = { page = "channels" },
                        icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = "チャット") },
                        label = { Text("チャット", style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        alwaysShowLabel = showAllNavLabels
                    )
                    NavigationBarItem(
                        selected = page == "activity",
                        onClick = { page = "activity" },
                        icon = {
                            BadgedBox(badge = {
                                if (unreadCount > 0) {
                                    Badge { Text(BottomNavigationPolicy.unreadBadge(unreadCount)) }
                                }
                            }) {
                                Icon(
                                    Icons.Default.NotificationsNone,
                                    contentDescription = if (unreadCount > 0) "通知・未読${unreadCount}件"
                                        else "通知"
                                )
                            }
                        },
                        label = { Text("通知", style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        alwaysShowLabel = showAllNavLabels
                    )
                    NavigationBarItem(
                        selected = page == "integrations",
                        onClick = { page = "integrations" },
                        icon = { Icon(Icons.Default.Extension, contentDescription = "連携") },
                        label = { Text("連携", style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        alwaysShowLabel = showAllNavLabels
                    )
                    NavigationBarItem(
                        selected = page == "settings",
                        onClick = { page = "settings" },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "設定") },
                        label = { Text("設定", style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        alwaysShowLabel = showAllNavLabels
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            pageStates.SaveableStateProvider(page) {
            when (page) {
                "home" -> HomeScreen(
                    channels = channels,
                    summaries = summaryById,
                    activity = activity,
                    onOpen = ::openChannel,
                    onAllChannels = { page = "channels" },
                    onAllActivity = { page = "activity" },
                    onNotificationAction = vm::updateNotification,
                    showHeader = showHomeHeader,
                    onShowHeaderChange = { visible ->
                        showHomeHeader = visible
                        uiStore.saveShowHomeHeader(visible)
                    },
                    showActivity = showHomeActivity,
                    showChannels = showHomeChannels,
                    onShowActivityChange = {
                        showHomeActivity = it
                        uiStore.saveShowHomeActivity(it)
                    },
                    onShowChannelsChange = {
                        showHomeChannels = it
                        uiStore.saveShowHomeChannels(it)
                    },
                    syncStatus = syncStatus,
                    syncAvailable = settings.mode != "local" && tokenConfigured &&
                        net.automationse.relayharbor.integration.RelayClient.validEndpoint(settings.endpoint),
                    foregroundSyncSeconds = foregroundSyncSeconds,
                    onForegroundSyncSecondsChange = { seconds ->
                        foregroundSyncSeconds = seconds
                        uiStore.saveForegroundSyncSeconds(seconds)
                    },
                    onSync = vm::syncNow
                )
                "calendar_weather" -> CalendarWeatherScreen(twoPane = twoPane)
                "channels" -> {
                    if (twoPane) {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(0.36f).fillMaxSize()) {
                    ChannelListScreen(
                        channels = channels,
                        summaries = summaryById,
                        query = channelQuery,
                        onQueryChange = {
                            channelQuery = it
                            uiStore.saveChannelSearch(it)
                        },
                        filter = channelFilter,
                        onFilterChange = {
                            channelFilter = it
                            uiStore.saveChannelListFilter(it)
                        },
                        onSelect = ::openChannel,
                        onAdd = { showCreate = true },
                        onDelete = vm::deleteChannel
                    )
                            }
                            androidx.compose.material3.VerticalDivider()
                            Box(Modifier.weight(0.64f).fillMaxSize()) {
                                if (selectedId != null &&
                                    channels.any { it.id == selectedId }) {
                                    val channel = channels.first { it.id == selectedId }
                                    ChatScreen(
                                        channelId = channel.id,
                                        name = channel.name,
                                        messages = messages,
                                        onBack = vm::leaveChannel,
                                        onSend = vm::sendMessage,
                                        onDemo = vm::addDemoCard,
                                        onNotificationAction = vm::updateNotification,
                                        sending = busyChannelId == selectedId,
                                        error = aiError
                                    )
                                } else {
                                    Box(
                                        Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "左のチャンネルを選択して会話を表示",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                    ChannelListScreen(
                        channels = channels,
                        summaries = summaryById,
                        query = channelQuery,
                        onQueryChange = {
                            channelQuery = it
                            uiStore.saveChannelSearch(it)
                        },
                        filter = channelFilter,
                        onFilterChange = {
                            channelFilter = it
                            uiStore.saveChannelListFilter(it)
                        },
                        onSelect = ::openChannel,
                        onAdd = { showCreate = true },
                        onDelete = vm::deleteChannel
                    )
                    }
                }
                "activity" -> ActivityScreen(
                    activity = activity,
                    channels = channels.associateBy { it.id },
                    onOpen = ::openChannel,
                    onNotificationAction = vm::updateNotification,
                    twoPane = twoPane
                )
                "chat" -> {
                    val chatId = selectedId
                    if (chatId != null) {
                    val channel = channels.firstOrNull { it.id == chatId }
                    ChatScreen(
                        channelId = chatId,
                        name = channel?.name ?: "チャット",
                        messages = messages,
                        onBack = ::closeChat,
                        onSend = vm::sendMessage,
                        onDemo = vm::addDemoCard,
                        onNotificationAction = vm::updateNotification,
                        sending = busyChannelId == selectedId,
                        error = aiError
                    )
                    }
                }
                "integrations" -> IntegrationsScreen(
                    endpoint = settings.endpoint,
                    localChannels = channels,
                    onCreateLocalChannel = vm::createChannel,
                    configured = settings.mode != "local" && tokenConfigured &&
                        net.automationse.relayharbor.integration.RelayClient.validEndpoint(settings.endpoint),
                    connections = integrations,
                    status = integrationStatus,
                    credential = integrationCredential,
                    transferBusy = integrationTransferBusy,
                    onExport = vm::exportIntegrationSettings,
                    onImport = vm::importIntegrationSettings,
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
                    foregroundSyncSeconds = foregroundSyncSeconds,
                    onForegroundSyncSecondsChange = { seconds ->
                        foregroundSyncSeconds = seconds
                        uiStore.saveForegroundSyncSeconds(seconds)
                    },
                    updateState = appUpdateState,
                    onCheckUpdate = vm::checkAppUpdate,
                    onOpenUpdatePage = vm::openAppUpdatePage,
                    resumeLastPage = resumeLastPage,
                    onResumeLastPageChange = {
                        resumeLastPage = it
                        uiStore.setResumeLastPage(it)
                    },
                    showHomeActivity = showHomeActivity,
                    onShowHomeActivityChange = {
                        showHomeActivity = it
                        uiStore.saveShowHomeActivity(it)
                    },
                    showHomeChannels = showHomeChannels,
                    onShowHomeChannelsChange = {
                        showHomeChannels = it
                        uiStore.saveShowHomeChannels(it)
                    },
                    showHomeHeader = showHomeHeader,
                    onShowHomeHeaderChange = {
                        showHomeHeader = it
                        uiStore.saveShowHomeHeader(it)
                    }
                )
            }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("チャンネルを作成") },
            text = {
                OutlinedTextField(
                    value = channelNameDraft,
                    onValueChange = { channelNameDraft = it.take(60) },
                    label = { Text("チャンネル名") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    enabled = channelNameDraft.isNotBlank(),
                    onClick = {
                        vm.createChannel(channelNameDraft)
                        channelNameDraft = ""
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
    onAllActivity: () -> Unit,
    onNotificationAction: (Long, NotificationAction) -> Unit,
    showHeader: Boolean,
    onShowHeaderChange: (Boolean) -> Unit,
    showActivity: Boolean,
    showChannels: Boolean,
    onShowActivityChange: (Boolean) -> Unit,
    onShowChannelsChange: (Boolean) -> Unit,
    syncStatus: String,
    syncAvailable: Boolean,
    foregroundSyncSeconds: Int,
    onForegroundSyncSecondsChange: (Int) -> Unit,
    onSync: () -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { HomePanelStore(context) }
    // The available window width separates compact (cover) and expanded views.
    // It changes automatically when the Fold opens, closes, or multi-window changes size.
    val profile = if (LocalConfiguration.current.screenWidthDp >= 600)
        HomePanelLayout.EXPANDED else HomePanelLayout.COVER
    var panels by remember(profile) { mutableStateOf(store.load(profile)) }
    var appDrawerOpen by rememberSaveable { mutableStateOf(false) }
    var favoritesPickerOpen by rememberSaveable { mutableStateOf(false) }
    val unreadCount = summaries.values.sumOf { it.unreadCount }
    val recentChannels = channels.sortedWith(
        compareByDescending<ChannelEntity> { summaries[it.id]?.lastTimestamp ?: 0L }
            .thenBy { it.position }
    ).take(5)

    Scaffold(
        topBar = {
            if (showHeader) WorkspaceBar("RelayHarbor", "あなたのワークスペース")
        },
        // The outer app Scaffold already applies the Android status-bar inset.
        // Without a Home top bar, a second default inset leaves a blank band.
        contentWindowInsets = if (showHeader) ScaffoldDefaults.contentWindowInsets
            else WindowInsets(0, 0, 0, 0)
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            CustomHomeDashboard(
                profile = profile,
                layout = panels,
                showHeader = showHeader,
                onShowHeaderChange = onShowHeaderChange,
                onLayoutChange = { changed ->
                    panels = HomePanelLayout.normalize(profile, changed)
                    store.save(profile, panels)
                },
                onReset = { panels = store.reset(profile) },
                panelHeader = { id ->
                    HomePanelFixedHeader(
                        id = id,
                        onFavoritesEdit = { favoritesPickerOpen = true },
                        onAllActivity = onAllActivity,
                        onAllChannels = onAllChannels
                    )
                }
            ) { id ->
                when (id) {
                    "overview" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                             Text("未読 $unreadCount 件")
                            Text("チャンネル ${channels.size} 件")
                            Text(
                                "アプリ・チャット・通知をここからすばやく開けます。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    "apps" -> QuickAppsSection(
                        showHeading = false,
                        externalPickerOpen = favoritesPickerOpen,
                        onExternalPickerChange = { favoritesPickerOpen = it }
                    )
                    "drawer" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                             Button(
                                onClick = { appDrawerOpen = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Apps, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("アプリ一覧を開く")
                            }
                        }
                    }
                    "sync" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                             Text(
                                if (syncAvailable) syncStatus else "Relay未接続（設定から接続）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = onSync, enabled = syncAvailable) {
                                Text("今すぐ同期")
                            }
                            Text(
                                "表示中の同期間隔",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                listOf(30 to "30秒", 60 to "1分", 120 to "2分", 300 to "5分")
                                    .forEach { (seconds, label) ->
                                        FilterChip(
                                            selected = foregroundSyncSeconds == seconds,
                                            onClick = { onForegroundSyncSecondsChange(seconds) },
                                            label = { Text(label) }
                                        )
                                    }
                            }
                        }
                    }
                    "activity" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                             if (!showActivity) {
                                Text("表示設定で最近の通知がオフになっています。")
                                TextButton(onClick = { onShowActivityChange(true) }) {
                                    Text("通知パネルを表示")
                                }
                            } else if (activity.isEmpty()) {
                                EmptyHint("Botからの通知は、ここにまとまります。")
                            } else {
                                activity.filter { !it.isDone }.sortedWith(
                                    compareByDescending<MessageEntity> { it.isPinned }
                                        .thenByDescending { it.timestamp }
                                ).take(3).forEach { message ->
                                    val channel = channels.firstOrNull { it.id == message.channelId }
                                    ActivityItem(
                                        message, channel, { onOpen(message.channelId) },
                                        onNotificationAction,
                                        compact = true
                                    )
                                }
                            }
                        }
                    }
                    "channels" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                             if (!showChannels) {
                                Text("表示設定でチャンネルがオフになっています。")
                                TextButton(onClick = { onShowChannelsChange(true) }) {
                                    Text("チャンネルを表示")
                                }
                            } else if (recentChannels.isEmpty()) {
                                EmptyHint("チャンネルを作成して、通知や会話を整理しましょう。")
                            } else {
                                recentChannels.forEach { channel ->
                                    ChannelItem(
                                        channel, summaries[channel.id],
                                        onClick = { onOpen(channel.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (appDrawerOpen) HomeAllAppsDrawer(onDismiss = { appDrawerOpen = false })
}

@Composable
private fun HomePanelFixedHeader(
    id: String,
    onFavoritesEdit: () -> Unit,
    onAllActivity: () -> Unit,
    onAllChannels: () -> Unit
) {
    val spec = HomePanelHeaders.spec(id)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            spec.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val action = when (id) {
            "apps" -> onFavoritesEdit
            "activity" -> onAllActivity
            "channels" -> onAllChannels
            else -> null
        }
        if (action != null) {
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = action
            ) {
                Icon(
                    if (id == "apps") Icons.Default.Add else Icons.Default.ArrowForward,
                    contentDescription = spec.actionLabel
                )
            }
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
    query: String,
    onQueryChange: (String) -> Unit,
    filter: String,
    onFilterChange: (String) -> Unit,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ChannelEntity?>(null) }
    val context = LocalContext.current
    val notificationPrefs = remember(context) { NotificationPreferences(context) }

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
                onValueChange = onQueryChange,
                placeholder = { Text("チャンネルを検索") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "検索をクリア")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                listOf("all" to "すべて", "recent" to "最近", "unread" to "未読").forEach { (value, label) ->
                    FilterChip(selected = filter == value, onClick = { onFilterChange(value) },
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
                                    var channelAlertsEnabled by remember(channel.id) {
                                        mutableStateOf(notificationPrefs.channelEnabled(channel.id))
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Text(if (channelAlertsEnabled) "通知をオフ" else "通知をオン")
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.NotificationsNone, contentDescription = null)
                                        },
                                        onClick = {
                                            channelAlertsEnabled = !channelAlertsEnabled
                                            notificationPrefs.setChannelEnabled(channel.id, channelAlertsEnabled)
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("通知音・振動を設定") },
                                        onClick = {
                                            showMenu = false
                                            MessageNotifications.openChannelSettings(
                                                context, channel.id, channel.name
                                            )
                                        }
                                    )
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


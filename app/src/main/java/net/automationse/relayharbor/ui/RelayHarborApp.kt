package net.automationse.relayharbor.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.widget.Toast
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import net.automationse.relayharbor.data.ChannelOrganizationStore
import net.automationse.relayharbor.data.ChannelSummary
import net.automationse.relayharbor.data.MessageEntity
import net.automationse.relayharbor.data.NotificationAction
import net.automationse.relayharbor.data.NotificationCenterRules
import net.automationse.relayharbor.data.CardCodec
import net.automationse.relayharbor.notification.NotificationOpenRequests
import net.automationse.relayharbor.floating.FloatingNavigationRequests
import net.automationse.relayharbor.notification.NotificationPreferences
import net.automationse.relayharbor.notification.NotificationPriorityPolicy
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
    val organization = remember(context) { ChannelOrganizationStore(context) }
    var organizationRevision by remember { mutableStateOf(0) }
    val displayChannels = remember(channels, organizationRevision) {
        channels.map(organization::present)
    }
    val globalMessageResults by vm.messageSearchResults.collectAsState()
    val globalMessageSearching by vm.messageSearchBusy.collectAsState()
    val summaries by vm.channelSummaries.collectAsState()
    val activity by vm.activity.collectAsState()
    val selectedId by vm.selectedChannelId.collectAsState()
    val notificationTarget by NotificationOpenRequests.channelId.collectAsState()
    val floatingTarget by FloatingNavigationRequests.page.collectAsState()
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
    LaunchedEffect(floatingTarget) {
        val target = floatingTarget
        if (target != null) {
            if (target == "home") vm.leaveChannel()
            if (target == "channels" && page == "chat") vm.leaveChannel()
            page = target
            FloatingNavigationRequests.consume(target)
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
                    channels = displayChannels,
                    summaries = summaryById,
                    activity = activity,
                    onOpen = ::openChannel,
                    onAllChannels = { page = "channels" },
                    onAllActivity = { page = "activity" },
                    onOpenCalendarWeather = { page = "calendar_weather" },
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
                        channels = displayChannels,
                        organization = organization,
                        organizationRevision = organizationRevision,
                        onOrganizationChanged = { organizationRevision++ },
                        searchResults = globalMessageResults,
                        searchBusy = globalMessageSearching,
                        onSearchMessages = vm::searchAllMessages,
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
                        onDelete = { id ->
                            organization.clearChannel(id)
                            NotificationPreferences(context).clearChannel(id)
                            organizationRevision++
                            vm.deleteChannel(id)
                        }
                    )
                            }
                            androidx.compose.material3.VerticalDivider()
                            Box(Modifier.weight(0.64f).fillMaxSize()) {
                                if (selectedId != null &&
                                    channels.any { it.id == selectedId }) {
                                    val channel = displayChannels.first { it.id == selectedId }
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
                        channels = displayChannels,
                        organization = organization,
                        organizationRevision = organizationRevision,
                        onOrganizationChanged = { organizationRevision++ },
                        searchResults = globalMessageResults,
                        searchBusy = globalMessageSearching,
                        onSearchMessages = vm::searchAllMessages,
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
                        onDelete = { id ->
                            organization.clearChannel(id)
                            NotificationPreferences(context).clearChannel(id)
                            organizationRevision++
                            vm.deleteChannel(id)
                        }
                    )
                    }
                }
                "activity" -> ActivityScreen(
                    activity = activity,
                    channels = displayChannels.associateBy { it.id },
                    onOpen = ::openChannel,
                    onNotificationAction = vm::updateNotification,
                    twoPane = twoPane
                )
                "chat" -> {
                    val chatId = selectedId
                    if (chatId != null) {
                    val channel = displayChannels.firstOrNull { it.id == chatId }
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
    onOpenCalendarWeather: () -> Unit,
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
    val nativeHost = remember(context) { AndroidHomeWidgetHost(context) }
    val pendingWidgets = remember(context) { AndroidWidgetPendingStore(context) }
    var nativePickerOpen by rememberSaveable { mutableStateOf(false) }

    LifecycleStartEffect(nativeHost) {
        runCatching { nativeHost.start() }
        onStopOrDispose { runCatching { nativeHost.stop() } }
    }

    val finishPending: (Boolean) -> Unit = { success ->
        pendingWidgets.pending()?.let { (id, targetProfile) ->
            if (success && runCatching { nativeHost.info(id) }.getOrNull() != null) {
                val old = if (targetProfile == profile) panels else store.load(targetProfile)
                val updated = HomePanelLayout.addNative(targetProfile, old, id)
                if (updated.any { it.id == HomePanelLayout.nativeId(id) }) {
                    store.save(targetProfile, updated)
                    if (targetProfile == profile) panels = updated
                } else {
                    runCatching { nativeHost.delete(id) }
                    Toast.makeText(context, "追加可能なウィジェットの上限です", Toast.LENGTH_SHORT).show()
                }
            } else {
                runCatching { nativeHost.delete(id) }
            }
            pendingWidgets.clear()
        }
    }

    val configureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        finishPending(result.resultCode == Activity.RESULT_OK)
    }

    val configurePending: (Int) -> Unit = { id ->
        val info = runCatching { nativeHost.info(id) }.getOrNull()
        if (info == null) {
            finishPending(false)
        } else if (info.configure != null) {
            try {
                configureLauncher.launch(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        .setComponent(info.configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                )
            } catch (_: Exception) {
                finishPending(false)
                Toast.makeText(context, "ウィジェットの設定画面を開けませんでした",
                    Toast.LENGTH_SHORT).show()
            }
        } else {
            finishPending(true)
        }
    }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingWidgets.pending()?.first
        if (result.resultCode == Activity.RESULT_OK && id != null &&
            (result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id) ?: id) == id) {
            configurePending(id)
        } else {
            finishPending(false)
        }
    }

    val addNativeWidget: (AppWidgetProviderInfo) -> Unit = { provider ->
        nativePickerOpen = false
        if (panels.count { HomePanelLayout.isNative(it.id) } >=
            HomePanelLayout.MAX_NATIVE_PER_PROFILE) {
            Toast.makeText(context, "ウィジェットは画面ごとに16個までです",
                Toast.LENGTH_SHORT).show()
        } else {
            pendingWidgets.pending()?.first?.let { stale ->
                runCatching { nativeHost.delete(stale) }
                pendingWidgets.clear()
            }
            try {
                val id = nativeHost.allocate()
                pendingWidgets.set(id, profile)
                if (nativeHost.bind(id, provider)) {
                    configurePending(id)
                } else {
                    bindLauncher.launch(
                        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE,
                                provider.profile)
                    )
                }
            } catch (_: Exception) {
                finishPending(false)
                Toast.makeText(context, "ウィジェットを追加できませんでした",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    val deleteNativeWidget: (String) -> Unit = { panelId ->
        val id = HomePanelLayout.nativeWidgetId(panelId)
        if (id != null) {
            panels = HomePanelLayout.removeNative(profile, panels, panelId)
            store.save(profile, panels)
            val otherProfile = if (profile == HomePanelLayout.COVER)
                HomePanelLayout.EXPANDED else HomePanelLayout.COVER
            if (store.load(otherProfile).none { it.id == panelId }) {
                runCatching { nativeHost.delete(id) }
            }
        }
    }
    var compactGaps by remember(profile) { mutableStateOf(store.compactGaps(profile)) }
    var appDrawerOpen by rememberSaveable { mutableStateOf(false) }
    var favoritesPickerOpen by rememberSaveable { mutableStateOf(false) }
    val unreadCount = summaries.values.sumOf { it.unreadCount }
    val homePinned = remember(context, channels) { ChannelPinStore(context).load() }
    val recentChannels = channels.sortedWith(
        compareByDescending<ChannelEntity> { it.id in homePinned }
            .thenByDescending { summaries[it.id]?.lastTimestamp ?: 0L }
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
                compactGaps = compactGaps,
                onAddAndroidWidget = { nativePickerOpen = true },
                onDeleteAndroidWidget = deleteNativeWidget,
                onCompactGapsChange = { compact ->
                    compactGaps = compact
                    store.saveCompactGaps(profile, compact)
                },
                onLayoutChange = { changed ->
                    panels = HomePanelLayout.normalize(profile, changed)
                    store.save(profile, panels)
                },
                onReset = {
                    val oldIds = panels.mapNotNull { HomePanelLayout.nativeWidgetId(it.id) }
                    panels = store.reset(profile)
                    val otherProfile = if (profile == HomePanelLayout.COVER)
                        HomePanelLayout.EXPANDED else HomePanelLayout.COVER
                    val keep = store.load(otherProfile).map { it.id }.toSet()
                    oldIds.filter { HomePanelLayout.nativeId(it) !in keep }.forEach { id ->
                        runCatching { nativeHost.delete(id) }
                    }
                },
                panelTitle = { id ->
                    HomePanelLayout.nativeWidgetId(id)?.let(nativeHost::widgetLabel)
                        ?: HomePanelLayout.titles[id].orEmpty().ifBlank { "ホームカード" }
                },
                panelHeader = { id ->
                    HomePanelFixedHeader(
                        id = id,
                        onFavoritesEdit = { favoritesPickerOpen = true },
                        onAllActivity = onAllActivity,
                        onAllChannels = onAllChannels
                    )
                }
            ) { id ->
                when {
                    HomePanelLayout.isNative(id) -> AndroidHostedWidget(
                        nativeHost, HomePanelLayout.nativeWidgetId(id)!!
                    )
                    id in setOf("clock", "date", "weather", "agenda", "battery") -> HomeMiniWidget(
                        id, onOpenCalendarWeather
                    )
                    id == "overview" -> {
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
                    id == "apps" -> QuickAppsSection(
                        showHeading = false,
                        externalPickerOpen = favoritesPickerOpen,
                        onExternalPickerChange = { favoritesPickerOpen = it }
                    )
                    id == "drawer" -> {
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
                    id == "sync" -> {
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
                    id == "activity" -> {
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
                    id == "channels" -> {
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
                                        onClick = { onOpen(channel.id) },
                                        pinned = channel.id in homePinned
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (nativePickerOpen) AndroidWidgetPicker(
        host = nativeHost,
        onSelect = addNativeWidget,
        onDismiss = { nativePickerOpen = false }
    )
    if (appDrawerOpen) HomeAllAppsDrawer(onDismiss = { appDrawerOpen = false })
}

@Composable
private fun HomePanelFixedHeader(
    id: String,
    onFavoritesEdit: () -> Unit,
    onAllActivity: () -> Unit,
    onAllChannels: () -> Unit
) {
    val context = LocalContext.current
    val spec = if (HomePanelLayout.isNative(id)) {
        val nativeId = HomePanelLayout.nativeWidgetId(id)!!
        val label = runCatching {
            AppWidgetManager.getInstance(context).getAppWidgetInfo(nativeId)
                ?.loadLabel(context.packageManager)
        }.getOrNull().orEmpty()
        HomePanelHeaderSpec(label.ifBlank { "Androidウィジェット" })
    } else HomePanelHeaders.spec(id)
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
    organization: ChannelOrganizationStore,
    organizationRevision: Int,
    onOrganizationChanged: () -> Unit,
    searchResults: List<MessageEntity>,
    searchBusy: Boolean,
    onSearchMessages: (String) -> Unit,
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
    var editingChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var assigningFolder by remember { mutableStateOf<ChannelEntity?>(null) }
    var priorityChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var managingFolders by remember { mutableStateOf(false) }
    var searchAllOpen by remember { mutableStateOf(false) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val notificationPrefs = remember(context) { NotificationPreferences(context) }
    val pinStore = remember(context) { ChannelPinStore(context) }
    var pinned by remember(context) { mutableStateOf(pinStore.load()) }
    val folders = remember(organizationRevision) { organization.folders() }
    val filtered = ChannelPinPolicy.visible(channels, summaries, query, filter, pinned)
        .filter { selectedFolder == null || organization.folder(it.id) == selectedFolder }
    // Group channels under their folder names without losing the in-folder sort.
    val orderById = filtered.mapIndexed { index, channel -> channel.id to index }.toMap()
    val visible = filtered.sortedWith(
        compareByDescending<ChannelEntity> { it.id in pinned }
            .thenBy { organization.folder(it.id) }
            .thenBy { orderById[it.id] ?: Int.MAX_VALUE }
    )
    val unreadTotal = channels.sumOf { summaries[it.id]?.unreadCount ?: 0 }

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
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TextButton(onClick = { searchAllOpen = true }) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text("メッセージ横断検索")
                }
                TextButton(onClick = { managingFolders = true }) {
                    Text("📁 フォルダー管理")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                listOf("all" to "すべて", "pinned" to "お気に入り",
                    "recent" to "最近", "unread" to "未読").forEach { (value, label) ->
                    FilterChip(selected = filter == value, onClick = { onFilterChange(value) },
                        label = { Text(label) })
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                FilterChip(
                    selected = selectedFolder == null,
                    onClick = { selectedFolder = null },
                    label = { Text("全フォルダー") }
                )
                FilterChip(
                    selected = selectedFolder == "",
                    onClick = { selectedFolder = "" },
                    label = { Text("未分類") }
                )
                folders.forEach { folder ->
                    FilterChip(
                        selected = selectedFolder == folder,
                        onClick = { selectedFolder = folder },
                        label = { Text("📁 " + folder) }
                    )
                }
            }
            Text(
                "チャンネル " + visible.size + " 件・未読 " + unreadTotal + " 件",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
            if (visible.isEmpty()) {
                EmptyHint(when (filter) {
                    "unread" -> "未読メッセージはありません。"
                    "pinned" -> "お気に入りはまだありません。チャンネルの︙から固定できます。"
                    else -> "該当するチャンネルはありません。"
                })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 92.dp)
                ) {
                    itemsIndexed(visible, key = { _, channel -> channel.id }) { index, channel ->
                        val section = (if (channel.id in pinned) "★ " else "") +
                            (organization.folder(channel.id).ifBlank { "未分類" })
                        val priorSection = if (index == 0) "" else {
                            val previous = visible[index - 1]
                            (if (previous.id in pinned) "★ " else "") +
                                organization.folder(previous.id).ifBlank { "未分類" }
                        }
                        if (index == 0 || priorSection != section) {
                            Text(
                                section,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(start = 18.dp, top = 12.dp, bottom = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                ChannelItem(channel, summaries[channel.id],
                                    onClick = { onSelect(channel.id) },
                                    pinned = channel.id in pinned)
                            }
                            Box {
                                var showMenu by remember { mutableStateOf(false) }
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "チャンネルの操作")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("表示名・アイコンを編集") },
                                        onClick = {
                                            showMenu = false
                                            editingChannel = channel
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("フォルダーを変更") },
                                        onClick = {
                                            showMenu = false
                                            assigningFolder = channel
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text("通知の優先度：" +
                                                NotificationPriorityPolicy.label(
                                                    notificationPrefs.channelPriority(channel.id)
                                                ))
                                        },
                                        onClick = {
                                            showMenu = false
                                            priorityChannel = channel
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (channel.id in pinned)
                                            "お気に入りを解除" else "お気に入りに固定") },
                                        leadingIcon = { Icon(if (channel.id in pinned)
                                            Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = null) },
                                        onClick = {
                                            pinned = ChannelPinPolicy.toggle(pinned, channel.id)
                                            pinStore.save(pinned)
                                            showMenu = false
                                        }
                                    )
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
    if (searchAllOpen) {
        ChannelSearchDialog(
            channels = channels,
            results = searchResults,
            busy = searchBusy,
            onQuery = onSearchMessages,
            onOpenChannel = onSelect,
            onDismiss = { searchAllOpen = false }
        )
    }
    if (managingFolders) {
        ChannelFolderManagerDialog(
            folders = folders,
            onCreate = { name ->
                val changed = organization.addFolder(name)
                if (changed) onOrganizationChanged()
                changed
            },
            onRename = { old, new ->
                val changed = organization.renameFolder(old, new)
                if (changed) {
                    if (selectedFolder == old) selectedFolder =
                        net.automationse.relayharbor.data.ChannelOrganizationRules.cleanFolder(new)
                    onOrganizationChanged()
                }
                changed
            },
            onRemove = { folder ->
                organization.deleteFolder(folder)
                if (selectedFolder == folder) selectedFolder = null
                onOrganizationChanged()
            },
            onDismiss = { managingFolders = false }
        )
    }
    editingChannel?.let { channel ->
        ChannelAppearanceDialog(
            channel = channel,
            onSave = { label, emoji ->
                organization.saveAppearance(channel.id, label, emoji)
                onOrganizationChanged()
                editingChannel = null
            },
            onDismiss = { editingChannel = null }
        )
    }
    assigningFolder?.let { channel ->
        ChannelFolderAssignDialog(
            channelName = channel.name,
            current = organization.folder(channel.id),
            folders = folders,
            onSelect = { folder ->
                if (organization.assign(channel.id, folder)) onOrganizationChanged()
                assigningFolder = null
            },
            onDismiss = { assigningFolder = null }
        )
    }
    priorityChannel?.let { channel ->
        ChannelPriorityDialog(
            channelName = channel.name,
            current = notificationPrefs.channelPriority(channel.id),
            onSet = { priority ->
                notificationPrefs.setChannelPriority(channel.id, priority)
                priorityChannel = null
            },
            onDismiss = { priorityChannel = null }
        )
    }
    pendingDelete?.let { channel ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("チャンネルを削除しますか？") },
            text = { Text("「" + channel.name + "」のメッセージ履歴も削除されます。") },
            confirmButton = {
                TextButton(onClick = {
                    pinned = pinned - channel.id
                    pinStore.save(pinned)
                    organization.clearChannel(channel.id)
                    notificationPrefs.clearChannel(channel.id)
                    onOrganizationChanged()
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
    onClick: () -> Unit,
    pinned: Boolean = false
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
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("# " + channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                if (pinned) Icon(Icons.Default.Star, contentDescription = "お気に入り",
                    modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
            }
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


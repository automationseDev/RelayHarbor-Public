package net.automationse.relayharbor.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import net.automationse.relayharbor.data.ChannelEntity
import net.automationse.relayharbor.notification.AndroidAppNotificationStore
import net.automationse.relayharbor.notification.AndroidAppRoute
import net.automationse.relayharbor.notification.AndroidNotificationRules

internal object AndroidNotificationAccess {
    fun granted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

    fun openSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

internal data class SourceApp(val packageName: String, val title: String)

/** Package visibility is limited to apps with a launcher entry, not QUERY_ALL_PACKAGES. */
internal fun installedNotificationSources(context: Context): List<SourceApp> {
    val pm = context.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(query, 0)
        .map { SourceApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.packageName != context.packageName &&
            AndroidNotificationRules.validPackage(it.packageName) }
        .distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
}

@Composable
internal fun AndroidAppNotificationSettingsCard(
    channels: List<ChannelEntity>,
    onCreateChannel: (String) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { AndroidAppNotificationStore(context) }
    var globallyEnabled by remember { mutableStateOf(store.globallyEnabled()) }
    var granted by remember { mutableStateOf(AndroidNotificationAccess.granted(context)) }
    var openManager by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = AndroidNotificationAccess.granted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var rules by remember { mutableStateOf(store.routes()) }
    fun refreshRules() { rules = store.routes() }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Androidアプリ通知", fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        "LINE・SMS・Gmailなどを端末内のチャンネルへ集約",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = globallyEnabled,
                    onCheckedChange = {
                        globallyEnabled = it
                        store.setGloballyEnabled(it)
                    }
                )
            }
            Text(
                if (!globallyEnabled) "取り込み停止中（初期設定はオフ）"
                else if (!granted) "Androidの通知アクセス許可が必要です"
                else if (rules.none { it.enabled }) "連携するアプリと保存先を選択してください"
                else "有効：${rules.count { it.enabled }}アプリを端末内で取り込み",
                style = MaterialTheme.typography.labelMedium,
                color = if (globallyEnabled && !granted) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondary
            )
            Text(
                "選択したアプリの今後の通知だけを保存します。過去のトーク履歴は取得できません。" +
                    " NAS・クラウドへの転送や元アプリへの返信はしません。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    refreshRules()
                    openManager = true
                }) { Text("連携アプリを管理") }
                OutlinedButton(onClick = { AndroidNotificationAccess.openSettings(context) }) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Text("通知アクセス")
                }
            }
        }
    }
    if (openManager) {
        AndroidAppRoutesDialog(
            channels = channels,
            rules = rules,
            onCreateChannel = onCreateChannel,
            onSave = { rule ->
                if (store.saveRoute(rule)) refreshRules()
            },
            onRemove = { pkg ->
                store.removeRoute(pkg)
                refreshRules()
            },
            onDismiss = {
                openManager = false
                refreshRules()
            }
        )
    }
}

@Composable
private fun AndroidAppRoutesDialog(
    channels: List<ChannelEntity>,
    rules: List<AndroidAppRoute>,
    onCreateChannel: (String) -> Unit,
    onSave: (AndroidAppRoute) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val apps = remember(context) { installedNotificationSources(context) }
    val labels = remember(apps) { apps.associateBy { it.packageName } }
    val all = remember(apps, rules) {
        (apps + rules.filterNot { it.packageName in labels }
            .map { SourceApp(it.packageName, it.packageName) })
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<SourceApp> { app ->
                    rules.any { it.packageName == app.packageName && it.enabled }
                }.thenBy { it.title.lowercase() }
            )
    }
    var search by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val currentRoute = rules.firstOrNull { it.packageName == selected }
    var selectedChannel by remember(selected) {
        mutableStateOf(currentRoute?.channelId ?: 0L)
    }
    var keyword by remember(selected) { mutableStateOf(currentRoute?.keyword.orEmpty()) }
    var routeEnabled by remember(selected) { mutableStateOf(currentRoute?.enabled ?: true) }
    var newChannel by remember(selected) { mutableStateOf("") }
    var channelMessage by remember(selected) { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.90f),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected != null) {
                        IconButton(onClick = { selected = null }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "アプリ一覧に戻る")
                        }
                    }
                    Text(
                        if (selected == null) "連携するアプリ" else "通知の振り分け",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("閉じる") }
                }
                HorizontalDivider()
                if (selected == null) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it.take(100) },
                        label = { Text("アプリ名で検索") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "アプリを選んで保存先チャンネルを指定してください。未選択のアプリは読み取りません。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val visible = all.filter {
                        search.isBlank() ||
                            it.title.contains(search, ignoreCase = true) ||
                            it.packageName.contains(search, ignoreCase = true)
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(visible, key = { it.packageName }) { app ->
                            val rule = rules.firstOrNull { it.packageName == app.packageName }
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { selected = app.packageName }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val icon = remember(app.packageName) {
                                    runCatching {
                                        context.packageManager
                                            .getApplicationIcon(app.packageName)
                                            .toBitmap(48, 48).asImageBitmap()
                                    }.getOrNull()
                                }
                                if (icon != null) {
                                    Image(
                                        bitmap = icon, contentDescription = null,
                                        modifier = Modifier.size(36.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Notifications, contentDescription = null,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(app.title, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (rule == null) "未登録"
                                        else if (rule.enabled) "保存先：#${channels.firstOrNull {
                                            it.id == rule.channelId
                                        }?.name ?: "削除されたチャンネル"}"
                                        else "取り込み停止",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                } else {
                    val app = all.firstOrNull { it.packageName == selected }
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(app?.title ?: selected.orEmpty(),
                            style = MaterialTheme.typography.titleMedium)
                        Text(selected.orEmpty(), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("このアプリの通知を取り込む", modifier = Modifier.weight(1f))
                            Switch(
                                checked = routeEnabled,
                                onCheckedChange = { routeEnabled = it }
                            )
                        }
                        Text("保存先チャンネル", fontWeight = FontWeight.SemiBold)
                        if (channels.isEmpty()) Text("チャンネルがありません。先に作成してください。")
                        channels.forEach { channel ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedChannel = channel.id
                                }.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedChannel == channel.id,
                                    onClick = { selectedChannel = channel.id }
                                )
                                Text("${channel.emoji} #${channel.name}",
                                    modifier = Modifier.weight(1f))
                            }
                        }
                        OutlinedTextField(
                            value = newChannel,
                            onValueChange = { newChannel = it.take(60) },
                            label = { Text("新しいチャンネル名（任意）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            enabled = newChannel.isNotBlank(),
                            onClick = {
                                onCreateChannel(newChannel.trim())
                                channelMessage = "追加後、表示されたチャンネルを選択してください"
                                newChannel = ""
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("チャンネルを作成")
                        }
                        if (channelMessage.isNotBlank()) {
                            Text(channelMessage, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = { keyword = it.take(80) },
                            label = { Text("通知フィルター（任意）") },
                            supportingText = {
                                Text("通知タイトルまたは本文にこの語句を含む場合のみ保存")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "認証コードなどの機密通知、進行中通知、グループの集約通知は取り込みません。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            enabled = currentRoute != null,
                            onClick = {
                                selected?.let(onRemove)
                                selected = null
                            }
                        ) { Text("登録を解除") }
                        Spacer(Modifier.weight(1f))
                        Button(
                            enabled = selectedChannel > 0L &&
                                channels.any { it.id == selectedChannel },
                            onClick = {
                                selected?.let { pkg ->
                                    onSave(AndroidAppRoute(
                                        packageName = pkg,
                                        channelId = selectedChannel,
                                        keyword = keyword.trim(),
                                        enabled = routeEnabled
                                    ))
                                }
                                selected = null
                            }
                        ) { Text("保存") }
                    }
                }
            }
        }
    }
}

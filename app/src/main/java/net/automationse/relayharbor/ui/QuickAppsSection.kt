package net.automationse.relayharbor.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val MAX_SHORTCUTS = 32

/** Favorites are stored only on this device and never sent to a server. */
internal class FavoriteAppStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "favorite_app_shortcuts", Context.MODE_PRIVATE
    )

    fun load(): List<String> = runCatching {
        val json = JSONArray(prefs.getString("items", "[]") ?: "[]")
        (0 until json.length()).mapNotNull { index ->
            json.optString(index).takeIf { it.isNotBlank() }
        }.distinct().take(MAX_SHORTCUTS)
    }.getOrDefault(emptyList())

    fun layout(): String = prefs.getString("layout", "grid")?.takeIf { it == "row" } ?: "grid"

    fun saveLayout(value: String) {
        require(value == "grid" || value == "row")
        prefs.edit().putString("layout", value).apply()
    }

    fun save(items: List<String>) {
        val json = JSONArray()
        items.distinct().take(MAX_SHORTCUTS).forEach { json.put(it) }
        prefs.edit().putString("items", json.toString()).apply()
    }
}

internal fun toggleFavoriteApp(items: List<String>, key: String): List<String> = when {
    key in items -> items.filterNot { it == key }
    items.size >= MAX_SHORTCUTS -> items
    else -> items + key
}

internal fun moveFavoriteApp(items: List<String>, key: String, offset: Int): List<String> {
    val oldIndex = items.indexOf(key)
    val newIndex = oldIndex + offset
    if (oldIndex < 0 || offset !in setOf(-1, 1) || newIndex !in items.indices) return items
    return items.toMutableList().apply {
        val other = this[newIndex]
        this[newIndex] = key
        this[oldIndex] = other
    }
}

private data class LaunchableApp(
    val component: ComponentName,
    val label: String,
    val resolveInfo: ResolveInfo
) {
    val key: String get() = component.flattenToString()
}

private fun queryLaunchableApps(context: Context): List<LaunchableApp> {
    val manager = context.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        manager.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        manager.queryIntentActivities(query, 0)
    }
    return matches.mapNotNull { info ->
        val activity = info.activityInfo ?: return@mapNotNull null
        if (!activity.exported || activity.packageName == context.packageName) {
            return@mapNotNull null
        }
        LaunchableApp(
            component = ComponentName(activity.packageName, activity.name),
            label = info.loadLabel(manager)?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: activity.packageName,
            resolveInfo = info
        )
    }.distinctBy { it.key }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

private fun launchFavoriteApp(context: Context, app: LaunchableApp) {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = app.component
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "アプリを起動できませんでした", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun InstalledAppIcon(app: LaunchableApp, size: Dp) {
    val context = LocalContext.current
    val image = remember(app.key) {
        runCatching {
            val drawable = app.resolveInfo.loadIcon(context.packageManager)
            val bitmap = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, 112, 112)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }.getOrNull()
    }
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(size))
    }
}

@Composable
private fun FavoriteAppTile(app: LaunchableApp, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        InstalledAppIcon(app, 48.dp)
        Text(
            app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** This is an in-app shortcut grid, not an Android default HOME replacement. */
@Composable
internal fun QuickAppsSection() {
    val context = LocalContext.current
    val store = remember(context) { FavoriteAppStore(context) }
    var favorites by remember { mutableStateOf(store.load()) }
    var displayMode by remember { mutableStateOf(store.layout()) }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var pickerTab by rememberSaveable { mutableStateOf("select") }

    LaunchedEffect(pickerOpen) {
        loading = true
        apps = withContext(Dispatchers.IO) { queryLaunchableApps(context) }
        loading = false
    }

    fun toggle(key: String) {
        val updated = toggleFavoriteApp(favorites, key)
        if (updated == favorites) {
            if (key !in favorites) {
                Toast.makeText(context, "最大32個まで登録できます", Toast.LENGTH_SHORT).show()
            }
            return
        }
        favorites = updated
        store.save(updated)
    }

    fun move(key: String, offset: Int) {
        val updated = moveFavoriteApp(favorites, key, offset)
        if (updated != favorites) {
            favorites = updated
            store.save(updated)
        }
    }

    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    val pinnedApps = favorites.mapNotNull { appsByKey[it] }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("お気に入りアプリ", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { pickerOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("追加・編集")
            }
        }
        if (pinnedApps.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = displayMode == "grid",
                    onClick = { displayMode = "grid"; store.saveLayout("grid") },
                    label = { Text("4列グリッド") }
                )
                FilterChip(
                    selected = displayMode == "row",
                    onClick = { displayMode = "row"; store.saveLayout("row") },
                    label = { Text("横スクロール") }
                )
            }
        }
        if (loading && pinnedApps.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else if (pinnedApps.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    "端末のアプリを選ぶと、ここからワンタップで起動できます。",
                    modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (displayMode == "row") {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pinnedApps, key = { it.key }) { app ->
                    Box(modifier = Modifier.width(82.dp)) {
                        FavoriteAppTile(app) { launchFavoriteApp(context, app) }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                pinnedApps.chunked(4).forEach { group ->
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEach { app ->
                            Box(modifier = Modifier.weight(1f)) {
                                FavoriteAppTile(app) { launchFavoriteApp(context, app) }
                            }
                        }
                        repeat(4 - group.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        val missing = favorites.size - pinnedApps.size
        if (!loading && missing > 0) {
            Text(
                "利用できないショートカットが${missing}件あります。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = {
                val validKeys = appsByKey.keys
                val updated = favorites.filter { it in validKeys }
                favorites = updated
                store.save(updated)
            }) { Text("利用できないショートカットを整理") }
        }
    }

    if (pickerOpen) {
        val visible = remember(apps, query) {
            apps.filter { app ->
                app.label.contains(query, ignoreCase = true) ||
                    app.component.packageName.contains(query, ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("ホームに表示するアプリ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${favorites.size}/32件選択中。変更は自動保存されます。",
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = pickerTab == "select",
                            onClick = { pickerTab = "select" },
                            label = { Text("アプリの追加・解除") }
                        )
                        FilterChip(
                            selected = pickerTab == "order",
                            onClick = { pickerTab = "order" },
                            label = { Text("並び替え") }
                        )
                    }
                    if (pickerTab == "order") {
                        if (pinnedApps.isEmpty()) {
                            Text("追加したアプリはここで表示順を変更できます。")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(pinnedApps, key = { _, app -> app.key }) { index, app ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        InstalledAppIcon(app, 36.dp)
                                        Text(app.label, modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                        IconButton(
                                            enabled = index > 0,
                                            onClick = { move(app.key, -1) }
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上に移動")
                                        }
                                        IconButton(
                                            enabled = index < pinnedApps.lastIndex,
                                            onClick = { move(app.key, 1) }
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下に移動")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("アプリを検索") }, singleLine = true
                        )
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else if (visible.isEmpty()) {
                            Text("表示できるアプリがありません。")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(visible, key = { it.key }) { app ->
                                    val selected = app.key in favorites
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable { toggle(app.key) }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        InstalledAppIcon(app, 40.dp)
                                        Text(app.label, modifier = Modifier.weight(1f),
                                            maxLines = 2,
                                            style = MaterialTheme.typography.bodyMedium)
                                        Checkbox(checked = selected,
                                            onCheckedChange = { toggle(app.key) })
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { pickerOpen = false }) { Text("完了") }
            }
        )
    }
}

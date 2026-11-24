package net.automationse.relayharbor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast

/**
 * Searchable application drawer for the optional system HOME mode.
 * Uses Android package visibility for ACTION_MAIN/CATEGORY_LAUNCHER only.
 * No QUERY_ALL_PACKAGES or accessibility permissions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun HomeAllAppsDrawer(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val favoriteStore = remember(context) { FavoriteAppStore(context) }
    val favoriteRevision by FavoriteAppStore.changes.collectAsState()
    val favorites = remember(favoriteRevision) { favoriteStore.load() }
    var selectedApp by remember { mutableStateOf<LaunchableApp?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { queryLaunchableApps(context) }
        loading = false
    }

    val displayed = remember(apps, query) {
        apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.component.packageName.contains(query, ignoreCase = true)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("アプリ一覧", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(100) },
                label = { Text("インストール済みアプリを検索") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (loading) {
                CircularProgressIndicator()
            } else if (displayed.isEmpty()) {
                Text(
                    "該当するアプリはありません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "${displayed.size}件 · タップで起動／長押しでメニュー",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 82.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(displayed, key = { it.key }) { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        onDismiss()
                                        launchFavoriteApp(context, app)
                                    },
                                    onLongClick = { selectedApp = app },
                                    onLongClickLabel = "アプリ情報またはお気に入りを操作"
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            InstalledAppIcon(app, 50.dp)
                            Text(
                                app.label,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
    selectedApp?.let { app ->
        val isFavorite = app.key in favorites
        AlertDialog(
            onDismissRequest = { selectedApp = null },
            title = { Text(app.label, maxLines = 2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("アプリの操作を選択",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val updated = toggleFavoriteApp(favorites, app.key)
                            if (updated == favorites && !isFavorite) {
                                Toast.makeText(context, "お気に入りは最大32個までです",
                                    Toast.LENGTH_SHORT).show()
                            } else {
                                favoriteStore.save(updated)
                                Toast.makeText(context,
                                    if (isFavorite) "お気に入りから外しました"
                                    else "お気に入りに追加しました",
                                    Toast.LENGTH_SHORT).show()
                            }
                            selectedApp = null
                        }
                    ) {
                        Text(if (isFavorite) "★ お気に入りから外す"
                            else "☆ お気に入りに追加")
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            selectedApp = null
                            onDismiss()
                            openLaunchableAppInfo(context, app)
                        }
                    ) { Text("アプリ情報・設定を開く") }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedApp = null }) { Text("閉じる") }
            }
        )
    }
}

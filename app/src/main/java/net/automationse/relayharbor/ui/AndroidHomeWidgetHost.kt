package net.automationse.relayharbor.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * OS AppWidgetHost, NOT a fake widget screenshot or a web view.
 * Widget instances belong to this application and cannot be reused from another
 * launcher. Do not request the privileged BIND_APPWIDGET manifest permission.
 */
internal class AndroidHomeWidgetHost(context: Context) {
    private val appContext = context.applicationContext
    private val manager = AppWidgetManager.getInstance(appContext)
    private val host = AppWidgetHost(appContext, 17021)

    fun start() {
        host.startListening()
    }

    fun stop() {
        host.stopListening()
    }

    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders
        .filter {
            it.widgetCategory == 0 ||
                (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0
        }.sortedWith(
            compareBy<AppWidgetProviderInfo> {
                it.loadLabel(appContext.packageManager).orEmpty().lowercase()
            }.thenBy { it.provider.flattenToString() }
        )

    fun allocate(): Int = host.allocateAppWidgetId()

    fun bind(id: Int, provider: AppWidgetProviderInfo): Boolean {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        }
        return manager.bindAppWidgetIdIfAllowed(
            id, provider.profile, provider.provider, options
        )
    }

    fun info(id: Int): AppWidgetProviderInfo? =
        if (id > 0) manager.getAppWidgetInfo(id) else null

    fun delete(id: Int) {
        if (id > 0) host.deleteAppWidgetId(id)
    }

    fun createView(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
        host.createView(context, id, info)

    fun updateSize(id: Int, widthDp: Int, heightDp: Int) {
        if (id <= 0 || widthDp <= 0 || heightDp <= 0) return
        val old = manager.getAppWidgetOptions(id)
        if (old.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, -1) == widthDp &&
            old.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, -1) == heightDp &&
            old.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, -1) == widthDp &&
            old.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, -1) == heightDp) return
        val options = Bundle(old).apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat()))
                )
            }
        }
        manager.updateAppWidgetOptions(id, options)
    }
}

/**
 * A crash/rotation during the system permission or configuration dialog must not
 * lose its allocated widget ID. A later add attempt can release stale pending IDs.
 */
internal class AndroidWidgetPendingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "android_widget_pending_v1", Context.MODE_PRIVATE
    )

    fun pending(): Pair<Int, String>? {
        val id = prefs.getInt("id", -1)
        val profile = prefs.getString("profile", null)
        return if (id > 0 && profile in setOf(HomePanelLayout.COVER,
            HomePanelLayout.EXPANDED)) id to profile!! else null
    }

    fun set(id: Int, profile: String) {
        require(id > 0 && profile in setOf(HomePanelLayout.COVER, HomePanelLayout.EXPANDED))
        prefs.edit().putInt("id", id).putString("profile", profile).apply()
    }

    fun clear() {
        prefs.edit().remove("id").remove("profile").apply()
    }
}

@Composable
internal fun AndroidWidgetPicker(
    host: AndroidHomeWidgetHost,
    onSelect: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val providers = remember(host) { runCatching { host.providers() }.getOrDefault(emptyList()) }
    var query by remember { mutableStateOf("") }
    val visible = providers.filter { info ->
        query.isBlank() || info.label.orEmpty().contains(query, ignoreCase = true) ||
            info.provider.packageName.contains(query, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Androidウィジェットを追加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("ウィジェットを検索") }
                )
                if (visible.isEmpty()) {
                    Text(
                        "使用可能なホームウィジェットがありません。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(visible, key = { it.provider.flattenToString() }) { info ->
                            Column(
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(info) }
                            ) {
                                Text(info.label.orEmpty().ifBlank { info.provider.shortClassName },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(info.provider.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

@Composable
internal fun AndroidHostedWidget(
    host: AndroidHomeWidgetHost,
    appWidgetId: Int
) {
    val info = remember(host, appWidgetId) {
        runCatching { host.info(appWidgetId) }.getOrNull()
    }
    if (info == null) {
        Text(
            "ウィジェットが見つかりません。アプリが削除された場合はカードを削除してください。",
            style = MaterialTheme.typography.bodySmall
        )
        return
    }
    val density = LocalDensity.current
    AndroidView(
        factory = { context -> host.createView(context, appWidgetId, info) },
        modifier = Modifier.fillMaxSize().onSizeChanged { size ->
            val width = with(density) { size.width.toDp().value.toInt() }
            val height = with(density) { size.height.toDp().value.toInt() }
            runCatching { host.updateSize(appWidgetId, width, height) }
        }
    )
}

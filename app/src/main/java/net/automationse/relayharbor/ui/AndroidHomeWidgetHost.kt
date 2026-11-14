package net.automationse.relayharbor.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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

    fun widgetLabel(id: Int): String = runCatching {
        info(id)?.loadLabel(appContext.packageManager).orEmpty()
    }.getOrDefault("").ifBlank { "Androidウィジェット" }

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

internal data class WidgetCatalogItem(
    val info: AppWidgetProviderInfo,
    val application: String,
    val title: String,
    val packageName: String
)

internal object WidgetPickerCatalog {
    fun group(
        widgets: List<WidgetCatalogItem>,
        query: String
    ): List<Pair<String, List<WidgetCatalogItem>>> {
        val needle = query.trim()
        val filtered = widgets.filter { item ->
            needle.isBlank() || listOf(item.application, item.title, item.packageName)
                .any { it.contains(needle, ignoreCase = true) }
        }
        return filtered.groupBy { it.application }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (label, entries) -> label to entries.sortedBy { it.title } }
    }

    fun sizeText(info: AppWidgetProviderInfo): String =
        if (info.minWidth > 0 && info.minHeight > 0)
            "最小 約" + info.minWidth + " × " + info.minHeight + " dp"
        else "サイズは追加後に調整"
}

private fun loadWidgetItems(host: AndroidHomeWidgetHost, context: Context): List<WidgetCatalogItem> {
    val pm = context.packageManager
    return host.providers().map { info ->
        val app = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
        }.getOrDefault(info.provider.packageName)
        val title = runCatching { info.loadLabel(pm) }.getOrNull()
            .orEmpty().ifBlank { info.provider.shortClassName }
        WidgetCatalogItem(info, app, title, info.provider.packageName)
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.application })
}

@Composable
internal fun AndroidWidgetPicker(
    host: AndroidHomeWidgetHost,
    onSelect: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val catalog = remember(host, context) {
        runCatching { loadWidgetItems(host, context) }.getOrDefault(emptyList())
    }
    var query by remember { mutableStateOf("") }
    val groups = remember(catalog, query) { WidgetPickerCatalog.group(catalog, query) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text("ウィジェットを選択", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(catalog.size.toString() + "件のウィジェットから追加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Widgets, contentDescription = null) },
                placeholder = { Text("アプリ名やウィジェット名で検索") },
                shape = RoundedCornerShape(18.dp)
            )
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (catalog.isEmpty()) "ホーム用ウィジェットが見つかりません"
                        else "一致するウィジェットはありません",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 36.dp)
                ) {
                    groups.forEach { (appLabel, options) ->
                        item(key = "app_" + appLabel) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AndroidWidgetDrawable(
                                        modifier = Modifier.size(30.dp),
                                        drawable = remember(options.first()) {
                                            runCatching {
                                                context.packageManager.getApplicationIcon(
                                                    options.first().packageName)
                                            }.getOrNull()
                                        }
                                    )
                                    Text(appLabel, style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f))
                                    Text(options.size.toString() + "件",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(options.size, key = { index ->
                                        options[index].info.provider.flattenToString() + ":" + index
                                    }) { index ->
                                        WidgetPreviewCard(options[index], onSelect)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AndroidWidgetDrawable(
    modifier: Modifier = Modifier,
    drawable: Drawable?
) {
    if (drawable == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Widgets, contentDescription = null)
        }
    } else {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(drawable.constantState?.newDrawable()?.mutate() ?: drawable)
                }
            },
            modifier = modifier
        )
    }
}

@Composable
private fun WidgetPreviewCard(
    item: WidgetCatalogItem,
    onSelect: (AppWidgetProviderInfo) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val drawable = remember(item.info.provider) {
        runCatching {
            item.info.loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
        }.getOrNull() ?: runCatching {
            item.info.loadIcon(context, context.resources.displayMetrics.densityDpi)
        }.getOrNull()
    }
    Card(
        modifier = Modifier.width(212.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        onClick = { onSelect(item.info) }
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(122.dp).padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidWidgetDrawable(Modifier.fillMaxSize(), drawable)
                }
            }
            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2,
                minLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            Text(WidgetPickerCatalog.sizeText(item.info),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
        }
    }
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

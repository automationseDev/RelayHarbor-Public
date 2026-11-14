package net.automationse.relayharbor.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Small, content-height based dashboard. Maintains original saved order and spans.
 * Dense packing backfills earlier gaps with later narrow widgets; the persisted
 * order and user-chosen panel dimensions stay unchanged.
 */
@Composable
internal fun CustomHomeDashboard(
    profile: String,
    layout: List<HomePanel>,
    showHeader: Boolean,
    onShowHeaderChange: (Boolean) -> Unit,
    compactGaps: Boolean,
    onAddAndroidWidget: () -> Unit,
    onDeleteAndroidWidget: (String) -> Unit,
    onCompactGapsChange: (Boolean) -> Unit,
    onLayoutChange: (List<HomePanel>) -> Unit,
    onReset: () -> Unit,
    panelTitle: (String) -> String,
    panelHeader: @Composable (String) -> Unit,
    panelContent: @Composable (String) -> Unit
) {
    val columns = HomePanelLayout.columns(profile)
    var editing by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var resetOpen by rememberSaveable { mutableStateOf(false) }
    var presetOpen by rememberSaveable { mutableStateOf(false) }
    var undoPreset by remember(profile) { mutableStateOf<List<HomePanel>?>(null) }
    val positions = remember(profile) { mutableMapOf<String, Rect>() }
    var activeDragId by remember(profile) { mutableStateOf<String?>(null) }
    var targetId by remember(profile) { mutableStateOf<String?>(null) }
    val activeLayout by rememberUpdatedState(layout)
    val emitLayout by rememberUpdatedState(onLayoutChange)
    val visible = layout.filter { it.visible }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            // With the app-title bar hidden, start Home content close to the safe top edge.
            // The parent Scaffold already keeps it below the Android status bar.
            .padding(
                start = 12.dp, end = 12.dp,
                top = if (showHeader) 10.dp else 2.dp,
                bottom = 10.dp
            ),
        verticalArrangement = Arrangement.spacedBy(if (showHeader) 12.dp else 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("マイホーム", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (profile == HomePanelLayout.EXPANDED)
                        "広い画面用の配置" else "コンパクト画面用の配置",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { editing = !editing }) {
                Icon(
                    if (editing) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = null, modifier = Modifier.size(18.dp)
                )
                Text(if (editing) "編集完了" else "カスタマイズ")
            }
        }
        if (editing) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("RelayHarborの上部ヘッダー", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "オフでタイトルバーを隠し、表示領域を拡大",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Switch(checked = showHeader, onCheckedChange = onShowHeaderChange)
                    }
                    HorizontalDivider()
                    Text(
                        "取っ手を長押しして移動。サイズ変更と情報ウィジェットの追加もできます。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { pickerOpen = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("カードを選択")
                        }
                        TextButton(onClick = {
                            emitLayout(HomePanelLayout.enableMiniWidgets(profile, activeLayout))
                        }) {
                            Text("情報カードを追加")
                        }
                    }
                    TextButton(onClick = onAddAndroidWidget) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Android標準ウィジェットを追加")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("カード間の余白を最小化（3dp）", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                        Switch(checked = compactGaps, onCheckedChange = onCompactGapsChange)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { presetOpen = true }) {
                            Text("おすすめ配置")
                        }
                        if (undoPreset != null) {
                            TextButton(onClick = {
                                undoPreset?.let(emitLayout)
                                undoPreset = null
                            }) { Text("配置を元に戻す") }
                        }
                        TextButton(onClick = { resetOpen = true }) {
                            Text("初期化")
                        }
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("表示するカードがありません", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { pickerOpen = true }) {
                        Text("カードを選択する")
                    }
                    TextButton(onClick = onAddAndroidWidget) {
                        Text("Android標準ウィジェットを追加")
                    }
                }
            }
        } else {
            HomeMasonryGrid(columns = columns, panels = visible,
                gap = if (compactGaps) 3.dp else 10.dp) { panel ->
                val currentLayout by rememberUpdatedState(activeLayout)
                var dragging by remember(panel.id) { mutableStateOf(false) }
                var displacement by remember(panel.id) { mutableStateOf(Offset.Zero) }
                var origin by remember(panel.id) { mutableStateOf(Offset.Zero) }
                val density = LocalDensity.current
                val cellWidth = with(density) {
                    (if (profile == HomePanelLayout.EXPANDED) 90.dp else 175.dp).toPx()
                }
                val cellHeight = with(density) { 110.dp.toPx() }
                var resizeDistance by remember(panel.id) { mutableStateOf(Offset.Zero) }
                var cardMenu by remember(panel.id) { mutableStateOf(false) }
                val visibleIds = currentLayout.filter { it.visible }.map { it.id }
                val panelIndex = visibleIds.indexOf(panel.id)

                DisposableEffect(profile, panel.id) {
                    onDispose { positions.remove(panel.id) }
                }
                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                        .onGloballyPositioned {
                            if (!dragging) positions[panel.id] = it.boundsInRoot()
                        }
                        .zIndex(if (dragging) 3f else 0f)
                        .graphicsLayer {
                            translationX = displacement.x
                            translationY = displacement.y
                        },
                    shape = RoundedCornerShape(18.dp),
                    border = if (editing) BorderStroke(
                        if (targetId == panel.id) 3.dp else 1.dp,
                        if (targetId == panel.id) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary
                    ) else null,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        if (editing) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f)
                                        .pointerInput(profile, panel.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    dragging = true
                                                    activeDragId = panel.id
                                                    targetId = null
                                                    origin = positions[panel.id]?.center ?: Offset.Zero
                                                    displacement = Offset.Zero
                                                },
                                                onDrag = { change, delta ->
                                                    change.consume()
                                                    displacement += delta
                                                    val point = origin + displacement
                                                    targetId = positions.entries.firstOrNull {
                                                        it.key != panel.id &&
                                                            currentLayout.any { current ->
                                                                current.id == it.key && current.visible
                                                            } && it.value.contains(point)
                                                    }?.key
                                                },
                                                onDragEnd = {
                                                    targetId?.let { target ->
                                                        emitLayout(
                                                            HomePanelLayout.move(
                                                                profile, currentLayout, panel.id, target
                                                            )
                                                        )
                                                    }
                                                    displacement = Offset.Zero
                                                    dragging = false
                                                    activeDragId = null
                                                    targetId = null
                                                },
                                                onDragCancel = {
                                                    displacement = Offset.Zero
                                                    dragging = false
                                                    activeDragId = null
                                                    targetId = null
                                                }
                                            )
                                        }
                                        .padding(vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DragIndicator,
                                        contentDescription = "長押ししてカードを移動",
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Text(
                                        if (targetId == panel.id && activeDragId != panel.id)
                                            "ここに移動"
                                        else panelTitle(panel.id),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Box {
                                    IconButton(onClick = { cardMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "カードの編集メニュー")
                                    }
                                    DropdownMenu(
                                        expanded = cardMenu,
                                        onDismissRequest = { cardMenu = false }
                                    ) {
                                        fun change(newLayout: List<HomePanel>) {
                                            emitLayout(newLayout)
                                            cardMenu = false
                                        }
                                        DropdownMenuItem(
                                            text = { Text("前へ移動") },
                                            enabled = panelIndex > 0,
                                            onClick = {
                                                if (panelIndex > 0) change(
                                                    HomePanelLayout.move(
                                                        profile, currentLayout,
                                                        panel.id, visibleIds[panelIndex - 1]
                                                    )
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("後ろへ移動") },
                                            enabled = panelIndex in 0 until visibleIds.lastIndex,
                                            onClick = {
                                                if (panelIndex in 0 until visibleIds.lastIndex) change(
                                                    HomePanelLayout.move(
                                                        profile, currentLayout,
                                                        panel.id, visibleIds[panelIndex + 1]
                                                    )
                                                )
                                            }
                                        )
                                        listOf(
                                            "幅を縮小" to (-1 to 0),
                                            "幅を拡大" to (1 to 0),
                                            "高さを縮小" to (0 to -1),
                                            "高さを拡大" to (0 to 1)
                                        ).forEach { (label, step) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                enabled = when (label) {
                                                    "幅を縮小" -> panel.span > 1
                                                    "幅を拡大" -> panel.span < columns
                                                    "高さを縮小" -> panel.height > 1
                                                    else -> panel.height < 6
                                                },
                                                onClick = {
                                                    change(
                                                        HomePanelLayout.resize(
                                                            profile, currentLayout, panel.id,
                                                            step.first, step.second
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("高さを内容に合わせる") },
                                            enabled = panel.manualHeight &&
                                                !HomePanelLayout.isNative(panel.id),
                                            onClick = {
                                                change(HomePanelLayout.automaticHeight(
                                                    profile, currentLayout, panel.id
                                                ))
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("カードを非表示") },
                                            onClick = {
                                                change(HomePanelLayout.visibility(
                                                    profile, currentLayout, panel.id, false
                                                ))
                                            }
                                        )
                                        if (HomePanelLayout.isNative(panel.id)) {
                                            DropdownMenuItem(
                                                text = { Text("ウィジェットを完全に削除") },
                                                onClick = {
                                                    cardMenu = false
                                                    onDeleteAndroidWidget(panel.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // The section chrome is deliberately OUTSIDE the body scroll:
                        // even when a long card scrolls, its title/actions remain visible.
                        if (!editing) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .heightIn(min = if (panel.id in HomePanelLayout.miniWidgetIds ||
                                            HomePanelLayout.isNative(panel.id))
                                        36.dp else 48.dp)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                panelHeader(panel.id)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val nativeWidget = HomePanelLayout.isNative(panel.id)
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .then(
                                    if (panel.manualHeight || nativeWidget) Modifier.height(
                                        (24 + panel.height * 96).dp
                                    ) else Modifier.heightIn(max = (110 + panel.height * 110).dp)
                                )
                                .clipToBounds()
                                .then(if (nativeWidget) Modifier else
                                    Modifier.verticalScroll(rememberScrollState()))
                                .padding(
                                    horizontal = if (nativeWidget) 0.dp else 10.dp,
                                    vertical = if (nativeWidget) 0.dp else 6.dp
                                )
                        ) {
                            panelContent(panel.id)
                        }
                        if (editing) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 3.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    "↘ サイズ調整",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.pointerInput(profile, panel.id) {
                                        detectDragGestures(
                                            onDragStart = { resizeDistance = Offset.Zero },
                                            onDrag = { change, delta ->
                                                change.consume()
                                                resizeDistance += delta
                                            },
                                            onDragEnd = {
                                                val widthStep = (resizeDistance.x / cellWidth).roundToInt()
                                                val heightStep = (resizeDistance.y / cellHeight).roundToInt()
                                                if (widthStep != 0 || heightStep != 0) {
                                                    emitLayout(HomePanelLayout.resize(
                                                        profile, currentLayout, panel.id,
                                                        widthStep, heightStep
                                                    ))
                                                }
                                                resizeDistance = Offset.Zero
                                            },
                                            onDragCancel = { resizeDistance = Offset.Zero }
                                        )
                                    }.padding(9.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (presetOpen) {
        AlertDialog(
            onDismissRequest = { presetOpen = false },
            title = { Text("ホームのおすすめ配置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "現在のカード配置・表示設定を置き換えます。適用後も「配置を元に戻す」で取り消せます。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    listOf(
                        Triple(HomePanelLayout.PRESET_BALANCED, "バランス型",
                            "時計と天気、チャンネル、お気に入りアプリを両立"),
                        Triple(HomePanelLayout.PRESET_INFORMATION, "情報重視型",
                            "時計・天気・次の予定を上段に配置"),
                        Triple(HomePanelLayout.PRESET_NOTIFICATIONS, "通知重視型",
                            "チャンネルと最近の通知を優先表示")
                    ).forEach { (id, title, description) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = {
                                undoPreset = activeLayout.toList()
                                emitLayout(HomePanelLayout.preset(profile, id, activeLayout))
                                presetOpen = false
                            }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(title, style = MaterialTheme.typography.titleSmall)
                                Text(description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { presetOpen = false }) { Text("閉じる") }
            }
        )
    }
    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("カード・ウィジェットを選択") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    layout.forEach { panel ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                panelTitle(panel.id),
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = panel.visible,
                                onCheckedChange = { visible ->
                                    emitLayout(HomePanelLayout.visibility(
                                        profile, layout, panel.id, visible
                                    ))
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { pickerOpen = false }) { Text("完了") }
            }
        )
    }
    if (resetOpen) {
        AlertDialog(
            onDismissRequest = { resetOpen = false },
            title = { Text("この画面の配置を初期化しますか？") },
            text = {
                Text("カード配置だけを初期状態に戻します。連携設定やチャット履歴は維持します。")
            },
            confirmButton = {
                Button(onClick = { resetOpen = false; onReset() }) { Text("初期化") }
            },
            dismissButton = {
                TextButton(onClick = { resetOpen = false }) { Text("キャンセル") }
            }
        )
    }
}

package net.automationse.relayharbor.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** A safe, user-controlled grid of built-in Home panels. No third-party code execution. */
@Composable
internal fun CustomHomeDashboard(
    profile: String,
    layout: List<HomePanel>,
    onLayoutChange: (List<HomePanel>) -> Unit,
    onReset: () -> Unit,
    panelContent: @Composable (String) -> Unit
) {
    val columns = HomePanelLayout.columns(profile)
    var editMode by rememberSaveable { mutableStateOf(false) }
    var showPanelPicker by rememberSaveable { mutableStateOf(false) }
    var resetConfirm by rememberSaveable { mutableStateOf(false) }
    val positions = remember(profile) { mutableMapOf<String, Rect>() }
    val density = LocalDensity.current
    var activeDragId by remember(profile) { mutableStateOf<String?>(null) }
    var hoverTarget by remember(profile) { mutableStateOf<String?>(null) }
    val liveLayout by rememberUpdatedState(layout)
    // Controls apply only to the current cover/expanded layout, not both.
    val updateLayout by rememberUpdatedState(onLayoutChange)

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        item(span = { GridItemSpan(columns) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("マイホーム", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (profile == HomePanelLayout.EXPANDED)
                            "広い画面用レイアウト" else "コンパクト画面用レイアウト",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { editMode = !editMode }) {
                    Icon(
                        if (editMode) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(if (editMode) "編集完了" else "カスタマイズ")
                }
            }
        }
        if (editMode) {
            item(span = { GridItemSpan(columns) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "上の取っ手を長押しして移動先までドラッグします。移動先が強調されたら指を離してください。幅・高さは − / ＋ でも変更できます。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showPanelPicker = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("表示カードを選択")
                        }
                        TextButton(onClick = { resetConfirm = true }) {
                            Text("このレイアウトを初期化")
                        }
                    }
                }
            }
        }
        items(layout.filter { it.visible }, key = { it.id }, span = { GridItemSpan(it.span) }) { panel ->
            DisposableEffect(profile, panel.id) {
                onDispose { positions.remove(panel.id) }
            }
            var drag by remember(panel.id) { mutableStateOf(Offset.Zero) }
            var dragOrigin by remember(panel.id) { mutableStateOf(Offset.Zero) }
            var dragging by remember(panel.id) { mutableStateOf(false) }
            var resizeDrag by remember(panel.id) { mutableStateOf(Offset.Zero) }
            val unitWidthPx = with(density) {
                (if (profile == HomePanelLayout.EXPANDED) 90.dp else 170.dp).toPx()
            }
            val unitHeightPx = with(density) { 92.dp.toPx() }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .onGloballyPositioned { positions[panel.id] = it.boundsInRoot() }
                    .zIndex(if (dragging) 2f else 0f)
                    .graphicsLayer {
                        translationX = drag.x
                        translationY = drag.y
                    },
                shape = MaterialTheme.shapes.medium,
                border = if (editMode) BorderStroke(
                    if (hoverTarget == panel.id && activeDragId != panel.id) 3.dp else 1.dp,
                    if (hoverTarget == panel.id && activeDragId != panel.id)
                        MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary
                ) else null,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(Modifier.fillMaxWidth()) {
                    if (editMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth().pointerInput(
                                profile, panel.id, editMode
                            ) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragging = true
                                        activeDragId = panel.id
                                        hoverTarget = null
                                        dragOrigin = positions[panel.id]?.center ?: Offset.Zero
                                        drag = Offset.Zero
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        drag += amount
                                        val center = dragOrigin + drag
                                        hoverTarget = positions.entries.firstOrNull {
                                            it.key != panel.id &&
                                                liveLayout.any { visiblePanel ->
                                                    visiblePanel.id == it.key && visiblePanel.visible
                                                } &&
                                                it.value.contains(center)
                                        }?.key
                                    },
                                    onDragEnd = {
                                        hoverTarget?.let { target ->
                                            updateLayout(
                                                HomePanelLayout.move(profile, liveLayout, panel.id, target)
                                            )
                                        }
                                        dragging = false
                                        activeDragId = null
                                        hoverTarget = null
                                        drag = Offset.Zero
                                    },
                                    onDragCancel = {
                                        dragging = false
                                        activeDragId = null
                                        hoverTarget = null
                                        drag = Offset.Zero
                                    }
                                )
                            }.padding(start = 8.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DragIndicator, contentDescription = "長押しして移動")
                            Text(
                                if (hoverTarget == panel.id && activeDragId != panel.id)
                                    "ここに移動"
                                else HomePanelLayout.titles[panel.id].orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    updateLayout(
                                        HomePanelLayout.visibility(profile, layout, panel.id, false)
                                    )
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "カードを非表示")
                            }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth()
                            .heightIn(max = (110 + panel.height * 110).dp)
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        panelContent(panel.id)
                    }
                    if (editMode) {
                        val visibleIds = liveLayout.filter { it.visible }.map { it.id }
                        val index = visibleIds.indexOf(panel.id)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(
                                enabled = index > 0,
                                onClick = {
                                    if (index > 0) updateLayout(
                                        HomePanelLayout.move(
                                            profile, liveLayout, panel.id, visibleIds[index - 1]
                                        )
                                    )
                                }
                            ) { Text("前に移動") }
                            TextButton(
                                enabled = index in 0 until visibleIds.lastIndex,
                                onClick = {
                                    if (index in 0 until visibleIds.lastIndex) updateLayout(
                                        HomePanelLayout.move(
                                            profile, liveLayout, panel.id, visibleIds[index + 1]
                                        )
                                    )
                                }
                            ) { Text("後ろに移動") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("幅", style = MaterialTheme.typography.labelSmall)
                            IconButton(
                                onClick = {
                                    updateLayout(HomePanelLayout.resize(profile, layout, panel.id, -1, 0))
                                },
                                enabled = panel.span > 1,
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.Remove, contentDescription = "幅を縮小") }
                            Text(panel.span.toString() + "/" + columns,
                                style = MaterialTheme.typography.labelSmall)
                            IconButton(
                                onClick = {
                                    updateLayout(HomePanelLayout.resize(profile, layout, panel.id, 1, 0))
                                },
                                enabled = panel.span < columns,
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.Add, contentDescription = "幅を拡大") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("高さ", style = MaterialTheme.typography.labelSmall)
                            IconButton(
                                onClick = {
                                    updateLayout(HomePanelLayout.resize(profile, layout, panel.id, 0, -1))
                                },
                                enabled = panel.height > 1,
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.Remove, contentDescription = "高さを縮小") }
                            Text(panel.height.toString() + "/6",
                                style = MaterialTheme.typography.labelSmall)
                            IconButton(
                                onClick = {
                                    updateLayout(HomePanelLayout.resize(profile, layout, panel.id, 0, 1))
                                },
                                enabled = panel.height < 6,
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.Add, contentDescription = "高さを拡大") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                "↘ サイズ変更",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.pointerInput(profile, panel.id) {
                                    detectDragGestures(
                                        onDragStart = { resizeDrag = Offset.Zero },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            resizeDrag += delta
                                        },
                                        onDragEnd = {
                                            val widthDelta = (resizeDrag.x / unitWidthPx).roundToInt()
                                            val heightDelta = (resizeDrag.y / unitHeightPx).roundToInt()
                                            if (widthDelta != 0 || heightDelta != 0) {
                                                updateLayout(
                                                    HomePanelLayout.resize(
                                                        profile, layout, panel.id,
                                                        widthDelta, heightDelta
                                                    )
                                                )
                                            }
                                            resizeDrag = Offset.Zero
                                        },
                                        onDragCancel = { resizeDrag = Offset.Zero }
                                    )
                                }.padding(6.dp)
                            )
                        }
                    }
                }
            }
        }
        if (layout.none { it.visible }) {
            item(span = { GridItemSpan(columns) }) {
                Text(
                    "表示するカードがありません。「カスタマイズ」→「表示カードを選択」から復元できます。",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showPanelPicker) {
        AlertDialog(
            onDismissRequest = { showPanelPicker = false },
            title = { Text("ホームに表示するカード") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    layout.forEach { panel ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(HomePanelLayout.titles[panel.id].orEmpty())
                            Switch(
                                checked = panel.visible,
                                onCheckedChange = { visible ->
                                    updateLayout(
                                        HomePanelLayout.visibility(profile, layout, panel.id, visible)
                                    )
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPanelPicker = false }) { Text("完了") }
            }
        )
    }
    if (resetConfirm) {
        AlertDialog(
            onDismissRequest = { resetConfirm = false },
            title = { Text("現在のレイアウトを初期化しますか？") },
            text = {
                Text("この画面幅のカード配置だけをリセットします。連携設定やチャット履歴は削除しません。")
            },
            confirmButton = {
                Button(onClick = {
                    resetConfirm = false
                    onReset()
                }) { Text("初期化") }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirm = false }) { Text("キャンセル") }
            }
        )
    }
}

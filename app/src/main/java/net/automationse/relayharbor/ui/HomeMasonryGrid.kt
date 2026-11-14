package net.automationse.relayharbor.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small bounded grid (max six built-in cards) measured by actual content height.
 * Unlike LazyVerticalGrid this places the next item into the earliest open column.
 * Avoid wrapping panels inside their own scroll containers: the outer screen scrolls.
 */
@Composable
internal fun HomeMasonryGrid(
    columns: Int,
    panels: List<HomePanel>,
    gap: Dp = 8.dp,
    card: @Composable (HomePanel) -> Unit
) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            panels.forEach { item ->
                key(item.id) { card(item) }
            }
        }
    ) { measurables, constraints ->
        val spacing = gap.roundToPx()
        val contentWidth = constraints.maxWidth
        val columnWidth = ((contentWidth - spacing * (columns - 1)) / columns).coerceAtLeast(1)
        val widths = panels.map {
            it.span.coerceIn(1, columns) * columnWidth +
                (it.span.coerceIn(1, columns) - 1) * spacing
        }
        val placeables = measurables.mapIndexed { index, measurable ->
            measurable.measure(
                Constraints(
                    minWidth = widths[index],
                    maxWidth = widths[index],
                    minHeight = 0,
                    maxHeight = Constraints.Infinity
                )
            )
        }
        val placements = HomeMasonry.place(
            columns = columns,
            spans = panels.map { it.span },
            heightsPx = placeables.map { it.height },
            cellWidthPx = columnWidth,
            gapPx = spacing
        )
        layout(contentWidth, HomeMasonry.totalHeight(placements)) {
            placeables.forEachIndexed { index, placeable ->
                val tile = placements[index]
                placeable.placeRelative(
                    x = tile.firstColumn * (columnWidth + spacing),
                    y = tile.topPx
                )
            }
        }
    }
}

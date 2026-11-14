package net.automationse.relayharbor.ui

/**
 * Pure pixel-based skyline placement for a dashboard containing a small,
 * bounded number of trusted panels. Cards can span 1..columns cells.
 *
 * A placement always chooses the left-most group with the lowest *top*;
 * unlike a regular grid, items next to a tall panel can begin immediately
 * below the preceding short panel. No overlaps and no reordering of inputs.
 */
internal data class MasonryTile(
    val firstColumn: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int
)

internal object HomeMasonry {
    fun place(
        columns: Int,
        spans: List<Int>,
        heightsPx: List<Int>,
        cellWidthPx: Int,
        gapPx: Int
    ): List<MasonryTile> {
        require(columns in 1..8)
        require(spans.size == heightsPx.size)
        require(cellWidthPx > 0 && gapPx >= 0)
        val skyline = IntArray(columns)
        return spans.indices.map { index ->
            val span = spans[index].coerceIn(1, columns)
            val height = heightsPx[index].coerceAtLeast(0)
            var bestColumn = 0
            var bestTop = Int.MAX_VALUE
            for (col in 0..(columns - span)) {
                var top = 0
                for (offset in 0 until span) top = maxOf(top, skyline[col + offset])
                if (top < bestTop) {
                    bestTop = top
                    bestColumn = col
                }
            }
            val tile = MasonryTile(
                firstColumn = bestColumn,
                topPx = bestTop,
                widthPx = cellWidthPx * span + gapPx * (span - 1),
                heightPx = height
            )
            for (offset in 0 until span) {
                skyline[bestColumn + offset] = bestTop + height + gapPx
            }
            tile
        }
    }

    fun totalHeight(tiles: List<MasonryTile>): Int =
        tiles.maxOfOrNull { it.topPx + it.heightPx } ?: 0
}

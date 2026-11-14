package net.automationse.relayharbor.ui

/**
 * Pixel-based, gap-aware packing for a small collection of trusted Home panels.
 * Unlike a skyline, a later narrow widget can occupy a pocket left ABOVE an
 * already placed wide panel. The saved order and dimensions never change.
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

        val tiles = mutableListOf<MasonryTile>()
        val occupiedSpans = mutableListOf<Int>()
        spans.indices.forEach { index ->
            val span = spans[index].coerceIn(1, columns)
            val height = heightsPx[index].coerceAtLeast(0)
            // Candidate top edges are enough: any valid packed position can
            // move upwards until either y=0 or the bottom of an existing tile.
            val candidateTops = (listOf(0) + tiles.map { it.topPx + it.heightPx + gapPx })
                .distinct().sorted()
            var chosenColumn = -1
            var chosenTop = 0

            for (top in candidateTops) {
                for (col in 0..(columns - span)) {
                    val blocked = tiles.indices.any { placedIndex ->
                        val other = tiles[placedIndex]
                        val horizontalOverlap =
                            col < other.firstColumn + occupiedSpans[placedIndex] &&
                                other.firstColumn < col + span
                        val verticalOverlap =
                            top < other.topPx + other.heightPx + gapPx &&
                                other.topPx < top + height + gapPx
                        horizontalOverlap && verticalOverlap
                    }
                    if (!blocked) {
                        chosenColumn = col
                        chosenTop = top
                        break
                    }
                }
                if (chosenColumn >= 0) break
            }
            check(chosenColumn >= 0) { "No free Home panel location" }
            tiles += MasonryTile(
                firstColumn = chosenColumn,
                topPx = chosenTop,
                widthPx = cellWidthPx * span + gapPx * (span - 1),
                heightPx = height
            )
            occupiedSpans += span
        }
        return tiles
    }

    fun totalHeight(tiles: List<MasonryTile>): Int =
        tiles.maxOfOrNull { it.topPx + it.heightPx } ?: 0
}

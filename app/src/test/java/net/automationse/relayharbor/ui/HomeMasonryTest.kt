package net.automationse.relayharbor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMasonryTest {
    @Test fun tallTilesDoNotForceShortNeighborsToLeaveUnusedVerticalSpace() {
        val placements = HomeMasonry.place(
            columns = 2,
            spans = listOf(1, 1, 1),
            heightsPx = listOf(500, 100, 80),
            cellWidthPx = 140,
            gapPx = 8
        )
        assertEquals(0, placements[0].firstColumn)
        assertEquals(1, placements[1].firstColumn)
        assertEquals(1, placements[2].firstColumn)
        assertEquals(108, placements[2].topPx)
        assertEquals(500, HomeMasonry.totalHeight(placements))
    }

    @Test fun wideTileAllowsFollowingNarrowTileToBackfillAnEarlierGap() {
        val placements = HomeMasonry.place(
            columns = 4,
            spans = listOf(1, 2, 1, 4, 1),
            heightsPx = listOf(200, 100, 250, 80, 70),
            cellWidthPx = 100,
            gapPx = 8
        )
        assertEquals(208, placements[1].widthPx)
        assertEquals(258, placements[3].topPx)
        assertEquals(424, placements[3].widthPx)
        assertEquals(108, placements[4].topPx)
        assertEquals(338, HomeMasonry.totalHeight(placements))
    }

    @Test fun widgetsAfterWidePanelFillTheShortColumnHole() {
        val placements = HomeMasonry.place(
            columns = 2,
            spans = listOf(1, 1, 2, 1, 1),
            heightsPx = listOf(500, 150, 100, 120, 120),
            cellWidthPx = 140,
            gapPx = 8
        )
        assertEquals(508, placements[2].topPx)
        assertEquals(1, placements[3].firstColumn)
        assertEquals(158, placements[3].topPx)
        assertEquals(1, placements[4].firstColumn)
        assertEquals(286, placements[4].topPx)
        assertEquals(608, HomeMasonry.totalHeight(placements))
    }

    @Test fun allCardsAreLaidOutOnceAndNeverOverlap() {
        val placements = HomeMasonry.place(
            columns = 4,
            spans = listOf(2, 1, 3, 1, 2, 4),
            heightsPx = listOf(90, 250, 60, 100, 140, 50),
            cellWidthPx = 100,
            gapPx = 8
        )
        assertEquals(6, placements.size)
        for (i in placements.indices) {
            val left = placements[i]
            assertTrue(left.widthPx > 0 && left.topPx >= 0)
            for (j in (i + 1) until placements.size) {
                val right = placements[j]
                val xLeft = left.firstColumn * 108
                val xRight = right.firstColumn * 108
                val intersects = xLeft < xRight + right.widthPx &&
                    xRight < xLeft + left.widthPx &&
                    left.topPx < right.topPx + right.heightPx &&
                    right.topPx < left.topPx + left.heightPx
                assertFalse("overlap: $i and $j", intersects)
            }
        }
    }

    @Test fun singleColumnIsSafeAndEmptyLayoutHasZeroHeight() {
        assertEquals(emptyList<MasonryTile>(), HomeMasonry.place(
            1, emptyList(), emptyList(), 100, 4
        ))
        assertEquals(0, HomeMasonry.totalHeight(emptyList()))
        val placed = HomeMasonry.place(1, listOf(5, 0), listOf(50, 40), 100, 8)
        assertEquals(listOf(0, 58), placed.map { it.topPx })
        assertEquals(98, HomeMasonry.totalHeight(placed))
    }

    @Test fun legacyHomePreferencesRemainValidWithoutMigration() {
        val previous = HomePanelLayout.default(HomePanelLayout.EXPANDED)
        val edited = HomePanelLayout.resize(
            HomePanelLayout.EXPANDED, previous, "overview", -1, 2
        )
        assertEquals(edited, HomePanelLayout.decode(
            HomePanelLayout.EXPANDED, HomePanelLayout.encode(HomePanelLayout.EXPANDED, edited)
        ))
    }
}

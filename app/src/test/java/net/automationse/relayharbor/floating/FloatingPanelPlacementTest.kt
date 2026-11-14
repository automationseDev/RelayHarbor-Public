package net.automationse.relayharbor.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingPanelPlacementTest {
    @Test fun rightSidePanelSitsLeftOfHandle() {
        val handleX = 950
        val width = 200
        val gap = 12
        val x = FloatingPanelPlacement.x(
            screenWidth = 1000, handleX = handleX, handleWidth = 50,
            panelWidth = width, gap = gap, side = "right"
        )
        assertEquals(738, x)
        assertTrue(x + width + gap <= handleX)
    }

    @Test fun leftSidePanelSitsRightOfHandle() {
        val handleWidth = 50
        val gap = 12
        val x = FloatingPanelPlacement.x(
            screenWidth = 1000, handleX = 0, handleWidth = handleWidth,
            panelWidth = 200, gap = gap, side = "left"
        )
        assertEquals(62, x)
        assertTrue(x >= handleWidth + gap)
    }

    @Test fun foldCoverScreenPanelRemainsReachable() {
        val width = 520
        val handleWidth = 96
        val panelWidth = 350
        val x = FloatingPanelPlacement.x(
            width, width - handleWidth, handleWidth,
            panelWidth, 12, "right"
        )
        assertTrue(x >= 0)
        assertTrue(x + panelWidth <= width)
        assertTrue(x + panelWidth < width - 1)
    }

    @Test fun extremelyNarrowLandscapeClampsWithoutNegativeCoordinates() {
        val width = 240
        val panel = 230
        val right = FloatingPanelPlacement.x(width, 196, 44, panel, 9, "right")
        val left = FloatingPanelPlacement.x(width, 0, 44, panel, 9, "left")
        assertTrue(right in 0..10)
        assertTrue(left in 0..10)
    }

    @Test fun panelHeightAndYNeverOverflowUsableScreenWhereSpacePermits() {
        val height = 1700
        val panel = 970
        val top = 120
        val bottom = 160
        val y = FloatingPanelPlacement.y(height, 1500, panel, top, bottom)
        assertEquals(570, y)
        assertTrue(y + panel + bottom <= height)
        assertEquals(top, FloatingPanelPlacement.y(height, 0, panel, top, bottom))
        assertEquals(1420, FloatingPanelPlacement.maxHeight(height, top, bottom))
    }

    @Test fun foldSmallHeightStillReturnsClampedMenuHeight() {
        assertEquals(1, FloatingPanelPlacement.maxHeight(120, 80, 80))
        val y = FloatingPanelPlacement.y(120, 300, 1, 80, 80)
        assertEquals(80, y)
        assertFalse(y < 0)
    }
}

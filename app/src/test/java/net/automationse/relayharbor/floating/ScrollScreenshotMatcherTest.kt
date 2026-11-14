package net.automationse.relayharbor.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollScreenshotMatcherTest {
    private fun synthetic(offset: Int, width: Int = 80, height: Int = 300): ScrollFrameSample {
        val pixels = IntArray(width * height) { position ->
            val y = position / width
            val x = position % width
            when {
                y < 27 -> 0xff334466.toInt() // unchanged status + header bar
                y >= height - 32 -> 0xff171923.toInt() // unchanged bottom controls
                else -> {
                    val row = y - 27 + offset
                    val hash = ((row * 93841) xor (x * 19739) xor
                        (row * x * 523)) * 31
                    val gray = (hash xor (hash ushr 16)) and 255
                    (0xff shl 24) or (gray shl 16) or
                        (((gray * 3 + x * 7) and 255) shl 8) or
                        ((gray * 11 + row * 5) and 255)
                }
            }
        }
        return ScrollFrameSample(width, height, pixels)
    }

    @Test fun unchangedViewportDoesNotCreateLongScreenshotStep() {
        assertNull(ScrollScreenshotMatcher.detectBand(synthetic(0), synthetic(0)))
    }

    @Test fun fixedHeadersAndFootersDetectedAndScrollOffsetRecovered() {
        val first = synthetic(0)
        val second = synthetic(96)
        val region = ScrollScreenshotMatcher.detectBand(first, second)
        assertNotNull(region)
        val band = requireNotNull(region)
        assertTrue("fixed header excluded", band.top in 18..35)
        assertTrue("fixed footer excluded", band.bottom in 250..280)
        val result = ScrollScreenshotMatcher.match(first, second, band)
        assertNotNull(result)
        assertEquals(96, result!!.shift)
        assertEquals(0.0, result.error, 0.001)
        val third = synthetic(170)
        assertEquals(74, ScrollScreenshotMatcher.match(second, third, band)?.shift)
    }

    @Test fun largeScrollWithoutOverlapCannotBeFalselyStitched() {
        val first = synthetic(0)
        val later = synthetic(220)
        val region = requireNotNull(ScrollScreenshotMatcher.detectBand(first, later))
        assertNull(ScrollScreenshotMatcher.match(first, later, region))
    }

    @Test fun mismatchedOrientationIsRejectedBeforeMatching() {
        val first = synthetic(0, width = 80)
        val second = synthetic(90, width = 96)
        try {
            ScrollScreenshotMatcher.detectBand(first, second)
            throw AssertionError("Mismatched screen dimensions must be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun limitsPreventBitmapAllocationsThatCouldExhaustMemory() {
        assertEquals(7, ScrollScreenshotMatcher.MAX_FRAMES)
        assertTrue(ScrollScreenshotMatcher.fitOutput(1200, 12000))
        assertFalse(ScrollScreenshotMatcher.fitOutput(1800, 16001))
        assertFalse(ScrollScreenshotMatcher.fitOutput(2500, 15000))
        assertFalse(ScrollScreenshotMatcher.fitOutput(0, 100))
        assertFalse(ScrollScreenshotMatcher.fitOutput(100, 0))
    }

    @Test fun noPossibleOverlapReturnsNull() {
        val frame = synthetic(0)
        val band = ScrollBand(0, 20)
        assertNull(ScrollScreenshotMatcher.match(frame, synthetic(20), band))
    }
}

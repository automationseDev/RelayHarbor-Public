package net.automationse.relayharbor.floating

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * JVM-testable heuristic to stitch screenshots of the same vertical scrolling
 * surface. Fixed top/bottom toolbars are detected and only included once.
 *
 * All coordinates here refer to downsampled RGB thumbnails, not physical px.
 * The user scrolls manually by <70% of the visible content per step.
 */
internal data class ScrollFrameSample(
    val width: Int,
    val height: Int,
    val pixels: IntArray
) {
    init {
        require(width in 8..512 && height in 40..4096)
        require(pixels.size == width * height)
    }
}

internal data class ScrollBand(val top: Int, val bottom: Int) {
    val height: Int get() = bottom - top
}

internal data class ScrollMatch(val shift: Int, val error: Double) {
    val overlap: Int get() = 0 // Use band.height - shift for overlap.
}

internal object ScrollScreenshotMatcher {
    const val MAX_FRAMES = 7
    const val MAX_PIXELS = 32_000_000L
    const val MAX_HEIGHT = 16_000

    private fun difference(a: Int, b: Int): Int {
        val red = abs(((a ushr 16) and 255) - ((b ushr 16) and 255))
        val green = abs(((a ushr 8) and 255) - ((b ushr 8) and 255))
        val blue = abs((a and 255) - (b and 255))
        return (red + green + blue) / 3
    }

    private fun checkPair(a: ScrollFrameSample, b: ScrollFrameSample) {
        require(a.width == b.width && a.height == b.height) {
            "画面の向きやサイズが変わりました。スクロール撮影を最初からやり直してください"
        }
    }

    /** Detect largest changing central region to exclude fixed status/header/footer bars. */
    fun detectBand(a: ScrollFrameSample, b: ScrollFrameSample): ScrollBand? {
        checkPair(a, b)
        val delta = BooleanArray(a.height)
        for (y in 0 until a.height) {
            var total = 0
            var noticeable = 0
            for (x in 2 until a.width - 2 step 2) {
                val i = y * a.width + x
                val d = difference(a.pixels[i], b.pixels[i])
                total += d
                if (d > 16) noticeable++
            }
            val n = ((a.width - 4) + 1) / 2
            delta[y] = total.toFloat() / n > 3.5f || noticeable >= 3
        }
        // Avoid picking up a one-row clock or typing cursor animation.
        fun populated(y: Int): Boolean =
            ((y - 5).coerceAtLeast(0)..(y + 5).coerceAtMost(a.height - 1))
                .count { delta[it] } >= 3
        val start = (0 until a.height).firstOrNull { populated(it) } ?: return null
        val end = (a.height - 1 downTo 0).firstOrNull { populated(it) } ?: return null
        val top = (start + 2).coerceAtLeast(0)
        val bottom = (end - 2).coerceAtMost(a.height)
        if (bottom - top < a.height * 0.28) return null
        return ScrollBand(top, bottom)
    }

    /**
     * Returns the amount to advance in sample rows. The new screenshot's top
     * must overlap the previous screenshot's bottom.
     *
     * Reject weak or ambiguous matches instead of silently creating a corrupt
     * stitched file. Candidate scores compare actual RGB pixels, not hashes.
     */
    fun match(a: ScrollFrameSample, b: ScrollFrameSample, band: ScrollBand): ScrollMatch? {
        checkPair(a, b)
        if (band.top < 0 || band.bottom > a.height || band.height < 24) return null
        val minShift = max(3, band.height / 18)
        val maxShift = min(band.height * 72 / 100, band.height - max(18, band.height / 5))
        if (minShift > maxShift) return null

        var bestShift = -1
        var bestScore = Double.POSITIVE_INFINITY
        var runnerUp = Double.POSITIVE_INFINITY
        for (shift in minShift..maxShift) {
            val overlap = band.height - shift
            val rowStride = max(1, overlap / 65)
            var error = 0L
            var count = 0
            for (y in 0 until overlap step rowStride) {
                val aOffset = (band.top + shift + y) * a.width
                val bOffset = (band.top + y) * a.width
                for (x in 2 until a.width - 2 step 2) {
                    error += difference(a.pixels[aOffset + x], b.pixels[bOffset + x])
                    count++
                }
            }
            val score = error.toDouble() / count.coerceAtLeast(1)
            if (score < bestScore) {
                runnerUp = bestScore
                bestScore = score
                bestShift = shift
            } else if (score < runnerUp) runnerUp = score
        }
        // Mostly identical blank pages can match many different offsets.
        if (bestShift < 0 || bestScore > 16.0) return null
        if (runnerUp - bestScore < 0.25 && bestScore > 1.5) return null
        return ScrollMatch(bestShift, bestScore)
    }

    fun fitOutput(width: Int, height: Int): Boolean =
        width > 0 && height > 0 &&
            height <= MAX_HEIGHT &&
            width.toLong() * height.toLong() <= MAX_PIXELS
}

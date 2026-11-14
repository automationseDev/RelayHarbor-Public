package net.automationse.relayharbor.floating

/**
 * Position a floating menu immediately beside its drag handle. Keeping the
 * handle outside the panel lets the user tap the same handle to close it.
 * Pixel arguments are display-relative; no Android dependency.
 */
internal object FloatingPanelPlacement {
    fun x(
        screenWidth: Int, handleX: Int, handleWidth: Int,
        panelWidth: Int, gap: Int, side: String
    ): Int {
        val available = (screenWidth - panelWidth).coerceAtLeast(0)
        val leftOfHandle = handleX - panelWidth - gap
        val rightOfHandle = handleX + handleWidth + gap
        val preferred = if (side == "left") rightOfHandle else leftOfHandle
        val fallback = if (side == "left") leftOfHandle else rightOfHandle
        val value = when {
            preferred in 0..available -> preferred
            fallback in 0..available -> fallback
            else -> preferred.coerceIn(0, available)
        }
        return value
    }

    fun y(
        screenHeight: Int, handleY: Int, panelHeight: Int,
        topMargin: Int, bottomMargin: Int
    ): Int {
        val lastTop = (screenHeight - panelHeight - bottomMargin)
            .coerceAtLeast(topMargin)
        return handleY.coerceIn(topMargin, lastTop)
    }

    fun maxHeight(screenHeight: Int, topMargin: Int, bottomMargin: Int): Int =
        (screenHeight - topMargin - bottomMargin).coerceAtLeast(1)
}

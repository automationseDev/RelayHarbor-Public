package net.automationse.relayharbor.ui

/** One definition of card density for the inbox, chat, and preview surfaces. */
internal enum class CardDensity { COMPACT, STANDARD, DETAILED }

/**
 * Available window width, not physical model detection, controls pane layout.
 * 600dp makes opened foldables usable while compact cover screens remain single-pane.
 */
internal object ResponsiveLayoutPolicy {
    const val TWO_PANE_MIN_WIDTH_DP = 600
    fun useTwoPanes(availableWidthDp: Int): Boolean =
        availableWidthDp >= TWO_PANE_MIN_WIDTH_DP
}

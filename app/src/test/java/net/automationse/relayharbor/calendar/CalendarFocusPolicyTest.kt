package net.automationse.relayharbor.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalendarFocusPolicyTest {
    private val today = LocalDate.of(2026, 11, 24)
    private val yesterday = today.minusDays(1)

    @Test fun todayAlwaysReceivesPrimaryFrameEvenWhenAnotherDayIsSelected() {
        assertEquals(CalendarOutline.TODAY, CalendarFocusPolicy.outline(today, today, yesterday))
        assertEquals(CalendarOutline.SELECTED,
            CalendarFocusPolicy.outline(yesterday, today, yesterday))
        assertEquals(CalendarOutline.NONE,
            CalendarFocusPolicy.outline(today.plusDays(1), today, yesterday))
        assertEquals(CalendarOutline.NONE, CalendarFocusPolicy.outline(null, today, today))
    }

    @Test fun midnightMovesFollowTodaySelectionButKeepsManualSelection() {
        val tomorrow = today.plusDays(1)
        assertEquals(tomorrow, CalendarFocusPolicy.afterDayChange(today, today, tomorrow))
        assertEquals(yesterday,
            CalendarFocusPolicy.afterDayChange(today, yesterday, tomorrow))
    }
}

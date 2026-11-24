package net.automationse.relayharbor.calendar

import java.time.LocalDate

/** Keeps the real current date visually distinct from a manually selected day. */
internal enum class CalendarOutline { NONE, TODAY, SELECTED }

internal object CalendarFocusPolicy {
    fun outline(date: LocalDate?, today: LocalDate, selected: LocalDate): CalendarOutline =
        when {
            date == null -> CalendarOutline.NONE
            date == today -> CalendarOutline.TODAY
            date == selected -> CalendarOutline.SELECTED
            else -> CalendarOutline.NONE
        }

    /** Advance the selected date at midnight only when it was following "today". */
    fun afterDayChange(previousToday: LocalDate, selected: LocalDate, now: LocalDate): LocalDate =
        if (selected == previousToday) now else selected
}

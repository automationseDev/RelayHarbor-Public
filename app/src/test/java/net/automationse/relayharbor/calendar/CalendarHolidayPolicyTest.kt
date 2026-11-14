package net.automationse.relayharbor.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarHolidayPolicyTest {
    private fun day(value: String) = LocalDate.parse(value)

    @Test fun saturdayBlueAndSundayRed() {
        assertEquals(CalendarDayTone.SATURDAY, CalendarHolidayPolicy.tone(day("2026-11-14")))
        assertEquals(CalendarDayTone.HOLIDAY, CalendarHolidayPolicy.tone(day("2026-11-15")))
        assertEquals(CalendarDayTone.REGULAR, CalendarHolidayPolicy.tone(day("2026-11-16")))
    }

    @Test fun holidayOnSaturdayTakesPrecedenceOverWeekendBlue() {
        assertEquals("勤労感謝の日", JapanesePublicHolidays.name(day("2026-11-23")))
        assertEquals(CalendarDayTone.HOLIDAY, CalendarHolidayPolicy.tone(day("2026-11-23")))
        assertEquals(CalendarDayTone.HOLIDAY, CalendarHolidayPolicy.tone(day("2027-01-01")))
    }

    @Test fun official2026JapanHolidaysIncludeSubstituteAndCitizenDays() {
        val expected = mapOf(
            "2026-01-01" to "元日",
            "2026-01-12" to "成人の日",
            "2026-02-11" to "建国記念の日",
            "2026-02-23" to "天皇誕生日",
            "2026-03-20" to "春分の日",
            "2026-04-29" to "昭和の日",
            "2026-05-03" to "憲法記念日",
            "2026-05-04" to "みどりの日",
            "2026-05-05" to "こどもの日",
            "2026-05-06" to "振替休日",
            "2026-07-20" to "海の日",
            "2026-08-11" to "山の日",
            "2026-09-21" to "敬老の日",
            "2026-09-22" to "国民の休日",
            "2026-09-23" to "秋分の日",
            "2026-10-12" to "スポーツの日",
            "2026-11-03" to "文化の日",
            "2026-11-23" to "勤労感謝の日"
        )
        assertEquals(expected.size, JapanesePublicHolidays.year(2026).size)
        expected.forEach { (date, holiday) ->
            assertEquals(date, holiday, JapanesePublicHolidays.name(day(date)))
        }
        assertNull(JapanesePublicHolidays.name(day("2026-09-24")))
    }

    @Test fun official2027JapanHolidaysIncludeSpringEquinoxSubstitute() {
        assertEquals("春分の日", JapanesePublicHolidays.name(day("2027-03-21")))
        assertEquals("振替休日", JapanesePublicHolidays.name(day("2027-03-22")))
        assertEquals("敬老の日", JapanesePublicHolidays.name(day("2027-09-20")))
        assertEquals("スポーツの日", JapanesePublicHolidays.name(day("2027-10-11")))
        assertEquals(17, JapanesePublicHolidays.year(2027).size)
    }

    @Test fun paidLeaveOverridesWeekdayAndPersistsLabelInDataObject() {
        val date = day("2026-11-18")
        val paid = PersonalHoliday.cleaned(PersonalHolidayType.PAID_LEAVE, "  午後休  ")
        assertEquals("午後休", paid.displayLabel)
        assertEquals(CalendarDayTone.HOLIDAY, CalendarHolidayPolicy.tone(date, paid))
        assertEquals("午後休", CalendarHolidayPolicy.description(date, paid))
        val ordinary = PersonalHoliday.cleaned(PersonalHolidayType.HOLIDAY, "")
        assertEquals("休日", ordinary.displayLabel)
    }

    @Test fun markWorkdayAndResetRestoreDefaultWeekendAndNationalHoliday() {
        val saturday = day("2026-11-14")
        val publicHoliday = day("2026-11-23")
        val workday = PersonalHoliday(PersonalHolidayType.WORKDAY)
        assertEquals(CalendarDayTone.REGULAR, CalendarHolidayPolicy.tone(saturday, workday))
        assertEquals(CalendarDayTone.REGULAR, CalendarHolidayPolicy.tone(publicHoliday, workday))
        assertEquals("勤務日", CalendarHolidayPolicy.description(publicHoliday, workday))
        assertEquals(CalendarDayTone.SATURDAY, CalendarHolidayPolicy.tone(saturday, null))
        assertEquals(CalendarDayTone.HOLIDAY, CalendarHolidayPolicy.tone(publicHoliday, null))
    }

    @Test fun longAndMultilineCustomLabelsAreNormalized() {
        val value = PersonalHoliday.cleaned(
            PersonalHolidayType.HOLIDAY, "  夏休み\n" + "あ".repeat(60)
        )
        assertTrue(value.label.startsWith("夏休み"))
        assertFalse(value.label.contains("\n"))
        assertTrue(value.label.length <= 40)
    }

    @Test fun noInventedPublicHolidaysOutsideSupportedYears() {
        assertTrue(JapanesePublicHolidays.year(2100).isEmpty())
        assertNull(JapanesePublicHolidays.name(day("2100-11-23")))
        assertEquals(CalendarDayTone.HOLIDAY, CalendarHolidayPolicy.tone(day("2026-11-15")))
    }
}

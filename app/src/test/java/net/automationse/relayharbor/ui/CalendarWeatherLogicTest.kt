package net.automationse.relayharbor.ui

import net.automationse.relayharbor.calendar.DeviceCalendarEvent
import net.automationse.relayharbor.weather.WeatherLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CalendarWeatherLogicTest {
    @Test fun noDeviceLocationPermissionNeededForCityCoordinates() {
        assertTrue(WeatherLabels.validCoordinates(35.681, 139.767))
        assertTrue(WeatherLabels.validCoordinates(-90.0, 180.0))
        assertFalse(WeatherLabels.validCoordinates(90.1, 139.7))
        assertFalse(WeatherLabels.validCoordinates(Double.NaN, 0.0))
        assertFalse(WeatherLabels.validCoordinates(1.0, Double.POSITIVE_INFINITY))
    }

    @Test fun weatherCodeCategoriesAndDayNightIcons() {
        assertEquals("快晴", WeatherLabels.condition(0))
        assertEquals("雨", WeatherLabels.condition(61))
        assertEquals("雪", WeatherLabels.condition(71))
        assertEquals("雷雨", WeatherLabels.condition(95))
        assertEquals("🌙", WeatherLabels.symbol(0, false))
        assertEquals("☀️", WeatherLabels.symbol(0, true))
        assertEquals("🌡️", WeatherLabels.symbol(-1))
    }

    @Test fun allDayCalendarEventsRespectExclusiveEndDate() {
        val start = Instant.parse("2026-11-14T00:00:00Z").toEpochMilli()
        val end = Instant.parse("2026-11-15T00:00:00Z").toEpochMilli()
        val event = DeviceCalendarEvent(10L, "研修", start, end, true, "")
        assertEquals("終日", event.timeLabel())
        assertTrue(event.occursOn(LocalDate.of(2026, 11, 14)))
        assertFalse(event.occursOn(LocalDate.of(2026, 11, 15)))
    }

    @Test fun multiDayCalendarEventCoversEveryIncludedDay() {
        val start = Instant.parse("2026-11-14T00:00:00Z").toEpochMilli()
        val end = Instant.parse("2026-11-17T00:00:00Z").toEpochMilli()
        val event = DeviceCalendarEvent(15L, "旅行", start, end, true, "東京")
        assertTrue(event.occursOn(LocalDate.of(2026, 11, 14)))
        assertTrue(event.occursOn(LocalDate.of(2026, 11, 16)))
        assertFalse(event.occursOn(LocalDate.of(2026, 11, 17)))
    }
}

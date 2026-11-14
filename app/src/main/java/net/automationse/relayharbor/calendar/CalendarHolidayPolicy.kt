package net.automationse.relayharbor.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/** Colors are resolved by the Compose UI; this policy is independent of Android. */
internal enum class CalendarDayTone { REGULAR, SATURDAY, HOLIDAY }

internal enum class PersonalHolidayType {
    PAID_LEAVE, HOLIDAY, WORKDAY
}

internal data class PersonalHoliday(
    val type: PersonalHolidayType,
    val label: String = ""
) {
    val displayLabel: String get() = label.ifBlank {
        when (type) {
            PersonalHolidayType.PAID_LEAVE -> "有休"
            PersonalHolidayType.HOLIDAY -> "休日"
            PersonalHolidayType.WORKDAY -> "勤務日"
        }
    }

    companion object {
        fun cleaned(type: PersonalHolidayType, label: String): PersonalHoliday =
            PersonalHoliday(type, label.trim().replace(Regex("[\\r\\n\\u0000-\\u001f]"), " ").take(40))
    }
}

internal object CalendarHolidayPolicy {
    /** Personal overrides take precedence even on Saturdays, Sundays and national holidays. */
    fun tone(date: LocalDate, personal: PersonalHoliday? = null): CalendarDayTone =
        when (personal?.type) {
            PersonalHolidayType.WORKDAY -> CalendarDayTone.REGULAR
            PersonalHolidayType.PAID_LEAVE, PersonalHolidayType.HOLIDAY ->
                CalendarDayTone.HOLIDAY
            null -> when {
                date.dayOfWeek == DayOfWeek.SUNDAY -> CalendarDayTone.HOLIDAY
                JapanesePublicHolidays.name(date) != null -> CalendarDayTone.HOLIDAY
                date.dayOfWeek == DayOfWeek.SATURDAY -> CalendarDayTone.SATURDAY
                else -> CalendarDayTone.REGULAR
            }
        }

    fun description(date: LocalDate, personal: PersonalHoliday? = null): String? {
        if (personal != null) return personal.displayLabel
        return JapanesePublicHolidays.name(date)
    }
}

/**
 * Built-in Japanese public holiday rules for 2000..2099.
 *
 * National Astronomical Observatory dates are authoritative for 2026/2027.
 * Later equinox dates are approximate until officially published by NAOJ
 * (typically in February of the previous year). Explicit personal overrides
 * allow users to correct dates or mark company-specific days off.
 *
 * Statutory holiday rules: https://www8.cao.go.jp/chosei/shukujitsu/gaiyou.html
 */
internal object JapanesePublicHolidays {
    private val byYear = ConcurrentHashMap<Int, Map<LocalDate, String>>()

    fun name(date: LocalDate): String? =
        if (date.year in 2000..2099)
            byYear.computeIfAbsent(date.year, ::buildYear)[date]
        else null

    fun year(year: Int): Map<LocalDate, String> =
        if (year in 2000..2099) byYear.computeIfAbsent(year, ::buildYear)
        else emptyMap()

    private fun nthMonday(year: Int, month: Int, nth: Int): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(nth, DayOfWeek.MONDAY))

    private fun equinox(year: Int, spring: Boolean): Int {
        // NAOJ published dates override estimates for the known official years.
        if (year == 2026) return if (spring) 20 else 23
        if (year == 2027) return if (spring) 21 else 23
        val base = if (spring) 20.8431 else 23.2488
        return floor(base + 0.242194 * (year - 1980) - floor((year - 1980) / 4.0)).toInt()
    }

    private fun buildYear(year: Int): Map<LocalDate, String> {
        val national = linkedMapOf<LocalDate, String>()
        fun add(month: Int, day: Int, label: String) {
            national[LocalDate.of(year, month, day)] = label
        }
        fun monday(month: Int, nth: Int, label: String) {
            national[nthMonday(year, month, nth)] = label
        }

        add(1, 1, "元日")
        monday(1, 2, "成人の日")
        add(2, 11, "建国記念の日")
        when {
            year >= 2020 -> add(2, 23, "天皇誕生日")
            year <= 2018 -> add(12, 23, "天皇誕生日")
        }
        add(3, equinox(year, true), "春分の日")
        add(4, 29, if (year >= 2007) "昭和の日" else "みどりの日")
        add(5, 3, "憲法記念日")
        if (year >= 2007) add(5, 4, "みどりの日")
        add(5, 5, "こどもの日")
        when (year) {
            2020 -> add(7, 23, "海の日")
            2021 -> add(7, 22, "海の日")
            else -> if (year < 2003) add(7, 20, "海の日")
                else monday(7, 3, "海の日")
        }
        if (year >= 2016) {
            when (year) {
                2020 -> add(8, 10, "山の日")
                2021 -> add(8, 8, "山の日")
                else -> add(8, 11, "山の日")
            }
        }
        if (year < 2003) add(9, 15, "敬老の日")
        else monday(9, 3, "敬老の日")
        add(9, equinox(year, false), "秋分の日")
        when (year) {
            2020 -> add(7, 24, "スポーツの日")
            2021 -> add(7, 23, "スポーツの日")
            else -> monday(10, 2, if (year >= 2020) "スポーツの日" else "体育の日")
        }
        add(11, 3, "文化の日")
        add(11, 23, "勤労感謝の日")
        if (year == 2019) {
            add(5, 1, "即位の日")
            add(10, 22, "即位礼正殿の儀")
        }

        val holidays = national.toMutableMap()
        // Article 3(3): a day between two national holidays is a public holiday.
        var day = LocalDate.of(year, 1, 2)
        val end = LocalDate.of(year, 12, 31)
        while (day.isBefore(end)) {
            if (day !in national && day.minusDays(1) in national &&
                day.plusDays(1) in national
            ) holidays[day] = "国民の休日"
            day = day.plusDays(1)
        }

        // Article 3(2): Sunday national holidays transfer to the next date that
        // is not itself a national holiday (e.g. 2026-05-06).
        national.keys.filter { it.dayOfWeek == DayOfWeek.SUNDAY }.forEach { sunday ->
            var substitute = sunday.plusDays(1)
            while (substitute in national) substitute = substitute.plusDays(1)
            if (substitute.year == year) holidays.putIfAbsent(substitute, "振替休日")
        }
        return holidays.toMap()
    }
}

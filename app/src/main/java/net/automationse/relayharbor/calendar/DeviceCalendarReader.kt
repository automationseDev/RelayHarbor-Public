package net.automationse.relayharbor.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal data class DeviceCalendarEvent(
    val eventId: Long,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val location: String
) {
    private val zone: ZoneId get() = if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()
    val startDate: LocalDate get() = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
    val lastDate: LocalDate get() = Instant.ofEpochMilli(maxOf(startMs, endMs - 1))
        .atZone(zone).toLocalDate()

    fun occursOn(date: LocalDate): Boolean = date >= startDate && date <= lastDate

    fun timeLabel(): String = if (allDay) "終日" else
        Instant.ofEpochMilli(startMs).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
}

internal object DeviceCalendarReader {
    fun permitted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun eventsForMonth(context: Context, month: YearMonth): List<DeviceCalendarEvent> {
        if (!permitted(context)) return emptyList()
        val zone = ZoneId.systemDefault()
        val begin = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val columns = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION
        )
        // Instances, unlike Events, expands recurring appointments into occurrences.
        val results = mutableListOf<DeviceCalendarEvent>()
        CalendarContract.Instances.query(context.contentResolver, columns, begin, end)?.use {
            val idCol = it.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleCol = it.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val startCol = it.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endCol = it.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayCol = it.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val locationCol = it.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            while (it.moveToNext() && results.size < 500) {
                val eventId = it.getLong(idCol)
                val eventStart = it.getLong(startCol)
                val eventEnd = it.getLong(endCol)
                if (eventId <= 0L || eventEnd <= eventStart) continue
                results.add(
                    DeviceCalendarEvent(
                        eventId,
                        it.getString(titleCol).orEmpty().ifBlank { "（タイトルなし）" }.take(160),
                        eventStart,
                        eventEnd,
                        it.getInt(allDayCol) != 0,
                        it.getString(locationCol).orEmpty().take(120)
                    )
                )
            }
        }
        return results.sortedBy { it.startMs }
    }
}

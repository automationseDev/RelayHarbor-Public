package net.automationse.relayharbor.calendar

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * Device-private holiday annotations. Does not read or modify CalendarContract
 * and never sends leave information to Relay, weather or other providers.
 */
internal class PersonalHolidayStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "calendar_personal_holidays_v1", Context.MODE_PRIVATE
    )

    fun load(): Map<LocalDate, PersonalHoliday> =
        preferences.all.entries.mapNotNull { entry ->
            runCatching {
                val date = LocalDate.parse(entry.key)
                val data = JSONObject(entry.value as String)
                val type = PersonalHolidayType.valueOf(data.getString("type"))
                date to PersonalHoliday.cleaned(type, data.optString("label", ""))
            }.getOrNull()
        }.toMap()

    fun put(date: LocalDate, setting: PersonalHoliday?) {
        val editor = preferences.edit()
        if (setting == null) {
            editor.remove(date.toString())
        } else {
            val safe = PersonalHoliday.cleaned(setting.type, setting.label)
            editor.putString(
                date.toString(),
                JSONObject().put("type", safe.type.name).put("label", safe.label).toString()
            )
        }
        editor.apply()
    }
}

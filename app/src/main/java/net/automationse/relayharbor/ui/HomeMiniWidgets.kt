package net.automationse.relayharbor.ui

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.automationse.relayharbor.calendar.DeviceCalendarEvent
import net.automationse.relayharbor.calendar.DeviceCalendarReader
import net.automationse.relayharbor.weather.WeatherLabels
import net.automationse.relayharbor.weather.WeatherPreferences
import net.automationse.relayharbor.weather.WeatherService
import net.automationse.relayharbor.weather.WeatherSnapshot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Small, opt-in local dashboard widgets, not arbitrary third-party code. */
@Composable
internal fun HomeMiniWidget(id: String) {
    when (id) {
        "clock" -> HomeClock()
        "date" -> HomeDate()
        "weather" -> HomeWeather()
        "agenda" -> HomeAgenda()
        "battery" -> HomeBattery()
    }
}

@Composable
private fun HomeClock() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(30_000L)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm")),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            now.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.JAPANESE)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeDate() {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now()
            delay(60_000L)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(today.dayOfMonth.toString(), style = MaterialTheme.typography.headlineMedium)
        Text(
            today.format(DateTimeFormatter.ofPattern("yyyy年M月 EEEE", Locale.JAPANESE)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeWeather() {
    val context = LocalContext.current
    val store = remember(context) { WeatherPreferences(context) }
    val place = remember(store) { store.place() }
    var snapshot by remember(place) { mutableStateOf<WeatherSnapshot?>(store.cached()) }
    var failed by remember(place) { mutableStateOf(false) }
    LaunchedEffect(place) {
        if (place != null && snapshot == null) {
            try {
                val response = withContext(Dispatchers.IO) { WeatherService.forecast(place) }
                snapshot = response.first
                store.cache(response.second)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                snapshot = store.cached(24 * 60 * 60 * 1000L)
                failed = true
            }
        }
    }
    val forecast = snapshot
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            place == null -> Text(
                "天気タブで地域を設定すると、現在の天気を表示します。",
                style = MaterialTheme.typography.bodySmall
            )
            forecast == null -> Text(
                if (failed) "天気を取得できませんでした" else "天気を取得中…",
                style = MaterialTheme.typography.bodySmall
            )
            else -> {
                Text(
                    WeatherLabels.symbol(forecast.weatherCode, forecast.isDay) + " " +
                        forecast.temperature.roundToInt().toString() + "°C",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    place.name + "・" + WeatherLabels.condition(forecast.weatherCode),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeAgenda() {
    val context = LocalContext.current
    val permitted = DeviceCalendarReader.permitted(context)
    var next by remember(permitted) { mutableStateOf<DeviceCalendarEvent?>(null) }
    var loaded by remember(permitted) { mutableStateOf(false) }
    LaunchedEffect(permitted) {
        if (permitted) {
            try {
                val now = LocalDate.now()
                val events = withContext(Dispatchers.IO) {
                    (listOf(YearMonth.from(now), YearMonth.from(now).plusMonths(1)))
                        .flatMap { DeviceCalendarReader.eventsForMonth(context, it) }
                }
                next = events.firstOrNull { it.endMs > System.currentTimeMillis() }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: SecurityException) {
                next = null
            } catch (_: Exception) {
                next = null
            }
        }
        loaded = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        when {
            !permitted -> Text(
                "カレンダータブで予定の読み取りを許可してください。",
                style = MaterialTheme.typography.bodySmall
            )
            !loaded -> Text("予定を確認中…", style = MaterialTheme.typography.bodySmall)
            next == null -> Text("今後の予定はありません", style = MaterialTheme.typography.bodySmall)
            else -> {
                val event = next!!
                Text(
                    event.startDate.format(DateTimeFormatter.ofPattern("M/d")) +
                        "  " + event.timeLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeBattery() {
    val context = LocalContext.current
    val battery = remember(context) {
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    }
    var capacity by remember { mutableIntStateOf(-1) }
    LaunchedEffect(battery) {
        while (true) {
            capacity = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 } ?: -1
            delay(60_000L)
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🔋", style = MaterialTheme.typography.headlineSmall)
        Column {
            Text(
                if (capacity >= 0) capacity.toString() + "%" else "取得不可",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "端末のバッテリー",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

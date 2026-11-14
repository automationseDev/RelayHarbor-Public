package net.automationse.relayharbor.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.automationse.relayharbor.weather.WeatherLabels
import net.automationse.relayharbor.weather.WeatherPlace
import net.automationse.relayharbor.weather.WeatherPreferences
import net.automationse.relayharbor.weather.WeatherService
import net.automationse.relayharbor.weather.WeatherSnapshot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** No GPS permission; users explicitly select the city whose forecast they want. */
@Composable
internal fun WeatherForecastSection() {
    val context = LocalContext.current
    val store = remember(context) { WeatherPreferences(context) }
    var place by remember { mutableStateOf(store.place()) }
    var report by remember { mutableStateOf<WeatherSnapshot?>(store.cached()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var editingPlace by rememberSaveable { mutableStateOf(place == null) }
    var cityQuery by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchAttempted by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(place, reload) {
        val selected = place
        if (selected == null) {
            report = null
            status = ""
        } else {
            val cached = if (reload == 0) store.cached() else null
            if (cached != null) {
                report = cached
                status = "保存済みの予報を表示中（最大30分キャッシュ）"
            } else {
                loading = true
                status = ""
                report = null
                try {
                    val result = withContext(Dispatchers.IO) {
                        WeatherService.forecast(selected)
                    }
                    report = result.first
                    store.cache(result.second)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    report = store.cached(24 * 60 * 60 * 1000L)
                    status = if (report != null) "通信できないため保存済みの予報を表示しています"
                        else "天気を取得できませんでした。通信状態を確認して再試行してください"
                } finally {
                    loading = false
                }
            }
        }
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, contentDescription = null)
                Text(
                    "  天気予報",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (place != null) {
                    IconButton(onClick = { editingPlace = !editingPlace }) {
                        Icon(Icons.Default.Edit, contentDescription = "地域を変更")
                    }
                    IconButton(onClick = { reload++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "天気を更新")
                    }
                }
            }
            if (place != null) {
                Text(
                    place?.label.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Text(
                    "表示する地域を選んでください。位置情報の許可は不要です。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (editingPlace) {
                OutlinedTextField(
                    value = cityQuery,
                    onValueChange = { cityQuery = it.take(80) },
                    label = { Text("地域名（例：東京、大阪、札幌）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = cityQuery.trim().length >= 2 && !searching,
                    onClick = {
                        searching = true
                        searchAttempted = false
                        matches = emptyList()
                        scope.launch {
                            try {
                                matches = withContext(Dispatchers.IO) {
                                    WeatherService.search(cityQuery.trim())
                                }
                                searchAttempted = true
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (_: Exception) {
                                status = "地域検索に失敗しました。接続を確認してください"
                                searchAttempted = true
                            } finally {
                                searching = false
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Text(if (searching) "検索中…" else "地域を検索")
                }
                if (searchAttempted && matches.isEmpty()) {
                    Text(
                        "候補が見つかりません。市区町村名などで検索してください。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                matches.forEach { candidate ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            store.setPlace(candidate)
                            place = candidate
                            report = null
                            reload = 0
                            editingPlace = false
                            matches = emptyList()
                            cityQuery = ""
                            status = ""
                        }
                    ) {
                        Text(candidate.label, modifier = Modifier.weight(1f))
                        Text("選択")
                    }
                }
            }
            if (loading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text("予報を取得中…")
                }
            }
            if (status.isNotBlank()) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            report?.let { forecast ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            WeatherLabels.symbol(forecast.weatherCode, forecast.isDay),
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${forecast.temperature.roundToInt()}°C",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                WeatherLabels.condition(forecast.weatherCode),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "体感 ${forecast.feelsLike.roundToInt()}℃ ・ 湿度 ${forecast.humidity}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "更新: ${forecast.observedAt.replace('T', ' ')}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Text(
                    "これから12時間",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    forecast.hours.forEach { hour ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    hour.time.takeLast(5),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    WeatherLabels.symbol(hour.weatherCode),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    "${hour.temperature.roundToInt()}℃",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "☂ ${hour.rainChance}%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    "7日間の予報",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                forecast.days.forEach { day ->
                    val dateLabel = runCatching {
                        LocalDate.parse(day.date).format(
                            DateTimeFormatter.ofPattern("M/d（E）", Locale.JAPANESE)
                        )
                    }.getOrDefault(day.date)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            dateLabel,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            WeatherLabels.symbol(day.weatherCode),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${day.low.roundToInt()} / ${day.high.roundToInt()}℃",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "☂${day.rainChance}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "予報データ：Open-Meteo（CC BY 4.0）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))
                        )
                    }
                }) { Text("提供元") }
            }
        }
    }
}

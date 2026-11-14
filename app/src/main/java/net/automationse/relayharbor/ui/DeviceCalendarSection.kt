package net.automationse.relayharbor.ui

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.automationse.relayharbor.calendar.DeviceCalendarEvent
import net.automationse.relayharbor.calendar.DeviceCalendarReader
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The device's configured calendars are read-only; never synced to Relay or weather API. */
@Composable
internal fun DeviceCalendarSection() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(DeviceCalendarReader.permitted(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permission = granted
        if (granted) refresh++
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = DeviceCalendarReader.permitted(context)
                permission = granted
                if (granted) refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var monthIso by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedIso by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month = remember(monthIso) { YearMonth.parse(monthIso) }
    val selected = remember(selectedIso) { LocalDate.parse(selectedIso) }
    var events by remember { mutableStateOf<List<DeviceCalendarEvent>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(permission, monthIso, refresh) {
        if (!permission) {
            events = emptyList()
            error = null
        } else {
            loading = true
            error = null
            try {
                events = withContext(Dispatchers.IO) {
                    DeviceCalendarReader.eventsForMonth(context, month)
                }
            } catch (_: SecurityException) {
                permission = false
                error = "カレンダーの読み取り権限を確認してください"
            } catch (_: Exception) {
                error = "予定の取得に失敗しました。端末のカレンダー設定を確認してください"
            } finally {
                loading = false
            }
        }
    }

    fun changeMonth(delta: Long) {
        val next = month.plusMonths(delta)
        monthIso = next.toString()
        selectedIso = if (next == YearMonth.now()) LocalDate.now().toString()
            else next.atDay(1).toString()
    }
    val today = LocalDate.now()
    val selectedEvents = remember(events, selectedIso) {
        events.filter { it.occursOn(selected) }
    }
    val eventDates = remember(events, monthIso) {
        (1..month.lengthOfMonth()).filter { day ->
            events.any { it.occursOn(month.atDay(day)) }
        }.toSet()
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, contentDescription = null)
                Text(
                    "  カレンダー",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { refresh++ }, enabled = permission) {
                    Icon(Icons.Default.Refresh, contentDescription = "予定を更新")
                }
            }
            if (!permission) {
                Text(
                    "端末のカレンダーに登録されている予定を表示します。許可するまで予定は読み取りません。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { permissionRequest.launch(Manifest.permission.READ_CALENDAR) }) {
                    Text("カレンダーの読み取りを許可")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { changeMonth(-1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "前の月")
                    }
                    Text(
                        month.atDay(1).format(DateTimeFormatter.ofPattern("yyyy年 M月", Locale.JAPANESE)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { changeMonth(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "次の月")
                    }
                    TextButton(onClick = {
                        monthIso = YearMonth.now().toString()
                        selectedIso = today.toString()
                    }) {
                        Text("今日")
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("日", "月", "火", "水", "木", "金", "土").forEach { label ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                val offset = month.atDay(1).dayOfWeek.value % 7
                val cellCount = ((offset + month.lengthOfMonth() + 6) / 7) * 7
                repeat(cellCount / 7) { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        repeat(7) { weekday ->
                            val day = week * 7 + weekday - offset + 1
                            val valid = day in 1..month.lengthOfMonth()
                            val date = if (valid) month.atDay(day) else null
                            val highlighted = date == selected
                            Box(
                                modifier = Modifier.weight(1f).height(47.dp)
                                    .padding(1.dp)
                                    .background(
                                        if (highlighted) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .then(if (valid) Modifier.clickable {
                                        selectedIso = date.toString()
                                    } else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                if (valid) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            day.toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (date == today || highlighted)
                                                FontWeight.Bold else FontWeight.Normal,
                                            color = if (highlighted)
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (day in eventDates) {
                                            Text(
                                                "●",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selected.format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE)),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${selectedEvents.size}件", style = MaterialTheme.typography.labelMedium)
                }
                if (loading) {
                    Text("カレンダーを読み込み中…", style = MaterialTheme.typography.bodySmall)
                } else if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                } else if (selectedEvents.isEmpty()) {
                    Text(
                        "この日の予定はありません",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    selectedEvents.take(20).forEach { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            ContentUris.withAppendedId(
                                                CalendarContract.Events.CONTENT_URI, event.eventId
                                            )
                                        )
                                    )
                                }
                            }.padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                event.timeLabel(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(Modifier.weight(1f)) {
                                Text(event.title, style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (event.location.isNotBlank()) {
                                    Text(
                                        event.location,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    "端末のカレンダーから読み取り専用で表示しています",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

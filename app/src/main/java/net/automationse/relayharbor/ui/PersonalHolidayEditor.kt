package net.automationse.relayharbor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.calendar.PersonalHoliday
import net.automationse.relayharbor.calendar.PersonalHolidayType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Lightweight local-only marking. Does not create or change device calendar events. */
@Composable
internal fun PersonalHolidayEditor(
    date: LocalDate,
    current: PersonalHoliday?,
    onSave: (PersonalHoliday?) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember(date, current) {
        mutableStateOf(current?.type ?: PersonalHolidayType.PAID_LEAVE)
    }
    var label by remember(date, current) { mutableStateOf(current?.label.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPANESE)))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("この日の表示を設定", fontWeight = FontWeight.SemiBold)
                Text("有休・休日は薄い赤、勤務日は通常色で表示します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf(
                        PersonalHolidayType.PAID_LEAVE to "有休",
                        PersonalHolidayType.HOLIDAY to "休日",
                        PersonalHolidayType.WORKDAY to "勤務日"
                    ).forEach { (option, name) ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("表示名・メモ（任意）") },
                    placeholder = { Text("例：夏季休暇、代休、会社休業日") }
                )
                Text("設定はこの端末に保存されます。Androidのカレンダー予定は変更しません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(PersonalHoliday.cleaned(type, label)) }) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                if (current != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text("標準表示に戻す")
                    }
                }
                TextButton(onClick = onDismiss) { Text("キャンセル") }
            }
        }
    )
}

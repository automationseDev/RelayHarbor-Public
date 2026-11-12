package net.automationse.relayharbor.ui

import androidx.compose.foundation.layout.Arrangement
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.data.RichCard

@Composable
fun RichCardView(card: RichCard) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Media is loaded only for currently visible cards; users can disable
            // external image requests under Settings for privacy/data savings.
            val showMedia = remember(context) {
                WorkspaceUiStore(context).showRemoteThumbnails()
            }
            if (showMedia) {
                card.thumbnailUrl?.let { CardThumbnail(it, card.title) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(card.title, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium)
                    if (card.badge.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(card.badge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                card.score?.let { score ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(score.toString(), fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.headlineSmall)
                            Text("/ 100", color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (card.metrics.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                card.metrics.forEach { metric ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(metric.label, style = MaterialTheme.typography.labelMedium)
                        Text(metric.percent.toString() + "%", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(
                        progress = { (metric.percent.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (expanded && card.description.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(card.description, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                card.url?.let { link ->
                    AssistChip(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                            }
                        },
                        label = { Text("記事を開く") },
                        leadingIcon = {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                        }
                    )
                }
                if (card.description.isNotBlank()) {
                    AssistChip(
                        onClick = { expanded = !expanded },
                        label = { Text(if (expanded) "閉じる" else "詳細を表示") }
                    )
                }
                AssistChip(
                    onClick = {
                        clipboard.setText(AnnotatedString(
                            card.title + "\n" + card.description +
                                (card.score?.let { "\nスコア: " + it } ?: "")
                        ))
                    },
                    label = { Text("コピー") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                )
            }
        }
    }
}

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.data.RichCard

/**
 * Compact: readable summary in notification lists.
 * Standard: interactive card in chat, description on demand.
 * Detailed: full card in the right-side inspector.
 *
 * All modes share one rendering component so scores, badges and actions stay consistent.
 */
@Composable
internal fun RichCardView(
    card: RichCard,
    density: CardDensity = CardDensity.STANDARD
) {
    var expanded by remember(card.title) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val compact = density == CardDensity.COMPACT
    val detailed = density == CardDensity.DETAILED
    val showDescription = detailed || expanded

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 12.dp else 18.dp)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
        ) {
            if (!compact && remember(context) {
                    WorkspaceUiStore(context).showRemoteThumbnails()
                }
            ) {
                card.thumbnailUrl?.let { CardThumbnail(it, card.title) }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        card.title,
                        fontWeight = FontWeight.Bold,
                        style = if (compact) MaterialTheme.typography.titleSmall
                            else MaterialTheme.typography.titleMedium,
                        maxLines = if (compact) 2 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (card.badge.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                card.badge,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                card.score?.let { score ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
                            Text(
                                score.toString() + " / 100",
                                style = if (compact) MaterialTheme.typography.labelLarge
                                    else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            if (!compact && card.metrics.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                card.metrics.forEach { metric ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(metric.label, style = MaterialTheme.typography.labelMedium)
                        Text(
                            metric.percent.toString() + "%",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    LinearProgressIndicator(
                        progress = { metric.percent.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (showDescription && card.description.isNotBlank() && !compact) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(card.description, style = MaterialTheme.typography.bodySmall)
            }
            if (compact && card.description.isNotBlank()) {
                Text(
                    card.description,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!compact) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    card.url?.let { link ->
                        AssistChip(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                }
                            },
                            label = { Text("元のページ") },
                            leadingIcon = {
                                Icon(Icons.Default.OpenInNew, contentDescription = null)
                            }
                        )
                    }
                    card.actions.forEach { action ->
                        when (action.kind) {
                            "open_url" -> action.url?.let { safeLink ->
                                AssistChip(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(safeLink))
                                            )
                                        }
                                    },
                                    label = { Text(action.label) },
                                    leadingIcon = {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                                    }
                                )
                            }
                            "copy_text" -> action.text?.let { value ->
                                AssistChip(
                                    onClick = { clipboard.setText(AnnotatedString(value)) },
                                    label = { Text(action.label) },
                                    leadingIcon = {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                    if (density == CardDensity.STANDARD && card.description.isNotBlank()) {
                        AssistChip(
                            onClick = { expanded = !expanded },
                            label = { Text(if (expanded) "説明を閉じる" else "説明を見る") }
                        )
                    }
                    AssistChip(
                        onClick = {
                            clipboard.setText(
                                AnnotatedString(
                                    card.title + "\n" + card.description +
                                        (card.score?.let { "\nスコア: " + it } ?: "")
                                )
                            )
                        },
                        label = { Text("コピー") },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

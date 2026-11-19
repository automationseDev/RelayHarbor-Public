package net.automationse.relayharbor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.integration.AiUsageModel
import net.automationse.relayharbor.integration.AiUsageRules
import net.automationse.relayharbor.integration.AiUsageSummary
import java.text.NumberFormat
import java.util.Locale

private fun quantity(n: Number): String =
    NumberFormat.getIntegerInstance(Locale.JAPAN).format(n)

private fun dollars(n: Double?): String =
    n?.let { String.format(Locale.US, "$%.6f", it) } ?: "未算出"

@Composable
internal fun AiUsageSection(
    summary: AiUsageSummary?,
    days: Int,
    busy: Boolean,
    status: String,
    onPeriod: (Int) -> Unit,
    onRefresh: () -> Unit,
    onSaveRates: (String, String, Double?, Double?) -> Unit,
    onExport: () -> Unit
) {
    var editing by remember { mutableStateOf<AiUsageModel?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text("AIモデルサマリー", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onRefresh, enabled = !busy) { Text("更新") }
        }
        Text("RelayHarbor経由のAPI利用のみ集計。公式アプリや他端末のAPI使用量は含みません。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "今日", 7 to "7日", 30 to "30日",
                90 to "90日", 365 to "365日").forEach { (value, label) ->
                FilterChip(selected = days == value,
                    onClick = { onPeriod(value) }, label = { Text(label) })
            }
        }
        Text(status, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (summary != null && summary.periodDays == days) {
            val totals = summary.totals
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        UsageMetric("リクエスト", quantity(totals.requests), Modifier.weight(1f))
                        UsageMetric("合計トークン",
                            quantity(totals.inputTokens + totals.outputTokens), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        UsageMetric("エラー", quantity(totals.errorCount), Modifier.weight(1f))
                        UsageMetric("平均応答", "${quantity(totals.averageLatencyMs)} ms",
                            Modifier.weight(1f))
                    }
                    HorizontalDivider()
                    Text("推定利用料金（USD）", style = MaterialTheme.typography.labelMedium)
                    Text(dollars(totals.estimatedUsd),
                        style = MaterialTheme.typography.titleLarge)
                    if (totals.unpricedModels > 0) {
                        Text("単価未設定・トークン情報不足のモデル ${totals.unpricedModels} 件。算出可能分: ${dollars(totals.estimatedPartialUsd)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("入力 ${quantity(totals.inputTokens)} / 出力 ${quantity(totals.outputTokens)} tokens",
                        style = MaterialTheme.typography.bodySmall)
                    if (totals.unreportedCalls > 0) {
                        Text("トークン数を返さなかった呼び出し: ${quantity(totals.unreportedCalls)} 件",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (summary.daily.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("日別リクエスト数（JST）",
                            style = MaterialTheme.typography.titleSmall)
                        val displayed = summary.daily.takeLast(14)
                        val maxCount = displayed.maxOfOrNull { it.requests }?.coerceAtLeast(1) ?: 1
                        displayed.forEach { day ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()) {
                                Text(day.day.takeLast(5).replace("-", "/"),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(46.dp))
                                Box(Modifier.weight(1f).height(9.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(4.dp))) {
                                    if (day.requests > 0) {
                                        Box(Modifier.fillMaxWidth(day.requests.toFloat() / maxCount)
                                            .height(9.dp)
                                            .background(MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(4.dp)))
                                    }
                                }
                                Text(quantity(day.requests),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(32.dp))
                            }
                        }
                        if (summary.daily.size > 14) {
                            Text("グラフは直近14日を表示。集計値は選択期間全体です。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text("モデル別利用状況", style = MaterialTheme.typography.titleSmall)
            if (summary.models.isEmpty()) {
                Text("モデルが登録されていません。AI接続先を追加すると表示されます。",
                    style = MaterialTheme.typography.bodySmall)
            }
            summary.models.forEach { model ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(model.profileName, style = MaterialTheme.typography.titleSmall)
                        Text(model.model.ifBlank { "Dify app" } + " · " + model.kind,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("利用 ${quantity(model.requests)} 回（成功 ${model.successCount} / エラー ${model.errorCount}）")
                        Text("入力 ${quantity(model.inputTokens)} / 出力 ${quantity(model.outputTokens)} tokens",
                            style = MaterialTheme.typography.bodySmall)
                        Text("平均応答 ${quantity(model.averageLatencyMs)} ms · 推定 ${dollars(model.estimatedUsd)}",
                            style = MaterialTheme.typography.bodySmall)
                        if (model.unreportedCalls > 0) {
                            Text("使用量不明 ${model.unreportedCalls} 回",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (model.inputUsdPerMillion == null) "単価：未設定"
                                else "入力 $ ${model.inputUsdPerMillion} / 出力 $ ${model.outputUsdPerMillion}（100万tokens当たり）",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { editing = model }) { Text("単価設定") }
                        }
                    }
                }
            }
            TextButton(onClick = onExport, enabled = !busy) {
                Text("日別・モデル別集計をCSV出力")
            }
            Text("推定料金は設定した現在の単価を過去分にも適用する参考値です。各社の請求額・残高とは一致しない場合があります。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    editing?.let { model ->
        AiUsageRateDialog(model = model,
            onDismiss = { editing = null },
            onSave = { input, output ->
                onSaveRates(model.profileId, model.model, input, output)
                editing = null
            })
    }
}

@Composable
private fun UsageMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AiUsageRateDialog(
    model: AiUsageModel,
    onDismiss: () -> Unit,
    onSave: (Double?, Double?) -> Unit
) {
    var input by remember(model.profileId, model.model) {
        mutableStateOf(model.inputUsdPerMillion?.toString().orEmpty())
    }
    var output by remember(model.profileId, model.model) {
        mutableStateOf(model.outputUsdPerMillion?.toString().orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("モデル単価の設定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(model.profileName + " · " + model.model,
                    style = MaterialTheme.typography.bodySmall)
                Text("USD / 100万トークン（公式料金を確認して手入力）",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = input, onValueChange = { input = it.take(16) },
                    label = { Text("入力単価（USD）") }, singleLine = true)
                OutlinedTextField(value = output, onValueChange = { output = it.take(16) },
                    label = { Text("出力単価（USD）") }, singleLine = true)
                Text("料金は参考値です。単価変更時は選択期間の過去利用分も再計算されます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = AiUsageRules.validRates(input, output),
                onClick = { onSave(AiUsageRules.rate(input), AiUsageRules.rate(output)) }) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(null, null) }) { Text("単価削除") }
                TextButton(onClick = onDismiss) { Text("閉じる") }
            }
        }
    )
}

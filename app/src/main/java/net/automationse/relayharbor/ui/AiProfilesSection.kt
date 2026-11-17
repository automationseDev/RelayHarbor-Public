package net.automationse.relayharbor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import net.automationse.relayharbor.integration.AiProfile
import net.automationse.relayharbor.integration.AiProfileDraft
import net.automationse.relayharbor.integration.AiProfileRules

/** Provider API keys remain transient in the dialog and are sent only to Relay via HTTPS. */
@Composable
internal fun AiProfilesSection(
    profiles: List<AiProfile>,
    status: String,
    canManage: Boolean,
    busy: Boolean,
    onRefresh: () -> Unit,
    onCreate: (AiProfileDraft) -> Unit,
    onUpdate: (String, AiProfileDraft) -> Unit,
    onDelete: (String) -> Unit
) {
    var editing by remember { mutableStateOf<AiProfile?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AiProfile?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("AI接続先の管理", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onRefresh, enabled = !busy) { Text("再取得") }
        }
        Text("OpenAI・OpenAI互換API・Difyを複数登録できます。APIキーはRelayで暗号化保存され、端末には保存しません。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        if (!canManage) {
            Text("新規登録するにはRelayサーバーを更新し、RELAY_AI_ENCRYPTION_KEYを設定してください。従来の環境変数方式は引き続き利用できます。",
                style = MaterialTheme.typography.bodySmall)
        }
        profiles.forEach { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    Text(AiProfileRules.kindLabel(p.kind) +
                        (if (p.model.isNotBlank()) " · " + p.model else "") +
                        (if (!p.managed) " · 従来設定（閲覧のみ）" else ""),
                        style = MaterialTheme.typography.bodySmall)
                    Text(p.baseUrl, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (p.managed) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editing = p }, enabled = !busy) {
                                Text("編集")
                            }
                            TextButton(onClick = { deleting = p }, enabled = !busy) {
                                Text("削除")
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = { adding = true }, enabled = canManage && !busy) {
            Text("＋ AI接続先を追加")
        }
    }
    if (adding || editing != null) {
        val selected = editing
        AiProfileEditor(
            existing = selected,
            onDismiss = { adding = false; editing = null },
            onSave = { draft ->
                if (selected == null) onCreate(draft)
                else onUpdate(selected.id, draft)
                adding = false
                editing = null
            }
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("AI接続先を削除") },
            text = { Text("「" + p.name + "」をRelayから削除します。保存したAPIキーも削除されます。") },
            confirmButton = {
                TextButton(onClick = { onDelete(p.id); deleting = null }) { Text("削除する") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("キャンセル") }
            }
        )
    }
}

@Composable
private fun AiProfileEditor(
    existing: AiProfile?,
    onDismiss: () -> Unit,
    onSave: (AiProfileDraft) -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: "openai") }
    var baseUrl by remember(existing?.id) {
        mutableStateOf(existing?.baseUrl ?: "https://api.openai.com/v1")
    }
    var model by remember(existing?.id) {
        mutableStateOf(existing?.model ?: "gpt-4.1-mini")
    }
    var secret by remember(existing?.id) { mutableStateOf("") }
    val draft = AiProfileDraft(name, kind, baseUrl, model, secret)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "AI接続先の新規登録" else "AI接続先を編集") },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) },
                    label = { Text("表示名") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    listOf(
                        "openai" to "OpenAI",
                        "compatible" to "OpenAI互換API",
                        "dify" to "Dify"
                    ).forEach { (value, label) ->
                        FilterChip(selected = kind == value, onClick = {
                            if (kind != value) {
                                if (value == "openai" && kind != "openai") {
                                    baseUrl = "https://api.openai.com/v1"
                                } else if (kind == "openai") {
                                    baseUrl = ""
                                }
                                kind = value
                            }
                        }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it.take(512) },
                    label = { Text("APIベースURL（HTTPS）") },
                    placeholder = { Text(if (kind == "dify") "https://dify.example.com/v1"
                        else "https://api.example.com/v1") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (kind != "dify") {
                    OutlinedTextField(
                        value = model, onValueChange = { model = it.take(120) },
                        label = { Text("モデルID") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = secret, onValueChange = { secret = it.take(512) },
                    label = { Text("APIキー") },
                    placeholder = { Text(if (existing == null) "必須"
                        else "変更するときだけ入力（空欄なら維持）") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("キーは端末に保存されません。RelayがHTTPSで受け取り、サーバー内で暗号化します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                enabled = AiProfileRules.valid(draft, requireKey = existing == null),
                onClick = { onSave(draft) }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

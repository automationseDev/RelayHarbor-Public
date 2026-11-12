package net.automationse.relayharbor.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The system chooser always has the final say. No silent role change,
 * device administrator, accessibility service or permission escalation.
 */
@Composable
internal fun DefaultHomeSettings() {
    val context = LocalContext.current
    val active by DefaultHomeRole.active.collectAsState()
    var failure by remember { mutableStateOf<String?>(null) }
    val chooser = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        DefaultHomeRole.refresh(context)
    }

    fun openSystemChooser() {
        failure = null
        try {
            chooser.launch(DefaultHomeRole.requestIntent(context))
        } catch (_: Exception) {
            failure = "この端末では標準ホームの選択画面を開けませんでした。Androidの「設定 → アプリ → 標準アプリ → ホームアプリ」を使用してください。"
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Home, contentDescription = null)
                Text(
                    "標準ホームアプリ",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                if (active) "現在、RelayHarborが標準ホームに設定されています。"
                else "RelayHarborをAndroidのホームアプリとして選択できます。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "標準ホームに選ぶと、ホーム操作からRelayHarborを開けます。設定しなくても従来どおりチャット・通知アプリとして利用できます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = ::openSystemChooser, modifier = Modifier.fillMaxWidth()) {
                Text(if (active) "標準ホームを変更する" else "標準ホームに設定する")
            }
            Text(
                "元に戻す場合は上のボタンから以前のホーム（例：One UIホーム）を選択してください。Android側の確認なしに切り替えることはありません。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

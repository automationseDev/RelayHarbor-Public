package net.automationse.relayharbor.ui

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Opens the corresponding installed app, not an arbitrary sender-provided web URL. */
@Composable
internal fun SourceAppOpenButton(sourcePackage: String) {
    val context = LocalContext.current
    val intent = remember(sourcePackage) {
        if (sourcePackage.isBlank() || sourcePackage == context.packageName) null
        else runCatching {
            context.packageManager.getLaunchIntentForPackage(sourcePackage)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.getOrNull()
    }
    if (intent != null) {
        TextButton(onClick = {
            runCatching { context.startActivity(intent) }
        }) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Text("元アプリを開く")
        }
    }
}

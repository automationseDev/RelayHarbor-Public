package net.automationse.relayharbor.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.automationse.relayharbor.floating.ChromeRefreshAccessibilityService

/**
 * Prominent in-app disclosure + affirmative consent before navigating to
 * Android Accessibility settings. The OS alone controls the grant/revocation.
 */
@Composable
internal fun ChromeRefreshSettingsControls() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember(context) {
        mutableStateOf(ChromeRefreshAccessibilityService.permissionEnabled(context))
    }
    var disclosureOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = ChromeRefreshAccessibilityService.permissionEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openSystemSettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            error = "Android設定 → ユーザー補助 → インストール済みアプリでRelayHarborを探してください。"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Chromeの再読み込み",
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            "フローティングランチャーからChromeの現在のタブを再読み込みします。" +
                "利用する場合のみユーザー補助サービスを許可してください。" +
                "ChromeのUI変更によって操作できない場合があります。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (enabled) "ユーザー補助: 許可済み" else "ユーザー補助: 未許可（初期状態）",
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = {
            if (enabled) openSystemSettings()
            else disclosureOpen = true
        }) {
            Text(if (enabled) "Chrome操作の権限を確認・解除" else "Chrome操作を設定する")
        }
        Text(
            "強制リロード（キャッシュ無視）はChromeの外付けキーボード操作" +
                "「Ctrl+Shift+R / Shift+F5」が必要です。" +
                "RelayHarborからキーを送信する機能はありません。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (error.isNotBlank()) Text(
            error, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    if (disclosureOpen) {
        AlertDialog(
            onDismissRequest = { disclosureOpen = false },
            title = { Text("Chrome操作のユーザー補助権限") },
            text = {
                Text(
                    "RelayHarborがChromeの再読み込みボタンを押すため、" +
                        "Androidの「ユーザー補助」権限を使用します。\n\n" +
                        "この権限は一般に画面上の情報を読み取り、操作できる強い権限です。" +
                        "この機能ではChromeだけを対象にし、フローティングランチャーで" +
                        "「Chrome再読み込み」を押したときだけ、Chromeのメニューと" +
                        "ツールバーのボタン名を調べ、再読み込みを実行します。\n\n" +
                        "ページ本文・URL・入力内容・パスワード・履歴を保存したり、" +
                        "Relayや外部サーバーへ送ったりしません。" +
                        "画面情報の記録や自動定期操作は行いません。\n\n" +
                        "この機能は任意です。いつでもAndroidの設定から無効にできます。" +
                        "内容に同意する場合にだけ次へ進んでください。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    disclosureOpen = false
                    openSystemSettings()
                }) { Text("同意してAndroidの設定を開く") }
            },
            dismissButton = {
                TextButton(onClick = { disclosureOpen = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

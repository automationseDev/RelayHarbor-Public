package net.automationse.relayharbor.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import net.automationse.relayharbor.floating.FloatingMenuController
import net.automationse.relayharbor.floating.FloatingMenuPreferences
import kotlin.math.roundToInt

/** All options are off by default. SYSTEM_ALERT_WINDOW approval remains OS-owned. */
@Composable
internal fun FloatingMenuSettingsCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember(context) { FloatingMenuPreferences(context) }
    var options by remember(context) { mutableStateOf(prefs.load()) }
    var overlayAllowed by remember { mutableStateOf(FloatingMenuController.allowed(context)) }
    var pendingEnable by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun reload() {
        overlayAllowed = FloatingMenuController.allowed(context)
        if (pendingEnable && overlayAllowed) {
            pendingEnable = false
            if (!FloatingMenuController.enable(context)) {
                message = "ランチャーを開始できませんでした。Androidの権限・省電力設定をご確認ください。"
            }
        }
        if (!overlayAllowed && options.enabled) {
            FloatingMenuController.disable(context)
        }
        options = prefs.load()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reload()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val overlayPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { reload() }

    fun requestOverlay() {
        pendingEnable = true
        message = "表示権限を許可した後、この画面に戻ってください。"
        try {
            overlayPermission.launch(FloatingMenuController.permissionIntent(context))
        } catch (_: ActivityNotFoundException) {
            pendingEnable = false
            message = "Android設定 → アプリ → 特別なアプリアクセス → 他のアプリの上に表示 で許可してください。"
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            pendingEnable = false
            FloatingMenuController.disable(context)
            message = "サイドランチャーを停止しました"
        } else if (!FloatingMenuController.allowed(context)) {
            requestOverlay()
        } else if (!FloatingMenuController.enable(context)) {
            message = "ランチャーを開始できませんでした。設定を確認してください。"
        } else {
            message = "サイドランチャーを有効化しました。画面端のハンドルをタップして開けます。"
        }
        options = prefs.load()
        overlayAllowed = FloatingMenuController.allowed(context)
    }

    fun updateSide(side: String) {
        prefs.side(side)
        options = prefs.load()
        FloatingMenuController.refresh(context)
    }

    fun updateAlpha(alpha: Float) {
        prefs.opacity(alpha)
        options = prefs.load()
        FloatingMenuController.refresh(context)
    }

    fun updatePosition(position: Float) {
        prefs.yFraction(position)
        options = prefs.load()
        FloatingMenuController.refresh(context)
    }

    fun updateHandle(size: Int) {
        prefs.handleDp(size)
        options = prefs.load()
        FloatingMenuController.refresh(context)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("サイドフローティングランチャー",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        "他のアプリを使用中でも、画面端からスクショ・ホーム・チャット・通知を開けます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = options.enabled && overlayAllowed, onCheckedChange = ::setEnabled)
            }
            if (!overlayAllowed) {
                Text("利用するにはAndroidの「他のアプリの上に表示」の許可が必要です。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = ::requestOverlay) { Text("画面上への表示を許可する") }
            }
            if (message.isNotBlank()) Text(message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary)
            HorizontalDivider()
            Text("表示する画面端", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = options.side == "left",
                    onClick = { updateSide("left") },
                    label = { Text("左側") }
                )
                FilterChip(
                    selected = options.side == "right",
                    onClick = { updateSide("right") },
                    label = { Text("右側") }
                )
            }
            Text("ハンドルの縦位置: " + (options.yFraction * 100).roundToInt() + "%")
            Slider(
                value = options.yFraction,
                onValueChange = ::updatePosition,
                valueRange = 0.12f..0.82f
            )
            Text("透明度: " + (options.opacity * 100).roundToInt() + "%")
            Slider(
                value = options.opacity,
                onValueChange = ::updateAlpha,
                valueRange = 0.40f..1f
            )
            Text("ハンドルの大きさ: " + options.handleDp + "dp")
            Slider(
                value = options.handleDp.toFloat(),
                onValueChange = { updateHandle(it.roundToInt()) },
                valueRange = 32f..64f,
                steps = 7
            )
            Text(
                "ハンドルはドラッグでも位置を変更できます。長時間有効化する間は常駐通知が表示されます。機密画面や全画面アプリではハンドルが隠れる場合があります。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "スクショは撮影するたびにAndroidの画面共有許可が必要で、撮影画像は端末に保存します。アプリの強制的な小窓化はできませんが、Galaxyのマルチウィンドウ操作方法をメニューで確認できます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (options.enabled) TextButton(onClick = { setEnabled(false) }) {
                Text("常駐ランチャーを終了")
            }
        }
    }
}

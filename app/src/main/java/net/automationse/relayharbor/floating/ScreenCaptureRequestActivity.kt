package net.automationse.relayharbor.floating

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Per-capture, user-visible system consent. This transient activity never
 * caches or reuses a MediaProjection permission token.
 */
class ScreenCaptureRequestActivity : ComponentActivity() {
    companion object {
        const val ACTION_POPUP_GUIDE = "net.automationse.relayharbor.floating.POPUP_GUIDE"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_INTENT = "projection_result_intent"
    }

    private val consent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            // Hide both overlay windows before the screenshot session.
            FloatingMenuController.hideForCapture(this)
            try {
                val capture = Intent(this, ScreenCaptureService::class.java)
                    .putExtra(EXTRA_RESULT_CODE, result.resultCode)
                    .putExtra(EXTRA_RESULT_INTENT, data)
                ContextCompat.startForegroundService(this, capture)
            } catch (_: Exception) {
                FloatingMenuController.restoreAfterCapture(this)
                Toast.makeText(this, "スクショを開始できませんでした", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "スクリーンショットをキャンセルしました", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == ACTION_POPUP_GUIDE) {
            showPopupGuide()
            return
        }
        if (savedInstanceState != null) {
            // After process recreation do not re-use or re-request stale consent.
            finish()
            return
        }
        try {
            val projection = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            consent.launch(projection.createScreenCaptureIntent())
        } catch (_: Exception) {
            Toast.makeText(this, "画面キャプチャの許可画面を開けませんでした", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun showPopupGuide() {
        AlertDialog.Builder(this)
            .setTitle("現在のアプリを小窓で表示")
            .setMessage(
                "Galaxyでは「設定 → 便利な機能 → マルチウィンドウ」で" +
                    "「ポップアップ表示にスワイプ」を有効にします。\n\n" +
                    "対象アプリを表示した状態で画面上部の角から斜めにスワイプすると、" +
                    "対応アプリを小窓化できます。\n\n" +
                    "Androidは他社アプリをRelayHarborから強制的に小窓化する" +
                    "一般公開APIを提供していません。"
            )
            .setPositiveButton("Androidの設定を開く") { _, _ ->
                runCatching {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
                finish()
            }
            .setNegativeButton("閉じる") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}

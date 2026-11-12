package net.automationse.relayharbor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import net.automationse.relayharbor.notification.EXTRA_OPEN_CHANNEL_ID
import net.automationse.relayharbor.notification.MessageNotifications
import net.automationse.relayharbor.notification.NotificationOpenRequests
import net.automationse.relayharbor.notification.NotificationPreferences
import net.automationse.relayharbor.notification.NotificationSyncScheduler
import net.automationse.relayharbor.notification.VisibleChat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import net.automationse.relayharbor.ui.RelayHarborApp
import net.automationse.relayharbor.ui.DefaultHomeRole
import net.automationse.relayharbor.ui.HomeLauncherRequests

class MainActivity : ComponentActivity() {
    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        NotificationSyncScheduler.refresh(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onOpenIntent(intent)
        setContent {
            RelayTheme {
                RelayHarborApp()
            }
        }
        NotificationSyncScheduler.refresh(this)
        if (Build.VERSION.SDK_INT >= 33) {
            val prefs = NotificationPreferences(this)
            if (prefs.enabled() && !prefs.requestedRuntimePermission() &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                prefs.markPermissionRequested()
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        onOpenIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        VisibleChat.appVisible = true
        DefaultHomeRole.refresh(this)
        NotificationSyncScheduler.refresh(this)
    }

    override fun onPause() {
        VisibleChat.appVisible = false
        super.onPause()
    }

    private fun onOpenIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            HomeLauncherRequests.openHome()
            // Do not re-run HOME navigation after a configuration recreation.
            intent.removeCategory(Intent.CATEGORY_HOME)
            return
        }
        if (intent?.hasExtra(EXTRA_OPEN_CHANNEL_ID) == true) {
            NotificationOpenRequests.offer(intent.getLongExtra(EXTRA_OPEN_CHANNEL_ID, 0L))
            // Consume the activity intent extra so a later recreation does not
            // navigate to an old notification again.
            intent.removeExtra(EXTRA_OPEN_CHANNEL_ID)
        }
    }
}

private val HarborLight = lightColorScheme(
    primary = Color(0xFF583463),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF1E5F5),
    onPrimaryContainer = Color(0xFF351B3B),
    secondary = Color(0xFF007B74),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F3EF),
    onSecondaryContainer = Color(0xFF064D48),
    background = Color(0xFFFAF8FB),
    onBackground = Color(0xFF252129),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF252129),
    surfaceContainerLow = Color(0xFFF6F2F8),
    outlineVariant = Color(0xFFE8E1E9)
)

private val HarborDark = darkColorScheme(
    primary = Color(0xFFE0B9EB),
    onPrimary = Color(0xFF3B2143),
    primaryContainer = Color(0xFF4B3055),
    onPrimaryContainer = Color(0xFFF5DDF8),
    secondary = Color(0xFF83DED3),
    onSecondary = Color(0xFF003D39),
    secondaryContainer = Color(0xFF144B48),
    onSecondaryContainer = Color(0xFFC6F2EC),
    background = Color(0xFF18141B),
    onBackground = Color(0xFFF3EDF4),
    surface = Color(0xFF241E27),
    onSurface = Color(0xFFF3EDF4),
    surfaceContainerLow = Color(0xFF2D2630),
    outlineVariant = Color(0xFF514655)
)

@Composable
private fun RelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) HarborDark else HarborLight,
        content = content
    )
}

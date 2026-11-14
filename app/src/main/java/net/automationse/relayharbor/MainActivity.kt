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
import net.automationse.relayharbor.integration.LocalWebsiteScheduler
import net.automationse.relayharbor.notification.VisibleChat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
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
        LocalWebsiteScheduler.sync(this)
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
        LocalWebsiteScheduler.sync(this)
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
    primary = Color(0xFF365BB6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1E9FF),
    onPrimaryContainer = Color(0xFF1E3675),
    secondary = Color(0xFF117A77),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDEF3EF),
    onSecondaryContainer = Color(0xFF0D4B47),
    tertiary = Color(0xFF7858B0),
    tertiaryContainer = Color(0xFFEFE5FF),
    onTertiaryContainer = Color(0xFF39246A),
    background = Color(0xFFF8FAFD),
    onBackground = Color(0xFF172336),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF172336),
    onSurfaceVariant = Color(0xFF536174),
    surfaceContainerLow = Color(0xFFF2F5FA),
    surfaceContainer = Color(0xFFEBF0F7),
    outline = Color(0xFF8290A3),
    outlineVariant = Color(0xFFDBE3ED)
)

private val HarborDark = darkColorScheme(
    primary = Color(0xFFB6C9FF),
    onPrimary = Color(0xFF203B82),
    primaryContainer = Color(0xFF314C91),
    onPrimaryContainer = Color(0xFFE3E9FF),
    secondary = Color(0xFF84D4C9),
    onSecondary = Color(0xFF003B36),
    secondaryContainer = Color(0xFF174E49),
    onSecondaryContainer = Color(0xFFC4F2EA),
    tertiary = Color(0xFFD7B8FF),
    tertiaryContainer = Color(0xFF5E447F),
    onTertiaryContainer = Color(0xFFF0DCFF),
    background = Color(0xFF101827),
    onBackground = Color(0xFFE5ECFA),
    surface = Color(0xFF182234),
    onSurface = Color(0xFFE5ECFA),
    onSurfaceVariant = Color(0xFFB8C3D4),
    surfaceContainerLow = Color(0xFF202C3D),
    surfaceContainer = Color(0xFF293547),
    outline = Color(0xFF8191A8),
    outlineVariant = Color(0xFF465267)
)

private val HarborShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
private fun RelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) HarborDark else HarborLight,
        shapes = HarborShapes,
        content = content
    )
}

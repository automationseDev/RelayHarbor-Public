package net.automationse.relayharbor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import net.automationse.relayharbor.ui.RelayHarborApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RelayTheme {
                RelayHarborApp()
            }
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

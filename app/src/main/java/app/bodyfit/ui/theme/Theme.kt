package app.bodyfit.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F7A55),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC9F2E1),
    onPrimaryContainer = Color(0xFF04301F),
    secondary = Color(0xFFB24A16),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC9),
    onSecondaryContainer = Color(0xFF3A1500),
    tertiary = Color(0xFF1F5EA8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E4FF),
    onTertiaryContainer = Color(0xFF001C3A),
    background = Color(0xFFFCFCFB),
    onBackground = Color(0xFF0B0B0B),
    surface = Color(0xFFFCFCFB),
    onSurface = Color(0xFF0B0B0B),
    surfaceVariant = Color(0xFFEFEDE8),
    onSurfaceVariant = Color(0xFF52514E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F7F5),
    surfaceContainer = Color(0xFFF4F3F0),
    surfaceContainerHigh = Color(0xFFEEECE7),
    surfaceContainerHighest = Color(0xFFE9E7E2),
    outline = Color(0xFFA8A69F),
    outlineVariant = Color(0xFFDCDAD4),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF2F2F2C),
    inverseOnSurface = Color(0xFFF4F3F0),
    inversePrimary = Color(0xFF6FD9AE),
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD9AE),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF0B5C40),
    onPrimaryContainer = Color(0xFFC9F2E1),
    secondary = Color(0xFFFFB68E),
    onSecondary = Color(0xFF582200),
    secondaryContainer = Color(0xFF7C3300),
    onSecondaryContainer = Color(0xFFFFDCC9),
    tertiary = Color(0xFFA5C8FF),
    onTertiary = Color(0xFF00315C),
    tertiaryContainer = Color(0xFF004883),
    onTertiaryContainer = Color(0xFFD3E4FF),
    background = Color(0xFF141413),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1A1A19),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF3A3A37),
    onSurfaceVariant = Color(0xFFC3C2B7),
    surfaceContainerLowest = Color(0xFF121211),
    surfaceContainerLow = Color(0xFF1F1F1D),
    surfaceContainer = Color(0xFF242422),
    surfaceContainerHigh = Color(0xFF2E2E2B),
    surfaceContainerHighest = Color(0xFF393936),
    outline = Color(0xFF8C8B84),
    outlineVariant = Color(0xFF46453F),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    inverseSurface = Color(0xFFE6E4DF),
    inverseOnSurface = Color(0xFF2F2F2C),
    inversePrimary = Color(0xFF0F7A55),
    scrim = Color(0xFF000000),
)

/**
 * Both palettes are chosen, not derived. The dark scheme is its own set of steps
 * picked against the dark surface rather than an inverted copy of the light one.
 */
@Composable
fun BodyFitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val viz = if (darkTheme) DarkViz else LightViz
    val view = LocalView.current

    // Only an Activity has a window to tint. A cast would crash anywhere else the theme
    // is hosted, such as a ComposeView inside a dialog or a wallpaper service.
    val window = (view.context as? Activity)?.window
    if (!view.isInEditMode && window != null) {
        SideEffect {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalViz provides viz) {
        MaterialTheme(
            colorScheme = colors,
            typography = HealthTypography,
            content = content,
        )
    }
}

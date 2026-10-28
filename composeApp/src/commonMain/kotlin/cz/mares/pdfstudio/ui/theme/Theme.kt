package cz.mares.pdfstudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E6FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF00695C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFFC62828),
    background = Color(0xFFEEF2F6),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41474D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF102A43),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFEF9A9A),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE1E2E5),
    surface = Color(0xFF182026),
    onSurface = Color(0xFFE1E2E5),
    surfaceVariant = Color(0xFF232E38),
    onSurfaceVariant = Color(0xFFC1C7CE)
)

@Composable
fun PdfStudioTheme(
    content: @Composable () -> Unit
) {
    val useDark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content
    )
}

package ir.nv.navigation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C51),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF86F8CF),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4C635A),
    background = Color(0xFFF6FBF8),
    surface = Color(0xFFF6FBF8),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF68DBB4),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF00513D),
    onPrimaryContainer = Color(0xFF86F8CF),
    secondary = Color(0xFFB3CCC0),
    background = Color(0xFF0E1512),
    surface = Color(0xFF0E1512),
    error = Color(0xFFFFB4AB)
)

@Composable
fun NvTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}

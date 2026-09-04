package ir.nv.navigation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF007C91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4F3FB),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF355C64),
    tertiary = Color(0xFF006B5E),
    tertiaryContainer = Color(0xFF9EF2DE),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFDFBFF),
    surfaceVariant = Color(0xFFE0E5EE),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF20D5F4),
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF004E5B),
    onPrimaryContainer = Color(0xFFB9F2FC),
    secondary = Color(0xFFAACBD1),
    tertiary = Color(0xFF82D5C2),
    tertiaryContainer = Color(0xFF005047),
    background = Color(0xFF06111D),
    surface = Color(0xFF081522),
    surfaceVariant = Color(0xFF263541),
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

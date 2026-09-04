package ir.nv.navigation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF007C98),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F4FF),
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
    primary = Color(0xFF18D4FF),
    onPrimary = Color(0xFF031421),
    primaryContainer = Color(0xFF10344D),
    onPrimaryContainer = Color(0xFFE8F8FF),
    secondary = Color(0xFFAACBD1),
    tertiary = Color(0xFF82D5C2),
    tertiaryContainer = Color(0xFF005047),
    background = Color(0xFF04101D),
    surface = Color(0xFF071526),
    surfaceVariant = Color(0xFF102C45),
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

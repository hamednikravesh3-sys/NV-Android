package ir.nv.navigation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1268E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E7FF),
    onPrimaryContainer = Color(0xFF001A42),
    secondary = Color(0xFF455D83),
    tertiary = Color(0xFF006B5E),
    tertiaryContainer = Color(0xFF9EF2DE),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFDFBFF),
    surfaceVariant = Color(0xFFE0E5EE),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFACC7FF),
    onPrimary = Color(0xFF002F68),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD9E7FF),
    secondary = Color(0xFFBBC7DB),
    tertiary = Color(0xFF82D5C2),
    tertiaryContainer = Color(0xFF005047),
    background = Color(0xFF101318),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF43474E),
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

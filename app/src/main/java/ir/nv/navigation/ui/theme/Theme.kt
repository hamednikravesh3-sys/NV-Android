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
    primaryContainer = NvColors.RouteBlueSoft,
    onPrimaryContainer = NvColors.LightTextPrimary,
    secondary = Color(0xFF355C64),
    onSecondary = Color.White,
    tertiary = Color(0xFF006B5E),
    tertiaryContainer = Color(0xFF9EF2DE),
    background = NvColors.LightBackground,
    onBackground = NvColors.LightTextPrimary,
    surface = NvColors.LightSurface,
    onSurface = NvColors.LightTextPrimary,
    surfaceVariant = NvColors.LightSurfaceVariant,
    onSurfaceVariant = NvColors.LightTextSecondary,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    outline = Color(0xFF718493)
)

private val DarkColors = darkColorScheme(
    primary = NvColors.RouteBlue,
    onPrimary = NvColors.Navy950,
    primaryContainer = NvColors.Navy700,
    onPrimaryContainer = NvColors.TextPrimaryDark,
    secondary = Color(0xFFAACBD1),
    onSecondary = NvColors.Navy950,
    tertiary = NvColors.Success,
    onTertiary = NvColors.Navy950,
    tertiaryContainer = Color(0xFF005047),
    background = NvColors.Navy900,
    onBackground = NvColors.TextPrimaryDark,
    surface = NvColors.Navy850,
    onSurface = NvColors.TextPrimaryDark,
    surfaceVariant = NvColors.Navy700,
    onSurfaceVariant = NvColors.TextSecondaryDark,
    error = NvColors.Emergency,
    onError = Color.White,
    outline = NvColors.DividerDark
)

@Composable
fun NvTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NvTypography,
        content = content
    )
}

package ir.nv.navigation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Rahnama/NV design tokens shared by the Persian-first navigation surfaces.
 * Keep feature UIs on these semantic tokens instead of hard-coded colors/sizes.
 */
object NvColors {
    val Navy950 = Color(0xFF020A12)
    val Navy900 = Color(0xFF04101D)
    val Navy850 = Color(0xFF071526)
    val Navy800 = Color(0xFF0A1D30)
    val Navy700 = Color(0xFF10344D)
    val Navy600 = Color(0xFF174D6A)

    val RouteBlue = Color(0xFF18D4FF)
    val RouteBlueStrong = Color(0xFF00B8E6)
    val RouteBlueSoft = Color(0xFFC8F4FF)

    val Success = Color(0xFF43E66B)
    val Warning = Color(0xFFFFB52E)
    val Emergency = Color(0xFFFF4D57)
    val Info = Color(0xFF6FA8FF)

    val TextPrimaryDark = Color(0xFFF4FAFF)
    val TextSecondaryDark = Color(0xFFAFC3D3)
    val DividerDark = Color(0xFF27445A)

    val LightBackground = Color(0xFFF5F8FC)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE6EEF5)
    val LightTextPrimary = Color(0xFF0B1B29)
    val LightTextSecondary = Color(0xFF4A6173)
}

object NvSpacing {
    val Xs: Dp = 4.dp
    val Sm: Dp = 8.dp
    val Md: Dp = 12.dp
    val Lg: Dp = 16.dp
    val Xl: Dp = 24.dp
    val Xxl: Dp = 32.dp
}

object NvRadius {
    val Small: Dp = 10.dp
    val Medium: Dp = 14.dp
    val Large: Dp = 18.dp
    val Card: Dp = 20.dp
    val Sheet: Dp = 28.dp
    val Pill: Dp = 999.dp
}

object NvElevation {
    val Flat: Dp = 0.dp
    val Card: Dp = 4.dp
    val Floating: Dp = 8.dp
    val Overlay: Dp = 16.dp
}

object NvMotion {
    const val FastMs = 140
    const val StandardMs = 220
    const val EmphasizedMs = 320
}

object NvStateAlpha {
    const val Disabled = 0.38f
    const val Secondary = 0.72f
    const val Scrim = 0.56f
    const val Pressed = 0.88f
}

val NvTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

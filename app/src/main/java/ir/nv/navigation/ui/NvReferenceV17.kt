package ir.nv.navigation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.nv.navigation.ui.theme.AppThemeMode
import ir.nv.navigation.ui.theme.NvColors

/**
 * Active product shell. The old separate "define code" entry is intentionally gone:
 * code allocation now lives inside the QR surface. Smart navigation opens a direct,
 * functional destination assistant rather than the disconnected feature showcase.
 */
@Composable
fun NvReferenceV17(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel
) {
    val state by viewModel.state.collectAsState()
    var smartOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        if (state.currentLocation == null) viewModel.useCurrentLocationAsOrigin()
    }

    Box(Modifier.fillMaxSize()) {
        NvReferenceV16(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )

        if (!state.navigationActive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp, bottom = 240.dp)
                    .size(54.dp)
                    .clickable { smartOpen = true },
                shape = CircleShape,
                color = NvColors.RouteBlue,
                border = BorderStroke(2.dp, NvColors.TextPrimaryDark.copy(alpha = .85f)),
                shadowElevation = 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = "دستیار هوشمند مسیر",
                        tint = NvColors.TextPrimaryDark,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    if (smartOpen) {
        RahnamaSmartRouteAssistant(
            state = state,
            viewModel = viewModel,
            onDismiss = { smartOpen = false }
        )
    }
}

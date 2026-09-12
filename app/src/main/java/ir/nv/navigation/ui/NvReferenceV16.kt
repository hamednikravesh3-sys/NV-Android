package ir.nv.navigation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Emergency
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
 * Product shell for screens 1-12. Keeps the validated V15 home/navigation path and
 * adds an always-available emergency/SOS entry without changing the routing engine.
 */
@Composable
fun NvReferenceV16(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel
) {
    val state by viewModel.state.collectAsState()
    var emergencyOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        NvReferenceV15(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )

        if (!state.navigationActive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp, bottom = 110.dp)
                    .size(54.dp)
                    .clickable { emergencyOpen = true },
                shape = CircleShape,
                color = NvColors.Emergency,
                border = BorderStroke(2.dp, NvColors.TextPrimaryDark.copy(alpha = .85f)),
                shadowElevation = 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Emergency,
                        contentDescription = "SOS و خدمات اضطراری",
                        tint = NvColors.TextPrimaryDark,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }

    if (emergencyOpen) {
        RahnamaEmergencyOverlay(
            state = state,
            viewModel = viewModel,
            onDismiss = { emergencyOpen = false },
            onRouteToPlace = { place ->
                if (state.origin == null) viewModel.useCurrentLocationAsOrigin()
                viewModel.selectDestination(place)
                emergencyOpen = false
            }
        )
    }
}

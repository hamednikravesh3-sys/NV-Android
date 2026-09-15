package ir.nv.navigation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ir.nv.navigation.navigation.VehicleProfile
import ir.nv.navigation.ui.theme.AppThemeMode

/**
 * Emergency overlay owner. The visible SOS entry is supplied by the home menu.
 */
@Composable
fun NvReferenceV16(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel,
    onSmart: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var emergencyOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        NvReferenceV15(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel,
            onSmart = onSmart,
            onEmergency = { emergencyOpen = true }
        )
    }

    if (emergencyOpen) {
        RahnamaEmergencyOverlay(
            state = state,
            viewModel = viewModel,
            onDismiss = { emergencyOpen = false },
            onRouteToPlace = { place ->
                if (state.origin == null) {
                    viewModel.routeFromCurrentLocationTo(place, VehicleProfile.CAR)
                } else {
                    viewModel.selectDestination(place)
                    viewModel.setVehicleProfile(VehicleProfile.CAR)
                    viewModel.calculateRoute()
                }
                emergencyOpen = false
            }
        )
    }
}

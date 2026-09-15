package ir.nv.navigation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ir.nv.navigation.ui.theme.AppThemeMode

/**
 * Active product shell. Smart and emergency actions are routed into the home
 * quick-access menu instead of being scattered as unrelated floating buttons.
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
            viewModel = viewModel,
            onSmart = { smartOpen = true }
        )
    }

    if (smartOpen) {
        RahnamaSmartRouteAssistant(
            state = state,
            viewModel = viewModel,
            onDismiss = { smartOpen = false }
        )
    }
}

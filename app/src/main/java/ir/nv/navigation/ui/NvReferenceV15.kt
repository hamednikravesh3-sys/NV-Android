package ir.nv.navigation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ir.nv.navigation.ui.theme.AppThemeMode

/**
 * Product-architecture shell: keeps the validated V14 home/nearby flow and uses
 * the live-driving surface while navigation is active. The driving surface renders
 * only the committed route; rerouting remains owned by NvViewModel.
 */
@Composable
fun NvReferenceV15(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel
) {
    val state by viewModel.state.collectAsState()
    if (state.navigationActive) {
        RahnamaLiveDrivingScreen(
            darkMode = darkMode,
            viewModel = viewModel
        )
    } else {
        NvReferenceV14(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )
    }
}

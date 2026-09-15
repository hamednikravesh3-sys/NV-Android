package ir.nv.navigation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ir.nv.navigation.ui.theme.AppThemeMode

/**
 * Product shell: normal reference home before navigation, driving-only screen after
 * start. The driving screen renders only the committed route.
 */
@Composable
fun NvReferenceV15(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel,
    onSmart: () -> Unit,
    onEmergency: () -> Unit
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
            viewModel = viewModel,
            onSmart = onSmart,
            onEmergency = onEmergency
        )
    }
}

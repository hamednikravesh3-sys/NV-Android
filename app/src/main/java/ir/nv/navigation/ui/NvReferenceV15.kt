package ir.nv.navigation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ir.nv.navigation.ui.theme.AppThemeMode

/**
 * Product-architecture shell: keeps the validated V14 home/nearby flow while replacing
 * the legacy V8 active-navigation presentation with Rahnama screen 5/6.
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
        RahnamaNavigationScreen(
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

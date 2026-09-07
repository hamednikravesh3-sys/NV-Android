package ir.nv.navigation.ui

import androidx.compose.runtime.Composable
import ir.nv.navigation.ui.theme.AppThemeMode

/**
 * Legacy reference kept only for source compatibility.
 *
 * NV Code allocation is intentionally not implemented here. The production
 * Persian-first flow lives in [NvReferenceV13] and only allows online registry
 * allocation for new NV codes.
 */
@Composable
fun NvReferenceV10(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel
) {
    NvReferenceV8(
        darkMode = darkMode,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        viewModel = viewModel
    )
}

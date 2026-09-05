package ir.nv.navigation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import ir.nv.navigation.navigation.service.NvNavigationService
import ir.nv.navigation.ui.NvReferenceV5
import ir.nv.navigation.ui.NvViewModel
import ir.nv.navigation.ui.theme.AppThemeMode
import ir.nv.navigation.ui.theme.NvTheme

class MainActivity : ComponentActivity() {
    private lateinit var navigationViewModel: NvViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigationViewModel = ViewModelProvider(this)[NvViewModel::class.java]
        handleNavigationIntent(intent)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { getSharedPreferences("nv_ui", MODE_PRIVATE) }
            val systemDark = isSystemInDarkTheme()
            var themeMode by remember {
                val legacy = preferences.getBoolean("dark_mode", systemDark).takeIf { preferences.contains("dark_mode") }
                mutableStateOf(AppThemeMode.restore(preferences.getString("theme_mode", null), legacy))
            }
            val darkMode = themeMode.resolve(systemDark)
            val navigationState by navigationViewModel.state.collectAsState()

            LaunchedEffect(navigationState.navigationActive) {
                if (navigationState.navigationActive) {
                    NvNavigationService.start(
                        context = this@MainActivity,
                        destination = navigationState.destination?.name,
                        remaining = navigationState.remainingSeconds.takeIf { it > 0.0 }
                            ?.let { "${(it / 60.0).toInt()} دقیقه تا مقصد" }
                    )
                } else {
                    NvNavigationService.stop(this@MainActivity)
                }
            }

            NvTheme(darkTheme = darkMode) {
                NvReferenceV5(
                    darkMode = darkMode,
                    themeMode = themeMode,
                    onThemeModeChange = { selected ->
                        themeMode = selected
                        preferences.edit().putString("theme_mode", selected.name).remove("dark_mode").apply()
                    },
                    viewModel = navigationViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: Intent?) {
        if (::navigationViewModel.isInitialized && intent?.action == NvNavigationService.ACTION_STOP_NAVIGATION) {
            navigationViewModel.stopNavigation()
        }
    }
}

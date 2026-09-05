package ir.nv.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.nv.navigation.ui.NvReferenceV4
import ir.nv.navigation.ui.theme.AppThemeMode
import ir.nv.navigation.ui.theme.NvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { getSharedPreferences("nv_ui", MODE_PRIVATE) }
            val systemDark = isSystemInDarkTheme()
            var themeMode by remember {
                val legacy = preferences.getBoolean("dark_mode", systemDark).takeIf { preferences.contains("dark_mode") }
                mutableStateOf(AppThemeMode.restore(preferences.getString("theme_mode", null), legacy))
            }
            val darkMode = themeMode.resolve(systemDark)
            NvTheme(darkTheme = darkMode) {
                NvReferenceV4(darkMode = darkMode, themeMode = themeMode, onThemeModeChange = { selected ->
                    themeMode = selected
                    preferences.edit().putString("theme_mode", selected.name).remove("dark_mode").apply()
                })
            }
        }
    }
}

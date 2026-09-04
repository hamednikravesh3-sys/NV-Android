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
import ir.nv.navigation.ui.NvApp
import ir.nv.navigation.ui.theme.NvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { getSharedPreferences("nv_ui", MODE_PRIVATE) }
            val systemDark = isSystemInDarkTheme()
            var darkMode by remember {
                mutableStateOf(preferences.getBoolean("dark_mode", systemDark))
            }
            NvTheme(darkTheme = darkMode) {
                NvApp(
                    darkMode = darkMode,
                    onToggleTheme = {
                        darkMode = !darkMode
                        preferences.edit().putBoolean("dark_mode", darkMode).apply()
                    }
                )
            }
        }
    }
}

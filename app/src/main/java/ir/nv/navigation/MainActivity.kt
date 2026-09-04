package ir.nv.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ir.nv.navigation.rebuild.NvRebuildApp
import ir.nv.navigation.ui.theme.NvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NvTheme(darkTheme = true) {
                NvRebuildApp()
            }
        }
    }
}

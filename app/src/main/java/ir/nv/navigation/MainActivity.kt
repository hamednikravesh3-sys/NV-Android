package ir.nv.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.navigation.service.NvNavigationService
import ir.nv.navigation.ui.NvQrScannerOverlay
import ir.nv.navigation.ui.NvReferenceV13
import ir.nv.navigation.ui.NvViewModel
import ir.nv.navigation.ui.OfflineMapDiagnosticsButton
import ir.nv.navigation.ui.ProvinceDownloadOverlay
import ir.nv.navigation.ui.theme.AppThemeMode
import ir.nv.navigation.ui.theme.NvTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var navigationViewModel: NvViewModel

    override fun attachBaseContext(newBase: Context) {
        val faLocale = Locale.forLanguageTag("fa-IR")
        Locale.setDefault(faLocale)
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(faLocale)
            setLayoutDirection(faLocale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navigationViewModel = ViewModelProvider(this)[NvViewModel::class.java]
        handleNavigationIntent(intent)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { getSharedPreferences("nv_ui", MODE_PRIVATE) }
            var themeMode by remember {
                val legacy = preferences.getBoolean("dark_mode", false).takeIf { preferences.contains("dark_mode") }
                mutableStateOf(AppThemeMode.restore(preferences.getString("theme_mode", null), legacy))
            }
            var automaticNight by remember { mutableStateOf(isNightNow()) }
            var lastHazardFingerprint by remember { mutableStateOf<String?>(null) }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }

            LaunchedEffect(Unit) {
                while (true) {
                    automaticNight = isNightNow()
                    delay(60_000)
                }
            }
            val darkMode = when (themeMode) {
                AppThemeMode.AUTO -> automaticNight
                AppThemeMode.DAY -> false
                AppThemeMode.NIGHT -> true
            }
            val navigationState by navigationViewModel.state.collectAsState()

            LaunchedEffect(navigationState.navigationActive) {
                if (navigationState.navigationActive) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    NvNavigationService.start(
                        context = this@MainActivity,
                        destination = navigationState.destination?.name,
                        remaining = navigationState.remainingSeconds.takeIf { it > 0.0 }
                            ?.let { "${(it / 60.0).toInt()} دقیقه تا مقصد" }
                    )
                } else {
                    lastHazardFingerprint = null
                    NvNavigationService.stop(this@MainActivity)
                }
            }

            LaunchedEffect(navigationState.navigationActive, navigationState.routeNotices) {
                if (!navigationState.navigationActive) return@LaunchedEffect
                val hazard = navigationState.routeNotices.firstOrNull {
                    it.kind == RouteNotice.Kind.WEATHER && it.title.startsWith("هشدار")
                } ?: return@LaunchedEffect
                val fingerprint = "${hazard.title}|${hazard.detail}|${(hazard.distanceAheadMeters / 1000.0).toInt()}"
                if (fingerprint != lastHazardFingerprint) {
                    lastHazardFingerprint = fingerprint
                    NvNavigationService.notifyHazard(
                        context = this@MainActivity,
                        title = hazard.title,
                        detail = hazard.detail,
                        stableKey = fingerprint
                    )
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                NvTheme(darkTheme = darkMode) {
                    Box(Modifier.fillMaxSize()) {
                        NvReferenceV13(
                            darkMode = darkMode,
                            themeMode = themeMode,
                            onThemeModeChange = { selected ->
                                themeMode = selected
                                preferences.edit().putString("theme_mode", selected.name).remove("dark_mode").apply()
                            },
                            viewModel = navigationViewModel
                        )
                        OfflineMapDiagnosticsButton(
                            offlineReady = navigationState.offlineReady,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .navigationBarsPadding()
                                .padding(start = 12.dp, bottom = 92.dp)
                        )
                        if (!navigationState.navigationActive) {
                            ProvinceDownloadOverlay(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 92.dp)
                            )
                            NvQrScannerOverlay(
                                viewModel = navigationViewModel,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .navigationBarsPadding()
                                    .padding(end = 12.dp, bottom = 92.dp)
                            )
                        }
                    }
                }
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

    private fun isNightNow(): Boolean {
        val hour = LocalTime.now().hour
        return hour < 6 || hour >= 18
    }
}

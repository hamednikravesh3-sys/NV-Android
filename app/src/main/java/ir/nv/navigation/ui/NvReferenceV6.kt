package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.nv.navigation.ui.theme.AppThemeMode

private val V6Panel = Color(0xF20A1D2D)
private val V6Cyan = Color(0xFF16D9FF)
private val V6Text = Color(0xFFF6FBFF)
private val V6Muted = Color(0xFFA7BBC8)
private val V6Green = Color(0xFF42E66A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NvReferenceV6(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var lastRequestedPair by remember { mutableStateOf<String?>(null) }
    var lastPresentedRouteKey by remember { mutableStateOf<String?>(null) }
    var showRouteChooser by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.startNavigation()
            showRouteChooser = false
        }
    }

    fun beginDriving() {
        val hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasLocationPermission) {
            viewModel.startNavigation()
            showRouteChooser = false
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val origin = state.origin
    val destination = state.destination
    val routePairKey = if (origin != null && destination != null) {
        "${origin.coordinate.latitude},${origin.coordinate.longitude}->${destination.coordinate.latitude},${destination.coordinate.longitude}"
    } else null

    LaunchedEffect(routePairKey) {
        if (routePairKey != null && routePairKey != lastRequestedPair) {
            lastRequestedPair = routePairKey
            viewModel.clearRoute()
            viewModel.calculateRoute()
        }
    }

    val routeKey = state.routeAlternatives.takeIf { it.isNotEmpty() }?.joinToString("|") {
        "${it.distanceMeters.toLong()}:${it.travelSeconds.toLong()}"
    }
    LaunchedEffect(routeKey, state.routing) {
        if (!state.routing && routeKey != null && routeKey != lastPresentedRouteKey) {
            lastPresentedRouteKey = routeKey
            showRouteChooser = true
        }
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        NvReferenceV5(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )

        if (showRouteChooser && state.routeAlternatives.isNotEmpty() && !state.navigationActive) {
            ModalBottomSheet(
                onDismissRequest = { showRouteChooser = false },
                containerColor = V6Panel,
                contentColor = V6Text
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Route, contentDescription = null, tint = V6Cyan)
                        Spacer(Modifier.padding(horizontal = 5.dp))
                        Column {
                            Text("مسیرهای پیشنهادی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "${state.origin?.name.orEmpty()} ← ${state.destination?.name.orEmpty()}",
                                color = V6Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    state.routeAlternatives.forEachIndexed { index, route ->
                        val selected = index == state.selectedRouteIndex
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.selectRoute(index) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) Color(0xCC123148) else Color(0x99102131),
                            border = BorderStroke(1.dp, if (selected) V6Cyan else V6Muted.copy(alpha = 0.25f))
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("مسیر ${index + 1}", fontWeight = FontWeight.Bold)
                                    if (selected) Text("انتخاب‌شده", color = V6Cyan, style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    "${(route.travelSeconds / 60.0).toInt()} دقیقه • ${String.format("%.1f", route.distanceMeters / 1000.0)} km",
                                    color = V6Text
                                )
                            }
                        }
                    }

                    Button(
                        onClick = ::beginDriving,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.route != null
                    ) {
                        Icon(Icons.Rounded.Navigation, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("شروع رانندگی", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "بعد از شروع، حالت راننده، دنبال‌کردن GPS و راهنمای مانور فعال می‌شود.",
                        color = V6Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

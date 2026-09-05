package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.PhotoCamera
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.ui.theme.AppThemeMode

private val V6Panel = Color(0xF20A1D2D)
private val V6Cyan = Color(0xFF16D9FF)
private val V6Text = Color(0xFFF6FBFF)
private val V6Muted = Color(0xFFA7BBC8)
private val V6Green = Color(0xFF42E66A)
private val V6Gold = Color(0xFFFFD65A)

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

    Box(Modifier.fillMaxSize()) {
        NvReferenceV5(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )

        V6RightInfoPanel(
            state = state,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 8.dp)
        )

        if (state.navigationActive && state.currentLocation != null) {
            V6CarMarker(
                modifier = Modifier.align(Alignment.Center)
            )
        }

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
                        Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
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

@Composable
private fun V6RightInfoPanel(state: NvUiState, modifier: Modifier = Modifier) {
    val weather = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.WEATHER }
    val attraction = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.ATTRACTION }
    val temperature = weather?.detail?.let { Regex("(-?\\d+)°").find(it)?.groupValues?.getOrNull(1) }

    Surface(
        modifier = modifier.widthIn(min = 132.dp, max = 174.dp),
        shape = RoundedCornerShape(20.dp),
        color = V6Panel,
        border = BorderStroke(1.dp, V6Cyan.copy(alpha = 0.5f)),
        shadowElevation = 8.dp
    ) {
        Column(
            Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            V6InfoRow(
                icon = Icons.Rounded.Cloud,
                iconTint = V6Cyan,
                title = temperature?.let { "$it°" } ?: "آب‌وهوا",
                subtitle = weather?.title?.takeIf { it.isNotBlank() } ?: "وضعیت مسیر"
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xB7132939),
                border = BorderStroke(1.dp, V6Gold.copy(alpha = 0.42f))
            ) {
                V6InfoRow(
                    icon = Icons.Rounded.PhotoCamera,
                    iconTint = V6Gold,
                    title = "دیدنی‌ها",
                    subtitle = attraction?.title?.take(18) ?: "در ادامه مسیر"
                )
            }
        }
    }
}

@Composable
private fun V6InfoRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = iconTint.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, iconTint.copy(alpha = 0.55f))
        ) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(21.dp))
            }
        }
        Spacer(Modifier.padding(horizontal = 4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = V6Text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = V6Muted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun V6CarMarker(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(58.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF20B2435),
        border = BorderStroke(2.dp, V6Cyan),
        shadowElevation = 12.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = V6Cyan.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.DirectionsCar,
                        contentDescription = "خودرو",
                        tint = Color.White,
                        modifier = Modifier.size(31.dp)
                    )
                }
            }
        }
    }
}

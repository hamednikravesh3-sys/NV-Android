package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.ui.theme.AppThemeMode

private val V6Panel = Color(0xF20A1D2D)
private val V6Cyan = Color(0xFF16D9FF)
private val V6Text = Color(0xFFF6FBFF)
private val V6Muted = Color(0xFFA7BBC8)
private val V6Green = Color(0xFF42E66A)
private val V6Gold = Color(0xFFFFD65A)
private val V6Rose = Color(0xFFFF6B9D)
private val V6Purple = Color(0xFFB58CFF)

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
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
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

    val searchBusy = state.originSearching || state.destinationSearching ||
        (state.origin == null && state.originQuery.isNotBlank()) ||
        (state.destination == null && state.destinationQuery.isNotBlank())

    Box(Modifier.fillMaxSize()) {
        NvReferenceV5(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )

        if (!imeVisible && !searchBusy && !showRouteChooser) {
            V6RightInfoPanel(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 6.dp, top = 118.dp)
            )
        }

        if (state.navigationActive && state.currentLocation != null && state.followNavigation) {
            val maneuver = state.route?.maneuvers?.getOrNull(state.maneuverIndex)
            V6CarMarker(
                direction = maneuver?.direction,
                distanceToManeuverMeters = state.distanceToNextManeuverMeters,
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
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Route, contentDescription = null, tint = V6Cyan)
                        Spacer(Modifier.width(10.dp))
                        Text("مسیرهای پیشنهادی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }

                    V6EndpointSummary(
                        origin = state.origin?.name.orEmpty(),
                        destination = state.destination?.name.orEmpty()
                    )

                    state.routeAlternatives.forEachIndexed { index, route ->
                        val selected = index == state.selectedRouteIndex
                        val routeColor = routeColor(index)
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.selectRoute(index) },
                            shape = RoundedCornerShape(17.dp),
                            color = if (selected) routeColor.copy(alpha = 0.17f) else Color(0x99102131),
                            border = BorderStroke(if (selected) 2.dp else 1.dp, routeColor.copy(alpha = if (selected) 0.95f else 0.42f))
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(13.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = CircleShape, color = routeColor.copy(alpha = 0.18f)) {
                                            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                                                Text("${index + 1}", color = routeColor, fontWeight = FontWeight.Black)
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("مسیر ${index + 1}", fontWeight = FontWeight.Bold, color = V6Text)
                                            if (selected) Text("مسیر انتخاب‌شده", color = routeColor, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    if (index == 0) {
                                        Text("پیشنهاد NV", color = routeColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    V6RouteMetric(
                                        label = "فاصله مبدأ تا مقصد",
                                        value = "${String.format("%.1f", route.distanceMeters / 1000.0)} km",
                                        color = routeColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    V6RouteMetric(
                                        label = "زمان سفر",
                                        value = "${(route.travelSeconds / 60.0).toInt()} دقیقه",
                                        color = if (index % 2 == 0) V6Green else V6Gold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = ::beginDriving,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.route != null
                    ) {
                        Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("شروع رانندگی", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun V6EndpointSummary(origin: String, destination: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xAA102435),
        border = BorderStroke(1.dp, V6Muted.copy(alpha = 0.25f))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = V6Green.copy(alpha = 0.18f)) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { Text("●", color = V6Green) }
                }
                Spacer(Modifier.width(8.dp))
                Text("مبدأ", color = V6Muted, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Text(origin.ifBlank { "—" }, color = V6Text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = V6Rose.copy(alpha = 0.18f)) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { Text("●", color = V6Rose) }
                }
                Spacer(Modifier.width(8.dp))
                Text("مقصد", color = V6Muted, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Text(destination.ifBlank { "—" }, color = V6Text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun V6RouteMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.34f))
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(value, color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
            Text(label, color = V6Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun V6RightInfoPanel(state: NvUiState, modifier: Modifier = Modifier) {
    val weather = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.WEATHER }
    val attraction = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.ATTRACTION }
    val temperature = weather?.detail?.let { Regex("(-?\\d+)°").find(it)?.groupValues?.getOrNull(1) }
    val weatherVisual = weatherVisual(weather?.detail)

    Column(
        modifier.widthIn(min = 92.dp, max = 116.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = V6Panel,
            border = BorderStroke(1.dp, weatherVisual.second.copy(alpha = 0.50f)),
            shadowElevation = 5.dp
        ) {
            Row(Modifier.padding(horizontal = 7.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = weatherVisual.second.copy(alpha = 0.14f)) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Icon(weatherVisual.first, contentDescription = "وضعیت آب‌وهوا", tint = weatherVisual.second, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        temperature?.let { "$it°" } ?: "هوا",
                        color = V6Text,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                    Text(
                        weatherLabel(weather?.detail),
                        color = V6Muted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (attraction != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = V6Panel,
                border = BorderStroke(1.dp, V6Gold.copy(alpha = 0.50f)),
                shadowElevation = 5.dp
            ) {
                Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (!attraction.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = attraction.imageUrl,
                            contentDescription = attraction.title,
                            modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(9.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(9.dp),
                            color = V6Gold.copy(alpha = 0.11f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = V6Gold, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Text("دیدنی", color = V6Gold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        attraction.title,
                        color = V6Text,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${(attraction.distanceAheadMeters / 1000.0).coerceAtLeast(0.1).let { String.format("%.1f", it) }} km",
                        color = V6Muted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private fun weatherVisual(detail: String?): Pair<ImageVector, Color> {
    val value = detail.orEmpty()
    return when {
        value.contains("رعد") -> Icons.Rounded.Thunderstorm to V6Purple
        value.contains("برف") -> Icons.Rounded.AcUnit to Color(0xFFBCEBFF)
        value.contains("باران") || value.contains("بارش") -> Icons.Rounded.WaterDrop to V6Cyan
        value.contains("تندباد") || value.contains("باد") -> Icons.Rounded.Air to Color(0xFF9BE7E7)
        value.contains("مه") || value.contains("دید کمتر") -> Icons.Rounded.Visibility to Color(0xFFCFD8DC)
        value.contains("صاف") -> Icons.Rounded.WbSunny to V6Gold
        else -> Icons.Rounded.Cloud to V6Cyan
    }
}

private fun weatherLabel(detail: String?): String {
    val value = detail.orEmpty()
    return when {
        value.contains("رعد") -> "رعدوبرق"
        value.contains("برف") -> "برفی"
        value.contains("باران") || value.contains("بارش") -> "بارانی"
        value.contains("تندباد") -> "باد شدید"
        value.contains("مه") || value.contains("دید کمتر") -> "دید محدود"
        value.contains("صاف") -> "صاف"
        value.contains("ابری") -> "ابری"
        value.isBlank() -> "وضعیت مسیر"
        else -> value.substringBefore("•").trim().take(16)
    }
}

@Composable
private fun V6CarMarker(
    direction: RouteManeuver.Direction?,
    distanceToManeuverMeters: Double,
    modifier: Modifier = Modifier
) {
    val signal = when (direction) {
        RouteManeuver.Direction.LEFT,
        RouteManeuver.Direction.SLIGHT_LEFT,
        RouteManeuver.Direction.SHARP_LEFT -> -1
        RouteManeuver.Direction.RIGHT,
        RouteManeuver.Direction.SLIGHT_RIGHT,
        RouteManeuver.Direction.SHARP_RIGHT -> 1
        RouteManeuver.Direction.UTURN -> 2
        else -> 0
    }
    val signalActive = signal != 0 && distanceToManeuverMeters in 0.0..900.0
    val transition = rememberInfiniteTransition(label = "nv-turn-signal")
    val blinkAlpha by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(animation = tween(360), repeatMode = RepeatMode.Reverse),
        label = "nv-turn-signal-alpha"
    )
    val indicatorAlpha = if (signalActive) blinkAlpha else 0f

    Surface(
        modifier = modifier.size(70.dp),
        shape = CircleShape,
        color = Color(0xD7071724),
        border = BorderStroke(2.dp, V6Cyan.copy(alpha = 0.92f)),
        shadowElevation = 14.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.width(42.dp).height(58.dp)) {
                val body = Color(0xFFF4F7FA)
                val glass = Color(0xFF173B4F)
                val tire = Color(0xFF03090D)
                val trim = Color(0xFF93A9B5)
                val amber = Color(0xFFFFB300)

                drawRoundRect(
                    color = body,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.045f),
                    size = Size(size.width * 0.64f, size.height * 0.91f),
                    cornerRadius = CornerRadius(size.width * 0.18f, size.width * 0.18f)
                )
                drawRoundRect(trim, Offset(size.width * 0.235f, size.height * 0.18f), Size(size.width * 0.53f, size.height * 0.62f), CornerRadius(8f, 8f))
                drawRoundRect(glass, Offset(size.width * 0.27f, size.height * 0.22f), Size(size.width * 0.46f, size.height * 0.22f), CornerRadius(7f, 7f))
                drawRoundRect(glass, Offset(size.width * 0.29f, size.height * 0.57f), Size(size.width * 0.42f, size.height * 0.17f), CornerRadius(6f, 6f))
                drawRoundRect(body, Offset(size.width * 0.25f, size.height * 0.465f), Size(size.width * 0.50f, size.height * 0.075f), CornerRadius(4f, 4f))

                drawRoundRect(tire, Offset(size.width * 0.045f, size.height * 0.22f), Size(size.width * 0.16f, size.height * 0.21f), CornerRadius(4f, 4f))
                drawRoundRect(tire, Offset(size.width * 0.795f, size.height * 0.22f), Size(size.width * 0.16f, size.height * 0.21f), CornerRadius(4f, 4f))
                drawRoundRect(tire, Offset(size.width * 0.045f, size.height * 0.62f), Size(size.width * 0.16f, size.height * 0.21f), CornerRadius(4f, 4f))
                drawRoundRect(tire, Offset(size.width * 0.795f, size.height * 0.62f), Size(size.width * 0.16f, size.height * 0.21f), CornerRadius(4f, 4f))

                drawRoundRect(trim, Offset(size.width * 0.08f, size.height * 0.42f), Size(size.width * 0.12f, size.height * 0.08f), CornerRadius(4f, 4f))
                drawRoundRect(trim, Offset(size.width * 0.80f, size.height * 0.42f), Size(size.width * 0.12f, size.height * 0.08f), CornerRadius(4f, 4f))

                drawCircle(Color(0xFFFFF3B0), radius = size.width * 0.065f, center = Offset(size.width * 0.34f, size.height * 0.095f))
                drawCircle(Color(0xFFFFF3B0), radius = size.width * 0.065f, center = Offset(size.width * 0.66f, size.height * 0.095f))
                drawCircle(Color(0xFFFF4B55), radius = size.width * 0.055f, center = Offset(size.width * 0.34f, size.height * 0.90f))
                drawCircle(Color(0xFFFF4B55), radius = size.width * 0.055f, center = Offset(size.width * 0.66f, size.height * 0.90f))

                if (signal == -1 || signal == 2) {
                    drawCircle(amber.copy(alpha = indicatorAlpha), radius = size.width * 0.072f, center = Offset(size.width * 0.19f, size.height * 0.10f))
                    drawCircle(amber.copy(alpha = indicatorAlpha), radius = size.width * 0.072f, center = Offset(size.width * 0.19f, size.height * 0.89f))
                }
                if (signal == 1 || signal == 2) {
                    drawCircle(amber.copy(alpha = indicatorAlpha), radius = size.width * 0.072f, center = Offset(size.width * 0.81f, size.height * 0.10f))
                    drawCircle(amber.copy(alpha = indicatorAlpha), radius = size.width * 0.072f, center = Offset(size.width * 0.81f, size.height * 0.89f))
                }
            }
        }
    }
}

private fun routeColor(index: Int): Color = when (index % 4) {
    0 -> V6Cyan
    1 -> V6Green
    2 -> V6Gold
    else -> V6Purple
}
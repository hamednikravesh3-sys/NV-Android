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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import ir.nv.navigation.R
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.map.OfflineIranMap
import ir.nv.navigation.map.OnlineIranMap
import ir.nv.navigation.routing.NavigationModeResolver
import ir.nv.navigation.ui.theme.AppThemeMode

private val Cyan = Color(0xFF14D8FF)
private val Green = Color(0xFF43E66B)
private val Gold = Color(0xFFFFB52E)
private val Purple = Color(0xFFBB75FF)
private val NightPanel = Color(0xEE071B2B)
private val DayPanel = Color(0xEDF5FAFD)
private val NightText = Color(0xFFF6FBFF)
private val DayText = Color(0xFF102536)
private val NightMuted = Color(0xFFA9BDC9)
private val DayMuted = Color(0xFF526977)

private enum class V7SearchTarget { ORIGIN, DESTINATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NvReferenceV7(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val panel = if (darkMode) NightPanel else DayPanel
    val text = if (darkMode) NightText else DayText
    val muted = if (darkMode) NightMuted else DayMuted

    var searchVisible by remember { mutableStateOf(state.origin == null || state.destination == null) }
    var searchTarget by remember { mutableStateOf(V7SearchTarget.ORIGIN) }
    var settingsVisible by remember { mutableStateOf(false) }
    var lastPair by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) viewModel.startNavigation()
    }

    fun startDriving() {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startNavigation()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val pairKey = if (state.origin != null && state.destination != null) {
        "${state.origin!!.coordinate.latitude},${state.origin!!.coordinate.longitude}->${state.destination!!.coordinate.latitude},${state.destination!!.coordinate.longitude}"
    } else null

    LaunchedEffect(pairKey) {
        if (pairKey != null && pairKey != lastPair) {
            lastPair = pairKey
            searchVisible = false
            viewModel.clearRoute()
            viewModel.calculateRoute()
        }
    }

    Box(Modifier.fillMaxSize().background(if (darkMode) Color(0xFF07121C) else Color(0xFFEAF2F6))) {
        V7Map(state, viewModel, darkMode)

        if (!state.navigationActive && (searchVisible || state.routeAlternatives.isEmpty())) {
            V7SearchPanel(
                state = state,
                vm = viewModel,
                target = searchTarget,
                onTargetChange = { searchTarget = it },
                onClose = { if (state.origin != null && state.destination != null) searchVisible = false },
                panel = panel,
                text = text,
                muted = muted,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        if (!state.navigationActive && !searchVisible && state.routeAlternatives.isNotEmpty()) {
            V7RouteStrip(
                state = state,
                vm = viewModel,
                panel = panel,
                text = text,
                muted = muted,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)
            )
        }

        if (state.navigationActive) {
            V7ManeuverHud(
                state = state,
                panel = panel,
                text = text,
                muted = muted,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp, start = 12.dp, end = 12.dp)
            )
        }

        if (state.navigationActive && state.currentLocation != null && state.followNavigation) {
            val maneuver = state.route?.maneuvers?.getOrNull(state.maneuverIndex)
            V7RealCarMarker(
                direction = maneuver?.direction,
                distanceToManeuverMeters = state.distanceToNextManeuverMeters,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (!searchVisible && !state.routing) {
            V7SingleInfoPanel(
                state = state,
                panel = panel,
                text = text,
                muted = muted,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 10.dp, bottom = 92.dp)
            )
        }

        if (state.navigationActive) {
            V7SpeedBadge(
                speed = state.speedKmh,
                panel = panel,
                text = text,
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 12.dp, bottom = 96.dp)
            )
        }

        V7BottomBar(
            navigationActive = state.navigationActive,
            hasRoute = state.route != null,
            panel = panel,
            text = text,
            onSearch = {
                if (state.navigationActive) viewModel.stopNavigation()
                searchTarget = if (state.origin == null) V7SearchTarget.ORIGIN else V7SearchTarget.DESTINATION
                searchVisible = true
            },
            onStartStop = { if (state.navigationActive) viewModel.stopNavigation() else startDriving() },
            onRecenter = viewModel::recenterNavigation,
            onSettings = { settingsVisible = true },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)
        )

        if (settingsVisible) {
            ModalBottomSheet(
                onDismissRequest = { settingsVisible = false },
                containerColor = panel,
                contentColor = text
            ) {
                V7Settings(
                    state = state,
                    vm = viewModel,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    text = text,
                    muted = muted
                )
            }
        }
    }
}

@Composable
private fun V7Map(state: NvUiState, vm: NvViewModel, darkMode: Boolean) {
    val context = LocalContext.current
    val routes = state.routeAlternatives.ifEmpty { listOfNotNull(state.route) }
    val source = NavigationModeResolver.preferredSource(state.onlineAvailable, state.offlineReady, state.preferOffline)
    when {
        source == RouteSource.OFFLINE -> OfflineIranMap(
            context = context,
            mapFile = vm.mapFile(),
            routes = routes,
            selectedRouteIndex = state.selectedRouteIndex,
            traffic = state.traffic,
            trafficSegments = state.trafficSegments,
            currentLocation = state.currentLocation,
            followLocation = state.navigationActive && state.followNavigation,
            navigationActive = state.navigationActive,
            navigationZoomLevel = state.navigationZoomLevel,
            navigationRecenterToken = state.navigationRecenterToken,
            bearingDegrees = state.bearingDegrees,
            onManualGesture = vm::pauseNavigationFollow,
            darkMode = darkMode,
            modifier = Modifier.fillMaxSize()
        )
        state.onlineAvailable -> OnlineIranMap(
            context = context,
            routes = routes,
            selectedRouteIndex = state.selectedRouteIndex,
            traffic = state.traffic,
            trafficSegments = state.trafficSegments,
            codedPlaces = (state.personalPlaces + state.recentPlaces + listOfNotNull(state.origin, state.destination))
                .distinctBy { it.personalCode ?: it.code.toString() },
            currentLocation = state.currentLocation,
            followLocation = state.navigationActive && state.followNavigation,
            navigationActive = state.navigationActive,
            navigationZoomLevel = state.navigationZoomLevel,
            navigationRecenterToken = state.navigationRecenterToken,
            bearingDegrees = state.bearingDegrees,
            onManualGesture = vm::pauseNavigationFollow,
            darkMode = darkMode,
            satelliteMode = state.satelliteMode,
            modifier = Modifier.fillMaxSize()
        )
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("نقشه در دسترس نیست", color = if (darkMode) NightMuted else DayMuted)
        }
    }
}

@Composable
private fun V7SearchPanel(
    state: NvUiState,
    vm: NvViewModel,
    target: V7SearchTarget,
    onTargetChange: (V7SearchTarget) -> Unit,
    onClose: () -> Unit,
    panel: Color,
    text: Color,
    muted: Color,
    modifier: Modifier = Modifier
) {
    val isOrigin = target == V7SearchTarget.ORIGIN
    val query = if (isOrigin) state.originQuery else state.destinationQuery
    val suggestions = if (isOrigin) state.originSuggestions else state.destinationSuggestions

    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = panel, border = BorderStroke(1.dp, Cyan.copy(alpha = .55f)), shadowElevation = 8.dp) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = isOrigin,
                    onClick = { onTargetChange(V7SearchTarget.ORIGIN) },
                    label = { Text(state.origin?.name?.take(16) ?: "مبدأ") },
                    leadingIcon = { Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = vm::swapEndpoints) { Icon(Icons.Rounded.SwapHoriz, null, tint = Cyan) }
                FilterChip(
                    selected = !isOrigin,
                    onClick = { onTargetChange(V7SearchTarget.DESTINATION) },
                    label = { Text(state.destination?.name?.take(16) ?: "مقصد") },
                    leadingIcon = { Icon(Icons.Rounded.Place, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { if (isOrigin) vm.updateOriginQuery(it) else vm.updateDestinationQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(if (isOrigin) "جستجوی مبدأ یا کد NV" else "جستجوی مقصد یا کد NV", color = muted) },
                leadingIcon = { Icon(if (isOrigin) Icons.Rounded.MyLocation else Icons.Rounded.Place, null, tint = Cyan) },
                trailingIcon = {
                    IconButton(onClick = {
                        if (query.isNotBlank()) {
                            if (isOrigin) vm.updateOriginQuery("") else vm.updateDestinationQuery("")
                        } else onClose()
                    }) { Icon(Icons.Rounded.Close, null, tint = text) }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = text, unfocusedTextColor = text,
                    focusedBorderColor = Cyan.copy(alpha = .5f), unfocusedBorderColor = muted.copy(alpha = .3f),
                    cursorColor = Cyan
                )
            )

            if (isOrigin) {
                TextButton(onClick = {
                    vm.useCurrentLocationAsOrigin()
                    onTargetChange(V7SearchTarget.DESTINATION)
                }) {
                    Icon(Icons.Rounded.GpsFixed, null, tint = Green)
                    Spacer(Modifier.width(6.dp))
                    Text("موقعیت فعلی به‌عنوان مبدأ", color = text)
                }
            }

            if (query.isNotBlank() && suggestions.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(14.dp), color = panel, border = BorderStroke(1.dp, Cyan.copy(alpha = .25f))) {
                    LazyColumn(Modifier.heightIn(max = 190.dp)) {
                        items(suggestions.take(6)) { place ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (isOrigin) {
                                        vm.selectOrigin(place)
                                        onTargetChange(V7SearchTarget.DESTINATION)
                                    } else {
                                        vm.selectDestination(place)
                                        onClose()
                                    }
                                }.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(if (isOrigin) Icons.Rounded.MyLocation else Icons.Rounded.Place, null, tint = Cyan, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(place.name, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(place.personalCode ?: place.code.toString(), color = muted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V7RouteStrip(state: NvUiState, vm: NvViewModel, panel: Color, text: Color, muted: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.routeAlternatives.take(4).forEachIndexed { index, route ->
            val color = routeColor(index)
            val selected = state.selectedRouteIndex == index
            Surface(
                modifier = Modifier.width(118.dp).clickable { vm.selectRoute(index) },
                shape = RoundedCornerShape(17.dp),
                color = panel,
                border = BorderStroke(if (selected) 2.dp else 1.dp, color),
                shadowElevation = if (selected) 8.dp else 3.dp
            ) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("مسیر ${index + 1}", color = color, fontWeight = FontWeight.Black)
                    Text(String.format("%.1f km", route.distanceMeters / 1000.0), color = text, fontWeight = FontWeight.Bold)
                    Text("${(route.travelSeconds / 60.0).toInt()} دقیقه", color = text)
                    Text(if (index == 0) "پیشنهاد NV" else if (selected) "انتخاب‌شده" else "گزینه جایگزین", color = muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun V7ManeuverHud(state: NvUiState, panel: Color, text: Color, muted: Color, modifier: Modifier = Modifier) {
    val maneuver = state.route?.maneuvers?.getOrNull(state.maneuverIndex)
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = panel, border = BorderStroke(1.dp, Cyan.copy(alpha = .55f)), shadowElevation = 8.dp) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(maneuverIcon(maneuver?.direction), null, tint = Cyan, modifier = Modifier.size(38.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (state.distanceToNextManeuverMeters > 0) "${state.distanceToNextManeuverMeters.toInt()} متر" else "ادامه مسیر", color = text, fontWeight = FontWeight.Black)
                Text(maneuver?.instruction ?: "راهنمای مسیر", color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${(state.remainingSeconds / 60.0).toInt()} دقیقه", color = text, fontWeight = FontWeight.Bold)
                Text(String.format("%.1f km", state.remainingDistanceMeters / 1000.0), color = muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun V7SingleInfoPanel(state: NvUiState, panel: Color, text: Color, muted: Color, modifier: Modifier = Modifier) {
    val weather = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.WEATHER }
    val attraction = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.ATTRACTION }
    if (weather == null && attraction == null) return
    val weatherStyle = weatherVisual(weather?.detail)
    val temperature = weather?.detail?.let { Regex("(-?\\d+)°").find(it)?.groupValues?.getOrNull(1) }

    Surface(modifier = modifier.widthIn(min = 150.dp, max = 185.dp), shape = RoundedCornerShape(18.dp), color = panel, border = BorderStroke(1.dp, Cyan.copy(alpha = .35f)), shadowElevation = 7.dp) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (weather != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(weatherStyle.first, null, tint = weatherStyle.second, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(7.dp))
                    Column {
                        Text(temperature?.let { "$it°" } ?: "هوا", color = text, fontWeight = FontWeight.Black)
                        Text(weatherLabel(weather.detail), color = muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (weather != null && attraction != null) HorizontalDivider(color = muted.copy(alpha = .22f))
            if (attraction != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!attraction.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = attraction.imageUrl,
                            contentDescription = attraction.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Surface(shape = RoundedCornerShape(10.dp), color = Gold.copy(alpha = .12f)) {
                            Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Landscape, null, tint = Gold) }
                        }
                    }
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(attraction.title, color = text, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        Text(String.format("%.1f km جلوتر", attraction.distanceAheadMeters / 1000.0), color = muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun V7RealCarMarker(direction: RouteManeuver.Direction?, distanceToManeuverMeters: Double, modifier: Modifier = Modifier) {
    val signal = when (direction) {
        RouteManeuver.Direction.LEFT, RouteManeuver.Direction.SLIGHT_LEFT, RouteManeuver.Direction.SHARP_LEFT -> -1
        RouteManeuver.Direction.RIGHT, RouteManeuver.Direction.SLIGHT_RIGHT, RouteManeuver.Direction.SHARP_RIGHT -> 1
        RouteManeuver.Direction.UTURN -> 2
        else -> 0
    }
    val active = signal != 0 && distanceToManeuverMeters in 0.0..900.0
    val transition = rememberInfiniteTransition(label = "car-indicator")
    val alpha by transition.animateFloat(
        initialValue = .12f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(350), repeatMode = RepeatMode.Reverse),
        label = "car-indicator-alpha"
    )

    Box(modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Cyan.copy(alpha = .22f), radius = size.minDimension * .45f)
            drawCircle(Cyan.copy(alpha = .75f), radius = size.minDimension * .44f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        }
        Image(
            painter = painterResource(R.drawable.nv_car_top),
            contentDescription = "خودرو",
            modifier = Modifier.width(62.dp).height(82.dp),
            contentScale = ContentScale.Fit
        )
        if (active) {
            Canvas(Modifier.width(70.dp).height(80.dp)) {
                val amber = Gold.copy(alpha = alpha)
                if (signal == -1 || signal == 2) {
                    drawCircle(amber, radius = 6f, center = Offset(7f, 12f))
                    drawCircle(amber, radius = 6f, center = Offset(7f, size.height - 12f))
                }
                if (signal == 1 || signal == 2) {
                    drawCircle(amber, radius = 6f, center = Offset(size.width - 7f, 12f))
                    drawCircle(amber, radius = 6f, center = Offset(size.width - 7f, size.height - 12f))
                }
            }
        }
    }
}

@Composable
private fun V7SpeedBadge(speed: Int, panel: Color, text: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = panel, border = BorderStroke(3.dp, Green), shadowElevation = 7.dp) {
        Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(speed.toString(), color = text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("km/h", color = Green, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun V7BottomBar(
    navigationActive: Boolean,
    hasRoute: Boolean,
    panel: Color,
    text: Color,
    onSearch: () -> Unit,
    onStartStop: () -> Unit,
    onRecenter: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = panel, border = BorderStroke(1.dp, Cyan.copy(alpha = .3f)), shadowElevation = 12.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, null, tint = text) }
            IconButton(onClick = onRecenter) { Icon(Icons.Rounded.MyLocation, null, tint = text) }
            Button(onClick = onStartStop, enabled = hasRoute || navigationActive, shape = RoundedCornerShape(22.dp)) {
                Icon(if (navigationActive) Icons.Rounded.Stop else Icons.Rounded.Navigation, null)
                Spacer(Modifier.width(6.dp))
                Text(if (navigationActive) "پایان" else "شروع رانندگی", fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, null, tint = text) }
        }
    }
}

@Composable
private fun V7Settings(
    state: NvUiState,
    vm: NvViewModel,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    text: Color,
    muted: Color
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ظاهر و نقشه", color = text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text("حالت روز و شب", color = muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(mode.title) },
                    leadingIcon = {
                        Icon(
                            when (mode) {
                                AppThemeMode.AUTO -> Icons.Rounded.BrightnessAuto
                                AppThemeMode.DAY -> Icons.Rounded.WbSunny
                                AppThemeMode.NIGHT -> Icons.Rounded.DarkMode
                            }, null, modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("نمای ماهواره‌ای", color = text)
            Switch(checked = state.satelliteMode, onCheckedChange = { vm.toggleSatelliteMode() })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("اولویت آفلاین", color = text)
            Switch(checked = state.preferOffline, onCheckedChange = { vm.setPreferOffline(it) })
        }
        Button(onClick = vm::startMapDownload, modifier = Modifier.fillMaxWidth()) { Text("دانلود نقشه آفلاین ایران") }
        Spacer(Modifier.height(16.dp))
    }
}

private fun routeColor(index: Int): Color = when (index % 4) {
    0 -> Cyan
    1 -> Green
    2 -> Gold
    else -> Purple
}

private fun maneuverIcon(direction: RouteManeuver.Direction?): ImageVector = when (direction) {
    RouteManeuver.Direction.LEFT, RouteManeuver.Direction.SLIGHT_LEFT, RouteManeuver.Direction.SHARP_LEFT -> Icons.Rounded.TurnLeft
    RouteManeuver.Direction.RIGHT, RouteManeuver.Direction.SLIGHT_RIGHT, RouteManeuver.Direction.SHARP_RIGHT -> Icons.Rounded.TurnRight
    RouteManeuver.Direction.UTURN -> Icons.Rounded.UturnLeft
    RouteManeuver.Direction.ARRIVE -> Icons.Rounded.Place
    else -> Icons.Rounded.Straight
}

private fun weatherVisual(detail: String?): Pair<ImageVector, Color> {
    val value = detail.orEmpty()
    return when {
        value.contains("رعد") -> Icons.Rounded.Thunderstorm to Purple
        value.contains("برف") -> Icons.Rounded.AcUnit to Color(0xFFBCEBFF)
        value.contains("باران") || value.contains("بارش") -> Icons.Rounded.WaterDrop to Cyan
        value.contains("باد") -> Icons.Rounded.Air to Color(0xFF8EE9E9)
        value.contains("مه") -> Icons.Rounded.Visibility to Color(0xFFCFD8DC)
        value.contains("صاف") -> Icons.Rounded.WbSunny to Gold
        else -> Icons.Rounded.Cloud to Cyan
    }
}

private fun weatherLabel(detail: String?): String {
    val value = detail.orEmpty()
    return when {
        value.contains("رعد") -> "رعدوبرق"
        value.contains("برف") -> "برفی"
        value.contains("باران") || value.contains("بارش") -> "بارانی"
        value.contains("باد") -> "وزش باد"
        value.contains("مه") -> "مه‌آلود"
        value.contains("صاف") -> "صاف"
        value.contains("ابری") -> "ابری"
        else -> value.substringBefore("•").trim().ifBlank { "وضعیت مسیر" }.take(18)
    }
}

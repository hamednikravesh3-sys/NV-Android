package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.map.OfflineIranMap
import ir.nv.navigation.map.OnlineIranMap
import ir.nv.navigation.routing.NavigationModeResolver
import ir.nv.navigation.ui.theme.AppThemeMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val RefNavy = Color(0xF4071728)
private val RefPanel = Color(0xF20B243B)
private val RefPanel2 = Color(0xF4123552)
private val RefCyan = Color(0xFF14D8FF)
private val RefGreen = Color(0xFF50E86A)
private val RefAmber = Color(0xFFFFB52E)
private val RefRed = Color(0xFFFF4358)
private val RefText = Color(0xFFF5FAFF)
private val RefMuted = Color(0xFFA9BBC9)
private val RefBorder = Color(0xFF176B91)
private val RefManeuver = Color(0xE809665B)

private enum class RefPanelType { WEATHER, FAVORITES, SETTINGS }
private enum class RefLocationAction { ORIGIN, NAVIGATE }

@Composable
fun NvReferenceV3(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showPlanner by remember { mutableStateOf(false) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var sidePanel by remember { mutableStateOf<RefPanelType?>(null) }
    var pendingLocationAction by remember { mutableStateOf(RefLocationAction.ORIGIN) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            when (pendingLocationAction) {
                RefLocationAction.ORIGIN -> viewModel.useCurrentLocationAsOrigin()
                RefLocationAction.NAVIGATE -> viewModel.startNavigation()
            }
        }
    }

    fun requestLocation(action: RefLocationAction) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            when (action) {
                RefLocationAction.ORIGIN -> viewModel.useCurrentLocationAsOrigin()
                RefLocationAction.NAVIGATE -> viewModel.startNavigation()
            }
        } else {
            pendingLocationAction = action
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    fun openSearch(query: String = "") {
        if (query.isNotBlank()) viewModel.updateDestinationQuery(query)
        showPlanner = true
        sidePanel = null
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF06111C))) {
        val compact = maxWidth < 600.dp

        ReferenceMap(
            state = state,
            viewModel = viewModel,
            darkMode = darkMode,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color(0xB0071728),
                    .18f to Color.Transparent,
                    .72f to Color.Transparent,
                    1f to Color(0xC2071728)
                )
            )
        )

        ReferenceHeader(
            state = state,
            onSearch = { openSearch() },
            onWeather = { sidePanel = RefPanelType.WEATHER; showPlanner = false },
            onProfile = { sidePanel = RefPanelType.FAVORITES; showPlanner = false },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)
        )

        ReferenceServiceRail(
            onLayers = viewModel::toggleSatelliteMode,
            onGas = { openSearch("پمپ بنزین") },
            onFood = { openSearch("رستوران") },
            onHotel = { openSearch("اقامتگاه") },
            onSight = { openSearch("جاذبه دیدنی") },
            onMore = { sidePanel = RefPanelType.SETTINGS; showPlanner = false },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp, top = 72.dp, bottom = 150.dp)
        )

        if (state.route != null) {
            ReferenceRoutePanel(
                state = state,
                compact = compact,
                onSelectRoute = { index ->
                    if (state.navigationActive) viewModel.stopNavigation()
                    viewModel.selectRoute(index)
                    if (state.navigationActive) viewModel.startNavigation()
                },
                onStart = { requestLocation(RefLocationAction.NAVIGATE) },
                onStop = viewModel::stopNavigation,
                onEdit = { showPlanner = true },
                onCode = { showCodeDialog = true },
                modifier = if (compact) {
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 82.dp, end = 8.dp).widthIn(max = 196.dp)
                } else {
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 92.dp, end = 14.dp).width(330.dp)
                }
            )
        }

        if (state.navigationActive && state.route != null) {
            ReferenceManeuverCard(
                state = state,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 88.dp)
                    .widthIn(min = 210.dp, max = if (compact) 290.dp else 360.dp)
            )

            ReferenceSpeedGauge(
                speed = state.speedKmh,
                voiceEnabled = state.voiceEnabled,
                onToggleVoice = viewModel::toggleVoice,
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 18.dp, bottom = 154.dp)
            )

            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(onClick = viewModel::zoomNavigationIn, containerColor = RefPanel, contentColor = RefText) {
                    Icon(Icons.Rounded.Add, "بزرگ‌نمایی")
                }
                FloatingActionButton(onClick = viewModel::recenterNavigation, containerColor = RefPanel, contentColor = RefText) {
                    Icon(Icons.Rounded.MyLocation, "مرکز")
                }
                FloatingActionButton(onClick = viewModel::zoomNavigationOut, containerColor = RefPanel, contentColor = RefText) {
                    Icon(Icons.Rounded.Remove, "کوچک‌نمایی")
                }
            }
        }

        if (state.route != null) {
            ReferenceTripBar(
                state = state,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 10.dp, end = 10.dp, bottom = 78.dp)
            )
        }

        ReferenceBottomDock(
            routeAvailable = state.route != null,
            navigationActive = state.navigationActive,
            onNavigation = {
                if (state.route != null && !state.navigationActive) requestLocation(RefLocationAction.NAVIGATE)
                else if (state.route == null) showPlanner = true
            },
            onSearch = { openSearch() },
            onFavorites = { sidePanel = RefPanelType.FAVORITES; showPlanner = false },
            onRoutes = { if (state.route == null) showPlanner = true },
            onWeather = { sidePanel = RefPanelType.WEATHER; showPlanner = false },
            onSettings = { sidePanel = RefPanelType.SETTINGS; showPlanner = false },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        )

        if (showPlanner) {
            ReferencePlanner(
                state = state,
                onDismiss = { showPlanner = false },
                onOriginChange = viewModel::updateOriginQuery,
                onDestinationChange = viewModel::updateDestinationQuery,
                onOriginSelect = viewModel::selectOrigin,
                onDestinationSelect = viewModel::selectDestination,
                onUseCurrentLocation = { requestLocation(RefLocationAction.ORIGIN) },
                onSwap = viewModel::swapEndpoints,
                onRoute = {
                    viewModel.calculateRoute()
                    showPlanner = false
                },
                modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding().padding(start = 76.dp, end = 8.dp, bottom = 82.dp)
            )
        }

        sidePanel?.let { type ->
            ReferenceSideSheet(
                type = type,
                state = state,
                themeMode = themeMode,
                onDismiss = { sidePanel = null },
                onPlace = { place -> viewModel.selectDestination(place); sidePanel = null; showPlanner = true },
                onTheme = onThemeModeChange,
                onSatellite = viewModel::toggleSatelliteMode,
                onOffline = viewModel::setPreferOffline,
                onDownload = viewModel::startMapDownload,
                modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding().padding(start = 76.dp, end = 8.dp, bottom = 82.dp)
            )
        }

        if (showCodeDialog) {
            ReferenceCodeDialog(
                place = state.destination ?: state.origin,
                saved = state.personalPlaces,
                onDismiss = { showCodeDialog = false },
                onSave = { place, code -> viewModel.savePersonalCode(place, code); showCodeDialog = false },
                onDelete = viewModel::deletePersonalCode
            )
        }

        state.message?.let { message ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 76.dp, start = 84.dp, end = 210.dp),
                shape = RoundedCornerShape(14.dp),
                color = RefNavy,
                border = BorderStroke(1.dp, RefBorder)
            ) {
                Text(message, color = RefText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ReferenceMap(state: NvUiState, viewModel: NvViewModel, darkMode: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val source = NavigationModeResolver.preferredSource(state.onlineAvailable, state.offlineReady, state.preferOffline)
    when {
        source == RouteSource.OFFLINE -> OfflineIranMap(
            context = context,
            mapFile = viewModel.mapFile(),
            routes = state.routeAlternatives.ifEmpty { listOfNotNull(state.route) },
            selectedRouteIndex = state.selectedRouteIndex,
            traffic = state.traffic,
            trafficSegments = state.trafficSegments,
            currentLocation = state.currentLocation,
            followLocation = state.navigationActive && state.followNavigation,
            navigationActive = state.navigationActive,
            navigationZoomLevel = state.navigationZoomLevel,
            navigationRecenterToken = state.navigationRecenterToken,
            bearingDegrees = state.bearingDegrees,
            onManualGesture = viewModel::pauseNavigationFollow,
            darkMode = darkMode,
            modifier = modifier
        )
        state.onlineAvailable -> OnlineIranMap(
            context = context,
            routes = state.routeAlternatives.ifEmpty { listOfNotNull(state.route) },
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
            onManualGesture = viewModel::pauseNavigationFollow,
            darkMode = darkMode,
            satelliteMode = state.satelliteMode,
            modifier = modifier
        )
        else -> Box(modifier.background(Color(0xFF07111A)), contentAlignment = Alignment.Center) {
            Text("اینترنت را وصل کنید یا نقشه آفلاین ایران را دانلود کنید", color = RefMuted, modifier = Modifier.padding(24.dp))
        }
    }
}

@Composable
private fun ReferenceHeader(state: NvUiState, onSearch: () -> Unit, onWeather: () -> Unit, onProfile: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFDDE5EA))) {
            Row(Modifier.height(60.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("N", color = Color(0xFF101820), fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                Text("V", color = Color(0xFF00C93C), fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Surface(
            modifier = Modifier.weight(1f).height(60.dp).clickable(onClick = onSearch),
            shape = RoundedCornerShape(20.dp), color = RefPanel2, border = BorderStroke(1.dp, RefCyan.copy(alpha = .65f))
        ) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Mic, null, tint = RefText)
                Spacer(Modifier.width(8.dp))
                Text(state.destination?.name ?: "نام یا کد مکان را جستجو کنید…", color = if (state.destination == null) RefMuted else RefText,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Icon(Icons.Rounded.Search, "جستجو", tint = RefCyan)
            }
        }
        val weather = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.WEATHER }
        Surface(modifier = Modifier.size(60.dp).clickable(onClick = onWeather), shape = RoundedCornerShape(20.dp), color = RefPanel2, border = BorderStroke(1.dp, RefBorder)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.WbSunny, weather?.title ?: "آب‌وهوا", tint = Color(0xFFFFD43B), modifier = Modifier.size(31.dp)) }
        }
        Surface(modifier = Modifier.size(60.dp).clickable(onClick = onProfile), shape = RoundedCornerShape(20.dp), color = RefPanel2, border = BorderStroke(1.dp, RefBorder)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, "مکان‌های من", tint = RefText, modifier = Modifier.size(30.dp)) }
        }
    }
}

@Composable
private fun ReferenceServiceRail(onLayers: () -> Unit, onGas: () -> Unit, onFood: () -> Unit, onHotel: () -> Unit, onSight: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        RailButton(Icons.Rounded.Layers, "لایه‌ها", onLayers)
        RailButton(Icons.Rounded.LocalGasStation, "بنزین", onGas)
        RailButton(Icons.Rounded.Restaurant, "رستوران", onFood)
        RailButton(Icons.Rounded.Hotel, "اقامتگاه", onHotel)
        RailButton(Icons.Rounded.PhotoCamera, "دیدنی", onSight)
        RailButton(Icons.Rounded.MoreHoriz, "بیشتر", onMore)
    }
}

@Composable
private fun RailButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.width(66.dp).height(72.dp).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = RefNavy, border = BorderStroke(1.dp, RefBorder)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, label, tint = RefText, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = RefText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReferenceManeuverCard(state: NvUiState, modifier: Modifier = Modifier) {
    val maneuver = state.route?.maneuvers?.getOrNull(state.maneuverIndex)
    val distance = state.distanceToNextManeuverMeters
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = RefManeuver, border = BorderStroke(2.dp, Color(0xFF65F2C9))) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(maneuverIcon(maneuver?.direction), null, tint = Color.White, modifier = Modifier.size(42.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(formatMeters(distance), color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                Text(maneuver?.instruction ?: "ادامه مسیر", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun maneuverIcon(direction: RouteManeuver.Direction?) = when (direction) {
    RouteManeuver.Direction.LEFT, RouteManeuver.Direction.SLIGHT_LEFT, RouteManeuver.Direction.SHARP_LEFT -> Icons.Rounded.TurnLeft
    RouteManeuver.Direction.RIGHT, RouteManeuver.Direction.SLIGHT_RIGHT, RouteManeuver.Direction.SHARP_RIGHT -> Icons.Rounded.TurnRight
    RouteManeuver.Direction.UTURN -> Icons.Rounded.UturnLeft
    RouteManeuver.Direction.ARRIVE -> Icons.Rounded.Place
    else -> Icons.Rounded.Straight
}

@Composable
private fun ReferenceRoutePanel(
    state: NvUiState,
    compact: Boolean,
    onSelectRoute: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = RefNavy, border = BorderStroke(1.dp, RefBorder)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("مسیرها", color = RefText, fontWeight = FontWeight.Black)
            state.routeAlternatives.take(3).forEachIndexed { index, route ->
                val selected = index == state.selectedRouteIndex
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectRoute(index) },
                    shape = RoundedCornerShape(17.dp),
                    color = if (selected) RefPanel2 else RefPanel,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) RefCyan else RefBorder)
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(if (selected) RefCyan else RefMuted))
                            Spacer(Modifier.width(7.dp))
                            Text(if (index == 0) "مسیر پیشنهادی" else "مسیر ${index + 1}", color = RefMuted, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(formatDuration(route.travelSeconds), color = RefText, fontWeight = FontWeight.Black, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
                        Text(formatKm(route.distanceMeters), color = RefMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.routeAlternatives.isEmpty()) Text("مسیر جایگزین موجود نیست", color = RefMuted)
            Button(
                onClick = if (state.navigationActive) onStop else onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if (state.navigationActive) RefRed else RefCyan, contentColor = Color(0xFF04121C)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(if (state.navigationActive) Icons.Rounded.Stop else Icons.Rounded.Navigation, null)
                Spacer(Modifier.width(7.dp))
                Text(if (state.navigationActive) "توقف" else "شروع حرکت", fontWeight = FontWeight.Black)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp), border = BorderStroke(1.dp, RefBorder)) { Text("ویرایش", color = RefText) }
                OutlinedButton(onClick = onCode, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp), border = BorderStroke(1.dp, RefBorder)) { Text("کد NV", color = RefGreen) }
            }
            val attraction = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.ATTRACTION || it.kind == RouteNotice.Kind.SERVICE }
            attraction?.let {
                Divider(color = RefBorder.copy(alpha = .5f))
                Text("جلوتر در مسیر", color = RefText, fontWeight = FontWeight.Bold)
                Text(it.title, color = RefText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatMeters(it.distanceAheadMeters)} جلوتر", color = RefGreen, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReferenceSpeedGauge(speed: Int, voiceEnabled: Boolean, onToggleVoice: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.size(104.dp), shape = CircleShape, color = RefNavy, border = BorderStroke(4.dp, RefGreen)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(speed.toString(), color = RefGreen, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineLarge)
                Text("km/h", color = RefText, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalIconButton(onClick = onToggleVoice, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = RefPanel, contentColor = RefText)) {
            Icon(if (voiceEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, if (voiceEnabled) "قطع صدا" else "فعال‌کردن صدا")
        }
    }
}

@Composable
private fun ReferenceTripBar(state: NvUiState, modifier: Modifier = Modifier) {
    val seconds = if (state.navigationActive) state.remainingSeconds else state.route?.travelSeconds ?: 0.0
    val distance = if (state.navigationActive) state.remainingDistanceMeters else state.route?.distanceMeters ?: 0.0
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = RefNavy, border = BorderStroke(1.dp, RefBorder)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TripMetric(Icons.Rounded.Schedule, arrivalTime(seconds), "زمان رسیدن", Modifier.weight(1f))
            VerticalDivider(Modifier.height(42.dp), color = RefBorder.copy(alpha = .5f))
            TripMetric(Icons.Rounded.Timer, formatDuration(seconds), "باقی‌مانده", Modifier.weight(1f))
            VerticalDivider(Modifier.height(42.dp), color = RefBorder.copy(alpha = .5f))
            TripMetric(Icons.Rounded.Route, formatKm(distance), "تا مقصد", Modifier.weight(1f))
            VerticalDivider(Modifier.height(42.dp), color = RefBorder.copy(alpha = .5f))
            Column(Modifier.weight(1.25f).padding(start = 10.dp)) {
                Text("مقصد", color = RefMuted, style = MaterialTheme.typography.labelSmall)
                Text(state.destination?.name ?: "—", color = RefText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TripMetric(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = RefCyan, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(7.dp))
        Column {
            Text(value, color = RefText, fontWeight = FontWeight.Black, maxLines = 1)
            Text(label, color = RefMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun ReferenceBottomDock(
    routeAvailable: Boolean,
    navigationActive: Boolean,
    onNavigation: () -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onRoutes: () -> Unit,
    onWeather: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = RefNavy, border = BorderStroke(1.dp, RefBorder), shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
        Row(Modifier.height(72.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            DockButton(Icons.Rounded.Navigation, "مسیریابی", navigationActive || routeAvailable, onNavigation)
            DockButton(Icons.Rounded.Search, "جستجو", false, onSearch)
            DockButton(Icons.Rounded.BookmarkBorder, "علاقه‌مندی", false, onFavorites)
            DockButton(Icons.Rounded.Route, "مسیرها", routeAvailable, onRoutes)
            DockButton(Icons.Rounded.Cloud, "آب‌وهوا", false, onWeather)
            DockButton(Icons.Rounded.Settings, "تنظیمات", false, onSettings)
        }
    }
}

@Composable
private fun RowScope.DockButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = if (selected) RefCyan else RefMuted, modifier = Modifier.size(27.dp))
        Text(label, color = if (selected) RefCyan else RefMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun ReferencePlanner(
    state: NvUiState,
    onDismiss: () -> Unit,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onOriginSelect: (Place) -> Unit,
    onDestinationSelect: (Place) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onSwap: () -> Unit,
    onRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = RefNavy, border = BorderStroke(1.dp, RefBorder)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("انتخاب مسیر", color = RefText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onSwap) { Icon(Icons.Rounded.SwapVert, "جابجایی", tint = RefCyan) }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "بستن", tint = RefMuted) }
            }
            SearchPlaceField("مبدأ", state.originQuery, onOriginChange, onUseCurrentLocation, state.originSuggestions, onOriginSelect)
            SearchPlaceField("مقصد", state.destinationQuery, onDestinationChange, null, state.destinationSuggestions, onDestinationSelect)
            if (state.searchMessage != null) Text(state.searchMessage, color = RefAmber, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRoute, enabled = !state.routing && state.origin != null && state.destination != null, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = RefCyan, contentColor = Color(0xFF03121C))) {
                if (state.routing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Route, null)
                Spacer(Modifier.width(7.dp))
                Text(if (state.routing) "در حال محاسبه…" else "نمایش مسیرها", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun SearchPlaceField(label: String, value: String, onChange: (String) -> Unit, onGps: (() -> Unit)?, suggestions: List<Place>, onSelect: (Place) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            leadingIcon = { Icon(if (label == "مبدأ") Icons.Rounded.RadioButtonChecked else Icons.Rounded.Place, null, tint = RefCyan) },
            trailingIcon = {
                Row {
                    if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "پاک کردن") }
                    onGps?.let { IconButton(onClick = it) { Icon(Icons.Rounded.MyLocation, "موقعیت فعلی") } }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RefCyan, unfocusedBorderColor = RefBorder, focusedTextColor = RefText, unfocusedTextColor = RefText, focusedLabelColor = RefCyan, unfocusedLabelColor = RefMuted)
        )
        suggestions.take(4).forEach { place ->
            Surface(modifier = Modifier.fillMaxWidth().clickable { onSelect(place) }, color = RefPanel, shape = RoundedCornerShape(12.dp)) {
                Text(place.displayName, color = RefText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ReferenceSideSheet(
    type: RefPanelType,
    state: NvUiState,
    themeMode: AppThemeMode,
    onDismiss: () -> Unit,
    onPlace: (Place) -> Unit,
    onTheme: (AppThemeMode) -> Unit,
    onSatellite: () -> Unit,
    onOffline: (Boolean) -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = RefNavy, border = BorderStroke(1.dp, RefBorder)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when (type) { RefPanelType.WEATHER -> "آب‌وهوا و مسیر"; RefPanelType.FAVORITES -> "مکان‌های من"; RefPanelType.SETTINGS -> "تنظیمات" }, color = RefText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "بستن", tint = RefMuted) }
            }
            when (type) {
                RefPanelType.WEATHER -> {
                    val notices = state.routeNotices.filter { it.kind == RouteNotice.Kind.WEATHER || it.kind == RouteNotice.Kind.TRAFFIC || it.kind == RouteNotice.Kind.ATTRACTION || it.kind == RouteNotice.Kind.SERVICE }
                    if (notices.isEmpty()) Text("پس از محاسبه مسیر، آب‌وهوا و نقاط جلوتر اینجا نمایش داده می‌شوند.", color = RefMuted)
                    notices.take(8).forEach { notice ->
                        Surface(color = RefPanel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(11.dp)) {
                                Text(notice.title, color = RefText, fontWeight = FontWeight.Bold)
                                Text(notice.detail, color = RefMuted, style = MaterialTheme.typography.bodySmall)
                                Text("${formatMeters(notice.distanceAheadMeters)} جلوتر", color = RefGreen, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                RefPanelType.FAVORITES -> {
                    if (state.personalPlaces.isEmpty()) Text("هنوز کد شخصی یا مکان ذخیره‌شده ندارید.", color = RefMuted)
                    state.personalPlaces.take(10).forEach { place ->
                        Surface(modifier = Modifier.fillMaxWidth().clickable { onPlace(place) }, color = RefPanel, shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Bookmark, null, tint = RefGreen)
                                Spacer(Modifier.width(9.dp))
                                Text(place.displayName, color = RefText, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                RefPanelType.SETTINGS -> {
                    SettingSwitch("نمای ماهواره‌ای", state.satelliteMode, onSatellite)
                    SettingSwitch("اولویت نقشه آفلاین", state.preferOffline, { onOffline(!state.preferOffline) })
                    if (!state.offlineReady) Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(6.dp)); Text("دانلود نقشه آفلاین ایران") }
                    Text("حالت نمایش", color = RefMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.values().forEach { mode ->
                            FilterChip(selected = themeMode == mode, onClick = { onTheme(mode) }, label = { Text(mode.name) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onClick: () -> Unit) {
    Surface(color = RefPanel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = RefText, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = { onClick() })
        }
    }
}

@Composable
private fun ReferenceCodeDialog(place: Place?, saved: List<Place>, onDismiss: () -> Unit, onSave: (Place, String) -> Unit, onDelete: (String) -> Unit) {
    if (place == null) return
    var code by remember(place) { mutableStateOf(place.personalCode.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعریف کد NV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(place.name)
                OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit).take(9) }, label = { Text("کد عددی شخصی") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                val existing = saved.firstOrNull { it.personalCode == code && code.isNotBlank() }
                existing?.let {
                    Text("این کد برای «${it.name}» ذخیره شده است.", color = RefAmber)
                    TextButton(onClick = { onDelete(code); code = "" }) { Text("حذف کد") }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(place, code) }, enabled = code.isNotBlank()) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

private fun formatMeters(meters: Double): String = if (meters >= 1000.0) formatKm(meters) else "${meters.roundToInt().coerceAtLeast(0)} متر"
private fun formatKm(meters: Double): String = String.format(Locale.US, "%.1f km", (meters / 1000.0).coerceAtLeast(0.0))
private fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60.0).roundToInt().coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60}س ${minutes % 60}د" else "$minutes دقیقه"
}
private fun arrivalTime(seconds: Double): String {
    val future = System.currentTimeMillis() + seconds.coerceAtLeast(0.0).toLong() * 1000L
    return java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(future))
}

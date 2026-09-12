package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.map.OfflineIranMap
import ir.nv.navigation.map.OnlineIranMap
import ir.nv.navigation.routing.NavigationModeResolver
import ir.nv.navigation.ui.theme.AppThemeMode
import ir.nv.navigation.ui.theme.NvColors
import ir.nv.navigation.ui.theme.NvElevation
import ir.nv.navigation.ui.theme.NvRadius
import ir.nv.navigation.ui.theme.NvSpacing

private val V8Cyan = NvColors.RouteBlue
private val V8Green = NvColors.Success
private val V8Gold = NvColors.Warning
private val V8Purple = NvColors.Info

private enum class V8SearchTarget { ORIGIN, DESTINATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NvReferenceV8(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel,
    onNearby: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    onRefineOrigin: (() -> Unit)? = null,
    onRefineDestination: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val panel = MaterialTheme.colorScheme.surface.copy(alpha = if (darkMode) .95f else .97f)
    val text = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var searchVisible by remember { mutableStateOf(state.origin == null || state.destination == null) }
    var searchTarget by remember { mutableStateOf(V8SearchTarget.ORIGIN) }
    var settingsVisible by remember { mutableStateOf(false) }
    var lastPair by remember { mutableStateOf<String?>(null) }
    var preciseLocationWarning by remember { mutableStateOf(false) }

    fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    val navigationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            preciseLocationWarning = false
            viewModel.startNavigation()
        } else {
            preciseLocationWarning = true
        }
    }

    val currentLocationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            preciseLocationWarning = false
            viewModel.useCurrentLocationAsOrigin()
        } else {
            preciseLocationWarning = true
        }
    }

    fun startDriving() {
        if (hasFineLocationPermission()) {
            preciseLocationWarning = false
            viewModel.startNavigation()
        } else {
            navigationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    fun useCurrentLocationAsOrigin() {
        if (hasFineLocationPermission()) {
            preciseLocationWarning = false
            viewModel.useCurrentLocationAsOrigin()
        } else {
            currentLocationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    fun locateMe() {
        if (!hasFineLocationPermission()) {
            currentLocationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        preciseLocationWarning = false
        if (state.currentLocation != null) viewModel.recenterNavigation()
        else viewModel.useCurrentLocationAsOrigin()
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        V8Map(state, viewModel, darkMode)

        if (!state.navigationActive && (searchVisible || state.routeAlternatives.isEmpty())) {
            V8SearchPanel(
                state = state,
                vm = viewModel,
                target = searchTarget,
                onTargetChange = { searchTarget = it },
                onClose = { if (state.origin != null && state.destination != null) searchVisible = false },
                onUseCurrentLocation = ::useCurrentLocationAsOrigin,
                onRefineOrigin = onRefineOrigin,
                onRefineDestination = onRefineDestination,
                panel = panel,
                text = text,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(NvSpacing.Md)
            )
        }

        if (!state.navigationActive && !searchVisible && state.routeAlternatives.isNotEmpty()) {
            V8RouteStrip(
                state = state,
                vm = viewModel,
                panel = panel,
                text = text,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = NvSpacing.Sm)
            )
        }

        if (state.navigationActive) {
            V8ManeuverHud(
                state = state,
                panel = panel,
                text = text,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Sm)
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = NvSpacing.Md, bottom = 100.dp),
                shape = CircleShape,
                color = panel,
                border = BorderStroke(4.dp, V8Green),
                shadowElevation = NvElevation.Floating
            ) {
                Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.speedKmh.toString(),
                            color = text,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text("km/h", color = V8Green, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (!state.navigationActive) {
            Surface(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = NvSpacing.Md),
                shape = CircleShape,
                color = panel,
                border = BorderStroke(1.dp, V8Cyan.copy(alpha = .65f)),
                shadowElevation = NvElevation.Floating
            ) {
                IconButton(onClick = ::locateMe, modifier = Modifier.size(52.dp)) {
                    Icon(
                        Icons.Rounded.MyLocation,
                        contentDescription = "یافتن موقعیت من",
                        tint = V8Cyan,
                        modifier = Modifier.size(29.dp)
                    )
                }
            }
        }

        V8BottomBar(
            navigationActive = state.navigationActive,
            hasRoute = state.route != null,
            panel = panel,
            text = text,
            offline = state.preferOffline,
            onSearch = {
                if (state.navigationActive) viewModel.stopNavigation()
                searchTarget = if (state.origin == null) V8SearchTarget.ORIGIN else V8SearchTarget.DESTINATION
                searchVisible = true
            },
            onStartStop = { if (state.navigationActive) viewModel.stopNavigation() else startDriving() },
            onNearby = onNearby,
            onPin = onPin,
            onOffline = {
                if (state.offlineReady) viewModel.setPreferOffline(!state.preferOffline)
                else viewModel.startMapDownload()
            },
            onSettings = { settingsVisible = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = NvSpacing.Sm, vertical = NvSpacing.Sm)
        )

        if (preciseLocationWarning) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = NvSpacing.Lg, vertical = 92.dp),
                containerColor = panel,
                contentColor = text
            ) {
                Text("برای مکان دقیق، دسترسی «مکان دقیق / Precise location» را فعال کنید.")
            }
        }

        if (settingsVisible) {
            ModalBottomSheet(
                onDismissRequest = { settingsVisible = false },
                containerColor = panel,
                contentColor = text
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(NvSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)
                ) {
                    Text("تنظیمات NV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("روز / شب", color = muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.title) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("نقشه آفلاین ایران")
                        Switch(
                            checked = state.preferOffline,
                            onCheckedChange = { enabled ->
                                if (enabled && !state.offlineReady) viewModel.startMapDownload()
                                else viewModel.setPreferOffline(enabled)
                            }
                        )
                    }
                    Text(
                        if (state.offlineReady) {
                            "بسته آفلاین ایران نصب است؛ نمایش و مسیریابی می‌تواند بدون اینترنت انجام شود."
                        } else {
                            "برای استفاده آفلاین، بسته کامل ایران را دانلود کنید."
                        },
                        color = muted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (!state.offlineReady) {
                        Button(onClick = viewModel::startMapDownload, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                            Spacer(Modifier.width(NvSpacing.Sm))
                            Text("دانلود نقشه آفلاین ایران")
                        }
                    }
                    Spacer(Modifier.height(NvSpacing.Md))
                }
            }
        }
    }
}

@Composable
private fun V8Map(state: NvUiState, vm: NvViewModel, darkMode: Boolean) {
    val context = LocalContext.current
    val routes = remember(state.routeAlternatives, state.route) {
        state.routeAlternatives.ifEmpty { listOfNotNull(state.route) }
    }
    val coded = remember(state.personalPlaces, state.recentPlaces, state.origin, state.destination) {
        (state.personalPlaces + state.recentPlaces + listOfNotNull(state.origin, state.destination))
            .distinctBy { it.personalCode ?: it.code.toString() }
    }

    when (NavigationModeResolver.preferredSource(state.onlineAvailable, state.offlineReady, state.preferOffline)) {
        RouteSource.OFFLINE -> OfflineIranMap(
            context,
            vm.mapFile(),
            routes,
            state.selectedRouteIndex,
            state.traffic,
            state.trafficSegments,
            state.currentLocation,
            state.followNavigation,
            state.navigationActive,
            state.navigationZoomLevel,
            state.navigationRecenterToken,
            state.bearingDegrees,
            vm::pauseNavigationFollow,
            darkMode,
            Modifier.fillMaxSize()
        )

        RouteSource.ONLINE -> OnlineIranMap(
            context,
            routes,
            state.selectedRouteIndex,
            state.traffic,
            state.trafficSegments,
            coded,
            state.currentLocation,
            state.followNavigation,
            state.navigationActive,
            state.navigationZoomLevel,
            state.navigationRecenterToken,
            state.bearingDegrees,
            vm::pauseNavigationFollow,
            darkMode,
            false,
            Modifier.fillMaxSize()
        )

        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("نقشه در دسترس نیست", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun V8SearchPanel(
    state: NvUiState,
    vm: NvViewModel,
    target: V8SearchTarget,
    onTargetChange: (V8SearchTarget) -> Unit,
    onClose: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onRefineOrigin: (() -> Unit)?,
    onRefineDestination: (() -> Unit)?,
    panel: Color,
    text: Color,
    modifier: Modifier = Modifier
) {
    val isOrigin = target == V8SearchTarget.ORIGIN
    val query = if (isOrigin) state.originQuery else state.destinationQuery
    val suggestions = if (isOrigin) state.originSuggestions else state.destinationSuggestions
    val selectedPlace = if (isOrigin) state.origin else state.destination
    val refineAction = if (isOrigin) onRefineOrigin else onRefineDestination
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NvRadius.Card),
        color = panel,
        border = BorderStroke(1.dp, V8Cyan.copy(alpha = .6f)),
        shadowElevation = NvElevation.Card
    ) {
        Column(Modifier.padding(NvSpacing.Sm), verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                FilterChip(
                    selected = isOrigin,
                    onClick = { onTargetChange(V8SearchTarget.ORIGIN) },
                    label = { Text(state.origin?.name?.take(15) ?: "مبدأ") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = vm::swapEndpoints) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = "جابه‌جایی مبدأ و مقصد", tint = V8Cyan)
                }
                FilterChip(
                    selected = !isOrigin,
                    onClick = { onTargetChange(V8SearchTarget.DESTINATION) },
                    label = { Text(state.destination?.name?.take(15) ?: "مقصد") },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { if (isOrigin) vm.updateOriginQuery(it) else vm.updateDestinationQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(NvRadius.Medium),
                placeholder = { Text(if (isOrigin) "جستجوی مبدأ یا کد NV" else "جستجوی مقصد یا کد NV") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (query.isNotBlank()) {
                                if (isOrigin) vm.updateOriginQuery("") else vm.updateDestinationQuery("")
                            } else onClose()
                        }
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "بستن جستجو")
                    }
                }
            )

            if (isOrigin) {
                TextButton(onClick = { onUseCurrentLocation(); onTargetChange(V8SearchTarget.DESTINATION) }) {
                    Icon(Icons.Rounded.GpsFixed, contentDescription = null, tint = V8Green)
                    Spacer(Modifier.width(NvSpacing.Xs))
                    Text("موقعیت دقیق فعلی به عنوان مبدأ", color = text)
                }
            }

            if (selectedPlace != null && refineAction != null) {
                OutlinedButton(onClick = refineAction, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.EditLocationAlt, contentDescription = null, tint = V8Gold)
                    Spacer(Modifier.width(NvSpacing.Sm))
                    Text(if (isOrigin) "تنظیم دقیق نشانگر مبدأ روی نقشه" else "تنظیم دقیق نشانگر مقصد روی نقشه")
                }
            }

            if (query.isNotBlank() && suggestions.isNotEmpty()) {
                Column {
                    suggestions.take(6).forEach { place ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isOrigin) {
                                        vm.selectOrigin(place)
                                        onTargetChange(V8SearchTarget.DESTINATION)
                                        onRefineOrigin?.invoke()
                                    } else {
                                        vm.selectDestination(place)
                                        onRefineDestination?.invoke()
                                        onClose()
                                    }
                                }
                                .padding(NvSpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Place, contentDescription = null, tint = V8Cyan)
                            Spacer(Modifier.width(NvSpacing.Sm))
                            Text(
                                place.name,
                                color = text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                place.personalCode ?: place.code.toString(),
                                color = muted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V8RouteStrip(
    state: NvUiState,
    vm: NvViewModel,
    panel: Color,
    text: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = NvSpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
    ) {
        state.routeAlternatives.take(4).forEachIndexed { index, route ->
            val color = v8RouteColor(index)
            Surface(
                modifier = Modifier.width(116.dp).clickable { vm.selectRoute(index) },
                shape = RoundedCornerShape(NvRadius.Large),
                color = panel,
                border = BorderStroke(if (state.selectedRouteIndex == index) 3.dp else 1.dp, color),
                shadowElevation = NvElevation.Card
            ) {
                Column(Modifier.padding(NvSpacing.Sm), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("مسیر ${index + 1}", color = color, fontWeight = FontWeight.Black)
                    Text(String.format("%.1f km", route.distanceMeters / 1000.0), color = text, fontWeight = FontWeight.Bold)
                    Text("${(route.travelSeconds / 60.0).toInt()} دقیقه", color = text)
                }
            }
        }
    }
}

@Composable
private fun V8ManeuverHud(
    state: NvUiState,
    panel: Color,
    text: Color,
    modifier: Modifier = Modifier
) {
    val maneuver = state.route?.maneuvers?.getOrNull(state.maneuverIndex)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NvRadius.Card),
        color = panel,
        border = BorderStroke(2.dp, V8Cyan.copy(alpha = .7f)),
        shadowElevation = NvElevation.Floating
    ) {
        Row(Modifier.padding(NvSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                v8ManeuverIcon(maneuver?.direction),
                contentDescription = null,
                tint = V8Cyan,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.width(NvSpacing.Sm))
            Column(Modifier.weight(1f)) {
                Text(
                    "${state.distanceToNextManeuverMeters.toInt()} متر",
                    color = text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    maneuver?.instruction ?: "ادامه مسیر",
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${(state.remainingSeconds / 60.0).toInt()} دقیقه", color = text, fontWeight = FontWeight.Bold)
                Text(String.format("%.1f km", state.remainingDistanceMeters / 1000.0), color = muted)
            }
        }
    }
}

@Composable
private fun V8BottomBar(
    navigationActive: Boolean,
    hasRoute: Boolean,
    panel: Color,
    text: Color,
    offline: Boolean,
    onSearch: () -> Unit,
    onStartStop: () -> Unit,
    onNearby: (() -> Unit)?,
    onPin: (() -> Unit)?,
    onOffline: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(NvRadius.Sheet),
        color = panel,
        border = BorderStroke(1.dp, V8Cyan.copy(alpha = .35f)),
        shadowElevation = NvElevation.Floating
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Xs, vertical = NvSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, contentDescription = "جستجو", tint = text)
            }
            Button(
                onClick = onStartStop,
                enabled = hasRoute || navigationActive,
                shape = RoundedCornerShape(NvRadius.Pill)
            ) {
                Icon(if (navigationActive) Icons.Rounded.Stop else Icons.Rounded.Navigation, contentDescription = null)
                Spacer(Modifier.width(NvSpacing.Xs))
                Text(if (navigationActive) "پایان" else "شروع", fontWeight = FontWeight.Bold)
            }
            if (!navigationActive && onNearby != null) {
                IconButton(onClick = onNearby) {
                    Icon(Icons.Rounded.Explore, contentDescription = "اطراف من", tint = V8Green)
                }
            }
            if (!navigationActive && onPin != null) {
                IconButton(onClick = onPin) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = "سنجاق NV", tint = V8Gold)
                }
            }
            IconButton(onClick = onOffline) {
                Icon(Icons.Rounded.Map, contentDescription = "نقشه آفلاین", tint = if (offline) V8Green else text)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "تنظیمات", tint = text)
            }
        }
    }
}

private fun v8RouteColor(index: Int): Color = when (index % 4) {
    0 -> V8Cyan
    1 -> V8Green
    2 -> V8Gold
    else -> V8Purple
}

private fun v8ManeuverIcon(direction: RouteManeuver.Direction?): ImageVector = when (direction) {
    RouteManeuver.Direction.LEFT,
    RouteManeuver.Direction.SLIGHT_LEFT,
    RouteManeuver.Direction.SHARP_LEFT -> Icons.Rounded.TurnLeft

    RouteManeuver.Direction.RIGHT,
    RouteManeuver.Direction.SLIGHT_RIGHT,
    RouteManeuver.Direction.SHARP_RIGHT -> Icons.Rounded.TurnRight

    RouteManeuver.Direction.UTURN -> Icons.Rounded.UturnLeft
    RouteManeuver.Direction.ARRIVE -> Icons.Rounded.Place
    else -> Icons.Rounded.Straight
}

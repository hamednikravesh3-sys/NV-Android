package ir.nv.navigation.ui

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.nv.navigation.entitlement.PlayBillingManager
import ir.nv.navigation.entitlement.TrialManager
import ir.nv.navigation.map.OfflineIranMap
import ir.nv.navigation.map.OnlineIranMap
import ir.nv.navigation.routing.NavigationModeResolver
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.core.Place
import ir.nv.navigation.data.PlaceCodes
import ir.nv.navigation.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NvApp(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel = viewModel(),
    premiumShell: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val billing = remember { PlayBillingManager(context.applicationContext) }
    val billingState by billing.state.collectAsState()
    var showOfflineMaps by remember { mutableStateOf(false) }
    var showPlaceCode by remember { mutableStateOf(false) }
    var showPersonalCodes by remember { mutableStateOf(false) }
    var showRoutePlaces by remember { mutableStateOf(false) }
    var routePlacesInitialKind by remember { mutableStateOf<RouteNotice.Kind?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showThemeMode by remember { mutableStateOf(false) }
    var pendingLocationAction by remember { mutableStateOf(LocationAction.ORIGIN) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            when (pendingLocationAction) {
                LocationAction.ORIGIN -> viewModel.useCurrentLocationAsOrigin()
                LocationAction.NAVIGATE -> viewModel.startNavigation()
            }
        }
    }

    val requestLocation: (LocationAction) -> Unit = { action ->
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) {
            when (action) {
                LocationAction.ORIGIN -> viewModel.useCurrentLocationAsOrigin()
                LocationAction.NAVIGATE -> viewModel.startNavigation()
            }
        } else {
            pendingLocationAction = action
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val sharePlace: (Place) -> Unit = { place ->
        (place.personalCode?.let { "کد شخصی NV: $it" } ?: PlaceCodes.shareCode(place.code))?.let { code ->
            val message = "${place.name}\n$code"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            context.startActivity(Intent.createChooser(intent, "اشتراک کد مکان NV"))
        }
    }

    DisposableEffect(billing) { onDispose { billing.close() } }
    LaunchedEffect(billingState.purchased) { viewModel.refreshEntitlement(billingState.purchased) }

    val mapSource = NavigationModeResolver.preferredSource(
        onlineAvailable = state.onlineAvailable,
        offlineReady = state.offlineReady,
        preferOffline = state.preferOffline
    )
    val mapDarkMode = darkMode
    val entitlementBlocked = state.trialState is TrialManager.State.Expired ||
        state.trialState is TrialManager.State.Tampered

    NavigationVoice(
        active = state.navigationActive,
        enabled = state.voiceEnabled,
        instruction = state.route?.maneuvers?.getOrNull(state.maneuverIndex)?.instruction,
        safetyAlert = state.routeNotices.firstOrNull {
            it.kind == ir.nv.navigation.core.RouteNotice.Kind.WEATHER &&
                it.title.startsWith("هشدار") && it.distanceAheadMeters <= 10_000.0
        }?.let { "${it.title}. ${it.detail}" }
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (showThemeMode) {
            ThemeModeSheet(
                selected = themeMode,
                resolvedDark = darkMode,
                onSelect = { onThemeModeChange(it); showThemeMode = false },
                onDismiss = { showThemeMode = false }
            )
        }
        if (showOfflineMaps) {
            OfflineMapsSheet(
                state = state,
                onDismiss = { showOfflineMaps = false },
                onDownload = viewModel::startMapDownload,
                onRetry = viewModel::retryDownload,
                onCancel = viewModel::cancelDownload,
                onDelete = viewModel::deleteOfflineMap,
                onModeChange = viewModel::setPreferOffline
            )
        }
        if (showPlaceCode) {
            PlaceCodeDialog(
                place = state.destination ?: state.origin,
                onDismiss = { showPlaceCode = false },
                onSave = { place, code -> viewModel.savePersonalCode(place, code); showPlaceCode = false },
                onShare = sharePlace
            )
        }
        if (showPersonalCodes) {
            PersonalCodesSheet(
                selectedPlace = state.destination ?: state.origin,
                savedPlaces = state.personalPlaces,
                onDismiss = { showPersonalCodes = false },
                onAddCode = {
                    showPersonalCodes = false
                    if (state.destination != null || state.origin != null) showPlaceCode = true else showSearch = true
                },
                onDelete = viewModel::deletePersonalCode,
                onShare = sharePlace
            )
        }
        if (showRoutePlaces) {
            RoutePlacesSheet(
                destination = state.destination,
                notices = state.routeNotices,
                initialKind = routePlacesInitialKind,
                loading = state.routeInsightsLoading,
                onlineAvailable = state.onlineAvailable,
                offlineReady = state.offlineReady,
                onDismiss = { showRoutePlaces = false },
                onShowCode = { showRoutePlaces = false; showPlaceCode = true },
                onShare = sharePlace,
                onOpenOfflineMaps = { showRoutePlaces = false; showOfflineMaps = true }
            )
        }
        if (showSearch) {
            SearchSheet(
                state = state,
                onDismiss = { showSearch = false },
                onOriginChange = viewModel::updateOriginQuery,
                onDestinationChange = viewModel::updateDestinationQuery,
                onOriginSelect = viewModel::selectOrigin,
                onDestinationSelect = viewModel::selectDestination,
                onSwap = viewModel::swapEndpoints,
                onRoute = { viewModel.calculateRoute(); showSearch = false },
                onUseCurrentLocation = { requestLocation(LocationAction.ORIGIN) },
                onSaveCode = { showPlaceCode = true }
            )
        }
        if (entitlementBlocked && !billingState.purchased) {
            PurchaseDialog(
                trialState = state.trialState,
                billingState = billingState,
                onPurchase = { (context as? Activity)?.let(billing::launchPurchase) }
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                mapSource == RouteSource.OFFLINE -> OfflineIranMap(
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
                    darkMode = mapDarkMode,
                    modifier = Modifier.fillMaxSize()
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
                    darkMode = mapDarkMode,
                    satelliteMode = state.satelliteMode,
                    modifier = Modifier.fillMaxSize()
                )
                else -> NoMapConnection(modifier = Modifier.fillMaxSize())
            }

            if (!state.navigationActive && !premiumShell) {
                state.destination?.let { destination ->
                    MapCodeBadge(place = destination, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                if (state.navigationActive) {
                    state.route?.let { activeRoute ->
                        RightNavigationHud(
                            route = activeRoute,
                            maneuverIndex = state.maneuverIndex,
                            distanceToManeuverMeters = state.distanceToNextManeuverMeters,
                            speedKmh = state.speedKmh,
                            offRoute = state.offRoute,
                            voiceEnabled = state.voiceEnabled,
                            onToggleVoice = viewModel::toggleVoice,
                            onStop = viewModel::stopNavigation
                        )
                    }
                } else if (!premiumShell) {
                    if (state.route == null && (state.onlineAvailable || state.offlineReady)) {
                        DestinationSearchBar(
                            recentPlaces = state.recentPlaces,
                            personalPlaces = state.personalPlaces,
                            onlineAvailable = state.onlineAvailable,
                            offlineReady = state.offlineReady,
                            onClick = { showSearch = true },
                            onRecentClick = { place -> viewModel.selectDestination(place); showSearch = true }
                        )
                    } else if (state.route != null) {
                        SelectedRouteHeader(
                            origin = state.origin,
                            destination = state.destination,
                            onEdit = { showSearch = true },
                            onSwap = viewModel::swapEndpoints
                        )
                    }
                    state.message?.let { StatusMessage(text = it, modifier = Modifier.padding(top = 8.dp)) }
                }
            }

            if (!state.navigationActive) {
                state.route?.let { route ->
                    RouteSummaryCard(
                        route = route,
                        destination = state.destination,
                        alternatives = state.routeAlternatives,
                        selectedRouteIndex = state.selectedRouteIndex,
                        source = state.routeSource,
                        traffic = state.traffic,
                        notices = state.routeNotices,
                        insightsLoading = state.routeInsightsLoading,
                        navigationActive = false,
                        remainingDistanceMeters = state.remainingDistanceMeters,
                        remainingSeconds = state.remainingSeconds,
                        onStart = { requestLocation(LocationAction.NAVIGATE) },
                        onRouteSelect = viewModel::selectRoute,
                        onOpenPlaces = { routePlacesInitialKind = null; showRoutePlaces = true },
                        onOpenCode = { showPlaceCode = true },
                        onStop = viewModel::stopNavigation,
                        onClose = viewModel::clearRoute,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (premiumShell) 72.dp else 0.dp)
                    )
                }
            }

            if (state.navigationActive) {
                if (state.followNavigation) {
                    NavigationVehicleMarker(bearingDegrees = 0f, modifier = Modifier.align(Alignment.Center))
                }

                RightNavigationBottomBar(
                    remainingDistanceMeters = state.remainingDistanceMeters,
                    remainingSeconds = state.remainingSeconds.toLong(),
                    speedKmh = state.speedKmh,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp, vertical = 10.dp)
                )

                DrivingZoomControls(
                    zoomLevel = state.navigationZoomLevel,
                    automatic = state.cameraAutomatic,
                    following = state.followNavigation,
                    onZoomIn = viewModel::zoomNavigationIn,
                    onZoomOut = viewModel::zoomNavigationOut,
                    onRecenter = viewModel::recenterNavigation,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                )
                RightRouteInsightRail(
                    notices = state.routeNotices,
                    loading = state.routeInsightsLoading,
                    satelliteMode = state.satelliteMode,
                    darkMode = darkMode,
                    onlineAvailable = state.onlineAvailable,
                    onWeather = { routePlacesInitialKind = RouteNotice.Kind.WEATHER; showRoutePlaces = true },
                    onAttractions = { routePlacesInitialKind = RouteNotice.Kind.ATTRACTION; showRoutePlaces = true },
                    onToggleSatellite = viewModel::toggleSatelliteMode,
                    onToggleTheme = { showThemeMode = true },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                )
            } else if (!premiumShell && state.route != null) {
                RightRouteInsightRail(
                    notices = state.routeNotices,
                    loading = state.routeInsightsLoading,
                    satelliteMode = state.satelliteMode,
                    darkMode = darkMode,
                    onlineAvailable = state.onlineAvailable,
                    onWeather = { routePlacesInitialKind = RouteNotice.Kind.WEATHER; showRoutePlaces = true },
                    onAttractions = { routePlacesInitialKind = RouteNotice.Kind.ATTRACTION; showRoutePlaces = true },
                    onToggleSatellite = viewModel::toggleSatelliteMode,
                    onToggleTheme = { showThemeMode = true },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                )
            } else if (!premiumShell && state.route == null && (state.onlineAvailable || state.offlineReady)) {
                HomeMapControls(
                    darkMode = darkMode,
                    onMyLocation = { requestLocation(LocationAction.ORIGIN) },
                    onOpenOfflineMaps = { showOfflineMaps = true },
                    onToggleTheme = { showThemeMode = true },
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            if (!premiumShell && !state.navigationActive && state.route == null && (state.onlineAvailable || state.offlineReady)) {
                NvHomeDock(
                    onRoute = { showSearch = true },
                    onCodes = { showPersonalCodes = true },
                    onOfflineMaps = { showOfflineMaps = true },
                    darkMode = darkMode,
                    onToggleTheme = { showThemeMode = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            if (state.route == null && !state.onlineAvailable && !state.offlineReady) {
                OfflinePrompt(onOpenOfflineMaps = { showOfflineMaps = true }, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

private enum class LocationAction { ORIGIN, NAVIGATE }

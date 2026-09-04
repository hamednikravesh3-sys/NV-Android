package ir.nv.navigation.ui

import android.app.Activity
import android.Manifest
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
import ir.nv.navigation.core.RouteSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NvApp(
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: NvViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val billing = remember { PlayBillingManager(context.applicationContext) }
    val billingState by billing.state.collectAsState()
    var showOfflineMaps by remember { mutableStateOf(false) }
    var showPlaceCode by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showExplore by remember { mutableStateOf(false) }
    var experience by remember { mutableStateOf(NvExperience.HOME) }
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

    DisposableEffect(billing) {
        onDispose { billing.close() }
    }
    LaunchedEffect(billingState.purchased) {
        viewModel.refreshEntitlement(billingState.purchased)
    }
    LaunchedEffect(state.navigationActive) {
        if (state.navigationActive) experience = NvExperience.DRIVE
    }

    val mapSource = NavigationModeResolver.preferredSource(
        onlineAvailable = state.onlineAvailable,
        offlineReady = state.offlineReady,
        preferOffline = state.preferOffline
    )
    val entitlementBlocked = state.trialState is TrialManager.State.Expired ||
        state.trialState is TrialManager.State.Tampered

    NavigationVoice(
        active = state.navigationActive,
        enabled = state.voiceEnabled,
        instruction = state.route?.maneuvers?.getOrNull(state.maneuverIndex)?.instruction
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                onSave = { place, code ->
                    viewModel.savePersonalCode(place, code)
                    showPlaceCode = false
                }
            )
        }

        if (showSearch) {
            DfhiRouteSheet(
                state = state,
                onDismiss = {
                    showSearch = false
                    if (state.route == null) experience = NvExperience.HOME
                },
                onOriginChange = viewModel::updateOriginQuery,
                onDestinationChange = viewModel::updateDestinationQuery,
                onOriginSelect = viewModel::selectOrigin,
                onDestinationSelect = viewModel::selectDestination,
                onSwap = viewModel::swapEndpoints,
                onRoute = {
                    viewModel.calculateRoute()
                    experience = NvExperience.ROUTE
                    showSearch = false
                },
                onUseCurrentLocation = { requestLocation(LocationAction.ORIGIN) },
                onSaveCode = { showPlaceCode = true }
            )
        }

        if (showExplore) {
            DfhiExploreSheet(
                state = state,
                onDismiss = {
                    showExplore = false
                    if (state.route == null) experience = NvExperience.HOME
                },
                onChooseDestination = {
                    showExplore = false
                    experience = NvExperience.ROUTE
                    showSearch = true
                }
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
                    route = state.route,
                    currentLocation = state.currentLocation,
                    followLocation = state.navigationActive,
                    darkMode = darkMode,
                    modifier = Modifier.fillMaxSize()
                )

                state.onlineAvailable -> OnlineIranMap(
                    context = context,
                    route = state.route,
                    currentLocation = state.currentLocation,
                    followLocation = state.navigationActive,
                    darkMode = darkMode,
                    modifier = Modifier.fillMaxSize()
                )

                else -> NoMapConnection(modifier = Modifier.fillMaxSize())
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (state.navigationActive) {
                    state.route?.let { activeRoute ->
                        NavigationHud(
                            route = activeRoute,
                            maneuverIndex = state.maneuverIndex,
                            distanceToManeuverMeters = state.distanceToNextManeuverMeters,
                            offRoute = state.offRoute,
                            onStop = viewModel::stopNavigation
                        )
                    }
                } else {
                    NavigationTopBar(
                        state = state,
                        darkMode = darkMode,
                        onToggleTheme = onToggleTheme,
                        onOpenOfflineMaps = { showOfflineMaps = true }
                    )
                    state.message?.let {
                        StatusMessage(text = it, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            state.route?.let { route ->
                RouteSummaryCard(
                    route = route,
                    source = state.routeSource,
                    traffic = state.traffic,
                    notices = state.routeNotices,
                    navigationActive = state.navigationActive,
                    remainingDistanceMeters = state.remainingDistanceMeters,
                    remainingSeconds = state.remainingSeconds,
                    onStart = {
                        experience = NvExperience.DRIVE
                        requestLocation(LocationAction.NAVIGATE)
                    },
                    onStop = {
                        viewModel.stopNavigation()
                        experience = NvExperience.ROUTE
                    },
                    onClose = {
                        viewModel.clearRoute()
                        experience = NvExperience.HOME
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            if (state.navigationActive) {
                FloatingNavigationControls(
                    voiceEnabled = state.voiceEnabled,
                    darkMode = darkMode,
                    onToggleVoice = viewModel::toggleVoice,
                    onToggleTheme = onToggleTheme,
                    onOpenOfflineMaps = { showOfflineMaps = true },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            if (state.route == null && !state.onlineAvailable && !state.offlineReady) {
                OfflinePrompt(
                    onOpenOfflineMaps = { showOfflineMaps = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            } else if (state.route == null) {
                Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                    DfhiHomeSearch(
                        state = state,
                        onSearch = {
                            experience = NvExperience.ROUTE
                            showSearch = true
                        }
                    )
                    DfhiModeDock(
                        selected = experience,
                        onHome = { experience = NvExperience.HOME },
                        onRoute = {
                            experience = NvExperience.ROUTE
                            showSearch = true
                        },
                        onDrive = {
                            experience = NvExperience.DRIVE
                            showSearch = true
                        },
                        onExplore = {
                            experience = NvExperience.EXPLORE
                            showExplore = true
                        }
                    )
                }
            }
        }
    }
}

private enum class LocationAction { ORIGIN, NAVIGATE }

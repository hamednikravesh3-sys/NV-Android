package ir.nv.navigation.ui

import android.app.Activity
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

    DisposableEffect(billing) {
        onDispose { billing.close() }
    }
    LaunchedEffect(billingState.purchased) {
        viewModel.refreshEntitlement(billingState.purchased)
    }

    val mapSource = NavigationModeResolver.preferredSource(
        onlineAvailable = state.onlineAvailable,
        offlineReady = state.offlineReady,
        preferOffline = state.preferOffline
    )
    val entitlementBlocked = state.trialState is TrialManager.State.Expired ||
        state.trialState is TrialManager.State.Tampered

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
                    modifier = Modifier.fillMaxSize()
                )

                state.onlineAvailable -> OnlineIranMap(
                    context = context,
                    route = state.route,
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
                NavigationTopBar(
                    state = state,
                    darkMode = darkMode,
                    onToggleTheme = onToggleTheme,
                    onOpenOfflineMaps = { showOfflineMaps = true }
                )
                SearchPanel(
                    state = state,
                    onOriginChange = viewModel::updateOriginQuery,
                    onDestinationChange = viewModel::updateDestinationQuery,
                    onOriginSelect = viewModel::selectOrigin,
                    onDestinationSelect = viewModel::selectDestination,
                    onSwap = viewModel::swapEndpoints,
                    onRoute = viewModel::calculateRoute,
                    onSaveCode = { showPlaceCode = true }
                )
                state.message?.let {
                    StatusMessage(text = it, modifier = Modifier.padding(top = 8.dp))
                }
            }

            state.route?.let { route ->
                RouteSummaryCard(
                    route = route,
                    source = state.routeSource,
                    traffic = state.traffic,
                    notices = state.routeNotices,
                    onClose = viewModel::clearRoute,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            if (state.route == null && !state.onlineAvailable && !state.offlineReady) {
                OfflinePrompt(
                    onOpenOfflineMaps = { showOfflineMaps = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

package ir.nv.navigation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.map.OfflineIranMap
import ir.nv.navigation.map.OnlineIranMap
import ir.nv.navigation.routing.NavigationModeResolver
import ir.nv.navigation.ui.theme.NvColors
import ir.nv.navigation.ui.theme.NvElevation
import ir.nv.navigation.ui.theme.NvRadius
import ir.nv.navigation.ui.theme.NvSpacing
import kotlin.math.roundToInt

/**
 * Rahnama screen 5/6 shell. Navigation state and rerouting stay owned by NvViewModel;
 * this composable only presents the active journey using the new product architecture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RahnamaNavigationScreen(
    darkMode: Boolean,
    viewModel: NvViewModel
) {
    val state by viewModel.state.collectAsState()
    val route = state.route
    var alertsOpen by remember { mutableStateOf(false) }

    if (route == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)) {
                Text("مسیر فعال در دسترس نیست", color = NvColors.TextPrimaryDark)
                Button(onClick = viewModel::stopNavigation) { Text("بازگشت") }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        RahnamaDrivingMap(state = state, viewModel = viewModel, darkMode = darkMode)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
        ) {
            Text(
                "راهنمای مسیر",
                color = NvColors.TextPrimaryDark,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            RightNavigationHud(
                route = route,
                maneuverIndex = state.maneuverIndex,
                distanceToManeuverMeters = state.distanceToNextManeuverMeters,
                speedKmh = state.speedKmh,
                offRoute = state.offRoute,
                voiceEnabled = state.voiceEnabled,
                notices = state.routeNotices,
                onToggleVoice = viewModel::toggleVoice,
                onStop = viewModel::stopNavigation
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = NvSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NavigationFloatingButton(
                icon = Icons.Rounded.MyLocation,
                description = "مرکز کردن روی موقعیت من",
                onClick = viewModel::recenterNavigation
            )
            NavigationFloatingButton(
                icon = if (state.routeNotices.isEmpty()) Icons.Rounded.Warning else Icons.Rounded.Traffic,
                description = "هشدارهای مسیر",
                badge = state.routeNotices.size.takeIf { it > 0 },
                onClick = { alertsOpen = true }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
        ) {
            RightNavigationBottomBar(
                remainingDistanceMeters = state.remainingDistanceMeters,
                remainingSeconds = state.remainingSeconds.toLong(),
                speedKmh = state.speedKmh
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                OutlinedButton(
                    onClick = { alertsOpen = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(NvRadius.Pill)
                ) {
                    Icon(Icons.Rounded.Warning, contentDescription = null)
                    Spacer(Modifier.width(NvSpacing.Xs))
                    Text("هشدارهای مسیر")
                }
                Button(
                    onClick = viewModel::stopNavigation,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(NvRadius.Pill)
                ) {
                    Text("پایان مسیر")
                }
            }
        }
    }

    if (alertsOpen) {
        ModalBottomSheet(
            onDismissRequest = { alertsOpen = false },
            containerColor = NvColors.Navy900,
            contentColor = NvColors.TextPrimaryDark
        ) {
            RahnamaRouteAlerts(
                notices = state.routeNotices,
                loading = state.routeInsightsLoading,
                onClose = { alertsOpen = false }
            )
        }
    }
}

@Composable
private fun RahnamaDrivingMap(
    state: NvUiState,
    viewModel: NvViewModel,
    darkMode: Boolean
) {
    val context = LocalContext.current
    val routes = remember(state.routeAlternatives, state.route) {
        state.routeAlternatives.ifEmpty { listOfNotNull(state.route) }
    }
    val coded = remember(state.personalPlaces, state.recentPlaces, state.destination) {
        (state.personalPlaces + state.recentPlaces + listOfNotNull(state.destination))
            .distinctBy { it.personalCode ?: it.code.toString() }
    }

    when (NavigationModeResolver.preferredSource(state.onlineAvailable, state.offlineReady, state.preferOffline)) {
        RouteSource.OFFLINE -> OfflineIranMap(
            context,
            viewModel.mapFile(),
            routes,
            state.selectedRouteIndex,
            state.traffic,
            state.trafficSegments,
            state.currentLocation,
            state.followNavigation,
            true,
            state.navigationZoomLevel,
            state.navigationRecenterToken,
            state.bearingDegrees,
            viewModel::pauseNavigationFollow,
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
            true,
            state.navigationZoomLevel,
            state.navigationRecenterToken,
            state.bearingDegrees,
            viewModel::pauseNavigationFollow,
            darkMode,
            state.satelliteMode,
            Modifier.fillMaxSize()
        )

        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("نقشه در دسترس نیست", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NavigationFloatingButton(
    icon: ImageVector,
    description: String,
    badge: Int? = null,
    onClick: () -> Unit
) {
    Box {
        Surface(
            shape = CircleShape,
            color = NvColors.Navy900.copy(alpha = .96f),
            border = BorderStroke(1.dp, NvColors.RouteBlue.copy(alpha = .55f)),
            shadowElevation = NvElevation.Floating
        ) {
            IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
                Icon(icon, contentDescription = description, tint = NvColors.RouteBlue)
            }
        }
        badge?.let {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd),
                shape = CircleShape,
                color = NvColors.Emergency
            ) {
                Text(
                    it.coerceAtMost(99).toString(),
                    color = NvColors.TextPrimaryDark,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun RahnamaRouteAlerts(
    notices: List<RouteNotice>,
    loading: Boolean,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = NvSpacing.Lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)
    ) {
        Text("هشدارهای مسیر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(
            "رویدادهای پیش‌رو بر اساس داده‌های ترافیک، هوا و خدمات مسیر",
            color = NvColors.TextSecondaryDark,
            style = MaterialTheme.typography.bodySmall
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!loading && notices.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NvColors.Navy850,
                shape = RoundedCornerShape(NvRadius.Card),
                border = BorderStroke(1.dp, NvColors.DividerDark)
            ) {
                Text(
                    "هشدار فعالی برای مسیر ثبت نشده است",
                    modifier = Modifier.padding(NvSpacing.Lg),
                    color = NvColors.TextSecondaryDark
                )
            }
        }
        notices.sortedBy { it.distanceAheadMeters }.forEach { notice ->
            RouteAlertRow(notice)
        }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("بازگشت به مسیریابی") }
        Spacer(Modifier.size(NvSpacing.Xl))
    }
}

@Composable
private fun RouteAlertRow(notice: RouteNotice) {
    val (icon, accent, label) = when (notice.kind) {
        RouteNotice.Kind.TRAFFIC -> Triple(Icons.Rounded.Traffic, NvColors.Emergency, "ترافیک")
        RouteNotice.Kind.WEATHER -> Triple(Icons.Rounded.Cloud, NvColors.Warning, "هوا")
        RouteNotice.Kind.SERVICE -> Triple(Icons.Rounded.LocalGasStation, NvColors.Success, "خدمات")
        RouteNotice.Kind.ATTRACTION -> Triple(Icons.Rounded.Explore, NvColors.Info, "دیدنی")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NvColors.Navy850,
        shape = RoundedCornerShape(NvRadius.Card),
        border = BorderStroke(1.dp, accent.copy(alpha = .55f))
    ) {
        Row(Modifier.padding(NvSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = accent.copy(alpha = .14f)) {
                Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.padding(9.dp).size(24.dp))
            }
            Spacer(Modifier.width(NvSpacing.Md))
            Column(Modifier.weight(1f)) {
                Text(notice.title, color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (notice.detail.isNotBlank()) {
                    Text(notice.detail, color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(label, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(formatAlertDistance(notice.distanceAheadMeters), color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatAlertDistance(meters: Double): String = if (meters >= 1000.0) {
    val km = meters / 1000.0
    if (km >= 10.0) "${km.roundToInt()} km" else String.format("%.1f km", km)
} else {
    "${meters.coerceAtLeast(0.0).roundToInt()} متر"
}

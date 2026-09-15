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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.map.OfflineIranMap
import ir.nv.navigation.map.OnlineIranMap
import ir.nv.navigation.routing.NavigationModeResolver
import ir.nv.navigation.ui.theme.NvColors
import ir.nv.navigation.ui.theme.NvRadius
import ir.nv.navigation.ui.theme.NvSpacing

/**
 * Driving-only navigation surface.
 *
 * Only the committed route is rendered. Alternatives are intentionally not sent to
 * the map after navigation starts. NvViewModel remains responsible for off-route
 * detection and replaces state.route only after its reroute gate confirms deviation.
 */
@Composable
fun RahnamaLiveDrivingScreen(
    darkMode: Boolean,
    viewModel: NvViewModel
) {
    val state by viewModel.state.collectAsState()
    val route = state.route
    val context = LocalContext.current

    if (route == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)) {
                Text("مسیر فعال در دسترس نیست")
                Button(onClick = viewModel::stopNavigation) { Text("بازگشت") }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        val activeRouteOnly = listOf(route)
        val codedPlaces = (state.personalPlaces + state.recentPlaces + listOfNotNull(state.destination))
            .distinctBy { it.personalCode ?: it.code.toString() }

        when (NavigationModeResolver.preferredSource(state.onlineAvailable, state.offlineReady, state.preferOffline)) {
            RouteSource.OFFLINE -> OfflineIranMap(
                context = context,
                mapFile = viewModel.mapFile(),
                routes = activeRouteOnly,
                selectedRouteIndex = 0,
                traffic = state.traffic,
                trafficSegments = state.trafficSegments,
                currentLocation = state.currentLocation,
                followLocation = state.followNavigation,
                navigationActive = true,
                navigationZoomLevel = state.navigationZoomLevel,
                navigationRecenterToken = state.navigationRecenterToken,
                bearingDegrees = state.bearingDegrees,
                onManualGesture = viewModel::pauseNavigationFollow,
                darkMode = darkMode,
                modifier = Modifier.fillMaxSize()
            )

            RouteSource.ONLINE -> OnlineIranMap(
                context = context,
                routes = activeRouteOnly,
                selectedRouteIndex = 0,
                traffic = state.traffic,
                trafficSegments = state.trafficSegments,
                codedPlaces = codedPlaces,
                currentLocation = state.currentLocation,
                followLocation = state.followNavigation,
                navigationActive = true,
                navigationZoomLevel = state.navigationZoomLevel,
                navigationRecenterToken = state.navigationRecenterToken,
                bearingDegrees = state.bearingDegrees,
                onManualGesture = viewModel::pauseNavigationFollow,
                darkMode = darkMode,
                satelliteMode = state.satelliteMode,
                modifier = Modifier.fillMaxSize()
            )

            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("نقشه در دسترس نیست", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
        ) {
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

            if (state.offRoute) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = NvColors.Warning.copy(alpha = .18f),
                    shape = RoundedCornerShape(NvRadius.Medium),
                    border = BorderStroke(1.dp, NvColors.Warning.copy(alpha = .65f))
                ) {
                    Text(
                        "از مسیر خارج شدید؛ مسیر جایگزین فقط پس از تأیید انحراف محاسبه می‌شود.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = NvSpacing.Md),
            shape = CircleShape,
            color = NvColors.Navy900.copy(alpha = .96f),
            border = BorderStroke(1.dp, NvColors.RouteBlue.copy(alpha = .6f)),
            shadowElevation = 10.dp
        ) {
            IconButton(onClick = viewModel::recenterNavigation, modifier = Modifier.size(50.dp)) {
                Icon(Icons.Rounded.MyLocation, contentDescription = "مرکز کردن روی خودرو", tint = NvColors.RouteBlue)
            }
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
                speedKmh = state.speedKmh,
                speedLimitKmh = state.speedLimitKmh
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = NvColors.Navy900.copy(alpha = .96f),
                    shape = RoundedCornerShape(NvRadius.Pill),
                    border = BorderStroke(1.dp, NvColors.DividerDark)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text("GPS", color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)
                        Text(
                            state.locationAccuracyMeters?.let { "±${it.toInt()} متر" } ?: "در حال تثبیت",
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Button(
                    onClick = viewModel::stopNavigation,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(NvRadius.Pill)
                ) {
                    Text("پایان مسیر", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

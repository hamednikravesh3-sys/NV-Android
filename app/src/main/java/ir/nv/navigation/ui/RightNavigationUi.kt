package ir.nv.navigation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.core.RouteNotice
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DriveNavy = Color(0xF2071728)
private val DrivePanel = Color(0xF20B2942)
private val DriveCyan = Color(0xFF18D9FF)
private val DriveLime = Color(0xFFB7FF65)
private val DriveText = Color(0xFFF7FBFF)
private val DriveMuted = Color(0xFFA0B6C7)
private val DriveOutline = Color(0xFF216D91)

@Composable
fun RightNavigationHud(
    route: Route,
    maneuverIndex: Int,
    distanceToManeuverMeters: Double,
    speedKmh: Int,
    offRoute: Boolean,
    voiceEnabled: Boolean,
    notices: List<RouteNotice> = emptyList(),
    onToggleVoice: () -> Unit,
    onStop: () -> Unit
) {
    val maneuver = route.maneuvers.getOrNull(maneuverIndex)
    val distance = if (distanceToManeuverMeters > 0.0) distanceToManeuverMeters else maneuver?.distanceMeters ?: route.distanceMeters
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (offRoute) Color(0xF28A2232) else DriveNavy,
        border = BorderStroke(1.dp, if (offRoute) Color(0xFFFF7185) else DriveCyan.copy(alpha = .45f)),
        shadowElevation = 18.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Close, "توقف", tint = DriveText) }
                Surface(shape = RoundedCornerShape(15.dp), color = DriveCyan.copy(alpha = .14f), border = BorderStroke(1.dp, DriveCyan.copy(alpha = .5f))) {
                    Icon(maneuverIcon(maneuver?.direction), null, tint = DriveCyan, modifier = Modifier.padding(9.dp).size(40.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatDriveDistance(distance), color = DriveText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(if (offRoute) "خارج از مسیر؛ در حال محاسبه مسیر جدید…" else maneuver?.instruction ?: "ادامه مسیر", color = DriveText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    maneuver?.roadName?.takeIf { it.isNotBlank() }?.let { Text(it, color = DriveCyan, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                }
                IconButton(onClick = onToggleVoice, modifier = Modifier.size(40.dp)) {
                    Icon(if (voiceEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, "صدا", tint = if (voiceEnabled) DriveCyan else DriveMuted)
                }
            }
            if (!offRoute && !maneuver?.lanes.isNullOrEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                    maneuver!!.lanes.take(6).forEach { lane ->
                        Surface(modifier = Modifier.padding(horizontal = 3.dp), shape = RoundedCornerShape(9.dp), color = if (lane.recommended) DriveCyan.copy(alpha = .24f) else DrivePanel, border = BorderStroke(1.dp, if (lane.recommended) DriveCyan else DriveOutline.copy(alpha = .45f))) {
                            Icon(maneuverIcon(lane.direction), null, tint = if (lane.recommended) DriveCyan else DriveMuted, modifier = Modifier.padding(horizontal = 13.dp, vertical = 4.dp).size(22.dp))
                        }
                    }
                }
            }
            val insight = notices.firstOrNull { it.distanceAheadMeters in 0.0..10000.0 && (it.kind == RouteNotice.Kind.WEATHER || it.kind == RouteNotice.Kind.ATTRACTION || it.kind == RouteNotice.Kind.SERVICE) }
            insight?.let { notice ->
                Surface(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(12.dp), color = DrivePanel.copy(alpha = .92f), border = BorderStroke(1.dp, DriveOutline.copy(alpha = .55f))) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(noticeIcon(notice.kind), null, tint = DriveLime, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(notice.title, color = DriveText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(formatDriveDistance(notice.distanceAheadMeters), color = DriveCyan, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun RightNavigationBottomBar(
    remainingDistanceMeters: Double,
    remainingSeconds: Long,
    speedKmh: Int,
    modifier: Modifier = Modifier
) {
    val eta = Instant.now().plusSeconds(remainingSeconds.coerceAtLeast(0L)).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = DriveNavy, border = BorderStroke(1.dp, DriveCyan.copy(alpha = .42f)), shadowElevation = 16.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Surface(shape = CircleShape, color = DrivePanel, border = BorderStroke(2.dp, DriveLime)) {
                Column(Modifier.size(62.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("$speedKmh", color = DriveLime, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text("km/h", color = DriveMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Metric(eta, "زمان رسیدن")
            Metric(formatDriveTime(remainingSeconds), "باقی‌مانده")
            Metric(formatDriveDistance(remainingDistanceMeters), "تا مقصد")
        }
    }
}

@Composable
fun RightRouteInsightRail(
    notices: List<RouteNotice>,
    loading: Boolean,
    satelliteMode: Boolean,
    darkMode: Boolean,
    onlineAvailable: Boolean,
    onWeather: () -> Unit,
    onAttractions: () -> Unit,
    onToggleSatellite: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weather = notices.firstOrNull { it.kind == RouteNotice.Kind.WEATHER && it.distanceAheadMeters <= 10_000.0 }
    val attraction = notices.firstOrNull { it.kind == RouteNotice.Kind.ATTRACTION && it.distanceAheadMeters <= 10_000.0 }
    Column(
        modifier = modifier.width(72.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        InsightRailButton(
            icon = Icons.Rounded.Cloud,
            label = "هوا",
            value = railValue(weather, loading),
            accent = if (weather?.title?.startsWith("هشدار") == true) Color(0xFFFFB52E) else DriveCyan,
            onClick = onWeather
        )
        InsightRailButton(
            icon = Icons.Rounded.Explore,
            label = "دیدنی",
            value = railValue(attraction, loading),
            accent = DriveLime,
            onClick = onAttractions
        )
        InsightRailButton(
            icon = Icons.Rounded.Layers,
            label = "ماهواره",
            value = when {
                !onlineAvailable -> "آفلاین"
                satelliteMode -> "فعال"
                else -> "خاموش"
            },
            accent = if (satelliteMode) DriveLime else DriveCyan,
            onClick = onToggleSatellite
        )
        InsightRailButton(
            icon = if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            label = if (darkMode) "روز" else "شب",
            value = "نما",
            accent = DriveCyan,
            onClick = onToggleTheme
        )
    }
}

@Composable
private fun InsightRailButton(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = DriveNavy,
        border = BorderStroke(1.dp, accent.copy(alpha = .46f)),
        shadowElevation = 7.dp
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(icon, label, tint = accent, modifier = Modifier.size(20.dp))
            Text(label, color = DriveText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(value, color = DriveMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun railValue(notice: RouteNotice?, loading: Boolean): String = when {
    notice != null -> if (notice.distanceAheadMeters >= 9_500.0) "۱۰ km" else "${(notice.distanceAheadMeters / 1_000.0).coerceAtLeast(.1).let { String.format("%.1f", it) }} km"
    loading -> "…"
    else -> "—"
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = DriveText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
        Text(label, color = DriveMuted, style = MaterialTheme.typography.labelSmall)
    }
}

private fun maneuverIcon(direction: RouteManeuver.Direction?): ImageVector = when (direction) {
    RouteManeuver.Direction.LEFT, RouteManeuver.Direction.SLIGHT_LEFT, RouteManeuver.Direction.SHARP_LEFT -> Icons.Rounded.TurnLeft
    RouteManeuver.Direction.RIGHT, RouteManeuver.Direction.SLIGHT_RIGHT, RouteManeuver.Direction.SHARP_RIGHT -> Icons.Rounded.TurnRight
    RouteManeuver.Direction.UTURN -> Icons.Rounded.UTurnLeft
    else -> Icons.Rounded.ArrowUpward
}

private fun noticeIcon(kind: RouteNotice.Kind): ImageVector = when (kind) {
    RouteNotice.Kind.WEATHER -> Icons.Rounded.Cloud
    RouteNotice.Kind.ATTRACTION -> Icons.Rounded.Explore
    RouteNotice.Kind.SERVICE -> Icons.Rounded.LocalGasStation
    else -> Icons.Rounded.Navigation
}

private fun formatDriveDistance(meters: Double): String = if (meters >= 1000.0) {
    val km = meters / 1000.0
    if (km >= 10) "${km.roundToInt()} کیلومتر" else String.format("%.1f کیلومتر", km)
} else "${meters.coerceAtLeast(0.0).roundToInt()} متر"

private fun formatDriveTime(seconds: Long): String {
    val minutes = (seconds.coerceAtLeast(0L) / 60L).toInt()
    return if (minutes >= 60) "${minutes / 60}س ${minutes % 60}د" else "$minutes دقیقه"
}

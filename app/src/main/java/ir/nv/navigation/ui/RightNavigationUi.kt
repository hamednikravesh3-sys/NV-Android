package ir.nv.navigation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Navigation
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.Route
import kotlin.math.roundToInt

private val DriveNavy = Color(0xEC061A2D)
private val DrivePanel = Color(0xF20A2944)
private val DriveCyan = Color(0xFF16D8FF)
private val DriveLime = Color(0xFF89F36B)
private val DriveText = Color(0xFFF7FBFF)
private val DriveMuted = Color(0xFF9AB3C7)
private val DriveOutline = Color(0xFF1C6F96)

@Composable
fun RightNavigationHud(
    route: Route,
    maneuverIndex: Int,
    distanceToManeuverMeters: Double,
    speedKmh: Int,
    offRoute: Boolean,
    voiceEnabled: Boolean,
    onToggleVoice: () -> Unit,
    onStop: () -> Unit
) {
    val maneuver = route.maneuvers.getOrNull(maneuverIndex)
    val distance = if (distanceToManeuverMeters > 0.0) distanceToManeuverMeters else maneuver?.distanceMeters ?: route.distanceMeters
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (offRoute) Color(0xE08A2232) else DriveNavy,
        border = BorderStroke(1.dp, if (offRoute) Color(0xFFFF7185) else DriveOutline),
        shadowElevation = 14.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onStop, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Rounded.Close, "توقف", tint = DriveText)
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DriveCyan.copy(alpha = 0.13f),
                    border = BorderStroke(1.dp, DriveCyan.copy(alpha = 0.45f))
                ) {
                    Icon(
                        Icons.Rounded.Navigation,
                        null,
                        tint = DriveCyan,
                        modifier = Modifier.padding(9.dp).size(38.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatDriveDistance(distance), color = DriveText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(
                        if (offRoute) "خارج از مسیر؛ محاسبه مسیر جدید…" else maneuver?.instruction ?: "ادامه مسیر",
                        color = DriveText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggleVoice, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Rounded.VolumeUp, "صدا", tint = if (voiceEnabled) DriveCyan else DriveMuted)
                }
            }

            if (!offRoute && !maneuver?.lanes.isNullOrEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    maneuver?.lanes?.take(5)?.forEach { lane ->
                        Surface(
                            modifier = Modifier.padding(horizontal = 3.dp),
                            shape = RoundedCornerShape(9.dp),
                            color = if (lane.recommended) DriveCyan.copy(alpha = 0.25f) else DrivePanel.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, if (lane.recommended) DriveCyan else DriveOutline.copy(alpha = 0.45f))
                        ) {
                            Text(
                                "↑",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp),
                                color = if (lane.recommended) DriveCyan else DriveMuted,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                        }
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = DriveNavy,
        border = BorderStroke(1.dp, DriveOutline),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(shape = CircleShape, color = Color(0xFF08253A), border = BorderStroke(2.dp, DriveLime)) {
                Column(
                    modifier = Modifier.size(62.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("$speedKmh", color = DriveLime, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text("km/h", color = DriveMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDriveTime(remainingSeconds), color = DriveText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text("زمان باقی‌مانده", color = DriveMuted, style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDriveDistance(remainingDistanceMeters), color = DriveText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text("تا مقصد", color = DriveMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatDriveDistance(meters: Double): String = if (meters >= 1000.0) {
    val km = meters / 1000.0
    if (km >= 10) "${km.roundToInt()} کیلومتر" else String.format("%.1f کیلومتر", km)
} else "${meters.coerceAtLeast(0.0).roundToInt()} متر"

private fun formatDriveTime(seconds: Long): String {
    val minutes = (seconds.coerceAtLeast(0L) / 60L).toInt()
    return if (minutes >= 60) "${minutes / 60}س ${minutes % 60}د" else "$minutes دقیقه"
}

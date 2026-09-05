package ir.nv.navigation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.Route
import kotlin.math.roundToInt

private val RefNavy = Color(0xEE031426)
private val RefPanel = Color(0xEE082843)
private val RefPanelHigh = Color(0xF20A3558)
private val RefCyan = Color(0xFF19D8FF)
private val RefBlue = Color(0xFF178CFF)
private val RefLime = Color(0xFF68EF6B)
private val RefAmber = Color(0xFFFFBC32)
private val RefRed = Color(0xFFFF4055)
private val RefText = Color(0xFFF7FBFF)
private val RefMuted = Color(0xFFA9BDCE)
private val RefOutline = Color(0xFF176FA0)

@Composable
fun NvReferenceDrivingDashboard(
    state: NvUiState,
    onSelectRoute: (Int) -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 620.dp
        val sideWidth = if (compact) 168.dp else 320.dp

        ManeuverCard(
            distanceMeters = state.distanceToNextManeuverMeters,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = if (compact) 72.dp else 86.dp)
                .widthIn(min = if (compact) 180.dp else 260.dp, max = if (compact) 230.dp else 360.dp)
        )

        RouteStack(
            routes = state.routeAlternatives,
            selectedIndex = state.selectedRouteIndex,
            onSelect = onSelectRoute,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .width(sideWidth)
        )

        SpeedBadge(
            speedKmh = state.speedKmh,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 12.dp, bottom = if (compact) 150.dp else 164.dp)
        )

        TripStrip(
            state = state,
            onStopNavigation = onStopNavigation,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 10.dp, end = 10.dp, bottom = if (compact) 78.dp else 86.dp)
        )
    }
}

@Composable
private fun ManeuverCard(distanceMeters: Double, modifier: Modifier = Modifier) {
    val distanceText = when {
        distanceMeters <= 0.0 -> "ادامه مسیر"
        distanceMeters < 1_000 -> "${distanceMeters.roundToInt()} متر"
        else -> "%.1f کیلومتر".format(distanceMeters / 1_000.0)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color(0xF20A4C48),
        border = BorderStroke(2.dp, Color(0xFF6CF7D1)),
        shadowElevation = 18.dp
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.TurnRight, null, tint = RefText, modifier = Modifier.size(42.dp))
            Column {
                Text(distanceText, color = RefText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text("خروجی بعدی", color = RefText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RouteStack(
    routes: List<Route>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (routes.isEmpty()) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = RefNavy,
        border = BorderStroke(1.dp, RefOutline),
        shadowElevation = 18.dp
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            routes.take(3).forEachIndexed { index, route ->
                RouteCard(route, index, index == selectedIndex) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun RouteCard(route: Route, index: Int, selected: Boolean, onClick: () -> Unit) {
    val minutes = (route.travelSeconds / 60.0).roundToInt().coerceAtLeast(1)
    val km = route.distanceMeters / 1_000.0
    val accent = when (index) {
        0 -> RefCyan
        1 -> RefAmber
        else -> RefRed
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = if (selected) RefPanelHigh else RefPanel,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) RefCyan else RefOutline.copy(alpha = .72f))
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(11.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(7.dp))
                Text(if (index == 0) "مسیر پیشنهادی" else "مسیر ${index + 1}", color = RefMuted, style = MaterialTheme.typography.labelMedium)
            }
            Text("$minutes دقیقه", color = RefText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%.1f کیلومتر".format(km), color = RefMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .width(42.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent)
                )
            }
        }
    }
}

@Composable
private fun SpeedBadge(speedKmh: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(94.dp),
        shape = CircleShape,
        color = RefNavy,
        border = BorderStroke(3.dp, RefLime),
        shadowElevation = 15.dp
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(speedKmh.coerceAtLeast(0).toString(), color = RefLime, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
            Text("km/h", color = RefText, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TripStrip(state: NvUiState, onStopNavigation: () -> Unit, modifier: Modifier = Modifier) {
    val minutes = (state.remainingSeconds / 60.0).roundToInt().coerceAtLeast(0)
    val km = state.remainingDistanceMeters / 1_000.0
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = RefNavy,
        border = BorderStroke(1.dp, RefOutline),
        shadowElevation = 18.dp
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TripMetric(Icons.Rounded.Schedule, "$minutes دقیقه", "زمان باقی‌مانده", Modifier.weight(1f))
            TripMetric(Icons.Rounded.Route, "%.1f km".format(km), "مسافت تا مقصد", Modifier.weight(1f))
            Column(Modifier.weight(1.25f)) {
                Text("مقصد", color = RefMuted, style = MaterialTheme.typography.labelSmall)
                Text(
                    state.destination?.name ?: "—",
                    color = RefText,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledIconButton(
                onClick = onStopNavigation,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = RefRed, contentColor = RefText),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Rounded.Stop, "پایان مسیریابی")
            }
        }
    }
}

@Composable
private fun TripMetric(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = RefCyan, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(7.dp))
        Column {
            Text(value, color = RefText, fontWeight = FontWeight.Black, maxLines = 1)
            Text(label, color = RefMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

fun referenceDrivingScrim(): Brush = Brush.verticalGradient(
    listOf(Color(0x88020D18), Color.Transparent, Color.Transparent, Color(0xA6031426))
)

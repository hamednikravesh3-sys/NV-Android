package ir.nv.navigation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.Route
import ir.nv.navigation.ui.theme.NvColors
import ir.nv.navigation.ui.theme.NvRadius
import ir.nv.navigation.ui.theme.NvSpacing
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Product-level route comparison for Rahnama screen 10. It deliberately consumes the
 * already-ranked route alternatives from NvViewModel; selecting a row delegates back to
 * NvViewModel so map highlighting/navigation keep one source of truth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RahnamaRouteComparisonFeature(
    state: NvUiState,
    viewModel: NvViewModel,
    modifier: Modifier = Modifier
) {
    if (state.navigationActive || state.routeAlternatives.size < 2) return
    var open by remember(state.destination?.code, state.routeAlternatives.size) { mutableStateOf(false) }

    ExtendedFloatingActionButton(
        onClick = { open = true },
        modifier = modifier.semantics { contentDescription = "مقایسه مسیرها" },
        containerColor = NvColors.Navy900,
        contentColor = NvColors.RouteBlue,
        icon = { Icon(Icons.Rounded.AltRoute, contentDescription = null) },
        text = { Text("مقایسه مسیرها", fontWeight = FontWeight.Bold) }
    )

    if (!open) return

    ModalBottomSheet(
        onDismissRequest = { open = false },
        containerColor = NvColors.Navy900,
        contentColor = NvColors.TextPrimaryDark
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Lg, vertical = NvSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)
        ) {
            Text("مقایسه مسیرها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                state.destination?.name?.let { "به مقصد $it" } ?: "مسیرهای پیشنهادی راهنما",
                color = NvColors.TextSecondaryDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            HorizontalDivider(color = NvColors.DividerDark)

            state.routeAlternatives.take(4).forEachIndexed { index, route ->
                RouteComparisonRow(
                    route = route,
                    index = index,
                    selected = state.selectedRouteIndex == index,
                    baseline = state.routeAlternatives.first(),
                    onSelect = { viewModel.selectRoute(index) }
                )
            }

            Button(onClick = { open = false }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Navigation, contentDescription = null)
                Spacer(Modifier.width(NvSpacing.Sm))
                Text("تأیید مسیر انتخاب‌شده")
            }
            Spacer(Modifier.height(NvSpacing.Lg))
        }
    }
}

@Composable
private fun RouteComparisonRow(
    route: Route,
    index: Int,
    selected: Boolean,
    baseline: Route,
    onSelect: () -> Unit
) {
    val minutes = (route.travelSeconds / 60.0).roundToInt().coerceAtLeast(1)
    val distanceKm = route.distanceMeters / 1000.0
    val baselineMinutes = (baseline.travelSeconds / 60.0).roundToInt().coerceAtLeast(1)
    val deltaMinutes = minutes - baselineMinutes
    val etaMillis = System.currentTimeMillis() + route.travelSeconds.coerceAtLeast(0.0).toLong() * 1000L
    val eta = Instant.ofEpochMilli(etaMillis).atZone(ZoneId.systemDefault())
    val status = buildList {
        if (route.usesToll) add("عوارض")
        if (route.usesHighway) add("بزرگراه")
        if (route.usesFerry) add("شناور")
        route.roadQualityScore?.let { add("کیفیت جاده ${(it.coerceIn(0.0, 1.0) * 100).roundToInt()}٪") }
    }.joinToString(" • ").ifBlank { "مسیر عادی" }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        color = if (selected) NvColors.RouteBlue.copy(alpha = .14f) else NvColors.Navy850,
        shape = RoundedCornerShape(NvRadius.Card),
        border = BorderStroke(1.dp, if (selected) NvColors.RouteBlue else NvColors.DividerDark)
    ) {
        Column(Modifier.padding(NvSpacing.Md), verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (index == 0) "پیشنهاد راهنما" else "مسیر ${index + 1}",
                        fontWeight = FontWeight.Black,
                        color = NvColors.TextPrimaryDark
                    )
                    Text(status, color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)
                }
                if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = "مسیر انتخاب‌شده", tint = NvColors.Success)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RouteMetric("$minutes دقیقه", "زمان")
                RouteMetric(String.format("%.1f km", distanceKm), "فاصله")
                RouteMetric("%02d:%02d".format(eta.hour, eta.minute), "رسیدن")
            }

            if (index > 0 && deltaMinutes != 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = NvColors.Warning)
                    Spacer(Modifier.width(NvSpacing.Xs))
                    Text(
                        if (deltaMinutes > 0) "$deltaMinutes دقیقه کندتر از پیشنهاد اول" else "${-deltaMinutes} دقیقه سریع‌تر از پیشنهاد اول",
                        color = NvColors.TextSecondaryDark,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Bold)
        Text(label, color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)
    }
}

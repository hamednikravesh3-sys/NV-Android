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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.Place
import ir.nv.navigation.navigation.VehicleProfile
import ir.nv.navigation.ui.theme.NvColors
import ir.nv.navigation.ui.theme.NvRadius
import ir.nv.navigation.ui.theme.NvSpacing

/**
 * Direct smart-route assistant. It intentionally avoids the old showcase/menu of
 * disconnected "smart" cards. The user's sentence is reduced to a destination
 * query, searched by the same production HybridSearchEngine used by the app, and
 * the route is then created from the device's current location.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RahnamaSmartRouteAssistant(
    state: NvUiState,
    viewModel: NvViewModel,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var pendingDestination by remember { mutableStateOf<String?>(null) }
    var searchStarted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var selectedVehicle by remember { mutableStateOf(VehicleProfile.CAR) }

    LaunchedEffect(state.destinationSearching, pendingDestination) {
        if (pendingDestination != null && state.destinationSearching) searchStarted = true
    }

    LaunchedEffect(
        pendingDestination,
        searchStarted,
        state.destinationSearching,
        state.destinationQuery,
        state.destinationSuggestions
    ) {
        val pending = pendingDestination ?: return@LaunchedEffect
        if (state.destinationQuery.trim() != pending.trim()) return@LaunchedEffect

        val candidate = state.destinationSuggestions.firstOrNull()
        if (candidate != null && !state.destinationSearching) {
            status = "مقصد «${candidate.name}» پیدا شد؛ مسیر از موقعیت فعلی شما ساخته می‌شود…"
            pendingDestination = null
            searchStarted = false
            viewModel.routeFromCurrentLocationTo(candidate, selectedVehicle)
        } else if (searchStarted && !state.destinationSearching && state.destinationSuggestions.isEmpty()) {
            status = "مقصد «$pending» پیدا نشد. نام خیابان، مکان یا کد NV را دقیق‌تر بنویسید."
            pendingDestination = null
            searchStarted = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NvColors.Navy900,
        contentColor = NvColors.TextPrimaryDark
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(NvRadius.Medium),
                    color = NvColors.RouteBlue.copy(alpha = .16f),
                    border = BorderStroke(1.dp, NvColors.RouteBlue.copy(alpha = .55f))
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = NvColors.RouteBlue,
                        modifier = Modifier.padding(10.dp).size(25.dp)
                    )
                }
                Spacer(Modifier.width(NvSpacing.Sm))
                Column(Modifier.weight(1f)) {
                    Text("دستیار هوشمند مسیر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        "مبدأ همیشه موقعیت فعلی شماست؛ فقط مقصد را به زبان عادی بنویسید.",
                        color = NvColors.TextSecondaryDark,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NvColors.Navy850,
                shape = RoundedCornerShape(NvRadius.Card),
                border = BorderStroke(1.dp, NvColors.DividerDark)
            ) {
                Row(Modifier.padding(NvSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = null, tint = NvColors.Success)
                    Spacer(Modifier.width(NvSpacing.Sm))
                    Column(Modifier.weight(1f)) {
                        Text("مبدأ: موقعیت دقیق فعلی من", fontWeight = FontWeight.Bold)
                        Text(
                            state.locationAccuracyMeters?.let { "دقت فعلی GPS: ±${it.toInt()} متر" }
                                ?: if (state.locating) "در حال تثبیت GPS…" else "GPS پس از ارسال درخواست تثبیت می‌شود",
                            color = NvColors.TextSecondaryDark,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it; status = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("کجا برویم؟") },
                placeholder = { Text("مثلاً: منو ببر میدان آزادی") },
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NvColors.TextPrimaryDark,
                    unfocusedTextColor = NvColors.TextPrimaryDark,
                    cursorColor = NvColors.RouteBlue,
                    focusedBorderColor = NvColors.RouteBlue,
                    unfocusedBorderColor = NvColors.DividerDark,
                    focusedLabelColor = NvColors.RouteBlue,
                    unfocusedLabelColor = NvColors.TextSecondaryDark,
                    focusedPlaceholderColor = NvColors.TextSecondaryDark,
                    unfocusedPlaceholderColor = NvColors.TextSecondaryDark
                )
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                listOf(
                    VehicleProfile.CAR to "خودرو",
                    VehicleProfile.MOTORCYCLE to "موتور",
                    VehicleProfile.WALKING to "پیاده"
                ).forEach { (profile, label) ->
                    Surface(
                        modifier = Modifier.weight(1f).clickable { selectedVehicle = profile },
                        shape = RoundedCornerShape(NvRadius.Pill),
                        color = if (selectedVehicle == profile) NvColors.RouteBlue.copy(alpha = .22f) else NvColors.Navy850,
                        border = BorderStroke(
                            1.dp,
                            if (selectedVehicle == profile) NvColors.RouteBlue else NvColors.DividerDark
                        )
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                            fontWeight = if (selectedVehicle == profile) FontWeight.Black else FontWeight.Medium
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val destination = smartDestinationText(query)
                    if (destination.isBlank()) {
                        status = "نام مقصد را بنویسید."
                    } else {
                        pendingDestination = destination
                        searchStarted = false
                        status = "در حال جست‌وجوی «$destination»…"
                        viewModel.updateDestinationQuery(destination)
                        viewModel.useCurrentLocationAsOrigin()
                    }
                },
                enabled = query.isNotBlank() && pendingDestination == null && !state.routing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(NvRadius.Medium)
            ) {
                Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
                Spacer(Modifier.width(NvSpacing.Sm))
                Text("پیدا کن و مسیر بساز", fontWeight = FontWeight.Black)
            }

            if (pendingDestination != null || state.destinationSearching || state.routing || state.locating) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            status?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = NvColors.Navy850,
                    shape = RoundedCornerShape(NvRadius.Medium),
                    border = BorderStroke(1.dp, NvColors.RouteBlue.copy(alpha = .35f))
                ) {
                    Text(it, modifier = Modifier.padding(NvSpacing.Md), color = NvColors.TextPrimaryDark)
                }
            }

            if (pendingDestination != null && state.destinationSuggestions.isNotEmpty()) {
                Text("نتایج نزدیک", fontWeight = FontWeight.Bold)
                state.destinationSuggestions.take(3).forEach { place ->
                    SmartPlaceCandidate(place) {
                        pendingDestination = null
                        searchStarted = false
                        status = "مسیر به «${place.name}» در حال ساخته‌شدن است…"
                        viewModel.routeFromCurrentLocationTo(place, selectedVehicle)
                    }
                }
            }

            if (state.route != null && state.destination != null && !state.routing) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = NvColors.Success.copy(alpha = .13f),
                    shape = RoundedCornerShape(NvRadius.Medium),
                    border = BorderStroke(1.dp, NvColors.Success.copy(alpha = .55f))
                ) {
                    Text(
                        "مسیر به ${state.destination.name} آماده است. با بستن این پنجره مسیر واقعی روی نقشه نمایش داده می‌شود.",
                        modifier = Modifier.padding(NvSpacing.Md),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("بازگشت به نقشه") }
            Spacer(Modifier.height(NvSpacing.Xl))
        }
    }
}

@Composable
private fun SmartPlaceCandidate(place: Place, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = NvColors.Navy850,
        shape = RoundedCornerShape(NvRadius.Medium),
        border = BorderStroke(1.dp, NvColors.DividerDark)
    ) {
        Row(Modifier.padding(NvSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Place, contentDescription = null, tint = NvColors.RouteBlue)
            Spacer(Modifier.width(NvSpacing.Sm))
            Column(Modifier.weight(1f)) {
                Text(place.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                place.address?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun smartDestinationText(raw: String): String {
    var text = raw.trim()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
    val phrases = listOf(
        "لطفاً", "لطفا", "می‌خوام برم", "میخوام برم", "می خوام برم", "می‌خواهم بروم",
        "منو ببر", "مرا ببر", "ببر منو", "برو به", "مسیر بده به", "مسیر بده",
        "مسیریابی کن به", "مسیریابی کن", "راه را نشان بده", "راه رو نشون بده",
        "از اینجا", "از موقعیت من", "با ماشین", "با خودرو", "با موتور", "پیاده"
    )
    phrases.forEach { text = text.replace(it, " ", ignoreCase = true) }
    text = text.replace(Regex("\\s+"), " ").trim()
    text = text.replace(Regex("^(به|تا|سمت)\\s+"), "").trim()
    return text
}

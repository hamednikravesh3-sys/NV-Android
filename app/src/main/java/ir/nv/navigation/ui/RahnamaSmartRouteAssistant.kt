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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.nv.navigation.navigation.VehicleProfile
import ir.nv.navigation.ui.theme.NvColors
import ir.nv.navigation.ui.theme.NvRadius
import ir.nv.navigation.ui.theme.NvSpacing

/**
 * Production smart-route assistant.
 *
 * There is no origin field: NvViewModel resolves a precise device fix, extracts
 * the destination intent, proximity-ranks search results and starts routing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RahnamaSmartRouteAssistant(
    state: NvUiState,
    viewModel: NvViewModel,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedVehicle by remember { mutableStateOf(VehicleProfile.CAR) }
    val locationAccuracy = state.locationAccuracyMeters

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NvColors.Navy900,
        contentColor = NvColors.TextPrimaryDark
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = NvSpacing.Lg),
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
                    Text(
                        "چت هوشمند سفر",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "فقط بگویید کجا می‌خواهید بروید؛ مبدأ از GPS دقیق گرفته می‌شود.",
                        color = NvColors.TextSecondaryDark,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NvColors.Navy850,
                shape = RoundedCornerShape(NvRadius.Card),
                border = BorderStroke(
                    1.dp,
                    if ((locationAccuracy ?: Float.MAX_VALUE) <= 12f) NvColors.Success
                    else NvColors.Warning
                )
            ) {
                Row(Modifier.padding(NvSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.MyLocation,
                        contentDescription = null,
                        tint = if ((locationAccuracy ?: Float.MAX_VALUE) <= 12f) NvColors.Success else NvColors.Warning
                    )
                    Spacer(Modifier.width(NvSpacing.Sm))
                    Column(Modifier.weight(1f)) {
                        Text("مبدأ خودکار: موقعیت فعلی من", fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                state.locating -> "در حال تثبیت GPS دقیق…"
                                locationAccuracy != null ->
                                    "دقت فعلی: ±${locationAccuracy.toInt()} متر"
                                else -> "هنگام ارسال درخواست، GPS دقیق تثبیت می‌شود"
                            },
                            color = NvColors.TextSecondaryDark,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("کجا می‌خواهید بروید؟") },
                placeholder = { Text("مثلاً: نزدیک‌ترین بیمارستان یا منو ببر میدان آزادی") },
                minLines = 2,
                maxLines = 4,
                enabled = !state.smartJourneyPlanning
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
            ) {
                listOf(
                    VehicleProfile.CAR to "خودرو",
                    VehicleProfile.MOTORCYCLE to "موتور",
                    VehicleProfile.WALKING to "پیاده",
                    VehicleProfile.TRANSIT to "عمومی"
                ).forEach { (profile, label) ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !state.smartJourneyPlanning) { selectedVehicle = profile },
                        shape = RoundedCornerShape(NvRadius.Pill),
                        color = if (selectedVehicle == profile) {
                            NvColors.RouteBlue.copy(alpha = .22f)
                        } else {
                            NvColors.Navy850
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selectedVehicle == profile) NvColors.RouteBlue else NvColors.DividerDark
                        )
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 9.dp),
                            fontWeight = if (selectedVehicle == profile) FontWeight.Black else FontWeight.Medium,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.planJourneyFromChat(query, selectedVehicle) },
                enabled = query.isNotBlank() && !state.smartJourneyPlanning && !state.routing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(NvRadius.Medium)
            ) {
                Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
                Spacer(Modifier.width(NvSpacing.Sm))
                Text("تشخیص مقصد و ساخت مسیر", fontWeight = FontWeight.Black)
            }

            if (state.smartJourneyPlanning || state.routing || state.locating) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            state.smartJourneyStatus?.let { status ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = NvColors.Navy850,
                    shape = RoundedCornerShape(NvRadius.Medium),
                    border = BorderStroke(1.dp, NvColors.RouteBlue.copy(alpha = .35f))
                ) {
                    Text(
                        status,
                        modifier = Modifier.padding(NvSpacing.Md),
                        color = NvColors.TextPrimaryDark
                    )
                }
            }

            Spacer(Modifier.height(NvSpacing.Xl))
        }
    }
}

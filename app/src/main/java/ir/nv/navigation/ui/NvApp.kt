package ir.nv.navigation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.nv.navigation.core.Place
import ir.nv.navigation.entitlement.TrialManager
import ir.nv.navigation.map.IranPackManager
import ir.nv.navigation.map.OfflineIranMap
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun NvApp(viewModel: NvViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    when (val pack = state.packStatus) {
        IranPackManager.Status.Ready -> NavigationScreen(state, viewModel)
        else -> PackDownloadScreen(pack, viewModel::retryDownload)
    }
}

@Composable
private fun PackDownloadScreen(
    status: IranPackManager.Status,
    retry: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("NV", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
            Text(
                "ناوبری آفلاین ایران",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(40.dp))
            when (status) {
                IranPackManager.Status.NotStarted,
                IranPackManager.Status.Installing -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(if (status is IranPackManager.Status.Installing) "در حال نصب…" else "آماده‌سازی…")
                }
                is IranPackManager.Status.Downloading -> {
                    val determinate = status.totalBytes > 0
                    if (determinate) {
                        LinearProgressIndicator(
                            progress = {
                                status.bytes.toFloat() / status.totalBytes.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (determinate) {
                            formatBytes(status.bytes) + " از " + formatBytes(status.totalBytes)
                        } else {
                            "در حال دانلود نقشه کامل ایران…"
                        }
                    )
                }
                is IranPackManager.Status.Failed -> {
                    Text(
                        status.reason,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = retry) { Text("تلاش دوباره") }
                }
                IranPackManager.Status.Ready -> Unit
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "این بسته فقط داده‌های ایران را دریافت می‌کند و پس از نصب، نقشه و مسیریابی بدون اینترنت اجرا می‌شوند.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun NavigationScreen(state: NvUiState, viewModel: NvViewModel) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        OfflineIranMap(
            context = context,
            mapFile = viewModel.mapFile(),
            route = state.route,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("NV", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        TrialBadge(state.trialState)
                    }
                    PlaceField(
                        label = "مبدأ: نام یا کد",
                        value = state.originQuery,
                        suggestions = state.originSuggestions,
                        onValueChange = viewModel::updateOriginQuery,
                        onSelect = viewModel::selectOrigin
                    )
                    PlaceField(
                        label = "مقصد: نام یا کد",
                        value = state.destinationQuery,
                        suggestions = state.destinationSuggestions,
                        onValueChange = viewModel::updateDestinationQuery,
                        onSelect = viewModel::selectDestination
                    )
                    Button(
                        onClick = viewModel::calculateRoute,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.routing
                    ) {
                        Text(if (state.routing) "در حال محاسبه…" else "مسیریابی آفلاین")
                    }
                    state.message?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        state.route?.let { route ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("خلاصه مسیر", fontWeight = FontWeight.Bold)
                    val distanceKm = route.distanceMeters / 1_000.0
                    val minutes = (route.travelSeconds / 60.0).roundToInt()
                    val eta = Instant.now()
                        .plusSeconds(route.travelSeconds.toLong())
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text("%.1f کیلومتر  •  %d دقیقه  •  رسیدن %s".format(distanceKm, minutes, eta))
                    Text(
                        "ترافیک زنده: در انتظار اتصال سرویس داده",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceField(
    label: String,
    value: String,
    suggestions: List<Place>,
    onValueChange: (String) -> Unit,
    onSelect: (Place) -> Unit
) {
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = suggestions.isNotEmpty(),
            onDismissRequest = { },
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            suggestions.forEach { place ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(place.displayName)
                            Text(place.category, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = { onSelect(place) }
                )
            }
        }
    }
}

@Composable
private fun TrialBadge(state: TrialManager.State) {
    val label = when (state) {
        is TrialManager.State.Trial -> state.daysRemaining.toString() + " روز رایگان"
        TrialManager.State.Paid -> "نسخه کامل"
        TrialManager.State.Expired -> "نیاز به فعال‌سازی"
        TrialManager.State.Tampered -> "بررسی مجوز"
    }
    Text(
        text = label,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.labelMedium
    )
}

private fun formatBytes(value: Long): String {
    if (value < 1_024) return value.toString() + " B"
    val mb = value / (1_024.0 * 1_024.0)
    return "%.1f MB".format(mb)
}

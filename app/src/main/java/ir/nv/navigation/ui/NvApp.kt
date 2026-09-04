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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.nv.navigation.core.Place
import ir.nv.navigation.map.IranPackManager
import ir.nv.navigation.map.OfflineIranMap
import ir.nv.navigation.map.OnlineIranMap
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun NvApp(
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: NvViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showOfflineManager by remember { mutableStateOf(false) }
    var showCodeHelp by remember { mutableStateOf(false) }
    var personalCode by remember { mutableStateOf("") }
    val context = LocalContext.current
    val effectiveOffline = state.offlineReady && (state.preferOffline || !viewModel.isOnline())

    if (showCodeHelp) {
        AlertDialog(
            onDismissRequest = { showCodeHelp = false },
            confirmButton = { TextButton(onClick = { showCodeHelp = false }) { Text("متوجه شدم") } },
            title = { Text("کد مکان NV") },
            text = {
                Text(
                    "می‌توانید نام شهر یا مکان را جست‌وجو کنید. بعد از انتخاب یک مکان، برای آن کد شخصی دلخواه مثل HOME1 یا OFFICE بسازید. کدهای شخصی روی همین گوشی ذخیره می‌شوند و بدون اینترنت هم قابل جست‌وجو هستند."
                )
            }
        )
    }

    if (showOfflineManager) {
        OfflineManagerDialog(
            state = state,
            close = { showOfflineManager = false },
            start = viewModel::startMapDownload,
            retry = viewModel::retryDownload,
            cancel = viewModel::cancelDownload,
            setOffline = viewModel::setPreferOffline
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (effectiveOffline) {
            OfflineIranMap(
                context = context,
                mapFile = viewModel.mapFile(),
                route = state.route,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            OnlineIranMap(route = state.route, modifier = Modifier.fillMaxSize())
        }

        Column(
            modifier = Modifier.statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("NV", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            Text(
                                if (effectiveOffline) "آفلاین" else "آنلاین",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(onClick = { showOfflineManager = true }) { Text("نقشه آفلاین") }
                            TextButton(onClick = { showCodeHelp = true }) { Text("کد مکان") }
                            TextButton(onClick = onToggleTheme) { Text(if (darkMode) "روز" else "شب") }
                        }
                    }

                    PlaceField(
                        label = "از کجا؟ شهر، خیابان، مکان یا کد",
                        value = state.originQuery,
                        suggestions = state.originSuggestions,
                        onValueChange = viewModel::updateOriginQuery,
                        onSelect = viewModel::selectOrigin
                    )
                    PlaceField(
                        label = "به کجا؟ شهر، خیابان، مکان یا کد",
                        value = state.destinationQuery,
                        suggestions = state.destinationSuggestions,
                        onValueChange = viewModel::updateDestinationQuery,
                        onSelect = viewModel::selectDestination
                    )

                    val selectedPlace = state.destination ?: state.origin
                    if (selectedPlace != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = personalCode,
                                onValueChange = { personalCode = it },
                                label = { Text("کد شخصی") },
                                placeholder = { Text("مثلاً HOME1") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    viewModel.savePersonalCode(selectedPlace, personalCode)
                                    personalCode = ""
                                },
                                enabled = personalCode.trim().length >= 2
                            ) { Text("ذخیره") }
                        }
                    }

                    Button(
                        onClick = viewModel::calculateRoute,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.routing
                    ) {
                        Text(if (state.routing) "در حال محاسبه مسیر…" else "حرکت")
                    }

                    state.message?.let {
                        Text(
                            it,
                            color = if (it.contains("ذخیره شد")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        state.route?.let { route ->
            Card(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            ) {
                val distanceKm = route.distanceMeters / 1000.0
                val minutes = (route.travelSeconds / 60.0).roundToInt()
                val eta = Instant.now().plusSeconds(route.travelSeconds.toLong())
                    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("%.1f کیلومتر  •  %d دقیقه  •  رسیدن %s".format(distanceKm, minutes, eta), fontWeight = FontWeight.Bold)
                    Text(if (effectiveOffline) "مسیر با داده آفلاین محاسبه شد" else "مسیر آنلاین — در نبود اینترنت، NV خودکار از نقشه دانلودشده استفاده می‌کند", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun OfflineManagerDialog(
    state: NvUiState,
    close: () -> Unit,
    start: () -> Unit,
    retry: () -> Unit,
    cancel: () -> Unit,
    setOffline: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = close,
        confirmButton = { TextButton(onClick = close) { Text("بستن") } },
        title = { Text("نقشه‌های آفلاین") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("NV به‌صورت پیش‌فرض آنلاین کار می‌کند. دانلود نقشه اختیاری است و برای استفاده بدون اینترنت کاربرد دارد.")
                when (val status = state.packStatus) {
                    IranPackManager.Status.NotStarted -> {
                        Text("نقشه آفلاین نصب نشده است.")
                        Button(onClick = start, modifier = Modifier.fillMaxWidth()) { Text("دانلود نقشه آفلاین ایران") }
                    }
                    IranPackManager.Status.Installing -> {
                        CircularProgressIndicator()
                        Text("در حال نصب نقشه آفلاین…")
                    }
                    is IranPackManager.Status.Downloading -> {
                        val known = status.totalBytes > 0
                        if (known) {
                            LinearProgressIndicator(
                                progress = { status.bytes.toFloat() / status.totalBytes.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("${formatBytes(status.bytes)} از ${formatBytes(status.totalBytes)}")
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("در حال دانلود…")
                        }
                        TextButton(onClick = cancel) { Text("توقف دانلود") }
                    }
                    is IranPackManager.Status.Failed -> {
                        Text(status.reason, color = MaterialTheme.colorScheme.error)
                        Button(onClick = retry, modifier = Modifier.fillMaxWidth()) { Text("تلاش دوباره") }
                    }
                    IranPackManager.Status.Ready -> {
                        Text("نقشه آفلاین آماده است.", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { setOffline(false); close() }, modifier = Modifier.weight(1f)) { Text("حالت آنلاین") }
                            Button(onClick = { setOffline(true); close() }, modifier = Modifier.weight(1f)) { Text("حالت آفلاین") }
                        }
                    }
                }
                Text("در نسخه بعدی، دانلود استان‌به‌استان جای بسته یکپارچه ایران را می‌گیرد تا حجم دانلود بسیار کمتر شود.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start)
            }
        }
    )
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
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        )
        DropdownMenu(
            expanded = suggestions.isNotEmpty(),
            onDismissRequest = {},
            modifier = Modifier.fillMaxWidth(0.92f).background(MaterialTheme.colorScheme.surface)
        ) {
            suggestions.forEach { place ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(place.name, fontWeight = FontWeight.SemiBold)
                            Text(categoryLabel(place.category), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = { onSelect(place) }
                )
            }
        }
    }
}

private fun categoryLabel(category: String): String = when {
    category == "place:city" || category == "place:town" -> "شهر"
    category == "place:village" -> "روستا"
    category == "place:suburb" || category == "place:neighbourhood" -> "محله"
    category.startsWith("personal:") -> "مکان شخصی"
    category.startsWith("online:") -> "نتیجه آنلاین"
    category.startsWith("tourism:") -> "دیدنی"
    category.startsWith("amenity:") -> "خدمات"
    category.startsWith("shop:") -> "فروشگاه"
    else -> "مکان"
}

private fun formatBytes(value: Long): String {
    if (value < 1024) return "$value B"
    val mb = value / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}

package ir.nv.navigation.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import ir.nv.navigation.entitlement.BillingState
import ir.nv.navigation.entitlement.PlayBillingManager
import ir.nv.navigation.entitlement.TrialManager
import ir.nv.navigation.map.IranPackManager
import ir.nv.navigation.map.OfflineIranMap
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
    val context = LocalContext.current
    val billing = remember { PlayBillingManager(context.applicationContext) }
    val billingState by billing.state.collectAsState()

    LaunchedEffect(billingState.purchased) {
        viewModel.refreshEntitlement(billingState.purchased)
    }
    DisposableEffect(billing) { onDispose(billing::close) }

    when (val pack = state.packStatus) {
        IranPackManager.Status.Ready -> when (state.trialState) {
            TrialManager.State.Expired,
            TrialManager.State.Tampered -> ActivationScreen(
                billing = billingState,
                purchase = { context.findActivity()?.let(billing::launchPurchase) }
            )
            else -> NavigationScreen(state, viewModel, darkMode, onToggleTheme)
        }
        else -> PackDownloadScreen(
            status = pack,
            start = viewModel::startMapDownload,
            retry = viewModel::retryDownload,
            cancel = viewModel::cancelDownload
        )
    }
}

@Composable
private fun ActivationScreen(billing: BillingState, purchase: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("NV", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(20.dp))
            Text("دوره رایگان ۳۰ روزه پایان یافته است", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Text("برای ادامه مسیریابی، نسخه کامل را فعال کنید.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = purchase, enabled = !billing.connecting) {
                Text(
                    if (billing.connecting) "در حال اتصال…"
                    else "فعال‌سازی" + (billing.formattedPrice?.let { " — $it" } ?: "")
                )
            }
            billing.message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PackDownloadScreen(
    status: IranPackManager.Status,
    start: () -> Unit,
    retry: () -> Unit,
    cancel: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("NV", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
            Text("ناوبری آفلاین با انتخاب خود شما", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(28.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("نقشه آفلاین کل ایران", fontWeight = FontWeight.Bold)
                    Text(
                        "شامل نقشه برداری، جست‌وجوی مکان و مسیریابی آفلاین. حجم فعلی بسته حدود ۱.۵ گیگابایت است. دانلود دیگر خودکار نیست."
                    )
                    when (status) {
                        IranPackManager.Status.NotStarted -> {
                            Button(onClick = start, modifier = Modifier.fillMaxWidth()) {
                                Text("دانلود نقشه کل ایران")
                            }
                            Text(
                                "اگر الآن اینترنت مناسب ندارید، هیچ فایلی دانلود نمی‌شود. هر زمان خواستید از همین صفحه شروع کنید.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IranPackManager.Status.Installing -> {
                            CircularProgressIndicator()
                            Text("در حال نصب داده‌های آفلاین…")
                        }
                        is IranPackManager.Status.Downloading -> {
                            val determinate = status.totalBytes > 0
                            if (determinate) {
                                LinearProgressIndicator(
                                    progress = { status.bytes.toFloat() / status.totalBytes.toFloat() },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Text(
                                if (determinate) "${formatBytes(status.bytes)} از ${formatBytes(status.totalBytes)}"
                                else "در حال دانلود…"
                            )
                            TextButton(onClick = cancel) { Text("توقف دانلود") }
                        }
                        is IranPackManager.Status.Failed -> {
                            Text(status.reason, color = MaterialTheme.colorScheme.error)
                            Button(onClick = retry, modifier = Modifier.fillMaxWidth()) {
                                Text("تلاش دوباره")
                            }
                        }
                        IranPackManager.Status.Ready -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationScreen(
    state: NvUiState,
    viewModel: NvViewModel,
    darkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    var showCodeHelp by remember { mutableStateOf(false) }
    var personalCode by remember { mutableStateOf("") }
    val selectedPlace = state.destination ?: state.origin

    if (showCodeHelp) {
        AlertDialog(
            onDismissRequest = { showCodeHelp = false },
            confirmButton = { TextButton(onClick = { showCodeHelp = false }) { Text("متوجه شدم") } },
            title = { Text("راهنمای کد مکان") },
            text = {
                Text(
                    "هر مکان عمومی یک کد عددی NV دارد و می‌توانید آن را مستقیم جست‌وجو کنید. " +
                        "برای مکان‌های شخصی نیز می‌توانید کد دلخواه خودتان مثل HOME1، OFFICE یا خانه_علی بسازید. " +
                        "کدهای شخصی فقط روی همین دستگاه ذخیره می‌شوند."
                )
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        OfflineIranMap(
            context = context,
            mapFile = viewModel.mapFile(),
            route = state.route,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier.statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
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
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { showCodeHelp = true }) { Text("کد مکان؟") }
                            TextButton(onClick = onToggleTheme) { Text(if (darkMode) "روز" else "شب") }
                        }
                    }

                    PlaceField(
                        label = "از کجا؟ شهر، مکان یا کد",
                        value = state.originQuery,
                        suggestions = state.originSuggestions,
                        onValueChange = viewModel::updateOriginQuery,
                        onSelect = viewModel::selectOrigin
                    )
                    PlaceField(
                        label = "به کجا؟ شهر، مکان یا کد",
                        value = state.destinationQuery,
                        suggestions = state.destinationSuggestions,
                        onValueChange = viewModel::updateDestinationQuery,
                        onSelect = viewModel::selectDestination
                    )

                    if (selectedPlace != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = personalCode,
                                onValueChange = { personalCode = it },
                                label = { Text("کد شخصی این مکان") },
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
                        Text(if (state.routing) "در حال پیدا کردن بهترین مسیر…" else "حرکت")
                    }
                    state.message?.let {
                        Text(
                            it,
                            color = if (it.contains("ذخیره شد")) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        state.route?.let { route ->
            Card(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val distanceKm = route.distanceMeters / 1_000.0
                    val trafficDelay = state.traffic?.delaySeconds ?: 0.0
                    val minutes = ((route.travelSeconds + trafficDelay) / 60.0).roundToInt()
                    val eta = Instant.now()
                        .plusSeconds((route.travelSeconds + trafficDelay).toLong())
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text("%.1f کیلومتر  •  %d دقیقه  •  رسیدن %s".format(distanceKm, minutes, eta), fontWeight = FontWeight.Bold)
                    state.routeNotices.take(3).forEach { notice ->
                        Text(
                            "${noticePrefix(notice.kind)}: ${notice.title} • ${formatAhead(notice.distanceAheadMeters)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        )
        DropdownMenu(
            expanded = suggestions.isNotEmpty(),
            onDismissRequest = { },
            modifier = Modifier.fillMaxWidth(0.92f).background(MaterialTheme.colorScheme.surface)
        ) {
            suggestions.forEach { place ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(place.displayName, fontWeight = FontWeight.SemiBold)
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
    category == "place:city" -> "شهر"
    category == "place:town" -> "شهر"
    category == "place:village" -> "روستا"
    category == "place:suburb" -> "محله"
    category.startsWith("personal:") -> "مکان شخصی"
    category.startsWith("tourism:") -> "دیدنی"
    category.startsWith("amenity:") -> "خدمات"
    category.startsWith("shop:") -> "فروشگاه"
    else -> "مکان"
}

private fun formatBytes(value: Long): String {
    if (value < 1_024) return "$value B"
    val mb = value / (1_024.0 * 1_024.0)
    return if (mb >= 1024) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}

private fun formatAhead(meters: Double): String = if (meters < 1_000) {
    "${meters.roundToInt()} متر"
} else {
    "%.1f کیلومتر".format(meters / 1_000.0)
}

private fun noticePrefix(kind: ir.nv.navigation.core.RouteNotice.Kind): String = when (kind) {
    ir.nv.navigation.core.RouteNotice.Kind.ATTRACTION -> "دیدنی جلوتر"
    ir.nv.navigation.core.RouteNotice.Kind.WEATHER -> "هوا جلوتر"
    ir.nv.navigation.core.RouteNotice.Kind.TRAFFIC -> "ترافیک جلوتر"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

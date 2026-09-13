package ir.nv.navigation.ui

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.nv.navigation.smart.SmartFeatureScreen
import ir.nv.navigation.smart.SmartMobilityEngine
import ir.nv.navigation.ui.theme.NvColors
import ir.nv.navigation.ui.theme.NvRadius
import ir.nv.navigation.ui.theme.NvSpacing
import kotlin.math.ceil
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RahnamaSmartMobilityHub(
    state: NvUiState,
    viewModel: NvViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("rahnama_smart", Context.MODE_PRIVATE) }
    val engine = remember { SmartMobilityEngine() }
    var screen by remember { mutableStateOf<SmartFeatureScreen?>(null) }
    var lowData by remember { mutableStateOf(preferences.getBoolean("low_data", false)) }
    var privacyMode by remember { mutableStateOf(preferences.getBoolean("privacy_mode", true)) }
    var ecoPriority by remember { mutableStateOf(preferences.getBoolean("eco_priority", false)) }
    var fuelPrice by remember { mutableStateOf(preferences.getString("fuel_price", "").orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NvColors.Navy900,
        contentColor = NvColors.TextPrimaryDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NvSpacing.Lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (screen != null) {
                    IconButton(onClick = { screen = null }) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = "بازگشت به مرکز هوشمند", tint = NvColors.RouteBlue)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        screen?.titleFa ?: "مرکز هوشمند راهنما",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (screen == null) "ابزارهای هوشمند سفر، با fallback صریح برای سرویس‌های متصل‌نشده" else "داده زنده فقط وقتی منبع واقعی در دسترس باشد نمایش داده می‌شود",
                        color = NvColors.TextSecondaryDark,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            HorizontalDivider(color = NvColors.DividerDark)

            when (val selected = screen) {
                null -> SmartHubMenu(onOpen = { screen = it })
                SmartFeatureScreen.CHAT -> SmartChatScreen(state, engine) { target -> screen = target }
                SmartFeatureScreen.RUSH -> RushModeScreen(state, engine, viewModel)
                SmartFeatureScreen.MULTIMODAL -> MultimodalScreen(state, engine)
                SmartFeatureScreen.STATION_TRANSFER -> StationTransferScreen(engine)
                SmartFeatureScreen.LIVE_METRO -> LiveMetroScreen(engine)
                SmartFeatureScreen.TAXI -> TaxiCoordinationScreen(state, engine)
                SmartFeatureScreen.ETA_CONFIDENCE -> EtaConfidenceScreen(state, engine)
                SmartFeatureScreen.TIME_COST -> TimeCostScreen(state, engine, fuelPrice) { value ->
                    fuelPrice = value.filter(Char::isDigit)
                    preferences.edit().putString("fuel_price", fuelPrice).apply()
                }
                SmartFeatureScreen.WALKING -> WalkingScreen(state, engine)
                SmartFeatureScreen.PREFERENCES -> SmartPreferencesScreen(
                    state = state,
                    viewModel = viewModel,
                    lowData = lowData,
                    privacyMode = privacyMode,
                    ecoPriority = ecoPriority,
                    onLowData = {
                        lowData = it
                        preferences.edit().putBoolean("low_data", it).apply()
                    },
                    onPrivacyMode = {
                        privacyMode = it
                        preferences.edit().putBoolean("privacy_mode", it).apply()
                    },
                    onEcoPriority = {
                        ecoPriority = it
                        preferences.edit().putBoolean("eco_priority", it).apply()
                    }
                )
            }

            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("بستن") }
            Spacer(Modifier.height(NvSpacing.Xl))
        }
    }
}

@Composable
private fun SmartHubMenu(onOpen: (SmartFeatureScreen) -> Unit) {
    SmartFeatureScreen.entries.chunked(2).forEach { rowItems ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
            rowItems.forEach { feature ->
                Surface(
                    modifier = Modifier.weight(1f).clickable { onOpen(feature) },
                    color = NvColors.Navy850,
                    shape = RoundedCornerShape(NvRadius.Card),
                    border = BorderStroke(1.dp, NvColors.RouteBlue.copy(alpha = .35f))
                ) {
                    Column(
                        modifier = Modifier.padding(NvSpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
                    ) {
                        Icon(featureIcon(feature), contentDescription = feature.titleFa, tint = featureColor(feature))
                        Text(feature.titleFa, color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Bold)
                        Text(featureSubtitle(feature), color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SmartChatScreen(
    state: NvUiState,
    engine: SmartMobilityEngine,
    onOpen: (SmartFeatureScreen) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    val reply = remember(submittedQuery, state.route, state.destination) {
        engine.assistant(submittedQuery, state.route != null, state.destination != null)
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("سؤال سفر") },
        placeholder = { Text("مثلاً سریع‌ترین مسیر چیست؟") },
        minLines = 2
    )
    Button(onClick = { submittedQuery = query }, enabled = query.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
        Spacer(Modifier.size(NvSpacing.Xs))
        Text("تحلیل درخواست")
    }
    SmartInfoCard(reply.titleFa, reply.messageFa, NvColors.RouteBlue)
    reply.suggestedScreen?.let { target ->
        OutlinedButton(onClick = { onOpen(target) }, modifier = Modifier.fillMaxWidth()) {
            Text("باز کردن ${target.titleFa}")
        }
    }
}

@Composable
private fun RushModeScreen(state: NvUiState, engine: SmartMobilityEngine, viewModel: NvViewModel) {
    val decision = remember(state.routeAlternatives, state.selectedRouteIndex) {
        engine.rush(state.routeAlternatives, state.selectedRouteIndex)
    }
    if (decision == null) {
        SmartInfoCard("مسیر آماده نیست", "ابتدا مقصد را انتخاب کنید و یک مسیر بسازید تا حالت عجله بتواند گزینه‌ها را مقایسه کند", NvColors.Warning)
        return
    }
    val best = state.routeAlternatives[decision.recommendedIndex]
    SmartMetricRow(
        listOf(
            "${ceil(best.travelSeconds / 60.0).toInt()} دقیقه" to "زمان",
            "%.1f km".format(best.distanceMeters / 1000.0) to "فاصله",
            "مسیر ${decision.recommendedIndex + 1}" to "پیشنهاد"
        )
    )
    SmartInfoCard("تصمیم حالت عجله", decision.reasonFa, NvColors.Warning)
    Button(
        onClick = { viewModel.selectRoute(decision.recommendedIndex) },
        enabled = decision.recommendedIndex != state.selectedRouteIndex,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Rounded.ElectricBolt, contentDescription = null)
        Spacer(Modifier.size(NvSpacing.Xs))
        Text(if (decision.recommendedIndex == state.selectedRouteIndex) "سریع‌ترین مسیر انتخاب شده" else "انتخاب سریع‌ترین مسیر")
    }
}

@Composable
private fun MultimodalScreen(state: NvUiState, engine: SmartMobilityEngine) {
    val plan = remember(state.route) { engine.fallbackMultimodal(state.route) }
    if (!plan.available) {
        SmartInfoCard("مسیر چندحالته آماده نیست", plan.warningFa.orEmpty(), NvColors.Warning)
        return
    }
    plan.legs.forEach { leg ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = NvColors.Navy850,
            shape = RoundedCornerShape(NvRadius.Card),
            border = BorderStroke(1.dp, NvColors.DividerDark)
        ) {
            Row(Modifier.padding(NvSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                Icon(modeIcon(leg.mode), contentDescription = leg.mode.titleFa, tint = NvColors.Success)
                Spacer(Modifier.size(NvSpacing.Sm))
                Column(Modifier.weight(1f)) {
                    Text(leg.titleFa, fontWeight = FontWeight.Bold)
                    Text(
                        "${ceil(leg.travelSeconds / 60.0).toInt()} دقیقه • %.1f km • ${if (leg.live) "زنده" else "برآورد"}".format(leg.distanceMeters / 1000.0),
                        color = NvColors.TextSecondaryDark,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
    plan.warningFa?.let { SmartInfoCard("محدودیت provider", it, NvColors.Warning) }
}

@Composable
private fun StationTransferScreen(engine: SmartMobilityEngine) {
    val availability = engine.transitAvailability()
    SmartInfoCard(
        "تعویض هوشمند ایستگاه",
        if (availability.available) "فید حمل‌ونقل عمومی فعال است و تعویض ایستگاه می‌تواند با زمان زنده رتبه‌بندی شود" else availability.messageFa,
        if (availability.available) NvColors.Success else NvColors.Warning
    )
    Text("Adapter: ${availability.source}", color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun LiveMetroScreen(engine: SmartMobilityEngine) {
    val availability = engine.transitAvailability()
    val statuses = remember { engine.metroStatus() }
    if (!availability.available) {
        SmartInfoCard("متروی زنده در دسترس نیست", availability.messageFa, NvColors.Warning)
        return
    }
    if (statuses.isEmpty()) {
        SmartInfoCard("داده‌ای دریافت نشد", "provider فعال است اما وضعیت ایستگاه قابل دریافت نیست", NvColors.Warning)
        return
    }
    statuses.forEach { status ->
        SmartInfoCard(
            status.stationName,
            "${status.lineName} • ${status.nextArrivalMinutes?.let { "$it دقیقه تا قطار" } ?: "زمان نامشخص"} • ${if (status.live) "زنده" else "برآورد"}",
            NvColors.Success
        )
    }
}

@Composable
private fun TaxiCoordinationScreen(state: NvUiState, engine: SmartMobilityEngine) {
    val availability = engine.taxiAvailability()
    val route = state.route
    val estimate = remember(route, availability.available) {
        route?.let { engine.taxiEstimate(it.distanceMeters) }
    }
    if (!availability.available) {
        SmartInfoCard("رزرو تاکسی غیرفعال", availability.messageFa, NvColors.Warning)
        Text("Fallback: اطلاعات مسیر حفظ می‌شود و هیچ رزرو یا قیمت زنده جعلی نمایش داده نمی‌شود", color = NvColors.TextSecondaryDark)
        return
    }
    if (estimate == null) {
        SmartInfoCard("برآورد تاکسی دریافت نشد", "provider پاسخ معتبر نداد", NvColors.Warning)
        return
    }
    SmartMetricRow(
        listOf(
            "${estimate.etaMinutes} دقیقه" to "رسیدن تاکسی",
            (estimate.fareMin?.toString() ?: "—") to "حداقل کرایه",
            (estimate.fareMax?.toString() ?: "—") to "حداکثر کرایه"
        )
    )
    SmartInfoCard("منبع", estimate.source, if (estimate.bookable) NvColors.Success else NvColors.Warning)
}

@Composable
private fun EtaConfidenceScreen(state: NvUiState, engine: SmartMobilityEngine) {
    val route = state.route
    if (route == null) {
        SmartInfoCard("ETA آماده نیست", "برای برآورد اطمینان زمان رسیدن ابتدا مسیر بسازید", NvColors.Warning)
        return
    }
    val result = remember(route, state.traffic, state.currentLocation, state.onlineAvailable) {
        engine.etaConfidence(
            route = route,
            liveTrafficDelaySeconds = state.traffic?.delaySeconds,
            locationActive = state.currentLocation != null,
            onlineAvailable = state.onlineAvailable
        )
    }
    val percent = (result.confidence * 100.0).roundToInt()
    val uncertaintyMinutes = ceil(result.uncertaintySeconds / 60.0).toInt()
    SmartMetricRow(
        listOf(
            "${ceil(result.etaSeconds / 60.0).toInt()} دقیقه" to "ETA",
            "$percent٪" to "اطمینان",
            "±$uncertaintyMinutes دقیقه" to "بازه"
        )
    )
    LinearProgressIndicator(progress = { result.confidence.toFloat() }, modifier = Modifier.fillMaxWidth())
    SmartInfoCard(result.labelFa, "اطمینان از GPS، ترافیک زنده و ساختار مسیر محاسبه شده است", if (percent >= 82) NvColors.Success else NvColors.Info)
}

@Composable
private fun TimeCostScreen(
    state: NvUiState,
    engine: SmartMobilityEngine,
    fuelPrice: String,
    onFuelPriceChange: (String) -> Unit
) {
    val route = state.route
    if (route == null) {
        SmartInfoCard("زمان و هزینه آماده نیست", "ابتدا مسیر بسازید", NvColors.Warning)
        return
    }
    OutlinedTextField(
        value = fuelPrice,
        onValueChange = onFuelPriceChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("قیمت هر لیتر سوخت (اختیاری)") },
        supportingText = { Text("پروفایل محلی فعلاً مصرف ۸ لیتر در ۱۰۰ کیلومتر را مبنا می‌گیرد") },
        singleLine = true
    )
    val price = fuelPrice.toLongOrNull()
    val estimate = remember(route, price) { engine.timeAndCost(route, fuelPricePerLiter = price, currency = if (price != null) "واحد پولی تنظیم‌شده" else null) }
    SmartMetricRow(
        listOf(
            "${ceil(estimate.travelSeconds / 60.0).toInt()} دقیقه" to "زمان",
            "%.1f km".format(estimate.distanceMeters / 1000.0) to "فاصله",
            "%.1f L".format(estimate.fuelLiters) to "سوخت"
        )
    )
    SmartInfoCard(
        "هزینه پولی",
        estimate.monetaryCost?.let { "$it ${estimate.currency.orEmpty()}" } ?: estimate.warningFa.orEmpty(),
        if (estimate.monetaryCost != null) NvColors.Success else NvColors.Warning
    )
}

@Composable
private fun WalkingScreen(state: NvUiState, engine: SmartMobilityEngine) {
    val route = state.route
    if (route == null) {
        SmartInfoCard("مسیریابی پیاده آماده نیست", "ابتدا مقصد و مسیر را مشخص کنید", NvColors.Warning)
        return
    }
    val estimate = remember(route) { engine.walking(route.distanceMeters) }
    SmartMetricRow(
        listOf(
            "${ceil(estimate.travelSeconds / 60.0).toInt()} دقیقه" to "زمان پیاده",
            "%.1f km".format(estimate.distanceMeters / 1000.0) to "فاصله",
            "${(estimate.confidence * 100).roundToInt()}٪" to "اطمینان"
        )
    )
    SmartInfoCard(
        "برآورد محلی، نه مسیر عابر دقیق",
        "provider اختصاصی walking هنوز به موتور route متصل نشده است؛ بنابراین این صفحه زمان پیاده‌روی را از فاصله مسیر فعلی تخمین می‌زند و آن را زنده/دقیق برچسب نمی‌زند",
        NvColors.Warning
    )
}

@Composable
private fun SmartPreferencesScreen(
    state: NvUiState,
    viewModel: NvViewModel,
    lowData: Boolean,
    privacyMode: Boolean,
    ecoPriority: Boolean,
    onLowData: (Boolean) -> Unit,
    onPrivacyMode: (Boolean) -> Unit,
    onEcoPriority: (Boolean) -> Unit
) {
    PreferenceSwitch("اولویت آفلاین", "در صورت نصب بسته، نقشه و مسیر آفلاین را ترجیح می‌دهد", state.preferOffline) {
        viewModel.setPreferOffline(it)
    }
    PreferenceSwitch("مصرف دیتای کمتر", "ترجیح محلی برای اجتناب از درخواست‌های غیرضروری در ابزارهای هوشمند", lowData, onLowData)
    PreferenceSwitch("حریم خصوصی سخت‌گیرانه", "هیچ داده‌ای از این مرکز به provider خارجی ارسال نمی‌شود مگر provider صریحاً فعال باشد", privacyMode, onPrivacyMode)
    PreferenceSwitch("اولویت اقتصادی", "برای رتبه‌بندی هوشمند آینده ذخیره می‌شود؛ موتور route فعلی همچنان منبع حقیقت مسیر است", ecoPriority, onEcoPriority)
    if (!state.offlineReady) {
        SmartInfoCard("بسته آفلاین نصب نیست", "برای فعال‌کردن اولویت آفلاین، بسته نقشه را از تنظیمات اصلی دانلود کنید", NvColors.Warning)
    }
}

@Composable
private fun PreferenceSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SmartInfoCard(title: String, message: String, accent: androidx.compose.ui.graphics.Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NvColors.Navy850,
        shape = RoundedCornerShape(NvRadius.Card),
        border = BorderStroke(1.dp, accent.copy(alpha = .55f))
    ) {
        Column(Modifier.padding(NvSpacing.Md), verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
            Text(title, color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Black)
            Text(message, color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SmartMetricRow(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
        items.forEach { (value, label) ->
            Surface(
                modifier = Modifier.weight(1f),
                color = NvColors.Navy850,
                shape = RoundedCornerShape(NvRadius.Medium),
                border = BorderStroke(1.dp, NvColors.DividerDark)
            ) {
                Column(Modifier.padding(NvSpacing.Sm), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(label, color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun featureIcon(feature: SmartFeatureScreen): ImageVector = when (feature) {
    SmartFeatureScreen.CHAT -> Icons.Rounded.AutoAwesome
    SmartFeatureScreen.RUSH -> Icons.Rounded.ElectricBolt
    SmartFeatureScreen.MULTIMODAL -> Icons.Rounded.AltRoute
    SmartFeatureScreen.STATION_TRANSFER -> Icons.Rounded.SwapHoriz
    SmartFeatureScreen.LIVE_METRO -> Icons.Rounded.Subway
    SmartFeatureScreen.TAXI -> Icons.Rounded.LocalTaxi
    SmartFeatureScreen.ETA_CONFIDENCE -> Icons.Rounded.AccessTime
    SmartFeatureScreen.TIME_COST -> Icons.Rounded.DirectionsCar
    SmartFeatureScreen.WALKING -> Icons.Rounded.DirectionsWalk
    SmartFeatureScreen.PREFERENCES -> Icons.Rounded.Settings
}

private fun featureColor(feature: SmartFeatureScreen) = when (feature) {
    SmartFeatureScreen.RUSH -> NvColors.Warning
    SmartFeatureScreen.LIVE_METRO, SmartFeatureScreen.MULTIMODAL, SmartFeatureScreen.STATION_TRANSFER -> NvColors.Info
    SmartFeatureScreen.TAXI -> NvColors.Success
    else -> NvColors.RouteBlue
}

private fun featureSubtitle(feature: SmartFeatureScreen): String = when (feature) {
    SmartFeatureScreen.CHAT -> "درک محلی درخواست و هدایت به ابزار مناسب"
    SmartFeatureScreen.RUSH -> "انتخاب سریع‌ترین alternative واقعی"
    SmartFeatureScreen.MULTIMODAL -> "پیاده، حمل‌ونقل عمومی و تاکسی با fallback"
    SmartFeatureScreen.STATION_TRANSFER -> "آماده اتصال GTFS/GTFS-RT"
    SmartFeatureScreen.LIVE_METRO -> "نمایش live فقط از feed واقعی"
    SmartFeatureScreen.TAXI -> "Adapter قیمت/رزرو با حالت unavailable"
    SmartFeatureScreen.ETA_CONFIDENCE -> "ETA به‌همراه confidence و بازه خطا"
    SmartFeatureScreen.TIME_COST -> "زمان، فاصله، مصرف و هزینه تنظیم‌پذیر"
    SmartFeatureScreen.WALKING -> "برآورد پیاده‌روی با اعلام سطح دقت"
    SmartFeatureScreen.PREFERENCES -> "آفلاین، دیتا، حریم خصوصی و اقتصاد"
}

private fun modeIcon(mode: ir.nv.navigation.smart.MobilityMode): ImageVector = when (mode) {
    ir.nv.navigation.smart.MobilityMode.WALK -> Icons.Rounded.DirectionsWalk
    ir.nv.navigation.smart.MobilityMode.METRO -> Icons.Rounded.Subway
    ir.nv.navigation.smart.MobilityMode.BUS, ir.nv.navigation.smart.MobilityMode.BRT -> Icons.Rounded.DirectionsBus
    ir.nv.navigation.smart.MobilityMode.TAXI -> Icons.Rounded.LocalTaxi
    ir.nv.navigation.smart.MobilityMode.BIKE, ir.nv.navigation.smart.MobilityMode.SCOOTER -> Icons.Rounded.DirectionsWalk
}

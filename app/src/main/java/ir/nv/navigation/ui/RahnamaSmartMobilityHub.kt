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
import androidx.compose.material.icons.rounded.LocalParking
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
import ir.nv.navigation.navigation.EvRoutePreferences
import ir.nv.navigation.navigation.RouteProfile
import ir.nv.navigation.navigation.TruckRestrictions
import ir.nv.navigation.navigation.VehicleProfile
import ir.nv.navigation.smart.SmartMobilityEngine
import ir.nv.navigation.smart.TravelPreferences
import ir.nv.navigation.smart.Urgency
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
    var ecoPriority by remember { mutableStateOf(preferences.getBoolean("eco_priority", false)) }
    var urgency by remember {
        mutableStateOf(runCatching { Urgency.valueOf(preferences.getString("urgency", Urgency.NORMAL.name) ?: Urgency.NORMAL.name) }.getOrDefault(Urgency.NORMAL))
    }
    var maxWalkingMeters by remember { mutableStateOf(preferences.getInt("max_walking_meters", 1500)) }
    var maxTransfers by remember { mutableStateOf(preferences.getInt("max_transfers", 2)) }
    var avoidCrowding by remember { mutableStateOf(preferences.getBoolean("avoid_crowding", false)) }
    var accessibilityRequired by remember { mutableStateOf(preferences.getBoolean("accessibility_required", false)) }
    var weatherSensitive by remember { mutableStateOf(preferences.getBoolean("weather_sensitive", false)) }
    var fuelPrice by remember { mutableStateOf(preferences.getString("fuel_price", "").orEmpty()) }
    val travelPreferences = remember(urgency, maxWalkingMeters, maxTransfers, avoidCrowding, accessibilityRequired, weatherSensitive) {
        TravelPreferences(
            urgency = urgency,
            maxWalkingMeters = maxWalkingMeters,
            maxTransfers = maxTransfers,
            avoidCrowding = avoidCrowding,
            accessibilityRequired = accessibilityRequired,
            weatherSensitive = weatherSensitive
        ).normalized()
    }

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
                SmartFeatureScreen.CHAT -> SmartChatScreen(state, engine, viewModel) { target -> screen = target }
                SmartFeatureScreen.RUSH -> RushModeScreen(state, engine, viewModel)
                SmartFeatureScreen.MULTIMODAL -> MultimodalScreen(state, engine, travelPreferences)
                SmartFeatureScreen.STATION_TRANSFER -> StationTransferScreen(engine, travelPreferences)
                SmartFeatureScreen.LIVE_METRO -> LiveMetroScreen(engine)
                SmartFeatureScreen.TAXI -> TaxiCoordinationScreen(state, engine)
                SmartFeatureScreen.ETA_CONFIDENCE -> EtaConfidenceScreen(state, engine)
                SmartFeatureScreen.TIME_COST -> TimeCostScreen(state, engine, fuelPrice) { value ->
                    fuelPrice = value.filter(Char::isDigit)
                    preferences.edit().putString("fuel_price", fuelPrice).apply()
                }
                SmartFeatureScreen.WALKING -> WalkingScreen(state, engine, viewModel)
                SmartFeatureScreen.PARKING -> ParkingScreen(state, viewModel)
                SmartFeatureScreen.PREFERENCES -> SmartPreferencesScreen(
                    state = state,
                    viewModel = viewModel,
                    lowData = lowData,
                    privacyMode = state.privacySettings.strictMode,
                    ecoPriority = ecoPriority,
                    travelPreferences = travelPreferences,
                    onUrgency = { value ->
                        urgency = value
                        preferences.edit().putString("urgency", value.name).apply()
                    },
                    onMaxWalkingMeters = { value ->
                        maxWalkingMeters = value
                        preferences.edit().putInt("max_walking_meters", value).apply()
                    },
                    onMaxTransfers = { value ->
                        maxTransfers = value
                        preferences.edit().putInt("max_transfers", value).apply()
                    },
                    onAvoidCrowding = { value ->
                        avoidCrowding = value
                        preferences.edit().putBoolean("avoid_crowding", value).apply()
                    },
                    onAccessibilityRequired = { value ->
                        accessibilityRequired = value
                        preferences.edit().putBoolean("accessibility_required", value).apply()
                    },
                    onWeatherSensitive = { value ->
                        weatherSensitive = value
                        preferences.edit().putBoolean("weather_sensitive", value).apply()
                    },
                    onLowData = {
                        lowData = it
                        preferences.edit().putBoolean("low_data", it).apply()
                    },
                    onPrivacyMode = {
                        viewModel.setStrictPrivacy(it)
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
    viewModel: NvViewModel,
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
    Button(
        onClick = {
            submittedQuery = query
            viewModel.planJourneyFromChat(query)
        },
        enabled = query.isNotBlank() && !state.smartJourneyPlanning,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
        Spacer(Modifier.size(NvSpacing.Xs))
        Text(if (state.smartJourneyPlanning) "در حال ساخت مسیر…" else "تشخیص مقصد و ساخت مسیر از موقعیت من")
    }
    if (state.smartJourneyPlanning) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.smartJourneyStatus?.let { SmartInfoCard("دستیار مسیر", it, NvColors.RouteBlue) }
    if (submittedQuery.isNotBlank() && state.smartJourneyStatus == null) {
        SmartInfoCard(reply.titleFa, reply.messageFa, NvColors.RouteBlue)
    }
    if (state.routeAlternatives.isNotEmpty()) {
        Text("مسیرهای قابل انتخاب", fontWeight = FontWeight.Bold)
        state.routeAlternatives.take(4).forEachIndexed { index, route ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.selectRoute(index) },
                color = NvColors.Navy850,
                shape = RoundedCornerShape(NvRadius.Medium),
                border = BorderStroke(1.dp, if (index == state.selectedRouteIndex) NvColors.Success else NvColors.DividerDark)
            ) {
                Row(Modifier.padding(NvSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("مسیر ${index + 1}", fontWeight = FontWeight.Black)
                        Text(
                            "${ceil(route.travelSeconds / 60.0).toInt()} دقیقه • %.1f km".format(route.distanceMeters / 1000.0),
                            color = NvColors.TextSecondaryDark,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(if (index == state.selectedRouteIndex) "انتخاب‌شده" else "انتخاب", color = NvColors.RouteBlue)
                }
            }
        }
    }
    state.route?.let { route ->
        val autoPlan = remember(route, submittedQuery) { engine.chatJourneyPlan(route, submittedQuery) }
        Text("برنامه پیشنهادی دستیار", fontWeight = FontWeight.Bold)
        autoPlan.legs.forEachIndexed { index, leg ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NvColors.Navy850,
                shape = RoundedCornerShape(NvRadius.Medium),
                border = BorderStroke(1.dp, NvColors.DividerDark)
            ) {
                Row(Modifier.padding(NvSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(modeIcon(leg.mode), contentDescription = null, tint = NvColors.Success)
                    Spacer(Modifier.size(NvSpacing.Sm))
                    Column(Modifier.weight(1f)) {
                        Text("${index + 1}. ${leg.titleFa}", fontWeight = FontWeight.Bold)
                        Text(
                            "${ceil(leg.travelSeconds / 60.0).toInt()} دقیقه • %.1f km • ${if (leg.live) "زنده" else "برآورد"}".format(leg.distanceMeters / 1000.0),
                            color = NvColors.TextSecondaryDark,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        autoPlan.warningFa?.let { SmartInfoCard("وضعیت سرویس‌ها", it, NvColors.Warning) }
        Button(onClick = viewModel::startNavigation, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
            Spacer(Modifier.size(NvSpacing.Xs))
            Text("شروع راهنمایی مسیر")
        }
    }
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
private fun MultimodalScreen(state: NvUiState, engine: SmartMobilityEngine, preferences: TravelPreferences) {
    val plans = remember(state.route, preferences) { engine.multimodalCandidates(state.route, preferences) }
    if (state.route == null) {
        SmartInfoCard("مسیر چندحالته آماده نیست", "ابتدا مقصد و مسیر را مشخص کنید", NvColors.Warning)
        return
    }
    if (plans.isEmpty()) {
        SmartInfoCard(
            "گزینه چندحالته معتبر پیدا نشد",
            "حداکثر پیاده‌روی/تعداد تعویض یا providerهای مترو و تاکسی اجازه ساخت گزینه معتبر نمی‌دهند. ${engine.transitAvailability().messageFa}؛ ${engine.taxiAvailability().messageFa}",
            NvColors.Warning
        )
        return
    }
    val plan = plans.first()
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
private fun StationTransferScreen(engine: SmartMobilityEngine, preferences: TravelPreferences) {
    val availability = engine.transitAvailability()
    SmartInfoCard(
        "تعویض هوشمند ایستگاه",
        if (availability.available) "فید حمل‌ونقل عمومی فعال است و تعویض ایستگاه می‌تواند با زمان زنده رتبه‌بندی شود" else availability.messageFa,
        if (availability.available) NvColors.Success else NvColors.Warning
    )
    Text(
        "Adapter: ${availability.source} • حداکثر پیاده ${preferences.maxWalkingMeters} متر • حداکثر ${preferences.maxTransfers} تعویض",
        color = NvColors.TextSecondaryDark,
        style = MaterialTheme.typography.labelSmall
    )
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
    val risk = remember(result, state.traffic) { engine.etaRisk(result, state.traffic?.delaySeconds) }
    SmartInfoCard(result.labelFa, "اطمینان از GPS، ترافیک زنده و ساختار مسیر محاسبه شده است", if (percent >= 82) NvColors.Success else NvColors.Info)
    SmartInfoCard("ریسک ETA: ${risk.level.titleFa}", risk.reasonFa, if (risk.riskScore < .25) NvColors.Success else NvColors.Warning)
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
private fun WalkingScreen(state: NvUiState, engine: SmartMobilityEngine, viewModel: NvViewModel) {
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
        "برآورد محلی",
        "برای مسیر واقعی پیاده، پروفایل Walking به موتور route ارسال می‌شود؛ برآورد بالا فقط مقایسه سریع است",
        NvColors.Warning
    )
    Button(
        onClick = {
            viewModel.setVehicleProfile(VehicleProfile.WALKING)
            viewModel.calculateRoute()
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("محاسبه مسیر واقعی پیاده") }
}

@Composable
private fun ParkingScreen(state: NvUiState, viewModel: NvViewModel) {
    if (state.destination == null && state.parkingFinalDestination == null) {
        SmartInfoCard("مقصد مشخص نیست", "ابتدا مقصد نهایی را انتخاب کنید", NvColors.Warning)
        return
    }
    if (state.parkingHandoffAvailable) {
        SmartInfoCard(
            "به پارکینگ رسیدید",
            state.parkingFinalDestination?.let { "ادامه پیاده تا ${it.name}" } ?: "ادامه پیاده تا مقصد",
            NvColors.Success
        )
        Button(onClick = viewModel::continueWalkingAfterParking, modifier = Modifier.fillMaxWidth()) {
            Text("شروع ادامه مسیر پیاده")
        }
        OutlinedButton(onClick = viewModel::cancelParkingHandoff, modifier = Modifier.fillMaxWidth()) {
            Text("لغو ادامه پیاده")
        }
        return
    }
    Button(
        onClick = { viewModel.searchParkingNearDestination() },
        enabled = !state.parkingSearchLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Rounded.LocalParking, contentDescription = null)
        Spacer(Modifier.size(NvSpacing.Xs))
        Text("جستجوی پارکینگ نزدیک مقصد")
    }
    if (state.parkingSearchLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    state.parkingOptions.forEach { option ->
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.routeToParking(option) },
            color = NvColors.Navy850,
            shape = RoundedCornerShape(NvRadius.Medium),
            border = BorderStroke(1.dp, NvColors.DividerDark)
        ) {
            Row(Modifier.padding(NvSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocalParking, contentDescription = null, tint = NvColors.RouteBlue)
                Spacer(Modifier.size(NvSpacing.Sm))
                Column(Modifier.weight(1f)) {
                    Text(option.place.name, fontWeight = FontWeight.Bold)
                    val openText = when (option.place.isOpen) { true -> "باز"; false -> "بسته"; null -> "وضعیت نامشخص" }
                    Text(
                        "${option.walkingDistanceMeters.roundToInt()} متر پیاده • $openText",
                        color = NvColors.TextSecondaryDark,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text("مسیر", color = NvColors.RouteBlue)
            }
        }
    }
    if (state.parkingOptions.isNotEmpty()) {
        Text(
            "availability و قیمت فقط در صورت ارائه provider نمایش داده می‌شوند و در نبود داده جعل نمی‌شوند",
            color = NvColors.TextSecondaryDark,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun SmartPreferencesScreen(
    state: NvUiState,
    viewModel: NvViewModel,
    lowData: Boolean,
    privacyMode: Boolean,
    ecoPriority: Boolean,
    travelPreferences: TravelPreferences,
    onUrgency: (Urgency) -> Unit,
    onMaxWalkingMeters: (Int) -> Unit,
    onMaxTransfers: (Int) -> Unit,
    onAvoidCrowding: (Boolean) -> Unit,
    onAccessibilityRequired: (Boolean) -> Unit,
    onWeatherSensitive: (Boolean) -> Unit,
    onLowData: (Boolean) -> Unit,
    onPrivacyMode: (Boolean) -> Unit,
    onEcoPriority: (Boolean) -> Unit
 ) {
    Text("ترجیحات سفر", fontWeight = FontWeight.Black)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
        Urgency.entries.forEach { item ->
            FilterChip(
                selected = travelPreferences.urgency == item,
                onClick = { onUrgency(item) },
                label = { Text(urgencyTitle(item)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
    Text("حداکثر پیاده‌روی: ${travelPreferences.maxWalkingMeters} متر", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
        listOf(500, 1000, 1500, 3000).forEach { meters ->
            FilterChip(
                selected = travelPreferences.maxWalkingMeters == meters,
                onClick = { onMaxWalkingMeters(meters) },
                label = { Text(if (meters >= 1000) "${meters/1000}km" else "${meters}m") },
                modifier = Modifier.weight(1f)
            )
        }
    }
    Text("حداکثر تعویض: ${travelPreferences.maxTransfers}", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
        (0..3).forEach { transfers ->
            FilterChip(
                selected = travelPreferences.maxTransfers == transfers,
                onClick = { onMaxTransfers(transfers) },
                label = { Text(transfers.toString()) },
                modifier = Modifier.weight(1f)
            )
        }
    }
    PreferenceSwitch("پرهیز از شلوغی", "در رتبه‌بندی transfer، crowding شناخته‌شده جریمه می‌شود", travelPreferences.avoidCrowding, onAvoidCrowding)
    PreferenceSwitch("دسترسی‌پذیری الزامی", "گزینه‌های شناخته‌شده غیرقابل‌دسترس حذف می‌شوند", travelPreferences.accessibilityRequired, onAccessibilityRequired)
    PreferenceSwitch("حساس به آب‌وهوا", "برای تصمیم‌های چندحالته و پیشنهادها ذخیره می‌شود", travelPreferences.weatherSensitive, onWeatherSensitive)
    Text("پروفایل مسیر", fontWeight = FontWeight.Black)
    RouteProfile.entries.filter { it != RouteProfile.CUSTOM }.chunked(3).forEach { profiles ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
            profiles.forEach { profile ->
                FilterChip(
                    selected = state.routeProfile == profile,
                    onClick = { viewModel.setRouteProfile(profile) },
                    label = { Text(routeProfileTitle(profile), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(3 - profiles.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Text("وسیله نقلیه", fontWeight = FontWeight.Black)
    VehicleProfile.entries.chunked(3).forEach { vehicles ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
            vehicles.forEach { vehicle ->
                FilterChip(
                    selected = state.vehicleProfile == vehicle,
                    onClick = { viewModel.setVehicleProfile(vehicle) },
                    label = { Text(vehicleProfileTitle(vehicle), maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(3 - vehicles.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    when (state.vehicleProfile) {
        VehicleProfile.TRUCK -> TruckPreferencesEditor(state.truckRestrictions, viewModel)
        VehicleProfile.EV -> EvPreferencesEditor(state.evRoutePreferences, viewModel)
        else -> Unit
    }
    if (state.origin != null && state.destination != null) {
        Button(onClick = viewModel::calculateRoute, modifier = Modifier.fillMaxWidth()) { Text("محاسبه مسیر با تنظیمات فعلی") }
    }
    PreferenceSwitch("اولویت آفلاین", "در صورت نصب بسته، نقشه و مسیر آفلاین را ترجیح می‌دهد", state.preferOffline) {
        viewModel.setPreferOffline(it)
    }
    PreferenceSwitch("مصرف دیتای کمتر", "ترجیح محلی برای اجتناب از درخواست‌های غیرضروری در ابزارهای هوشمند", lowData, onLowData)
    PreferenceSwitch("حریم خصوصی سخت‌گیرانه", "قابلیت‌های اختیاری شبکه بدون رضایت صریح فعال نمی‌شوند", privacyMode, onPrivacyMode)
    PreferenceSwitch("ارسال گزارش‌های مشارکتی", "گزارش محلی همیشه ممکن است؛ ارسال شبکه فقط با این رضایت و endpoint HTTPS", state.privacySettings.communityUploads) {
        viewModel.setCommunityUploads(it)
    }
    PreferenceSwitch("همگام‌سازی ابری", "رضایت استفاده از Cloud Sync؛ خاموش به‌صورت پیش‌فرض", state.privacySettings.cloudSync) {
        viewModel.setCloudSyncConsent(it)
    }
    PreferenceSwitch("تاریخچه موقعیت", "فقط رضایت را ذخیره می‌کند؛ جمع‌آوری تاریخچه در این نسخه پیاده نشده است", state.privacySettings.locationHistory) {
        viewModel.setLocationHistoryConsent(it)
    }
    PreferenceSwitch("تحلیل استفاده", "فقط رضایت را ذخیره می‌کند؛ SDK تحلیلی در این نسخه وجود ندارد", state.privacySettings.analytics) {
        viewModel.setAnalyticsConsent(it)
    }
    PreferenceSwitch("اولویت اقتصادی", "برای رتبه‌بندی هوشمند آینده ذخیره می‌شود؛ موتور route فعلی همچنان منبع حقیقت مسیر است", ecoPriority, onEcoPriority)
    if (!state.offlineReady) {
        SmartInfoCard("بسته آفلاین نصب نیست", "برای فعال‌کردن اولویت آفلاین، بسته نقشه را از تنظیمات اصلی دانلود کنید", NvColors.Warning)
    }
}

@Composable
private fun TruckPreferencesEditor(value: TruckRestrictions, viewModel: NvViewModel) {
    Text("محدودیت‌های کامیون", fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
        NumericPreferenceField("ارتفاع m", value.heightMeters) { viewModel.updateTruckRestrictions(value.copy(heightMeters = it)) }
        NumericPreferenceField("وزن t", value.weightTons) { viewModel.updateTruckRestrictions(value.copy(weightTons = it)) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
        NumericPreferenceField("عرض m", value.widthMeters) { viewModel.updateTruckRestrictions(value.copy(widthMeters = it)) }
        NumericPreferenceField("طول m", value.lengthMeters) { viewModel.updateTruckRestrictions(value.copy(lengthMeters = it)) }
    }
    PreferenceSwitch("محموله خطرناک", "محدودیت hazmat به provider سازگار ارسال می‌شود", value.hazardousCargo) {
        viewModel.updateTruckRestrictions(value.copy(hazardousCargo = it))
    }
}

@Composable
private fun EvPreferencesEditor(value: EvRoutePreferences, viewModel: NvViewModel) {
    Text("تنظیمات خودرو برقی", fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
        NumericPreferenceField("باتری %", value.batteryPercent.toDouble()) {
            viewModel.updateEvRoutePreferences(value.copy(batteryPercent = (it ?: value.batteryPercent.toDouble()).roundToInt()))
        }
        NumericPreferenceField("برد km", value.estimatedRangeKm) {
            viewModel.updateEvRoutePreferences(value.copy(estimatedRangeKm = it))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
        NumericPreferenceField("مصرف Wh/km", value.consumptionWhPerKm) {
            viewModel.updateEvRoutePreferences(value.copy(consumptionWhPerKm = it))
        }
        NumericPreferenceField("حداقل مقصد %", value.minimumArrivalBatteryPercent.toDouble()) {
            viewModel.updateEvRoutePreferences(value.copy(minimumArrivalBatteryPercent = (it ?: value.minimumArrivalBatteryPercent.toDouble()).roundToInt()))
        }
    }
    var connectors by remember(value.connectorTypes) { mutableStateOf(value.connectorTypes.joinToString(",")) }
    OutlinedTextField(
        value = connectors,
        onValueChange = { connectors = it },
        label = { Text("کانکتورها (مثلاً CCS2,Type2)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedButton(
        onClick = {
            viewModel.updateEvRoutePreferences(value.copy(connectorTypes = connectors.split(',').map(String::trim).filter(String::isNotEmpty).toSet()))
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("ثبت کانکتورها") }
}

@Composable
private fun NumericPreferenceField(label: String, value: Double?, onChange: (Double?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter { it.isDigit() || it == '.' }
            onChange(text.toDoubleOrNull())
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(0.5f),
        singleLine = true
    )
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
    SmartFeatureScreen.PARKING -> Icons.Rounded.LocalParking
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
    SmartFeatureScreen.WALKING -> "برآورد و مسیر واقعی با پروفایل Walking"
    SmartFeatureScreen.PARKING -> "پارک نزدیک مقصد و ادامه مسیر پیاده"
    SmartFeatureScreen.PREFERENCES -> "وسیله، پروفایل مسیر، آفلاین و حریم خصوصی"
}

private fun urgencyTitle(value: Urgency): String = when (value) {
    Urgency.RELAXED -> "آرام"
    Urgency.NORMAL -> "عادی"
    Urgency.HURRY -> "عجله"
}

private fun routeProfileTitle(profile: RouteProfile): String = when (profile) {
    RouteProfile.FASTEST -> "سریع"
    RouteProfile.SHORTEST -> "کوتاه"
    RouteProfile.LOW_TRAFFIC -> "کم‌ترافیک"
    RouteProfile.ECO -> "اقتصادی"
    RouteProfile.SAFE -> "ایمن"
    RouteProfile.SCENIC -> "دیدنی"
    RouteProfile.AVOID_TOLL -> "بدون عوارض"
    RouteProfile.AVOID_HIGHWAY -> "بدون بزرگراه"
    RouteProfile.AVOID_FERRY -> "بدون فری"
    RouteProfile.CUSTOM -> "سفارشی"
    RouteProfile.SMART -> "هوشمند"
}

private fun vehicleProfileTitle(profile: VehicleProfile): String = when (profile) {
    VehicleProfile.CAR -> "خودرو"
    VehicleProfile.MOTORCYCLE -> "موتور"
    VehicleProfile.TRUCK -> "کامیون"
    VehicleProfile.EV -> "برقی"
    VehicleProfile.BICYCLE -> "دوچرخه"
    VehicleProfile.WALKING -> "پیاده"
    VehicleProfile.TRANSIT -> "عمومی"
}

private fun modeIcon(mode: ir.nv.navigation.smart.MobilityMode): ImageVector = when (mode) {
    ir.nv.navigation.smart.MobilityMode.WALK -> Icons.Rounded.DirectionsWalk
    ir.nv.navigation.smart.MobilityMode.METRO -> Icons.Rounded.Subway
    ir.nv.navigation.smart.MobilityMode.BUS, ir.nv.navigation.smart.MobilityMode.BRT -> Icons.Rounded.DirectionsBus
    ir.nv.navigation.smart.MobilityMode.TAXI -> Icons.Rounded.LocalTaxi
    ir.nv.navigation.smart.MobilityMode.BIKE, ir.nv.navigation.smart.MobilityMode.SCOOTER -> Icons.Rounded.DirectionsWalk
}

from pathlib import Path

root = Path('.')


def replace(rel: str, old: str, new: str) -> None:
    path = root / rel
    text = path.read_text(encoding='utf-8')
    if old not in text:
        raise RuntimeError(f'Hotfix pattern not found in {rel}: {old[:120]!r}')
    path.write_text(text.replace(old, new), encoding='utf-8')


# Version this as a distinct device-testable build.
replace(
    'app/build.gradle.kts',
    'versionCode = 24\n        versionName = "0.19.0-reference"',
    'versionCode = 25\n        versionName = "0.19.1-hotfix"',
)

# Precision-first location policy. A weak 10-12m home fix must not be presented as exact.
replace(
    'core/location/src/main/java/ir/nv/navigation/location/DeviceLocationProvider.kt',
    '''        const val LOCATION_COLLECTION_WINDOW_MS = 15_000L
        const val TARGET_ACCURACY_METERS = 5f
        const val EXCELLENT_ACCURACY_METERS = 3f
        const val ACCEPTABLE_LAST_KNOWN_ACCURACY_METERS = 6f
        const val MAX_CURRENT_LOCATION_ACCURACY_METERS = 10f
        const val GOOD_NAVIGATION_ACCURACY_METERS = 10f
        const val MAX_NAVIGATION_ACCURACY_METERS = 12f
        const val ABSOLUTE_MAX_ACCURACY_METERS = 35f''',
    '''        const val LOCATION_COLLECTION_WINDOW_MS = 20_000L
        const val TARGET_ACCURACY_METERS = 4f
        const val EXCELLENT_ACCURACY_METERS = 3f
        const val ACCEPTABLE_LAST_KNOWN_ACCURACY_METERS = 5f
        const val MAX_CURRENT_LOCATION_ACCURACY_METERS = 8f
        const val GOOD_NAVIGATION_ACCURACY_METERS = 8f
        const val MAX_NAVIGATION_ACCURACY_METERS = 10f
        const val ABSOLUTE_MAX_ACCURACY_METERS = 25f''',
)
replace(
    'app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt',
    'const val HOME_LOCATION_ACCURACY_METERS = 12f',
    'const val HOME_LOCATION_ACCURACY_METERS = 8f',
)
replace(
    'app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt',
    '        else -> "Location روشن است اما هنوز GPS به دقت مناسب نرسیده؛ چند لحظه صبر کنید یا نزدیک فضای باز بروید"',
    '        else -> "GPS روشن است اما هنوز به دقت دقیق (حداکثر ±8 متر) نرسیده؛ نقطه تقریبی نمایش داده نمی‌شود"',
)

# Give mobile geocoders enough time to answer and add a second independent OSM-name fallback.
replace(
    'app/src/main/java/ir/nv/navigation/search/HybridSearchEngine.kt',
    'const val ONLINE_SEARCH_BUDGET_MS = 3_000L',
    'const val ONLINE_SEARCH_BUDGET_MS = 9_000L',
)
replace(
    'app/src/main/java/ir/nv/navigation/online/OnlineNavigationService.kt',
    '''    private val searchClient = client.newBuilder()
        .connectTimeout(2_200, TimeUnit.MILLISECONDS)
        .readTimeout(2_600, TimeUnit.MILLISECONDS)
        .callTimeout(2_800, TimeUnit.MILLISECONDS)
        .build()''',
    '''    private val searchClient = client.newBuilder()
        .connectTimeout(4_500, TimeUnit.MILLISECONDS)
        .readTimeout(6_500, TimeUnit.MILLISECONDS)
        .callTimeout(7_500, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()''',
)
replace(
    'app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt',
    '            online = PlaceSearchProvider { query -> online.search(query) }',
    '''            online = PlaceSearchProvider { query ->
                val geocoder = runCatching { online.search(query) }
                val center = mutableState.value.currentLocation
                val nearby = if (center != null) {
                    runCatching { onlinePlaces.searchNamedNearby(center, query, radiusMeters = 100_000, limit = 30) }
                } else {
                    Result.success(emptyList())
                }
                val combined = combineSearchResults(
                    geocoder.getOrDefault(emptyList()) + nearby.getOrDefault(emptyList())
                )
                if (combined.isEmpty() && geocoder.isFailure && nearby.isFailure) {
                    throw geocoder.exceptionOrNull()
                        ?: nearby.exceptionOrNull()
                        ?: IllegalStateException("سرویس جست‌وجو پاسخ نداد")
                }
                combined
            }''',
)
replace(
    'app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt',
    '                "جست‌وجوی آنلاین پاسخ نداد؛ اتصال اینترنت را بررسی کنید"',
    '                "جست‌وجوی مقصد از چند سرویس پاسخ نگرفت؛ دوباره تلاش کنید یا نام مقصد را کوتاه‌تر بنویسید"',
)

# Stronger text and control contrast for the dark navigation UI.
replace(
    'app/src/main/java/ir/nv/navigation/ui/theme/NvDesignTokens.kt',
    'val TextSecondaryDark = Color(0xFFB6C6D8)',
    'val TextSecondaryDark = Color(0xFFDCE7F3)',
)
replace(
    'app/src/main/java/ir/nv/navigation/ui/theme/NvDesignTokens.kt',
    'val DividerDark = Color(0xFF24425F)',
    'val DividerDark = Color(0xFF476985)',
)
replace(
    'app/src/main/java/ir/nv/navigation/ui/theme/Theme.kt',
    'outline = NvColors.DividerDark',
    'outline = Color(0xFF8AA2B8)',
)

home = root / 'app/src/main/java/ir/nv/navigation/ui/RahnamaHome.kt'
home_text = home.read_text(encoding='utf-8')
home_text = home_text.replace(
    '''                            label = { Text("همه اطراف من") },
                            modifier = Modifier.weight(1f)''',
    '''                            label = { Text("همه اطراف من") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = NvColors.Navy850,
                                labelColor = NvColors.TextPrimaryDark,
                                selectedContainerColor = NvColors.RouteBlueStrong,
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true, selected = selectedRadiusKm == null,
                                borderColor = NvColors.TextSecondaryDark,
                                selectedBorderColor = NvColors.RouteBlue
                            ),
                            modifier = Modifier.weight(1f)''',
)
home_text = home_text.replace(
    '''                                label = { Text("$radiusKm کیلومتر") },
                                modifier = Modifier.weight(1f)''',
    '''                                label = { Text("$radiusKm کیلومتر") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = NvColors.Navy850,
                                    labelColor = NvColors.TextPrimaryDark,
                                    selectedContainerColor = NvColors.RouteBlueStrong,
                                    selectedLabelColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true, selected = selectedRadiusKm == radiusKm,
                                    borderColor = NvColors.TextSecondaryDark,
                                    selectedBorderColor = NvColors.RouteBlue
                                ),
                                modifier = Modifier.weight(1f)''',
)
home.write_text(home_text, encoding='utf-8')

# Make the smart station screen useful even before a GTFS-RT feed is configured.
replace(
    'app/src/main/java/ir/nv/navigation/places/NearbySearchPolicy.kt',
    '    EV("ev", "شارژ خودرو برقی", "شارژ خودرو", listOf("amenity:charging_station")),\n    PARKS',
    '    EV("ev", "شارژ خودرو برقی", "شارژ خودرو", listOf("amenity:charging_station")),\n    METRO("metro", "مترو", "مترو", listOf("railway:station", "public_transport:station")),\n    PARKS',
)

smart = root / 'app/src/main/java/ir/nv/navigation/ui/RahnamaSmartMobilityHub.kt'
smart_text = smart.read_text(encoding='utf-8')
smart_text = smart_text.replace(
    'import androidx.compose.runtime.setValue',
    'import androidx.compose.runtime.setValue\nimport androidx.compose.runtime.rememberCoroutineScope\nimport androidx.compose.runtime.LaunchedEffect',
)
smart_text = smart_text.replace(
    'import ir.nv.navigation.smart.Urgency',
    'import ir.nv.navigation.smart.Urgency\nimport ir.nv.navigation.places.NearbyCategory\nimport ir.nv.navigation.places.NearbyScope\nimport ir.nv.navigation.places.NearbySearchRequest\nimport kotlinx.coroutines.launch',
)
smart_text = smart_text.replace(
    '                SmartFeatureScreen.STATION_TRANSFER -> StationTransferScreen(engine, travelPreferences)',
    '                SmartFeatureScreen.STATION_TRANSFER -> StationTransferScreen(state, engine, travelPreferences, viewModel)',
)
old_station = '''@Composable
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
'''
new_station = '''@Composable
private fun StationTransferScreen(
    state: NvUiState,
    engine: SmartMobilityEngine,
    preferences: TravelPreferences,
    viewModel: NvViewModel
) {
    val availability = engine.transitAvailability()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var stations by remember { mutableStateOf(emptyList<ir.nv.navigation.core.Place>()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refreshStations() {
        if (state.currentLocation == null) {
            error = "برای پیشنهاد ایستگاه، ابتدا GPS دقیق را دریافت کنید"
            return
        }
        scope.launch {
            loading = true
            error = null
            runCatching {
                viewModel.discoverNearby(
                    NearbySearchRequest(
                        category = NearbyCategory.METRO,
                        scope = NearbyScope.AROUND_ME,
                        radiusMeters = 25_000,
                        limit = 12
                    )
                )
            }.onSuccess { stations = it }
                .onFailure { error = it.message ?: "جست‌وجوی ایستگاه مترو ناموفق بود" }
            loading = false
        }
    }

    LaunchedEffect(state.currentLocation) {
        if (state.currentLocation != null && stations.isEmpty()) refreshStations()
    }

    SmartInfoCard(
        "تعویض هوشمند ایستگاه",
        if (availability.available) {
            "فید حمل‌ونقل عمومی فعال است؛ ایستگاه‌ها و زمان زنده قابل رتبه‌بندی هستند"
        } else {
            "حتی بدون GTFS زنده، نزدیک‌ترین ایستگاه‌های واقعی از داده نقشه پیدا می‌شوند. زمان ورود قطار تا اتصال فید رسمی نمایش داده نمی‌شود."
        },
        if (availability.available) NvColors.Success else NvColors.Info
    )

    Button(onClick = { refreshStations() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.Subway, contentDescription = null)
        Spacer(Modifier.size(NvSpacing.Xs))
        Text(if (loading) "در حال یافتن ایستگاه…" else "یافتن نزدیک‌ترین ایستگاه‌های مترو")
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { SmartInfoCard("وضعیت جست‌وجو", it, NvColors.Warning) }
    stations.take(6).forEach { station ->
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.routeFromCurrentLocationTo(station, VehicleProfile.WALKING) },
            color = NvColors.Navy850,
            shape = RoundedCornerShape(NvRadius.Medium),
            border = BorderStroke(1.dp, NvColors.DividerDark)
        ) {
            Row(Modifier.padding(NvSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Subway, contentDescription = null, tint = NvColors.Success)
                Spacer(Modifier.size(NvSpacing.Sm))
                Column(Modifier.weight(1f)) {
                    Text(station.name, color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Bold)
                    val distance = station.distance
                    Text(
                        distance?.let { if (it >= 1000) "%.1f کیلومتر".format(it / 1000.0) else "${it.toInt()} متر" } ?: "ایستگاه مترو",
                        color = NvColors.TextSecondaryDark,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text("مسیر پیاده", color = NvColors.RouteBlue, fontWeight = FontWeight.Bold)
            }
        }
    }
    Text(
        "حداکثر پیاده ${preferences.maxWalkingMeters} متر • حداکثر ${preferences.maxTransfers} تعویض",
        color = NvColors.TextSecondaryDark,
        style = MaterialTheme.typography.labelMedium
    )
}
'''
if old_station not in smart_text:
    raise RuntimeError('StationTransferScreen pattern not found')
smart_text = smart_text.replace(old_station, new_station)
smart.write_text(smart_text, encoding='utf-8')

print('v0.19.1 hotfix applied')

package ir.nv.navigation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.Place
import ir.nv.navigation.online.OnlinePlacesService
import ir.nv.navigation.places.NearbyCategory
import ir.nv.navigation.places.NearbyPlaceProvider
import ir.nv.navigation.places.NearbyScope
import ir.nv.navigation.places.NearbySearchRequest
import ir.nv.navigation.places.PlaceSearchContext
import ir.nv.navigation.places.UnifiedPlaceRepository
import ir.nv.navigation.ui.theme.AppThemeMode
import ir.nv.navigation.ui.theme.NvColors
import ir.nv.navigation.ui.theme.NvRadius
import ir.nv.navigation.ui.theme.NvSpacing
import kotlinx.coroutines.launch

@Composable
fun NvReferenceV14(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel
) {
    val state by viewModel.state.collectAsState()
    var nearbyOpen by remember { mutableStateOf(false) }
    var savedOpen by remember { mutableStateOf(false) }
    var selectedPlace by remember { mutableStateOf<Place?>(null) }

    if (state.navigationActive) {
        NvReferenceV8(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )
    } else {
        RahnamaHomeScreen(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel,
            onNearby = { nearbyOpen = true },
            onPin = { savedOpen = true },
            onRefineDestination = { }
        )
    }

    if (nearbyOpen) {
        RahnamaNearbyDialog(
            state = state,
            viewModel = viewModel,
            onPlaceSelected = { selectedPlace = it },
            onDismiss = { nearbyOpen = false }
        )
    }

    selectedPlace?.let { place ->
        RahnamaPlaceDetailsDialog(
            place = place,
            onRoute = {
                if (state.origin == null) viewModel.useCurrentLocationAsOrigin()
                viewModel.selectDestination(place)
                selectedPlace = null
                nearbyOpen = false
            },
            onDismiss = { selectedPlace = null }
        )
    }

    if (savedOpen) {
        RahnamaSavedDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { savedOpen = false }
        )
    }
}

@Composable
private fun RahnamaNearbyDialog(
    state: NvUiState,
    viewModel: NvViewModel,
    onPlaceSelected: (Place) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val onlineService = remember { OnlinePlacesService() }
    val repository = remember(onlineService) {
        UnifiedPlaceRepository(
            textSearch = { _, _, _, _ -> emptyList() },
            onlineNearby = NearbyPlaceProvider { center, category, radiusMeters, limit ->
                onlineService.searchNearby(center, category.query, radiusMeters, limit)
            }
        )
    }
    var radiusKm by remember { mutableIntStateOf(5) }
    var nearbyScope by remember { mutableStateOf(NearbyScope.AROUND_ME) }
    var selectedCategory by remember { mutableStateOf<NearbyCategory?>(null) }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun search(category: NearbyCategory? = selectedCategory) {
        val resolvedCategory = category ?: return
        selectedCategory = resolvedCategory
        results = emptyList()
        error = null

        val context = PlaceSearchContext(
            currentLocation = state.currentLocation,
            origin = state.origin?.coordinate,
            destination = state.destination?.coordinate,
            route = state.route,
            onlineAvailable = state.onlineAvailable,
            preferOffline = state.preferOffline
        )
        val missingAnchor = when (nearbyScope) {
            NearbyScope.AROUND_ME -> context.currentLocation == null
            NearbyScope.NEAR_ORIGIN -> context.origin == null
            NearbyScope.NEAR_DESTINATION -> context.destination == null
            NearbyScope.ALONG_ROUTE -> context.route == null
        }
        if (missingAnchor) {
            if (nearbyScope == NearbyScope.AROUND_ME) viewModel.useCurrentLocationAsOrigin()
            error = when (nearbyScope) {
                NearbyScope.AROUND_ME -> "در حال دریافت موقعیت شما؛ چند لحظه دیگر دوباره تلاش کنید"
                NearbyScope.NEAR_ORIGIN -> "ابتدا مبدأ را مشخص کنید"
                NearbyScope.NEAR_DESTINATION -> "ابتدا مقصد را مشخص کنید"
                NearbyScope.ALONG_ROUTE -> "ابتدا یک مسیر محاسبه کنید"
            }
            return
        }
        if (!state.onlineAvailable && !state.offlineReady) {
            error = "اینترنت در دسترس نیست و داده آفلاین مکان‌ها نصب نشده است"
            return
        }

        loading = true
        coroutineScope.launch {
            val value = runCatching {
                repository.nearby(
                    NearbySearchRequest(
                        category = resolvedCategory,
                        scope = nearbyScope,
                        radiusMeters = radiusKm * 1_000,
                        limit = 40
                    ),
                    context
                )
            }
            value.onSuccess {
                results = it
                if (it.isEmpty()) error = "در محدوده انتخاب‌شده نتیجه‌ای پیدا نشد"
            }.onFailure { error = it.message ?: "جستجوی اطراف ناموفق بود" }
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NvColors.Navy900,
        title = {
            Column {
                Text("اطراف من", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Black)
                Text("دسته، محدوده و شعاع جستجو را انتخاب کنید", color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)
            ) {
                Text("محدوده جستجو", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Bold)
                NearbyScope.entries.chunked(2).forEach { rowScopes ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                        rowScopes.forEach { item ->
                            FilterChip(
                                selected = nearbyScope == item,
                                onClick = {
                                    nearbyScope = item
                                    selectedCategory?.let(::search)
                                },
                                label = { Text(item.title) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Text("شعاع جستجو", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                    listOf(5, 10, 25, 50, 100).forEach { km ->
                        FilterChip(
                            selected = radiusKm == km,
                            onClick = {
                                radiusKm = km
                                selectedCategory?.let(::search)
                            },
                            label = { Text("$km") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("دسته‌ها", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Bold)
                NearbyCategory.entries.chunked(3).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                        rowItems.forEach { category ->
                            val selected = selectedCategory == category
                            Surface(
                                modifier = Modifier.weight(1f).clickable { search(category) },
                                color = if (selected) NvColors.RouteBlue.copy(alpha = .18f) else NvColors.Navy850,
                                shape = RoundedCornerShape(NvRadius.Medium),
                                border = BorderStroke(1.dp, if (selected) NvColors.RouteBlue else NvColors.DividerDark)
                            ) {
                                Column(Modifier.padding(vertical = 11.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(categoryIcon(category), contentDescription = category.title, tint = categoryColor(category))
                                    Spacer(Modifier.height(5.dp))
                                    Text(category.title, color = NvColors.TextPrimaryDark, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = NvColors.Warning) }

                if (results.isNotEmpty()) {
                    Text("نتایج ${selectedCategory?.title.orEmpty()}", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Bold)
                    results.take(20).forEach { place ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onPlaceSelected(place) },
                            color = NvColors.Navy850,
                            shape = RoundedCornerShape(NvRadius.Medium),
                            border = BorderStroke(1.dp, NvColors.DividerDark)
                        ) {
                            Row(Modifier.padding(NvSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Place, contentDescription = null, tint = NvColors.Success)
                                Spacer(Modifier.width(NvSpacing.Sm))
                                Column(Modifier.weight(1f)) {
                                    Text(place.name, color = NvColors.TextPrimaryDark, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val detail = buildList {
                                        place.distance?.let { add(formatDistance(it)) }
                                        place.rating?.let { add("★ %.1f".format(it)) }
                                        place.address?.takeIf(String::isNotBlank)?.let { add(it) }
                                    }.joinToString(" • ")
                                    if (detail.isNotBlank()) Text(detail, color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Rounded.ChevronLeft, contentDescription = "جزئیات مکان", tint = NvColors.RouteBlue)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن", color = NvColors.RouteBlue) } }
    )
}

@Composable
private fun RahnamaPlaceDetailsDialog(
    place: Place,
    onRoute: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NvColors.Navy900,
        icon = {
            Surface(
                color = NvColors.RouteBlue.copy(alpha = .16f),
                shape = RoundedCornerShape(NvRadius.Card),
                border = BorderStroke(1.dp, NvColors.RouteBlue.copy(alpha = .5f))
            ) {
                Icon(Icons.Rounded.Place, contentDescription = null, tint = NvColors.RouteBlue, modifier = Modifier.padding(14.dp).size(30.dp))
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(place.name, color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                place.category.takeIf(String::isNotBlank)?.let {
                    Text(it, color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)) {
                place.address?.takeIf(String::isNotBlank)?.let { PlaceDetailRow(Icons.Rounded.LocationOn, "نشانی", it) }
                place.phone?.takeIf(String::isNotBlank)?.let { PlaceDetailRow(Icons.Rounded.Call, "تلفن", it) }
                place.distance?.let { PlaceDetailRow(Icons.Rounded.NearMe, "فاصله", formatDistance(it)) }
                place.rating?.let { rating ->
                    val reviews = place.reviewCount?.takeIf { it > 0 }?.let { " • $it نظر" }.orEmpty()
                    PlaceDetailRow(Icons.Rounded.Star, "امتیاز", "%.1f%s".format(rating, reviews))
                }
                val openText = when {
                    place.open24Hours == true -> "شبانه‌روزی"
                    place.isOpen == true -> "اکنون باز است"
                    place.isOpen == false -> "اکنون بسته است"
                    else -> null
                }
                openText?.let { PlaceDetailRow(Icons.Rounded.Schedule, "وضعیت", it) }
                PlaceDetailRow(Icons.Rounded.Info, "منبع", place.source.ifBlank { "نامشخص" })
            }
        },
        confirmButton = {
            Button(onClick = onRoute) {
                Icon(Icons.Rounded.Navigation, contentDescription = null)
                Spacer(Modifier.width(NvSpacing.Xs))
                Text("مسیریابی")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بازگشت") } }
    )
}

@Composable
private fun PlaceDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = NvColors.RouteBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(NvSpacing.Sm))
        Column(Modifier.weight(1f)) {
            Text(label, color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)
            Text(value, color = NvColors.TextPrimaryDark, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun categoryIcon(category: NearbyCategory) = when (category) {
    NearbyCategory.EMERGENCY -> Icons.Rounded.Emergency
    NearbyCategory.HOSPITAL -> Icons.Rounded.LocalHospital
    NearbyCategory.PHARMACY -> Icons.Rounded.LocalPharmacy
    NearbyCategory.POLICE -> Icons.Rounded.LocalPolice
    NearbyCategory.FIRE -> Icons.Rounded.LocalFireDepartment
    NearbyCategory.RESCUE -> Icons.Rounded.HealthAndSafety
    NearbyCategory.PARKING -> Icons.Rounded.LocalParking
    NearbyCategory.FUEL -> Icons.Rounded.LocalGasStation
    NearbyCategory.EV -> Icons.Rounded.EvStation
    NearbyCategory.PARKS -> Icons.Rounded.Park
    NearbyCategory.RECREATION -> Icons.Rounded.Attractions
    NearbyCategory.RESTAURANTS -> Icons.Rounded.Restaurant
    NearbyCategory.CAFE -> Icons.Rounded.LocalCafe
    NearbyCategory.HOTEL -> Icons.Rounded.Hotel
    NearbyCategory.SHOPPING -> Icons.Rounded.ShoppingBag
    NearbyCategory.CINEMA -> Icons.Rounded.Movie
    NearbyCategory.ATTRACTIONS -> Icons.Rounded.PhotoCamera
    NearbyCategory.REPAIR -> Icons.Rounded.CarRepair
    NearbyCategory.BANK -> Icons.Rounded.AccountBalance
    NearbyCategory.ATM -> Icons.Rounded.LocalAtm
    NearbyCategory.SERVICES -> Icons.Rounded.HomeRepairService
}

private fun categoryColor(category: NearbyCategory) = when (category) {
    NearbyCategory.EMERGENCY, NearbyCategory.FIRE -> NvColors.Emergency
    NearbyCategory.HOSPITAL, NearbyCategory.POLICE -> NvColors.Info
    NearbyCategory.PHARMACY, NearbyCategory.PARKS -> NvColors.Success
    NearbyCategory.FUEL, NearbyCategory.RESTAURANTS, NearbyCategory.CAFE -> NvColors.Warning
    else -> NvColors.RouteBlue
}

private fun formatDistance(distanceMeters: Double): String =
    if (distanceMeters < 1_000) "${distanceMeters.toInt()} متر" else String.format("%.1f کیلومتر", distanceMeters / 1_000.0)

@Composable
private fun RahnamaSavedDialog(state: NvUiState, viewModel: NvViewModel, onDismiss: () -> Unit) {
    val saved = remember(state.personalPlaces, state.recentPlaces) {
        (state.personalPlaces + state.recentPlaces).distinctBy { it.personalCode ?: it.code.toString() }.take(20)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NvColors.Navy900,
        title = { Text("ذخیره‌ها و اخیر", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                if (saved.isEmpty()) {
                    Text("هنوز مکان ذخیره‌شده‌ای ندارید", color = NvColors.TextSecondaryDark)
                } else {
                    saved.forEach { place ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (state.origin == null) viewModel.useCurrentLocationAsOrigin()
                                viewModel.selectDestination(place)
                                onDismiss()
                            }.padding(vertical = NvSpacing.Md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = NvColors.Warning)
                            Spacer(Modifier.width(NvSpacing.Sm))
                            Column(Modifier.weight(1f)) {
                                Text(place.name, color = NvColors.TextPrimaryDark, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(place.personalCode ?: "مکان اخیر", color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = NvColors.TextSecondaryDark)
                        }
                        HorizontalDivider(color = NvColors.DividerDark)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن", color = NvColors.RouteBlue) } }
    )
}

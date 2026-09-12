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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.Place
import ir.nv.navigation.online.OnlinePlacesService
import ir.nv.navigation.ui.theme.AppThemeMode
import ir.nv.navigation.ui.theme.NvColors
import ir.nv.navigation.ui.theme.NvRadius
import ir.nv.navigation.ui.theme.NvSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            onDismiss = { nearbyOpen = false }
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

private data class RahnamaNearbyCategory(val title: String, val query: String, val icon: String)

@Composable
private fun RahnamaNearbyDialog(
    state: NvUiState,
    viewModel: NvViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val service = remember { OnlinePlacesService() }
    var radiusKm by remember { mutableIntStateOf(5) }
    var selectedCategory by remember { mutableStateOf<RahnamaNearbyCategory?>(null) }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val categories = remember {
        listOf(
            RahnamaNearbyCategory("اورژانس", "اورژانس بیمارستان", "🚑"),
            RahnamaNearbyCategory("بیمارستان", "بیمارستان", "🏥"),
            RahnamaNearbyCategory("داروخانه", "داروخانه", "💊"),
            RahnamaNearbyCategory("پلیس", "پلیس", "🚓"),
            RahnamaNearbyCategory("آتش‌نشانی", "آتش نشانی", "🚒"),
            RahnamaNearbyCategory("امداد", "امداد نجات", "🛟"),
            RahnamaNearbyCategory("پارکینگ", "پارکینگ", "🅿️"),
            RahnamaNearbyCategory("سوخت", "پمپ بنزین", "⛽"),
            RahnamaNearbyCategory("شارژ EV", "شارژ خودرو برقی", "🔌"),
            RahnamaNearbyCategory("پارک", "پارک", "🌳"),
            RahnamaNearbyCategory("تفریح", "تفریح", "🎡"),
            RahnamaNearbyCategory("رستوران", "رستوران", "🍽"),
            RahnamaNearbyCategory("کافه", "کافه", "☕"),
            RahnamaNearbyCategory("هتل", "هتل", "🏨"),
            RahnamaNearbyCategory("خرید", "مرکز خرید", "🛍"),
            RahnamaNearbyCategory("سینما", "سینما", "🎬"),
            RahnamaNearbyCategory("دیدنی", "جاذبه گردشگری", "📸"),
            RahnamaNearbyCategory("تعمیرگاه", "تعمیرگاه خودرو", "🔧"),
            RahnamaNearbyCategory("بانک", "بانک", "🏦"),
            RahnamaNearbyCategory("خودپرداز", "خودپرداز", "🏧"),
            RahnamaNearbyCategory("خدمات", "خدمات", "🧰")
        )
    }

    fun search(category: RahnamaNearbyCategory) {
        selectedCategory = category
        results = emptyList()
        error = null
        val center = state.currentLocation ?: state.origin?.coordinate
        if (center == null) {
            viewModel.useCurrentLocationAsOrigin()
            error = "در حال دریافت موقعیت دقیق شما؛ دوباره دسته را انتخاب کنید"
            return
        }
        if (!state.onlineAvailable) {
            error = "برای جستجوی آنلاین اطراف، اتصال اینترنت لازم است"
            return
        }
        loading = true
        scope.launch {
            val value = withContext(Dispatchers.IO) {
                runCatching { service.searchNearby(center, category.query, radiusKm * 1000, 40) }
            }
            value.onSuccess {
                results = it
                if (it.isEmpty()) error = "در شعاع $radiusKm کیلومتر نتیجه‌ای پیدا نشد"
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
                Text("دسته‌بندی خدمات و مکان‌های نزدیک", color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)
            ) {
                Text("شعاع جستجو", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                    listOf(5, 10, 25, 50, 100).forEach { km ->
                        FilterChip(
                            selected = radiusKm == km,
                            onClick = {
                                radiusKm = km
                                selectedCategory?.let(::search)
                            },
                            label = { Text("$km km") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("دسته‌ها", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Bold)
                categories.chunked(3).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                        rowItems.forEach { category ->
                            val selected = selectedCategory == category
                            Surface(
                                modifier = Modifier.weight(1f).clickable { search(category) },
                                color = if (selected) NvColors.RouteBlue.copy(alpha = .18f) else NvColors.Navy850,
                                shape = RoundedCornerShape(NvRadius.Medium),
                                border = BorderStroke(1.dp, if (selected) NvColors.RouteBlue else NvColors.DividerDark)
                            ) {
                                Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(category.icon, style = MaterialTheme.typography.titleLarge)
                                    Text(category.title, color = NvColors.TextPrimaryDark, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = NvColors.Warning) }

                if (results.isNotEmpty()) {
                    Text("نتایج", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Bold)
                    results.take(20).forEach { place ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (state.origin == null) viewModel.useCurrentLocationAsOrigin()
                                viewModel.selectDestination(place)
                                onDismiss()
                            },
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
                                        place.distance?.let { add(if (it < 1000) "${it.toInt()} متر" else String.format("%.1f km", it / 1000.0)) }
                                        place.rating?.let { add("★ %.1f".format(it)) }
                                        place.address?.takeIf(String::isNotBlank)?.let { add(it) }
                                    }.joinToString(" • ")
                                    if (detail.isNotBlank()) Text(detail, color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Rounded.Navigation, contentDescription = "مسیریابی", tint = NvColors.RouteBlue)
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

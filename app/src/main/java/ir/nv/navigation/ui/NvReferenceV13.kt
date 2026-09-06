package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.data.*
import ir.nv.navigation.map.NvCodePickerMap
import ir.nv.navigation.map.NvMapInteractionBus
import ir.nv.navigation.online.OnlinePlacesService
import ir.nv.navigation.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

private val V13Panel = Color(0xF2071B2B)
private val V13Cyan = Color(0xFF14D8FF)
private val V13Gold = Color(0xFFFFB52E)
private val V13Green = Color(0xFF43E66B)

@Composable
fun NvReferenceV13(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val store = remember { NvBookmarkStore(context.applicationContext) }
    val allocator = remember { NvCodeAllocationService() }
    val local = remember { NvLocalSequentialCodeAllocator(context.applicationContext) }
    val qrManager = remember { NvQrShareManager(context) }
    val nearbyService = remember { OnlinePlacesService() }

    var bookmark by remember { mutableStateOf(store.load()) }
    var picker by remember { mutableStateOf(false) }
    var pin by remember {
        mutableStateOf<Coordinate?>(
            bookmark?.coordinate ?: state.currentLocation ?: state.destination?.coordinate ?: state.origin?.coordinate
        )
    }
    var qrOpen by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<NvQrShareManager.SavedQr?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var nearbyOpen by remember { mutableStateOf(false) }
    var nearbyQuery by remember { mutableStateOf("") }
    var nearbyResults by remember { mutableStateOf<List<Place>>(emptyList()) }
    var nearbyLoading by remember { mutableStateOf(false) }
    var nearbyError by remember { mutableStateOf<String?>(null) }

    var smartResults by remember { mutableStateOf<List<Place>>(emptyList()) }
    var smartLoading by remember { mutableStateOf(false) }

    fun savePin() {
        val coordinate = pin ?: return
        val place = Place(-8300000000L, "سنجاق NV", coordinate, "bookmark:pin")
        store.save(place)
        bookmark = place
        qr = null
        error = null
        picker = false
    }

    val doubleTapListener = remember {
        { coordinate: Coordinate ->
            pin = coordinate
            val place = Place(-8300000000L, "پرچم NV", coordinate, "bookmark:flag")
            store.save(place)
            bookmark = place
            qr = null
            error = null
            NvMapInteractionBus.recenterOn(coordinate)
        }
    }
    DisposableEffect(Unit) {
        NvMapInteractionBus.onDoubleTap = doubleTapListener
        onDispose { NvMapInteractionBus.clearListener(doubleTapListener) }
    }

    fun useCurrent() {
        state.currentLocation?.let { coordinate ->
            pin = coordinate
            val place = Place(-8300000000L, "موقعیت فعلی من", coordinate, "bookmark:current")
            store.save(place)
            bookmark = place
            qr = null
            error = null
            picker = false
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) viewModel.useCurrentLocationAsOrigin()
    }

    fun locate() {
        if (state.currentLocation != null) {
            useCurrent()
            viewModel.recenterNavigation()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.useCurrentLocationAsOrigin()
        } else {
            locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun nearbySearch(query: String) {
        nearbyQuery = query
        nearbyResults = emptyList()
        nearbyError = null
        val center = state.currentLocation
        if (center == null) {
            locate()
            nearbyError = "در حال دریافت موقعیت دقیق شما؛ پس از نمایش موقعیت، دوباره دسته را لمس کنید"
            return
        }
        if (!state.onlineAvailable) {
            nearbyError = "برای جستجوی کامل اطراف، اتصال اینترنت لازم است"
            return
        }
        nearbyLoading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { nearbyService.searchNearby(center, query) }
            }
            result.onSuccess { places ->
                nearbyResults = places
                if (places.isEmpty()) nearbyError = "در شعاع فعلی مکانی از این نوع پیدا نشد"
            }.onFailure { throwable ->
                nearbyError = throwable.message ?: "دریافت مکان‌های اطراف ناموفق بود"
            }
            nearbyLoading = false
        }
    }

    LaunchedEffect(state.currentLocation) {
        if (state.currentLocation != null && !state.locating) pin = state.currentLocation
    }

    // If the normal geocoder cannot resolve a typed destination, search named POIs
    // and relevant POI categories around the user's current GPS position.
    LaunchedEffect(state.destinationQuery, state.currentLocation, state.destinationSuggestions) {
        val query = state.destinationQuery.trim()
        val center = state.currentLocation
        if (query.length < 3 || center == null || state.destinationSuggestions.isNotEmpty()) {
            smartResults = emptyList()
            smartLoading = false
            return@LaunchedEffect
        }
        delay(450)
        smartLoading = true
        smartResults = withContext(Dispatchers.IO) {
            val named = runCatching { nearbyService.searchNamedNearby(center, query) }.getOrDefault(emptyList())
            val categorized = runCatching { nearbyService.searchNearby(center, query) }.getOrDefault(emptyList())
            (named + categorized)
                .distinctBy {
                    Triple(
                        it.name,
                        (it.coordinate.latitude * 10_000).toInt(),
                        (it.coordinate.longitude * 10_000).toInt()
                    )
                }
                .sortedBy { nearbyDistanceMeters(center, it.coordinate) }
                .take(8)
        }
        smartLoading = false
    }

    Box(Modifier.fillMaxSize()) {
        NvReferenceV8(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel,
            onNearby = { nearbyOpen = true }
        )

        if (
            !state.navigationActive &&
            state.destinationQuery.trim().length >= 3 &&
            state.destinationSuggestions.isEmpty() &&
            (smartLoading || smartResults.isNotEmpty())
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(top = 230.dp)
                    .fillMaxWidth(),
                color = V13Panel,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, V13Cyan.copy(alpha = .55f)),
                shadowElevation = 8.dp
            ) {
                Column(Modifier.padding(8.dp)) {
                    if (smartLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    smartResults.take(5).forEach { place ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectDestination(place)
                                    smartResults = emptyList()
                                }
                                .padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Place, null, tint = V13Cyan)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                place.name,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            state.currentLocation?.let { current ->
                                Text(
                                    formatNearbyDistance(nearbyDistanceMeters(current, place.coordinate)),
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (nearbyOpen) {
        AlertDialog(
            onDismissRequest = { nearbyOpen = false },
            containerColor = V13Panel,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NearMe, null, tint = V13Green)
                    Spacer(Modifier.width(8.dp))
                    Text("اطراف من", color = Color.White, fontWeight = FontWeight.Black)
                }
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 550.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        "مکان‌های عمومی، ضروری و دیدنی بر اساس فاصله واقعی از GPS شما",
                        color = Color.LightGray
                    )
                    val categories = listOf(
                        "🚑 اورژانس" to "اورژانس بیمارستان",
                        "🏥 بیمارستان" to "بیمارستان",
                        "💊 داروخانه" to "داروخانه",
                        "🚓 پلیس" to "پلیس",
                        "🚒 آتش‌نشانی" to "آتش نشانی",
                        "🩺 درمانگاه" to "درمانگاه",
                        "🚌 ترمینال" to "ترمینال پایانه",
                        "✈️ فرودگاه" to "فرودگاه",
                        "🚆 راه‌آهن" to "راه آهن",
                        "🚇 مترو" to "مترو",
                        "🚕 تاکسی" to "تاکسی",
                        "🅿️ پارکینگ" to "پارکینگ",
                        "⛽ پمپ‌بنزین" to "پمپ بنزین",
                        "🔌 شارژ خودرو" to "شارژ خودرو",
                        "🍽 رستوران" to "رستوران",
                        "☕ کافه" to "کافه",
                        "🏨 هتل" to "هتل",
                        "🛒 فروشگاه" to "فروشگاه",
                        "🛍 مرکز خرید" to "مرکز خرید",
                        "🏦 بانک" to "بانک",
                        "🏧 خودپرداز" to "خودپرداز",
                        "🥖 نانوایی" to "نانوایی",
                        "🏫 مدرسه" to "مدرسه",
                        "🎓 دانشگاه" to "دانشگاه",
                        "🕌 مسجد" to "مسجد",
                        "📮 پست" to "پست",
                        "🚻 سرویس" to "سرویس بهداشتی",
                        "🔧 تعمیرگاه" to "تعمیرگاه",
                        "🏟 ورزشگاه" to "ورزشگاه",
                        "🎬 سینما" to "سینما",
                        "🏛 موزه" to "موزه",
                        "🌳 پارک" to "پارک",
                        "📸 دیدنی" to "جاذبه گردشگری دیدنی",
                        "🏺 تاریخی" to "اثر تاریخی",
                        "🏞 طبیعت" to "طبیعت منظره",
                        "🎡 تفریحی" to "تفریح",
                        "🏖 ساحل" to "ساحل",
                        "⛰ کوه/منظره" to "کوه منظره",
                        "🌉 پل دیدنی" to "پل"
                    )
                    categories.chunked(2).forEach { rowItems ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            rowItems.forEach { (label, query) ->
                                OutlinedButton(
                                    onClick = { nearbySearch(query) },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (nearbyQuery == query) V13Green else V13Cyan.copy(alpha = .4f)
                                    )
                                ) {
                                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }

                    if (state.locating || nearbyLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    nearbyError?.let { Text(it, color = V13Gold) }

                    nearbyResults.forEach { place ->
                        val distance = state.currentLocation?.let {
                            nearbyDistanceMeters(it, place.coordinate)
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (state.origin == null) viewModel.useCurrentLocationAsOrigin()
                                    viewModel.selectDestination(place)
                                    nearbyOpen = false
                                },
                            color = Color.Black.copy(alpha = .18f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, V13Cyan.copy(alpha = .25f))
                        ) {
                            Row(
                                Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Place, null, tint = V13Green)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        place.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        distance?.let(::formatNearbyDistance) ?: "در حال دریافت فاصله",
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Icon(Icons.Rounded.Navigation, null, tint = V13Green)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { nearbyOpen = false }) { Text("بستن") }
            }
        )
    }

    if (picker) {
        Dialog(onDismissRequest = { picker = false }) {
            Surface(
                Modifier.fillMaxWidth().fillMaxHeight(.82f),
                color = V13Panel,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        NvCodePickerMap(
                            context,
                            pin,
                            state.satelliteMode,
                            { pin = it },
                            Modifier.fillMaxSize()
                        )
                    }
                    pin?.let {
                        Text(
                            "سنجاق: %.6f, %.6f".format(it.latitude, it.longitude),
                            color = Color.White,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { locate() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.MyLocation, null)
                            Text("موقعیت من")
                        }
                        Button(
                            onClick = { savePin() },
                            enabled = pin != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Bookmark, null)
                            Text("ثبت سنجاق")
                        }
                    }
                }
            }
        }
    }

    if (qrOpen) {
        AlertDialog(
            onDismissRequest = { if (!working) qrOpen = false },
            containerColor = V13Panel,
            title = {
                Text("QR و کد عددی NV", color = Color.White, fontWeight = FontWeight.Black)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    bookmark?.let {
                        Text(it.name, color = Color.White)
                        Text(
                            "%.6f, %.6f".format(it.coordinate.latitude, it.coordinate.longitude),
                            color = Color.LightGray
                        )
                    }
                    qr?.let { saved ->
                        NvQrCode(saved.payload, Modifier.size(220.dp))
                        Text("کد عددی NV", color = Color.LightGray)
                        Surface(
                            color = Color.Black.copy(alpha = .22f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, V13Green.copy(alpha = .65f))
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    saved.code,
                                    color = V13Green,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                IconButton(
                                    onClick = { clipboard.setText(AnnotatedString(saved.code)) }
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, "کپی کد عددی", tint = V13Cyan)
                                }
                            }
                        }
                    }
                    error?.let { Text(it, color = Color.Red) }

                    if (qr == null) {
                        Button(
                            onClick = {
                                val savedBookmark = bookmark ?: return@Button
                                working = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val existingCode = savedBookmark.personalCode
                                        if (!existingCode.isNullOrBlank()) {
                                            qrManager.createAndSave(
                                                existingCode,
                                                savedBookmark.name,
                                                savedBookmark.coordinate
                                            )
                                        } else {
                                            val allocation = if (allocator.isConfigured()) {
                                                allocator.allocateOnline(
                                                    savedBookmark.name,
                                                    savedBookmark.coordinate
                                                )
                                            } else {
                                                Result.success(
                                                    NvCodeAllocationService.Allocation(
                                                        local.nextCode(
                                                            state.personalPlaces.mapNotNull { it.personalCode }
                                                        ),
                                                        savedBookmark.name,
                                                        savedBookmark.coordinate,
                                                        false
                                                    )
                                                )
                                            }
                                            allocation.fold(
                                                onSuccess = { allocated ->
                                                    viewModel.savePersonalCode(savedBookmark, allocated.code)
                                                    store.attachCode(allocated.code)
                                                    bookmark = savedBookmark.copy(personalCode = allocated.code)
                                                    qrManager.createAndSave(
                                                        allocated.code,
                                                        savedBookmark.name,
                                                        savedBookmark.coordinate
                                                    )
                                                },
                                                onFailure = { Result.failure(it) }
                                            )
                                        }
                                    }
                                    result.onSuccess { qr = it }
                                        .onFailure { error = it.message ?: "ساخت QR ناموفق بود" }
                                    working = false
                                }
                            },
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ساخت کد NV و QR", fontWeight = FontWeight.Black)
                        }
                    } else {
                        Button(
                            onClick = { qr?.let(qrManager::share) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = V13Green,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Rounded.Share, null)
                            Text(" اشتراک‌گذاری", fontWeight = FontWeight.Black)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { if (!working) qrOpen = false }) { Text("بستن") }
            }
        )
    }
}

private fun nearbyDistanceMeters(a: Coordinate, b: Coordinate): Double {
    val earthRadius = 6_371_000.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
}

private fun formatNearbyDistance(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} متر"
    else String.format("%.1f کیلومتر", meters / 1000.0)

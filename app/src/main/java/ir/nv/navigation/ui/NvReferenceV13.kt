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
import ir.nv.navigation.data.NvBookmarkStore
import ir.nv.navigation.data.NvCodeAllocationService
import ir.nv.navigation.data.NvQrShareManager
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
    val qrManager = remember { NvQrShareManager(context) }
    val nearbyService = remember { OnlinePlacesService() }

    var bookmark by remember { mutableStateOf(store.load()) }
    var pickerOpen by remember { mutableStateOf(false) }
    var pin by remember {
        mutableStateOf<Coordinate?>(
            bookmark?.coordinate ?: state.currentLocation ?: state.destination?.coordinate ?: state.origin?.coordinate
        )
    }
    var codeDialogOpen by remember { mutableStateOf(false) }
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

    fun openPicker() {
        pin = state.destination?.coordinate ?: state.currentLocation ?: state.origin?.coordinate ?: pin
        error = null
        pickerOpen = true
    }

    fun savePinAndOpenCode() {
        val coordinate = pin ?: return
        val place = Place(-8300000000L, "مکان انتخاب‌شده", coordinate, "bookmark:pin")
        store.save(place)
        bookmark = place
        qr = null
        error = null
        pickerOpen = false
        codeDialogOpen = true
    }

    val doubleTapListener = remember {
        { coordinate: Coordinate ->
            pin = coordinate
            NvMapInteractionBus.recenterOn(coordinate)
        }
    }
    DisposableEffect(Unit) {
        NvMapInteractionBus.onDoubleTap = doubleTapListener
        onDispose { NvMapInteractionBus.clearListener(doubleTapListener) }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) viewModel.useCurrentLocationAsOrigin()
    }

    fun useCurrentPin() {
        state.currentLocation?.let {
            pin = it
            NvMapInteractionBus.recenterOn(it)
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.useCurrentLocationAsOrigin()
        else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    fun nearbySearch(query: String) {
        nearbyQuery = query
        nearbyResults = emptyList()
        nearbyError = null
        val center = state.currentLocation
        if (center == null) {
            useCurrentPin()
            nearbyError = "در حال دریافت موقعیت شما؛ سپس دوباره جستجو کنید"
            return
        }
        if (!state.onlineAvailable) {
            nearbyError = "جستجوی آنلاین اطراف در حالت آفلاین در دسترس نیست"
            return
        }
        nearbyLoading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { nearbyService.searchNearby(center, query) } }
            result.onSuccess {
                nearbyResults = it
                if (it.isEmpty()) nearbyError = "مکانی از این نوع پیدا نشد"
            }.onFailure { nearbyError = it.message ?: "جستجو ناموفق بود" }
            nearbyLoading = false
        }
    }

    LaunchedEffect(state.currentLocation) {
        if (state.currentLocation != null && !state.locating && pin == null) pin = state.currentLocation
    }

    LaunchedEffect(state.destinationQuery, state.currentLocation, state.destinationSuggestions) {
        val query = state.destinationQuery.trim()
        val center = state.currentLocation
        if (query.length < 3 || center == null || state.destinationSuggestions.isNotEmpty() || !state.onlineAvailable) {
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
                .distinctBy { Triple(it.name, (it.coordinate.latitude * 10_000).toInt(), (it.coordinate.longitude * 10_000).toInt()) }
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
            onNearby = { nearbyOpen = true },
            onPin = ::openPicker
        )

        if (!state.navigationActive && state.destinationQuery.trim().length >= 3 && state.destinationSuggestions.isEmpty() && (smartLoading || smartResults.isNotEmpty())) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 18.dp).padding(top = 230.dp).fillMaxWidth(),
                color = V13Panel,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, V13Cyan.copy(alpha = .55f)),
                shadowElevation = 8.dp
            ) {
                Column(Modifier.padding(8.dp)) {
                    if (smartLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    smartResults.take(5).forEach { place ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                viewModel.selectDestination(place)
                                smartResults = emptyList()
                            }.padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Place, null, tint = V13Cyan)
                            Spacer(Modifier.width(7.dp))
                            Text(place.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            state.currentLocation?.let { current ->
                                Text(formatNearbyDistance(nearbyDistanceMeters(current, place.coordinate)), color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
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
            title = { Text("اطراف من", color = Color.White, fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 550.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("مکان‌های نزدیک بر اساس موقعیت واقعی شما", color = Color.LightGray)
                    val categories = listOf(
                        "🚑 اورژانس" to "اورژانس بیمارستان", "🏥 بیمارستان" to "بیمارستان",
                        "💊 داروخانه" to "داروخانه", "🚓 پلیس" to "پلیس",
                        "⛽ پمپ‌بنزین" to "پمپ بنزین", "🅿️ پارکینگ" to "پارکینگ",
                        "🍽 رستوران" to "رستوران", "☕ کافه" to "کافه",
                        "🏨 هتل" to "هتل", "🏦 بانک" to "بانک",
                        "🌳 پارک" to "پارک", "📸 دیدنی" to "جاذبه گردشگری"
                    )
                    categories.chunked(2).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            rowItems.forEach { (label, query) ->
                                OutlinedButton(onClick = { nearbySearch(query) }, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1) }
                            }
                        }
                    }
                    if (nearbyLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    nearbyError?.let { Text(it, color = V13Gold) }
                    nearbyResults.forEach { place ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (state.origin == null) viewModel.useCurrentLocationAsOrigin()
                                viewModel.selectDestination(place)
                                nearbyOpen = false
                            },
                            color = Color.Black.copy(alpha = .18f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, V13Cyan.copy(alpha = .25f))
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Place, null, tint = V13Green)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(place.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    state.currentLocation?.let { Text(formatNearbyDistance(nearbyDistanceMeters(it, place.coordinate)), color = Color.LightGray) }
                                }
                                Icon(Icons.Rounded.Navigation, null, tint = V13Green)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { nearbyOpen = false }) { Text("بستن") } }
        )
    }

    if (pickerOpen) {
        Dialog(onDismissRequest = { pickerOpen = false }) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(.82f), color = V13Panel, shape = RoundedCornerShape(24.dp)) {
                Column {
                    Text("نقطه دقیق را روی نقشه مشخص کنید", color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.padding(14.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        NvCodePickerMap(context, pin, state.satelliteMode, { pin = it }, Modifier.fillMaxSize())
                    }
                    pin?.let {
                        Text("نشانگر: %.6f, %.6f".format(it.latitude, it.longitude), color = Color.White, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = ::useCurrentPin, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.MyLocation, null)
                            Spacer(Modifier.width(4.dp))
                            Text("موقعیت من")
                        }
                        Button(onClick = ::savePinAndOpenCode, enabled = pin != null, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Place, null)
                            Spacer(Modifier.width(4.dp))
                            Text("تأیید مکان")
                        }
                    }
                }
            }
        }
    }

    if (codeDialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!working) codeDialogOpen = false },
            containerColor = V13Panel,
            title = { Text("کد عددی و QR مکان", color = Color.White, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    bookmark?.let { saved ->
                        Text(saved.name, color = Color.White)
                        Text("%.6f, %.6f".format(saved.coordinate.latitude, saved.coordinate.longitude), color = Color.LightGray)
                    }

                    qr?.let { saved ->
                        NvQrCode(saved.payload, Modifier.size(220.dp))
                        Surface(color = Color.Black.copy(alpha = .22f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, V13Green.copy(alpha = .65f))) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(saved.code, color = V13Green, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = { clipboard.setText(AnnotatedString(saved.code)) }) {
                                    Icon(Icons.Rounded.ContentCopy, "کپی کد", tint = V13Cyan)
                                }
                            }
                        }
                    }

                    if (!state.onlineAvailable && qr == null) {
                        Text("برای تعریف کد جدید باید آنلاین باشید.", color = V13Gold)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    if (qr == null) {
                        Button(
                            onClick = {
                                val savedBookmark = bookmark ?: return@Button
                                if (!state.onlineAvailable) {
                                    error = "اینترنت در دسترس نیست؛ کد جدید فقط آنلاین تعریف می‌شود"
                                    return@Button
                                }
                                if (!allocator.isConfigured()) {
                                    error = "سامانه مرکزی کد NV هنوز تنظیم نشده است"
                                    return@Button
                                }
                                working = true
                                error = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        allocator.allocateOnline(savedBookmark.name, savedBookmark.coordinate).fold(
                                            onSuccess = { allocation ->
                                                viewModel.savePersonalCode(savedBookmark, allocation.code)
                                                store.attachCode(allocation.code)
                                                bookmark = savedBookmark.copy(personalCode = allocation.code)
                                                qrManager.createAndSave(allocation.code, savedBookmark.name, allocation.coordinate)
                                            },
                                            onFailure = { Result.failure(it) }
                                        )
                                    }
                                    result.onSuccess { qr = it }.onFailure { error = it.message ?: "دریافت کد NV ناموفق بود" }
                                    working = false
                                }
                            },
                            enabled = !working && state.onlineAvailable,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.QrCode2, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (working) "در حال دریافت..." else "دریافت کد یکتا و QR", fontWeight = FontWeight.Black)
                        }
                    } else {
                        Button(
                            onClick = { qr?.let(qrManager::share) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = V13Green, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Rounded.Share, null)
                            Spacer(Modifier.width(6.dp))
                            Text("اشتراک‌گذاری کد و QR", fontWeight = FontWeight.Black)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { if (!working) codeDialogOpen = false }) { Text("بستن") } }
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
    if (meters < 1000) "${meters.roundToInt()} متر" else String.format("%.1f کیلومتر", meters / 1000.0)

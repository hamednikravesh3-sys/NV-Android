package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.data.NvCodeAllocationService
import ir.nv.navigation.data.NvLocalSequentialCodeAllocator
import ir.nv.navigation.data.NvQrShareManager
import ir.nv.navigation.map.NvCodePickerMap
import ir.nv.navigation.map.SatelliteIranMap
import ir.nv.navigation.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val V11Cyan = Color(0xFF14D8FF)
private val V11Gold = Color(0xFFFFB52E)
private val V11Green = Color(0xFF43E66B)
private val V11Red = Color(0xFFFF304C)
private val V11Panel = Color(0xF2071B2B)

@Composable
fun NvReferenceV11(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onlineAllocator = remember { NvCodeAllocationService() }
    val localAllocator = remember { NvLocalSequentialCodeAllocator(context.applicationContext) }
    val qrManager = remember { NvQrShareManager(context) }

    var pickerVisible by remember { mutableStateOf(false) }
    var selectedPoint by remember { mutableStateOf<Coordinate?>(null) }
    var placeName by remember { mutableStateOf("") }
    var pickerSatellite by remember { mutableStateOf(state.satelliteMode) }
    var allocating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedQr by remember { mutableStateOf<NvQrShareManager.SavedQr?>(null) }
    var allocationOnline by remember { mutableStateOf(false) }
    var locatePending by remember { mutableStateOf(false) }
    var oldOrigin by remember { mutableStateOf(state.origin) }

    fun openCodePicker(initial: Coordinate? = state.currentLocation ?: state.destination?.coordinate ?: state.origin?.coordinate) {
        selectedPoint = initial
        placeName = ""
        pickerSatellite = state.satelliteMode
        allocating = false
        error = null
        savedQr = null
        allocationOnline = false
        pickerVisible = true
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            oldOrigin = state.origin
            locatePending = true
            viewModel.useCurrentLocationAsOrigin()
        }
    }

    fun locateMe() {
        if (state.currentLocation != null) {
            viewModel.recenterNavigation()
            return
        }
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            oldOrigin = state.origin
            locatePending = true
            viewModel.useCurrentLocationAsOrigin()
        } else {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(state.currentLocation, locatePending) {
        if (locatePending && state.currentLocation != null) {
            viewModel.recenterNavigation()
            oldOrigin?.let { previous ->
                if (previous.coordinate != state.origin?.coordinate) viewModel.selectOrigin(previous)
            }
            locatePending = false
        }
    }

    // Observe two quick taps at the parent without consuming map gestures.
    val doubleTapObserver = Modifier.pointerInput(Unit) {
        var lastUp = 0L
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size == 1 && event.changes.first().changedToUpIgnoreConsumed()) {
                    val now = System.currentTimeMillis()
                    if (now - lastUp in 80..360) {
                        openCodePicker()
                        lastUp = 0L
                    } else {
                        lastUp = now
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().then(doubleTapObserver)) {
        // Directly use V8: V9/V10 stacking is intentionally removed, so only one End control remains.
        NvReferenceV8(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )

        // Force the genuine satellite raster map to render when selected; do not gate it on NetworkMonitor state.
        if (state.satelliteMode) {
            SatelliteIranMap(
                context = context,
                routes = state.routeAlternatives.ifEmpty { listOfNotNull(state.route) },
                selectedRouteIndex = state.selectedRouteIndex,
                codedPlaces = (state.personalPlaces + state.recentPlaces + listOfNotNull(state.origin, state.destination)).distinctBy { it.personalCode ?: it.code.toString() },
                currentLocation = state.currentLocation,
                followLocation = state.followNavigation,
                navigationActive = state.navigationActive,
                navigationZoomLevel = state.navigationZoomLevel,
                navigationRecenterToken = state.navigationRecenterToken,
                bearingDegrees = state.bearingDegrees,
                onManualGesture = viewModel::pauseNavigationFollow,
                modifier = Modifier.fillMaxSize()
            )

            // Satellite mode always keeps a way back to vector map.
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
                color = V11Panel,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, V11Green)
            ) {
                IconButton(onClick = viewModel::toggleSatelliteMode) {
                    Icon(Icons.Rounded.Map, "بازگشت به نقشه معمولی", tint = V11Green)
                }
            }
        }

        // My location is available in browsing mode as well as driving mode.
        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(60.dp),
            color = V11Panel,
            shape = CircleShape,
            border = BorderStroke(2.dp, V11Cyan),
            shadowElevation = 10.dp
        ) {
            IconButton(onClick = ::locateMe) {
                if (state.locating) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp, color = V11Cyan)
                } else {
                    Icon(Icons.Rounded.MyLocation, "موقعیت من", tint = V11Cyan, modifier = Modifier.size(32.dp))
                }
            }
        }

        // Automatic NV code entry point: double tap also opens the same flow.
        Surface(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(58.dp),
            color = V11Panel,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(2.dp, V11Gold),
            shadowElevation = 8.dp
        ) {
            IconButton(onClick = { openCodePicker() }) {
                Icon(Icons.Rounded.QrCode2, "کد NV", tint = V11Gold, modifier = Modifier.size(30.dp))
            }
        }

        V11WeatherCard(
            state = state,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 10.dp, bottom = 96.dp)
        )

        if (state.navigationActive) {
            V11SignalOverlay(
                direction = state.route?.maneuvers?.getOrNull(state.maneuverIndex)?.direction,
                instruction = state.route?.maneuvers?.getOrNull(state.maneuverIndex)?.instruction.orEmpty(),
                distanceMeters = state.distanceToNextManeuverMeters,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (state.satelliteMode && state.navigationActive) {
            // Satellite overlay hides V8 controls, so keep exactly one explicit End Route button here.
            Button(
                onClick = viewModel::stopNavigation,
                colors = ButtonDefaults.buttonColors(containerColor = V11Red, contentColor = Color.White),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp).height(54.dp).widthIn(min = 170.dp)
            ) {
                Icon(Icons.Rounded.Stop, null)
                Spacer(Modifier.width(7.dp))
                Text("پایان مسیر", fontWeight = FontWeight.Black)
            }
        }

        if (pickerVisible) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                Box(Modifier.fillMaxSize()) {
                    NvCodePickerMap(
                        context = context,
                        initial = selectedPoint,
                        satellite = pickerSatellite,
                        onPointSelected = {
                            selectedPoint = it
                            savedQr = null
                            error = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(10.dp).fillMaxWidth(),
                        color = V11Panel,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, V11Cyan)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("کد NV خودکار 1 تا N", color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                                IconButton(onClick = { pickerVisible = false }) { Icon(Icons.Rounded.Close, "بستن", tint = Color.White) }
                            }
                            Text("روی نقطه دلخواه دوبار ضربه بزنید یا چند لحظه نگه دارید.", color = Color(0xFFB7C9D4))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ماهواره‌ای", color = Color.White, modifier = Modifier.weight(1f))
                                Switch(checked = pickerSatellite, onCheckedChange = { pickerSatellite = it })
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(10.dp).fillMaxWidth(),
                        color = V11Panel,
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, V11Cyan)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val point = selectedPoint
                            Text(
                                point?.let { "مختصات: %.6f, %.6f".format(it.latitude, it.longitude) } ?: "یک نقطه را دوبار لمس کنید",
                                color = if (point == null) V11Gold else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = placeName,
                                onValueChange = { placeName = it.take(60) },
                                label = { Text("نام مکان (اختیاری)") },
                                singleLine = true,
                                enabled = !allocating && savedQr == null,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (!onlineAllocator.isConfigured()) {
                                Text("Registry عمومی هنوز Deploy نشده؛ شماره فعلاً روی همین دستگاه 1 تا N است.", color = Color(0xFFFFC66D), style = MaterialTheme.typography.labelSmall)
                            } else {
                                Text("Registry مرکزی فعال است؛ شماره جهانی و غیرتکراری تخصیص داده می‌شود.", color = V11Green, style = MaterialTheme.typography.labelSmall)
                            }
                            error?.let { Text(it, color = Color(0xFFFF6B78), fontWeight = FontWeight.Bold) }

                            savedQr?.let { qr ->
                                Text("NV:${qr.code} • ${if (allocationOnline) "آنلاین / جهانی" else "محلی"}", color = if (allocationOnline) V11Green else V11Gold, fontWeight = FontWeight.Black)
                                Button(
                                    onClick = { qrManager.share(qr) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = V11Green, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Rounded.Share, null)
                                    Spacer(Modifier.width(7.dp))
                                    Text("اشتراک‌گذاری QR و کد NV", fontWeight = FontWeight.Black)
                                }
                            } ?: Button(
                                onClick = {
                                    val chosen = selectedPoint ?: return@Button
                                    allocating = true
                                    error = null
                                    val cleanName = placeName.trim().ifBlank { "مکان NV" }
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            if (onlineAllocator.isConfigured()) onlineAllocator.allocateOnline(cleanName, chosen)
                                            else {
                                                val code = localAllocator.nextCode(state.personalPlaces.mapNotNull { it.personalCode })
                                                Result.success(NvCodeAllocationService.Allocation(code, cleanName, chosen, false))
                                            }
                                        }
                                        result.onSuccess { allocation ->
                                            val place = ir.nv.navigation.core.Place(
                                                code = -8_100_000_000L,
                                                name = allocation.name.ifBlank { "مکان NV ${allocation.code}" },
                                                coordinate = allocation.coordinate,
                                                category = if (allocation.online) "nv:global" else "nv:local"
                                            )
                                            viewModel.savePersonalCode(place, allocation.code)
                                            withContext(Dispatchers.IO) { qrManager.createAndSave(allocation.code, place.name, place.coordinate) }
                                                .onSuccess { savedQr = it; allocationOnline = allocation.online }
                                                .onFailure { error = it.message ?: "ساخت QR ناموفق بود" }
                                        }.onFailure { error = it.message ?: "دریافت کد NV ناموفق بود" }
                                        allocating = false
                                    }
                                },
                                enabled = selectedPoint != null && !allocating,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (allocating) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(7.dp))
                                    Text("در حال دریافت کد…")
                                } else {
                                    Icon(Icons.Rounded.AutoAwesome, null)
                                    Spacer(Modifier.width(7.dp))
                                    Text("دریافت کد NV", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V11SignalOverlay(
    direction: RouteManeuver.Direction?,
    instruction: String,
    distanceMeters: Double,
    modifier: Modifier = Modifier
) {
    val text = instruction.lowercase()
    val inferredLeft = text.contains("چپ") || text.contains("left")
    val inferredRight = text.contains("راست") || text.contains("right")
    val left = direction in setOf(RouteManeuver.Direction.LEFT, RouteManeuver.Direction.SLIGHT_LEFT, RouteManeuver.Direction.SHARP_LEFT) || inferredLeft
    val right = direction in setOf(RouteManeuver.Direction.RIGHT, RouteManeuver.Direction.SLIGHT_RIGHT, RouteManeuver.Direction.SHARP_RIGHT) || inferredRight
    val both = direction == RouteManeuver.Direction.UTURN
    val active = (left || right || both) && (distanceMeters <= 1500.0 || distanceMeters <= 0.0)
    if (!active) return

    val transition = rememberInfiniteTransition(label = "v11-indicator")
    val blink by transition.animateFloat(
        initialValue = .20f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(260), repeatMode = RepeatMode.Reverse),
        label = "v11-indicator-blink"
    )

    Row(modifier.width(220.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (left || both) {
            Surface(shape = CircleShape, color = V11Gold, border = BorderStroke(4.dp, Color.White), modifier = Modifier.size(68.dp).alpha(blink)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ArrowBack, "راهنمای چپ", tint = Color.Black, modifier = Modifier.size(46.dp)) }
            }
        } else Spacer(Modifier.size(68.dp))
        if (right || both) {
            Surface(shape = CircleShape, color = V11Gold, border = BorderStroke(4.dp, Color.White), modifier = Modifier.size(68.dp).alpha(blink)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ArrowForward, "راهنمای راست", tint = Color.Black, modifier = Modifier.size(46.dp)) }
            }
        } else Spacer(Modifier.size(68.dp))
    }
}

@Composable
private fun V11WeatherCard(state: NvUiState, modifier: Modifier = Modifier) {
    val weather = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.WEATHER }
    val attraction = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.ATTRACTION }
    if (weather == null && attraction == null) return
    Surface(
        modifier = modifier.widthIn(min = 126.dp, max = 168.dp),
        color = V11Panel,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, V11Cyan.copy(alpha = .5f))
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            weather?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Cloud, null, tint = V11Cyan, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(Regex("(-?\\d+)°").find(it.detail)?.groupValues?.getOrNull(1)?.let { t -> "$t°" } ?: "هوا", color = Color.White, fontWeight = FontWeight.Black)
                }
            }
            attraction?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Landscape, null, tint = V11Gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(it.title.take(20), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

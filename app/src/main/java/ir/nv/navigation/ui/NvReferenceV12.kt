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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.data.NvCodeAllocationService
import ir.nv.navigation.data.NvLocalSequentialCodeAllocator
import ir.nv.navigation.data.NvQrShareManager
import ir.nv.navigation.map.NvCodePickerMap
import ir.nv.navigation.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val V12Cyan = Color(0xFF14D8FF)
private val V12Gold = Color(0xFFFFB52E)
private val V12Green = Color(0xFF43E66B)
private val V12Panel = Color(0xF2071B2B)

@Composable
fun NvReferenceV12(
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
    var previousOrigin by remember { mutableStateOf<Place?>(null) }

    fun openCodePicker(initial: Coordinate? = null) {
        selectedPoint = initial ?: state.currentLocation ?: state.destination?.coordinate ?: state.origin?.coordinate
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
            previousOrigin = state.origin
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
            previousOrigin = state.origin
            locatePending = true
            viewModel.useCurrentLocationAsOrigin()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(state.currentLocation, locatePending) {
        if (locatePending && state.currentLocation != null) {
            viewModel.recenterNavigation()
            previousOrigin?.let { old ->
                if (state.origin?.coordinate != old.coordinate) viewModel.selectOrigin(old)
            }
            locatePending = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(pickerVisible, state.navigationActive) {
                if (!pickerVisible && !state.navigationActive) {
                    detectTapGestures(onDoubleTap = { openCodePicker() })
                }
            }
    ) {
        // V8 is the single base surface. V9/V10 are intentionally not stacked, eliminating duplicate End Route controls.
        NvReferenceV8(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )

        // My Location is always available in browse mode and driving mode.
        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(60.dp),
            color = V12Panel,
            shape = CircleShape,
            border = BorderStroke(2.dp, V12Cyan),
            shadowElevation = 10.dp
        ) {
            IconButton(onClick = ::locateMe) {
                if (state.locating) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp, color = V12Cyan)
                } else {
                    Icon(Icons.Rounded.MyLocation, "موقعیت من", tint = V12Cyan, modifier = Modifier.size(32.dp))
                }
            }
        }

        // Manual shortcut for automatic NV-code mode. Double-tap on the browse map opens the same mode.
        if (!state.navigationActive) {
            Surface(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(58.dp),
                color = V12Panel,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(2.dp, V12Gold),
                shadowElevation = 8.dp
            ) {
                IconButton(onClick = { openCodePicker() }) {
                    Icon(Icons.Rounded.QrCode2, "کد NV", tint = V12Gold, modifier = Modifier.size(30.dp))
                }
            }
        }

        V12WeatherCard(
            state = state,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 10.dp, bottom = 98.dp)
        )

        if (state.navigationActive) {
            V12StrongSignal(
                direction = state.route?.maneuvers?.getOrNull(state.maneuverIndex)?.direction,
                instruction = state.route?.maneuvers?.getOrNull(state.maneuverIndex)?.instruction.orEmpty(),
                distanceMeters = state.distanceToNextManeuverMeters,
                modifier = Modifier.align(Alignment.Center)
            )
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
                        color = V12Panel,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, V12Cyan)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("کد NV خودکار 1 تا N", color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                                IconButton(onClick = { pickerVisible = false }) {
                                    Icon(Icons.Rounded.Close, "بستن", tint = Color.White)
                                }
                            }
                            Text("روی محل دلخواه دوبار ضربه بزنید یا چند لحظه نگه دارید.", color = Color(0xFFB7C9D4))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ماهواره‌ای", color = Color.White, modifier = Modifier.weight(1f))
                                Switch(checked = pickerSatellite, onCheckedChange = { pickerSatellite = it })
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(10.dp).fillMaxWidth(),
                        color = V12Panel,
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, V12Cyan)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                selectedPoint?.let { "مختصات: %.6f, %.6f".format(it.latitude, it.longitude) }
                                    ?: "یک نقطه روی نقشه انتخاب کنید",
                                color = if (selectedPoint == null) V12Gold else Color.White,
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

                            if (onlineAllocator.isConfigured()) {
                                Text("Registry مرکزی فعال است؛ شماره جهانی و غیرتکراری دریافت می‌شود.", color = V12Green, style = MaterialTheme.typography.labelSmall)
                            } else {
                                Text("Registry عمومی هنوز Deploy نشده؛ شماره فعلاً روی همین دستگاه 1 تا N است.", color = Color(0xFFFFC66D), style = MaterialTheme.typography.labelSmall)
                            }

                            error?.let { Text(it, color = Color(0xFFFF6B78), fontWeight = FontWeight.Bold) }

                            savedQr?.let { qr ->
                                Text(
                                    "NV:${qr.code} • ${if (allocationOnline) "آنلاین / جهانی" else "محلی"}",
                                    color = if (allocationOnline) V12Green else V12Gold,
                                    fontWeight = FontWeight.Black
                                )
                                Button(
                                    onClick = { qrManager.share(qr) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = V12Green, contentColor = Color.Black)
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
                                        val allocationResult = withContext(Dispatchers.IO) {
                                            if (onlineAllocator.isConfigured()) {
                                                onlineAllocator.allocateOnline(cleanName, chosen)
                                            } else {
                                                val code = localAllocator.nextCode(state.personalPlaces.mapNotNull { it.personalCode })
                                                Result.success(NvCodeAllocationService.Allocation(code, cleanName, chosen, false))
                                            }
                                        }
                                        allocationResult.onSuccess { allocation ->
                                            val place = Place(
                                                code = -8_200_000_000L,
                                                name = allocation.name.ifBlank { "مکان NV ${allocation.code}" },
                                                coordinate = allocation.coordinate,
                                                category = if (allocation.online) "nv:global" else "nv:local"
                                            )
                                            viewModel.savePersonalCode(place, allocation.code)
                                            withContext(Dispatchers.IO) {
                                                qrManager.createAndSave(allocation.code, place.name, place.coordinate)
                                            }.onSuccess {
                                                savedQr = it
                                                allocationOnline = allocation.online
                                            }.onFailure {
                                                error = it.message ?: "ساخت QR ناموفق بود"
                                            }
                                        }.onFailure {
                                            error = it.message ?: "دریافت کد NV ناموفق بود"
                                        }
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
private fun V12StrongSignal(
    direction: RouteManeuver.Direction?,
    instruction: String,
    distanceMeters: Double,
    modifier: Modifier = Modifier
) {
    val instructionText = instruction.lowercase()
    val left = direction == RouteManeuver.Direction.LEFT ||
        direction == RouteManeuver.Direction.SLIGHT_LEFT ||
        direction == RouteManeuver.Direction.SHARP_LEFT ||
        instructionText.contains("چپ") || instructionText.contains("left")
    val right = direction == RouteManeuver.Direction.RIGHT ||
        direction == RouteManeuver.Direction.SLIGHT_RIGHT ||
        direction == RouteManeuver.Direction.SHARP_RIGHT ||
        instructionText.contains("راست") || instructionText.contains("right")
    val both = direction == RouteManeuver.Direction.UTURN
    val active = (left || right || both) && (distanceMeters <= 1500.0 || distanceMeters <= 0.0)
    if (!active) return

    val transition = rememberInfiniteTransition(label = "v12-signal")
    val blink by transition.animateFloat(
        initialValue = .15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(250), repeatMode = RepeatMode.Reverse),
        label = "v12-signal-blink"
    )

    Row(
        modifier = modifier.width(225.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (left || both) {
            Surface(
                shape = CircleShape,
                color = V12Gold,
                border = BorderStroke(4.dp, Color.White),
                modifier = Modifier.size(68.dp).alpha(blink)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBack, "راهنمای چپ", tint = Color.Black, modifier = Modifier.size(46.dp))
                }
            }
        } else Spacer(Modifier.size(68.dp))

        if (right || both) {
            Surface(
                shape = CircleShape,
                color = V12Gold,
                border = BorderStroke(4.dp, Color.White),
                modifier = Modifier.size(68.dp).alpha(blink)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowForward, "راهنمای راست", tint = Color.Black, modifier = Modifier.size(46.dp))
                }
            }
        } else Spacer(Modifier.size(68.dp))
    }
}

@Composable
private fun V12WeatherCard(state: NvUiState, modifier: Modifier = Modifier) {
    val weather = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.WEATHER }
    val attraction = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.ATTRACTION }
    if (weather == null && attraction == null) return

    Surface(
        modifier = modifier.widthIn(min = 126.dp, max = 170.dp),
        color = V12Panel,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, V12Cyan.copy(alpha = .5f))
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            weather?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Cloud, null, tint = V12Cyan, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            Regex("(-?\\d+)°").find(it.detail)?.groupValues?.getOrNull(1)?.let { t -> "$t°" } ?: "هوا",
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                        Text(it.detail.substringBefore("•").trim().take(16), color = Color(0xFFB7C9D4), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            attraction?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Landscape, null, tint = V12Gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(it.title.take(20), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

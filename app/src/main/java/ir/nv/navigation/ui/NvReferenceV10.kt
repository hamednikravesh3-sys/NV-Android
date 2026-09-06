package ir.nv.navigation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.data.NvCodeAllocationService
import ir.nv.navigation.data.NvLocalSequentialCodeAllocator
import ir.nv.navigation.data.NvQrShareManager
import ir.nv.navigation.map.NvCodePickerMap
import ir.nv.navigation.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val V10Cyan = Color(0xFF14D8FF)
private val V10Gold = Color(0xFFFFB52E)
private val V10Green = Color(0xFF43E66B)
private val V10Panel = Color(0xF2071B2B)

@Composable
fun NvReferenceV10(
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

    Box(Modifier.fillMaxSize()) {
        NvReferenceV9(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )

        // Main-map current-location/recenter control.
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .size(58.dp)
                .clickable(enabled = state.currentLocation != null) { viewModel.recenterNavigation() },
            shape = CircleShape,
            color = V10Panel,
            border = BorderStroke(2.dp, if (state.currentLocation != null) V10Cyan else Color.Gray)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.MyLocation,
                    contentDescription = "موقعیت من",
                    tint = if (state.currentLocation != null) V10Cyan else Color.Gray,
                    modifier = Modifier.size(31.dp)
                )
            }
        }

        // Cover V9 manual-code button with the new automatic 1..N flow.
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 64.dp, bottom = 15.dp)
                .size(56.dp)
                .clickable {
                    selectedPoint = state.currentLocation ?: state.destination?.coordinate ?: state.origin?.coordinate
                    placeName = ""
                    pickerSatellite = state.satelliteMode
                    allocating = false
                    error = null
                    savedQr = null
                    allocationOnline = false
                    pickerVisible = true
                },
            shape = RoundedCornerShape(18.dp),
            color = V10Panel,
            border = BorderStroke(2.dp, V10Gold)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.QrCode2, "کد NV خودکار", tint = V10Gold, modifier = Modifier.size(30.dp))
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
                        color = V10Panel,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, V10Cyan)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.QrCode2, null, tint = V10Gold)
                                Spacer(Modifier.width(7.dp))
                                Text("کد NV خودکار 1 تا N", color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                                IconButton(onClick = { pickerVisible = false }) {
                                    Icon(Icons.Rounded.Close, "بستن", tint = Color.White)
                                }
                            }
                            Text("نقشه را حرکت دهید و روی محل موردنظر Long-press کنید. شماره را خود NV اختصاص می‌دهد.", color = Color(0xFFB7C9D4), style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ماهواره‌ای", color = Color.White, modifier = Modifier.weight(1f))
                                Switch(checked = pickerSatellite, onCheckedChange = { pickerSatellite = it })
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(10.dp).fillMaxWidth(),
                        color = V10Panel,
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, V10Cyan)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val point = selectedPoint
                            Text(
                                point?.let { "مختصات: %.6f, %.6f".format(it.latitude, it.longitude) } ?: "یک نقطه انتخاب کنید",
                                color = if (point == null) V10Gold else Color.White,
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
                                Text(
                                    "Registry عمومی هنوز Deploy نشده؛ کدی که الان ساخته می‌شود محلی است. بعد از تنظیم Registry، همین دکمه کد جهانی و غیرتکراری دریافت می‌کند.",
                                    color = Color(0xFFFFC66D),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else {
                                Text("Registry آنلاین فعال است؛ شماره از Sequence مرکزی و غیرتکراری دریافت می‌شود.", color = V10Green, style = MaterialTheme.typography.labelSmall)
                            }

                            error?.let { Text(it, color = Color(0xFFFF6B78), fontWeight = FontWeight.Bold) }

                            savedQr?.let { qr ->
                                Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                                    AsyncImage(
                                        model = qr.file,
                                        contentDescription = "QR کد NV ${qr.code}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().height(210.dp).padding(10.dp)
                                    )
                                }
                                Text(
                                    "NV:${qr.code}  •  ${if (allocationOnline) "آنلاین / جهانی" else "محلی"}",
                                    color = if (allocationOnline) V10Green else V10Gold,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                                Button(
                                    onClick = { qrManager.share(qr) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = V10Green, contentColor = Color.Black)
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
                                                Result.success(
                                                    NvCodeAllocationService.Allocation(
                                                        code = code,
                                                        name = cleanName,
                                                        coordinate = chosen,
                                                        online = false
                                                    )
                                                )
                                            }
                                        }
                                        allocationResult.onSuccess { allocation ->
                                            val place = Place(
                                                code = -8_000_000_000L,
                                                name = allocation.name.ifBlank { "مکان NV ${allocation.code}" },
                                                coordinate = allocation.coordinate,
                                                category = if (allocation.online) "nv:global" else "nv:local"
                                            )
                                            viewModel.savePersonalCode(place, allocation.code)
                                            val qrResult = withContext(Dispatchers.IO) {
                                                qrManager.createAndSave(allocation.code, place.name, place.coordinate)
                                            }
                                            qrResult.onSuccess {
                                                savedQr = it
                                                allocationOnline = allocation.online
                                            }.onFailure { e ->
                                                error = e.message ?: "ساخت QR ناموفق بود"
                                            }
                                        }.onFailure { e ->
                                            error = e.message ?: "دریافت کد NV ناموفق بود"
                                        }
                                        allocating = false
                                    }
                                },
                                enabled = selectedPoint != null && !allocating,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (allocating) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
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

package ir.nv.navigation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.map.NvCodePickerMap
import ir.nv.navigation.ui.theme.AppThemeMode

private val V9Cyan = Color(0xFF14D8FF)
private val V9Gold = Color(0xFFFFB52E)
private val V9Red = Color(0xFFFF304C)
private val V9Panel = Color(0xEE071B2B)

/**
 * V9 keeps the stable V8 navigation surface and adds the missing production controls:
 * - one compact weather/attraction card
 * - a very visible red End Route control while driving
 * - an arbitrary map-point NV code picker (long press anywhere)
 *
 * Global code uniqueness still requires the central registry backend to be deployed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NvReferenceV9(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pickerVisible by remember { mutableStateOf(false) }
    var selectedPoint by remember { mutableStateOf<Coordinate?>(null) }
    var placeName by remember { mutableStateOf("") }
    var placeCode by remember { mutableStateOf("") }
    var pickerSatellite by remember { mutableStateOf(state.satelliteMode) }
    var codeError by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        NvReferenceV8(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )

        // Cover/re-purpose the old V8 code slot with the correct arbitrary-point picker.
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 64.dp, bottom = 15.dp)
                .size(54.dp)
                .clickable {
                    selectedPoint = null
                    placeName = ""
                    placeCode = ""
                    codeError = null
                    pickerSatellite = state.satelliteMode
                    pickerVisible = true
                },
            shape = RoundedCornerShape(18.dp),
            color = V9Panel,
            border = BorderStroke(1.dp, V9Gold)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.QrCode2, "تعریف کد مکان", tint = V9Gold, modifier = Modifier.size(28.dp))
            }
        }

        V9WeatherCard(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = if (state.navigationActive) 156.dp else 96.dp)
        )

        if (state.navigationActive) {
            Button(
                onClick = viewModel::stopNavigation,
                colors = ButtonDefaults.buttonColors(containerColor = V9Red, contentColor = Color.White),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 86.dp)
                    .height(54.dp)
                    .widthIn(min = 168.dp)
            ) {
                Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("پایان مسیر", fontWeight = FontWeight.Black)
            }
        }

        if (pickerVisible) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(Modifier.fillMaxSize()) {
                    NvCodePickerMap(
                        context = context,
                        initial = selectedPoint ?: state.currentLocation ?: state.destination?.coordinate ?: state.origin?.coordinate,
                        satellite = pickerSatellite,
                        onPointSelected = { point ->
                            selectedPoint = point
                            codeError = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(10.dp)
                            .fillMaxWidth(),
                        color = V9Panel,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, V9Cyan)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("تعریف کد NV برای هر نقطه", color = Color.White, fontWeight = FontWeight.Black)
                            Text("روی هر نقطه نقشه چند لحظه نگه دارید (Long-press).", color = Color(0xFFB7C9D4), style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ماهواره‌ای", color = Color.White, modifier = Modifier.weight(1f))
                                Switch(checked = pickerSatellite, onCheckedChange = { pickerSatellite = it })
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(10.dp)
                            .fillMaxWidth(),
                        color = V9Panel,
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, V9Cyan)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val point = selectedPoint
                            Text(
                                point?.let { "نقطه: %.6f, %.6f".format(it.latitude, it.longitude) }
                                    ?: "هنوز نقطه‌ای انتخاب نشده",
                                color = if (point == null) V9Gold else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = placeName,
                                onValueChange = { placeName = it.take(60) },
                                label = { Text("نام مکان") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = placeCode,
                                onValueChange = { placeCode = it.filter(Char::isDigit).take(9) },
                                label = { Text("کد NV عددی") },
                                placeholder = { Text("مثلاً 1250") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "ثبت جهانی و جلوگیری از تکرار بین همه کاربران فقط بعد از اتصال این نسخه به NV Code Registry مرکزی قطعی می‌شود.",
                                color = Color(0xFFFFC66D),
                                style = MaterialTheme.typography.labelSmall
                            )
                            codeError?.let { Text(it, color = Color(0xFFFF6B78), fontWeight = FontWeight.Bold) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { pickerVisible = false }, modifier = Modifier.weight(1f)) {
                                    Text("انصراف")
                                }
                                Button(
                                    onClick = {
                                        val chosen = selectedPoint
                                        if (chosen == null) {
                                            codeError = "ابتدا روی نقشه یک نقطه انتخاب کنید"
                                            return@Button
                                        }
                                        if (placeCode.isBlank()) {
                                            codeError = "کد NV را وارد کنید"
                                            return@Button
                                        }
                                        val duplicate = state.personalPlaces.any { it.personalCode == placeCode }
                                        if (duplicate) {
                                            codeError = "این کد قبلاً روی این دستگاه استفاده شده است"
                                            return@Button
                                        }
                                        val place = Place(
                                            code = -9_000_000_000L,
                                            name = placeName.trim().ifBlank { "مکان NV $placeCode" },
                                            coordinate = chosen,
                                            category = "personal:map-picked"
                                        )
                                        viewModel.savePersonalCode(place, placeCode)
                                        pickerVisible = false
                                    },
                                    enabled = selectedPoint != null && placeCode.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("ذخیره")
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
private fun V9WeatherCard(state: NvUiState, modifier: Modifier = Modifier) {
    val weather = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.WEATHER }
    val attraction = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.ATTRACTION }
    if (weather == null && attraction == null) return

    Surface(
        modifier = modifier.widthIn(min = 132.dp, max = 180.dp),
        color = V9Panel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, V9Cyan.copy(alpha = .55f)),
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            weather?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Cloud, null, tint = V9Cyan, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            Regex("(-?\\d+)°").find(it.detail)?.groupValues?.getOrNull(1)?.let { t -> "$t°" } ?: "هوا",
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                        Text(it.detail.substringBefore("•").trim().take(18), color = Color(0xFFB7C9D4), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (weather != null && attraction != null) HorizontalDivider(color = Color.White.copy(alpha = .14f))
            attraction?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null, tint = V9Gold, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(it.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("%.1f km جلوتر".format(it.distanceAheadMeters / 1000.0), color = Color(0xFFB7C9D4), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

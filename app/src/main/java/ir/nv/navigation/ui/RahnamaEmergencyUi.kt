package ir.nv.navigation.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Emergency
import androidx.compose.material.icons.rounded.FireTruck
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.Place
import ir.nv.navigation.online.OnlinePlacesService
import ir.nv.navigation.ui.theme.NvColors
import ir.nv.navigation.ui.theme.NvRadius
import ir.nv.navigation.ui.theme.NvSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class EmergencyService(
    val title: String,
    val number: String,
    val query: String,
    val icon: ImageVector,
    val accent: Color
)

private val IranEmergencyServices = listOf(
    EmergencyService("اورژانس پزشکی", "115", "اورژانس", Icons.Rounded.LocalHospital, NvColors.Emergency),
    EmergencyService("پلیس", "110", "پلیس", Icons.Rounded.LocalPolice, NvColors.Info),
    EmergencyService("آتش‌نشانی", "125", "آتش نشانی", Icons.Rounded.FireTruck, NvColors.Warning),
    EmergencyService("امداد و نجات", "112", "امداد و نجات", Icons.Rounded.HealthAndSafety, NvColors.Success)
)

@Composable
fun RahnamaEmergencyOverlay(
    state: NvUiState,
    viewModel: NvViewModel,
    onDismiss: () -> Unit,
    onRouteToPlace: (Place) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val placesService = remember { OnlinePlacesService() }
    var selected by remember { mutableStateOf(IranEmergencyServices.first()) }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun dial(number: String) {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }

    fun search(service: EmergencyService = selected) {
        selected = service
        val center = state.currentLocation ?: state.origin?.coordinate
        if (center == null) {
            message = "ابتدا موقعیت فعلی را دریافت کنید"
            viewModel.useCurrentLocationAsOrigin()
            return
        }
        if (!state.onlineAvailable) {
            message = "برای جستجوی مراکز اضطراری نزدیک، اتصال اینترنت لازم است؛ تماس اضطراری همچنان در دسترس است"
            results = emptyList()
            return
        }
        loading = true
        message = null
        results = emptyList()
        scope.launch {
            val found = runCatching {
                withContext(Dispatchers.IO) {
                    placesService.searchNearby(center, service.query, radiusMeters = 25_000, limit = 20)
                }
            }
            found.onSuccess {
                results = it
                if (it.isEmpty()) message = "مرکز نزدیک در شعاع ۲۵ کیلومتر پیدا نشد"
            }.onFailure {
                message = it.message ?: "جستجوی مراکز اضطراری ناموفق بود"
            }
            loading = false
        }
    }

    LaunchedEffect(state.currentLocation, state.origin, state.onlineAvailable) {
        if (state.currentLocation == null && state.origin == null) {
            viewModel.useCurrentLocationAsOrigin()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NvColors.Navy900,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = NvColors.Emergency.copy(alpha = .16f),
                    border = BorderStroke(1.dp, NvColors.Emergency.copy(alpha = .55f))
                ) {
                    Icon(Icons.Rounded.Emergency, null, tint = NvColors.Emergency, modifier = Modifier.padding(9.dp).size(26.dp))
                }
                Spacer(Modifier.width(NvSpacing.Sm))
                Column(Modifier.weight(1f)) {
                    Text("SOS و خدمات اضطراری", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Black)
                    Text("تماس سریع یا مسیریابی به نزدیک‌ترین مرکز", color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "بستن", tint = NvColors.TextPrimaryDark) }
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)
            ) {
                Surface(
                    color = NvColors.Emergency.copy(alpha = .12f),
                    shape = RoundedCornerShape(NvRadius.Card),
                    border = BorderStroke(1.dp, NvColors.Emergency.copy(alpha = .45f))
                ) {
                    Column(Modifier.padding(NvSpacing.Md), verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                        Text("در خطر فوری؟", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Black)
                        Text("یکی از سرویس‌های زیر را انتخاب کنید. برنامه شماره را در شماره‌گیر باز می‌کند و تماس را خودکار برقرار نمی‌کند.", color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.bodySmall)
                    }
                }

                IranEmergencyServices.forEach { service ->
                    val active = selected == service
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selected = service
                            search(service)
                        },
                        color = if (active) service.accent.copy(alpha = .13f) else NvColors.Navy850,
                        shape = RoundedCornerShape(NvRadius.Medium),
                        border = BorderStroke(1.dp, if (active) service.accent else NvColors.DividerDark)
                    ) {
                        Row(Modifier.padding(NvSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                            Icon(service.icon, null, tint = service.accent, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(NvSpacing.Sm))
                            Column(Modifier.weight(1f)) {
                                Text(service.title, color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Bold)
                                Text(service.number, color = service.accent, fontWeight = FontWeight.Black)
                            }
                            FilledTonalButton(onClick = { dial(service.number) }) {
                                Icon(Icons.Rounded.Call, null)
                                Spacer(Modifier.width(4.dp))
                                Text("تماس")
                            }
                        }
                    }
                }

                Button(
                    onClick = { search(selected) },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.LocationOn, null)
                    Spacer(Modifier.width(NvSpacing.Sm))
                    Text("یافتن نزدیک‌ترین ${selected.title}")
                }

                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, color = NvColors.Warning, style = MaterialTheme.typography.bodySmall) }

                if (results.isNotEmpty()) {
                    Text("مراکز نزدیک", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Black)
                    results.take(10).forEach { place ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onRouteToPlace(place) },
                            color = NvColors.Navy850,
                            shape = RoundedCornerShape(NvRadius.Medium),
                            border = BorderStroke(1.dp, NvColors.DividerDark)
                        ) {
                            Row(Modifier.padding(NvSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                                Icon(selected.icon, null, tint = selected.accent)
                                Spacer(Modifier.width(NvSpacing.Sm))
                                Column(Modifier.weight(1f)) {
                                    Text(place.name, color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val details = buildList {
                                        place.distance?.let { add(if (it >= 1000) "%.1f کیلومتر".format(it / 1000.0) else "${it.toInt()} متر") }
                                        place.address?.takeIf(String::isNotBlank)?.let(::add)
                                        place.isOpen?.let { add(if (it) "باز" else "بسته") }
                                    }.joinToString(" • ")
                                    if (details.isNotBlank()) Text(details, color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Rounded.ChevronLeft, "مسیریابی", tint = NvColors.RouteBlue)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

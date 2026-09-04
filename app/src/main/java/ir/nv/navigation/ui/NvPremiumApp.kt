package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.nv.navigation.core.Place
import ir.nv.navigation.ui.theme.AppThemeMode

private val PremiumNavy = Color(0xFF071526)
private val PremiumPanelHigh = Color(0xFF102C45)
private val PremiumCyan = Color(0xFF18D4FF)
private val PremiumMuted = Color(0xFF91A9BC)
private val PremiumOutline = Color(0xFF25445E)
private val PremiumInk = Color(0xFF031421)
private val PremiumOrigin = Color(0xFF4C8DFF)
private val PremiumDestination = Color(0xFFFF5C76)
private val PremiumText = Color(0xFFF4FAFF)

@Composable
fun NvPremiumApp(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showPlanner by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) viewModel.useCurrentLocationAsOrigin()
    }

    fun useMyLocationAsOptionalOrigin() {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (allowed) viewModel.useCurrentLocationAsOrigin()
        else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Box(Modifier.fillMaxSize()) {
        NvApp(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )

        if (!state.navigationActive && state.route == null && (state.onlineAvailable || state.offlineReady)) {
            PremiumHomeHeader(
                state = state,
                onOpenPlanner = { showPlanner = true },
                onUseMyLocation = ::useMyLocationAsOptionalOrigin,
                onSwap = viewModel::swapEndpoints,
                onRoute = {
                    if (state.origin != null && state.destination != null) viewModel.calculateRoute()
                    else showPlanner = true
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )

            PremiumPoiRail(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp, bottom = 42.dp)
            )
        }

        if (showPlanner) {
            SearchSheet(
                state = state,
                onDismiss = { showPlanner = false },
                onOriginChange = viewModel::updateOriginQuery,
                onDestinationChange = viewModel::updateDestinationQuery,
                onOriginSelect = viewModel::selectOrigin,
                onDestinationSelect = viewModel::selectDestination,
                onSwap = viewModel::swapEndpoints,
                onRoute = {
                    viewModel.calculateRoute()
                    showPlanner = false
                },
                onUseCurrentLocation = ::useMyLocationAsOptionalOrigin,
                onSaveCode = { }
            )
        }
    }
}

@Composable
private fun PremiumHomeHeader(
    state: NvUiState,
    onOpenPlanner: () -> Unit,
    onUseMyLocation: () -> Unit,
    onSwap: () -> Unit,
    onRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = PremiumNavy,
        contentColor = PremiumText,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, PremiumOutline)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = PremiumCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, PremiumCyan.copy(alpha = 0.35f))
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("➤", color = PremiumCyan, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(5.dp))
                        Text("NV", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.weight(1f).clickable(onClick = onOpenPlanner),
                    shape = RoundedCornerShape(16.dp),
                    color = PremiumPanelHigh,
                    border = BorderStroke(1.dp, PremiumOutline)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, null, tint = PremiumCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("نام، آدرس یا کد NV…", color = PremiumMuted, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.WbSunny, null, tint = Color(0xFFFFD84D), modifier = Modifier.size(24.dp))
                    Text("ایران", color = PremiumText, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Rounded.AccountCircle, null, tint = PremiumMuted, modifier = Modifier.size(31.dp))
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("مبدأ و مقصد مستقل", color = PremiumCyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Text(
                        "هر دو نقطه را با نام، کد NV یا جستجو انتخاب کنید؛ GPS فقط یک گزینه اختیاری برای مبدأ است.",
                        color = PremiumMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onSwap) { Icon(Icons.Rounded.SwapVert, "جابه‌جایی", tint = PremiumCyan) }
            }

            EndpointRow(
                label = "مبدأ",
                place = state.origin,
                emptyText = "مبدأ را خودم انتخاب می‌کنم",
                marker = PremiumOrigin,
                onClick = onOpenPlanner,
                trailing = {
                    Surface(
                        modifier = Modifier.clickable(onClick = onUseMyLocation),
                        shape = RoundedCornerShape(12.dp),
                        color = PremiumCyan.copy(alpha = 0.13f),
                        border = BorderStroke(1.dp, PremiumCyan.copy(alpha = 0.28f))
                    ) {
                        Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (state.locating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = PremiumCyan)
                            else Icon(Icons.Rounded.MyLocation, null, tint = PremiumCyan, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("GPS اختیاری", color = PremiumCyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )
            HorizontalDivider(color = PremiumOutline, modifier = Modifier.padding(horizontal = 24.dp))
            EndpointRow(
                label = "مقصد",
                place = state.destination,
                emptyText = "مقصد را خودم انتخاب می‌کنم",
                marker = PremiumDestination,
                onClick = onOpenPlanner,
                trailing = { Icon(Icons.Rounded.Place, null, tint = PremiumDestination, modifier = Modifier.size(22.dp)) }
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRoute,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.routing,
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PremiumCyan,
                    contentColor = PremiumInk,
                    disabledContainerColor = PremiumCyan.copy(alpha = 0.55f),
                    disabledContentColor = PremiumInk.copy(alpha = 0.7f)
                )
            ) {
                if (state.routing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = PremiumInk)
                else Icon(Icons.Rounded.DirectionsCar, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.origin != null && state.destination != null) "نمایش مسیرهای پیشنهادی" else "انتخاب آزاد مبدأ و مقصد", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun EndpointRow(
    label: String,
    place: Place?,
    emptyText: String,
    marker: Color,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(11.dp).background(marker, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = PremiumMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(place?.name ?: emptyText, color = PremiumText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            place?.let {
                val displayCode = it.personalCode?.takeIf(String::isNotBlank) ?: it.code.takeIf { code -> code > 0 }?.let { code -> "NV:$code" }
                if (displayCode != null) {
                    Text(displayCode, color = PremiumCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.width(7.dp))
        trailing()
    }
}

@Composable
private fun PremiumPoiRail(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PoiButton(Icons.Rounded.Layers, "لایه‌ها")
        PoiButton(Icons.Rounded.LocalGasStation, "بنزین")
        PoiButton(Icons.Rounded.Restaurant, "رستوران")
        PoiButton(Icons.Rounded.Hotel, "اقامتگاه")
    }
}

@Composable
private fun PoiButton(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = PremiumNavy,
        border = BorderStroke(1.dp, PremiumOutline),
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = PremiumCyan, modifier = Modifier.size(22.dp))
            Text(label, color = PremiumText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

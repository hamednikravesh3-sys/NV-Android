package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.nv.navigation.core.Place
import ir.nv.navigation.ui.theme.AppThemeMode

private val PremiumNavy = Color(0xF2071728)
private val PremiumPanel = Color(0xF20D2B45)
private val PremiumCyan = Color(0xFF00E5FF)
private val PremiumMuted = Color(0xFFA0B6C7)
private val PremiumText = Color(0xFFF7FBFF)

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
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) viewModel.useCurrentLocationAsOrigin()
    }
    fun useMyLocation() {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (allowed) viewModel.useCurrentLocationAsOrigin() else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Box(Modifier.fillMaxSize()) {
        NvApp(darkMode = darkMode, themeMode = themeMode, onThemeModeChange = onThemeModeChange, viewModel = viewModel, premiumShell = true)
        if (!state.navigationActive && state.route == null && (state.onlineAvailable || state.offlineReady)) {
            MapFirstHeader(
                origin = state.origin?.name,
                destination = state.destination?.name,
                onSearch = { showPlanner = !showPlanner },
                onMyLocation = ::useMyLocation,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 10.dp, vertical = 7.dp)
            )
            if (!showPlanner) {
                RightRouteInsightRail(
                    notices = emptyList(),
                    loading = false,
                    satelliteMode = state.satelliteMode,
                    darkMode = darkMode,
                    onlineAvailable = state.onlineAvailable,
                    onWeather = { showPlanner = true },
                    onAttractions = { showPlanner = true },
                    onToggleSatellite = viewModel::toggleSatelliteMode,
                    onToggleTheme = {
                        onThemeModeChange(if (darkMode) AppThemeMode.DAY else AppThemeMode.NIGHT)
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 7.dp)
                )
            }
        }
        if (showPlanner && !state.navigationActive) {
            CompactRoutePlanner(
                state = state,
                onClose = { showPlanner = false },
                onOriginChange = viewModel::updateOriginQuery,
                onDestinationChange = viewModel::updateDestinationQuery,
                onOriginSelect = viewModel::selectOrigin,
                onDestinationSelect = viewModel::selectDestination,
                onMyLocation = ::useMyLocation,
                onSwap = viewModel::swapEndpoints,
                onRoute = { viewModel.calculateRoute(); showPlanner = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            )
        }
    }
}

@Composable
private fun MapFirstHeader(origin: String?, destination: String?, onSearch: () -> Unit, onMyLocation: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Surface(shape = RoundedCornerShape(15.dp), color = PremiumNavy, border = BorderStroke(1.dp, PremiumCyan.copy(alpha = .45f)), shadowElevation = 10.dp) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("➤", color = PremiumCyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(4.dp)); Text("NV", color = PremiumText, fontWeight = FontWeight.Black)
            }
        }
        Surface(modifier = Modifier.weight(1f).clickable(onClick = onSearch), shape = RoundedCornerShape(16.dp), color = PremiumNavy, border = BorderStroke(1.dp, PremiumCyan.copy(alpha = .28f)), shadowElevation = 10.dp) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = PremiumCyan, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(destination ?: "کجا می‌روید؟", color = if (destination == null) PremiumMuted else PremiumText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (origin != null) Text("از: $origin", color = PremiumMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Surface(shape = RoundedCornerShape(15.dp), color = PremiumNavy, border = BorderStroke(1.dp, PremiumCyan.copy(alpha = .28f))) {
            IconButton(onClick = onMyLocation, modifier = Modifier.size(45.dp)) { Icon(Icons.Rounded.MyLocation, "موقعیت من", tint = PremiumCyan) }
        }
    }
}

@Composable
private fun CompactRoutePlanner(
    state: NvUiState,
    onClose: () -> Unit,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onOriginSelect: (Place) -> Unit,
    onDestinationSelect: (Place) -> Unit,
    onMyLocation: () -> Unit,
    onSwap: () -> Unit,
    onRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = PremiumNavy, border = BorderStroke(1.dp, PremiumCyan.copy(.42f)), shadowElevation = 14.dp) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("مسیر", color = PremiumText, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                IconButton(onClick = onSwap, enabled = state.origin != null && state.destination != null, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.SwapVert, "جابجایی", tint = PremiumCyan) }
                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.Close, "بستن", tint = PremiumMuted) }
            }
            CompactPlaceField("مبدأ", state.originQuery, state.originSuggestions, state.originSearching, onOriginChange, onOriginSelect, onMyLocation)
            CompactPlaceField("مقصد", state.destinationQuery, state.destinationSuggestions, state.destinationSearching, onDestinationChange, onDestinationSelect, null)
            if (state.origin != null && state.destination != null) {
                Button(onClick = onRoute, enabled = !state.routing, modifier = Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = PremiumCyan, contentColor = Color(0xFF02151F))) {
                    if (state.routing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Navigation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("نمایش مسیرها", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun CompactPlaceField(label: String, value: String, suggestions: List<Place>, searching: Boolean, onChange: (String) -> Unit, onSelect: (Place) -> Unit, onLocation: (() -> Unit)?) {
    Column {
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth().height(54.dp), singleLine = true,
            label = { Text(label) },
            leadingIcon = { Icon(if (label == "مبدأ") Icons.Rounded.TripOrigin else Icons.Rounded.Place, null, tint = PremiumCyan, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searching) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = PremiumCyan)
                    if (value.isNotBlank()) IconButton(onClick = { onChange("") }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, "پاک کردن", tint = PremiumText, modifier = Modifier.size(19.dp)) }
                    else if (onLocation != null) IconButton(onClick = onLocation, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.MyLocation, "موقعیت فعلی", tint = PremiumCyan, modifier = Modifier.size(18.dp)) }
                }
            },
            shape = RoundedCornerShape(13.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = PremiumText, unfocusedTextColor = PremiumText, focusedBorderColor = PremiumCyan, unfocusedBorderColor = Color(0xFF31516A), focusedLabelColor = PremiumCyan, unfocusedLabelColor = PremiumMuted, cursorColor = PremiumCyan)
        )
        if (value.isNotBlank() && suggestions.isNotEmpty()) {
            Surface(Modifier.fillMaxWidth().padding(top = 3.dp), shape = RoundedCornerShape(11.dp), color = PremiumPanel) {
                Column {
                    suggestions.take(3).forEach { place ->
                        Row(Modifier.fillMaxWidth().clickable { onSelect(place) }.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Place, null, tint = PremiumCyan, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp))
                            Text(place.name, color = PremiumText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            if (place.code > 0) Text("NV:${place.code}", color = PremiumMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactPoiRail(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        PoiIcon(Icons.Rounded.WbSunny, "هوا")
        PoiIcon(Icons.Rounded.Explore, "دیدنی")
        PoiIcon(Icons.Rounded.LocalGasStation, "سوخت")
        PoiIcon(Icons.Rounded.Restaurant, "غذا")
    }
}

@Composable
private fun PoiIcon(icon: ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(11.dp), color = PremiumNavy, border = BorderStroke(1.dp, PremiumCyan.copy(alpha = .25f)), shadowElevation = 6.dp) {
        Column(Modifier.width(45.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = PremiumCyan, modifier = Modifier.size(19.dp))
            Text(label, color = PremiumText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

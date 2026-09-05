package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WbSunny
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
import ir.nv.navigation.ui.theme.AppThemeMode

private val PremiumNavy = Color(0xF2071728)
private val PremiumPanel = Color(0xF20D2B45)
private val PremiumCyan = Color(0xFF18D9FF)
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
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (allowed) viewModel.useCurrentLocationAsOrigin() else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Box(Modifier.fillMaxSize()) {
        NvApp(darkMode = darkMode, themeMode = themeMode, onThemeModeChange = onThemeModeChange, viewModel = viewModel, premiumShell = true)
        if (!state.navigationActive && state.route == null && (state.onlineAvailable || state.offlineReady)) {
            MapFirstHeader(
                origin = state.origin?.name,
                destination = state.destination?.name,
                onSearch = { showPlanner = true },
                onMyLocation = ::useMyLocation,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 10.dp, vertical = 7.dp)
            )
            CompactPoiRail(Modifier.align(Alignment.CenterEnd).padding(end = 9.dp))
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
                onRoute = { viewModel.calculateRoute(); showPlanner = false },
                onUseCurrentLocation = ::useMyLocation,
                onSaveCode = { }
            )
        }
    }
}

@Composable
private fun MapFirstHeader(origin: String?, destination: String?, onSearch: () -> Unit, onMyLocation: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Surface(shape = RoundedCornerShape(15.dp), color = PremiumNavy, border = BorderStroke(1.dp, PremiumCyan.copy(alpha = .45f)), shadowElevation = 12.dp) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("➤", color = PremiumCyan, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(4.dp))
                Text("NV", color = PremiumText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            }
        }
        Surface(modifier = Modifier.weight(1f).clickable(onClick = onSearch), shape = RoundedCornerShape(16.dp), color = PremiumNavy, border = BorderStroke(1.dp, PremiumCyan.copy(alpha = .28f)), shadowElevation = 12.dp) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = PremiumCyan, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(destination ?: "کجا می‌روید؟ نام، آدرس یا کد NV", color = if (destination == null) PremiumMuted else PremiumText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun CompactPoiRail(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        PoiIcon(Icons.Rounded.WbSunny, "هوا")
        PoiIcon(Icons.Rounded.LocalGasStation, "سوخت")
        PoiIcon(Icons.Rounded.Restaurant, "غذا")
        PoiIcon(Icons.Rounded.Hotel, "اقامت")
        PoiIcon(Icons.Rounded.Explore, "دیدنی")
    }
}

@Composable
private fun PoiIcon(icon: ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(13.dp), color = PremiumNavy, border = BorderStroke(1.dp, PremiumCyan.copy(alpha = .25f)), shadowElevation = 8.dp) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = PremiumCyan, modifier = Modifier.size(20.dp))
            Text(label, color = PremiumText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

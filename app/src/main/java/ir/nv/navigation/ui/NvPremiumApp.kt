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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.nv.navigation.ui.theme.AppThemeMode

private val PremiumNavy = Color(0xF2071526)
private val PremiumPanel = Color(0xF20B2035)
private val PremiumCyan = Color(0xFF18D4FF)
private val PremiumLime = Color(0xFFD7FF5B)
private val PremiumMuted = Color(0xFF91A9BC)
private val PremiumOutline = Color(0xFF25445E)
private val PremiumInk = Color(0xFF031421)
private val PremiumOrigin = Color(0xFF4C8DFF)
private val PremiumDestination = Color(0xFFFF5C76)

/**
 * Premium shell used by the launcher activity. It keeps the existing NV engine intact,
 * but makes origin/destination selection explicit on the main map exactly where a driver
 * expects it. Both manual search/code selection and device GPS remain available.
 */
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

    fun useMyLocation() {
        val allowed = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (allowed) {
            viewModel.useCurrentLocationAsOrigin()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        NvApp(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )

        if (!state.navigationActive && state.route == null && (state.onlineAvailable || state.offlineReady)) {
            PremiumEndpointPanel(
                state = state,
                darkMode = darkMode,
                onOpenPlanner = { showPlanner = true },
                onUseMyLocation = ::useMyLocation,
                onSwap = viewModel::swapEndpoints,
                onRoute = {
                    if (state.origin != null && state.destination != null) {
                        viewModel.calculateRoute()
                    } else {
                        showPlanner = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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
                onUseCurrentLocation = ::useMyLocation,
                onSaveCode = { /* code management remains available from the NV dock */ }
            )
        }
    }
}

@Composable
private fun PremiumEndpointPanel(
    state: NvUiState,
    darkMode: Boolean,
    onOpenPlanner: () -> Unit,
    onUseMyLocation: () -> Unit,
    onSwap: () -> Unit,
    onRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelColor = if (darkMode) PremiumNavy else Color.White.copy(alpha = 0.98f)
    val primaryText = if (darkMode) Color(0xFFF4FAFF) else Color(0xFF102A3C)
    val mutedText = if (darkMode) PremiumMuted else Color(0xFF647B8C)
    val lineColor = if (darkMode) PremiumOutline else Color(0xFFD8E3E9)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = panelColor,
        contentColor = primaryText,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, if (darkMode) PremiumOutline else Color.White)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = PremiumCyan.copy(alpha = if (darkMode) 0.16f else 0.12f)
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = if (darkMode) PremiumCyan else Color(0xFF087C9B)
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "مسیر خود را مشخص کنید",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "مبدأ و مقصد را جست‌وجو کنید یا از مکان فعلی استفاده کنید",
                        color = mutedText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onSwap) {
                    Icon(Icons.Rounded.SwapVert, "جابه‌جایی مبدأ و مقصد", tint = if (darkMode) PremiumCyan else Color(0xFF087C9B))
                }
            }

            Spacer(Modifier.size(6.dp))

            EndpointRow(
                title = "مبدأ",
                value = state.origin?.name ?: "مبدأ را انتخاب کنید",
                markerColor = PremiumOrigin,
                textColor = primaryText,
                mutedText = mutedText,
                onClick = onOpenPlanner,
                trailing = {
                    Surface(
                        modifier = Modifier.clickable(onClick = onUseMyLocation),
                        shape = RoundedCornerShape(12.dp),
                        color = PremiumCyan.copy(alpha = if (darkMode) 0.16f else 0.12f)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.locating) {
                                CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = PremiumCyan)
                            } else {
                                Icon(Icons.Rounded.MyLocation, null, tint = if (darkMode) PremiumCyan else Color(0xFF087C9B), modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(5.dp))
                            Text("مکان من", color = if (darkMode) PremiumCyan else Color(0xFF087C9B), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            )

            HorizontalDivider(color = lineColor, modifier = Modifier.padding(start = 30.dp, end = 4.dp))

            EndpointRow(
                title = "مقصد",
                value = state.destination?.name ?: "مقصد را انتخاب کنید",
                markerColor = PremiumDestination,
                textColor = primaryText,
                mutedText = mutedText,
                onClick = onOpenPlanner,
                trailing = {
                    Icon(Icons.Rounded.Place, null, tint = PremiumDestination, modifier = Modifier.size(22.dp))
                }
            )

            Spacer(Modifier.size(8.dp))

            Button(
                onClick = onRoute,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.routing,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PremiumCyan,
                    contentColor = PremiumInk,
                    disabledContainerColor = PremiumCyan.copy(alpha = 0.55f),
                    disabledContentColor = PremiumInk.copy(alpha = 0.7f)
                )
            ) {
                if (state.routing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = PremiumInk)
                } else {
                    Icon(Icons.Rounded.DirectionsCar, null)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.origin != null && state.destination != null) "نمایش مسیرهای پیشنهادی" else "انتخاب مبدأ و مقصد",
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun EndpointRow(
    title: String,
    value: String,
    markerColor: Color,
    textColor: Color,
    mutedText: Color,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
            Surface(modifier = Modifier.size(10.dp), shape = CircleShape, color = markerColor) {}
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = mutedText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                value,
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

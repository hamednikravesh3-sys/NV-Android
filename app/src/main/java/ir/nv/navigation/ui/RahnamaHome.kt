package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.map.OfflineIranMap
import ir.nv.navigation.map.OnlineIranMap
import ir.nv.navigation.routing.NavigationModeResolver
import ir.nv.navigation.ui.theme.AppThemeMode
import ir.nv.navigation.ui.theme.NvColors
import ir.nv.navigation.ui.theme.NvElevation
import ir.nv.navigation.ui.theme.NvRadius
import ir.nv.navigation.ui.theme.NvSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RahnamaHomeScreen(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel,
    onNearby: () -> Unit,
    onPin: () -> Unit,
    onRefineDestination: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var settingsVisible by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var lastPair by remember { mutableStateOf<String?>(null) }

    fun hasFineLocationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.useCurrentLocationAsOrigin()
            viewModel.recenterNavigation()
        }
    }

    val navigationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) viewModel.startNavigation()
    }

    fun locateMe() {
        if (hasFineLocationPermission()) {
            if (state.currentLocation == null) viewModel.useCurrentLocationAsOrigin() else viewModel.recenterNavigation()
        } else {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    fun startNavigation() {
        if (hasFineLocationPermission()) viewModel.startNavigation()
        else navigationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val pairKey = if (state.origin != null && state.destination != null) {
        "${state.origin!!.coordinate.latitude},${state.origin!!.coordinate.longitude}->${state.destination!!.coordinate.latitude},${state.destination!!.coordinate.longitude}"
    } else null

    LaunchedEffect(pairKey) {
        if (pairKey != null && pairKey != lastPair) {
            lastPair = pairKey
            viewModel.clearRoute()
            viewModel.calculateRoute()
        }
    }

    Box(Modifier.fillMaxSize()) {
        RahnamaMap(state, viewModel, darkMode)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = NvSpacing.Lg, vertical = NvSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
        ) {
            RahnamaSearchBar(
                state = state,
                viewModel = viewModel,
                expanded = searchExpanded,
                onExpandedChange = { searchExpanded = it },
                onSelectDestination = { place ->
                    if (state.origin == null) viewModel.useCurrentLocationAsOrigin()
                    viewModel.selectDestination(place)
                    searchExpanded = false
                    onRefineDestination()
                }
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
            ) {
                RahnamaStatusChip(
                    icon = if (state.onlineAvailable) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                    text = if (state.onlineAvailable) "آنلاین" else if (state.offlineReady) "آفلاین آماده" else "بدون شبکه",
                    accent = if (state.onlineAvailable || state.offlineReady) NvColors.Success else NvColors.Warning
                )
                RahnamaStatusChip(
                    icon = if (state.satelliteMode) Icons.Rounded.SatelliteAlt else Icons.Rounded.Map,
                    text = if (state.satelliteMode) "ماهواره‌ای" else "نقشه",
                    accent = NvColors.RouteBlue
                )
                if (state.routing) RahnamaStatusChip(Icons.Rounded.Route, "در حال محاسبه مسیر", NvColors.Warning)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = NvSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
        ) {
            RahnamaMapButton(Icons.Rounded.Layers, "تغییر نمای نقشه") { viewModel.toggleSatelliteMode() }
            RahnamaMapButton(Icons.Rounded.MyLocation, "موقعیت من", highlight = true, onClick = ::locateMe)
        }

        if (state.routeAlternatives.isEmpty() && !searchExpanded) {
            RahnamaQuickPanel(
                onNearby = onNearby,
                onEmergency = onNearby,
                onPharmacy = onNearby,
                onParking = onNearby,
                onPin = onPin,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = NvSpacing.Lg, end = NvSpacing.Lg, bottom = 88.dp)
            )
        }

        if (state.routeAlternatives.isNotEmpty()) {
            RahnamaRouteCard(
                state = state,
                viewModel = viewModel,
                onStart = ::startNavigation,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = NvSpacing.Lg, end = NvSpacing.Lg, bottom = 88.dp)
            )
        }

        RahnamaBottomBar(
            onHome = { searchExpanded = false },
            onSearch = { searchExpanded = true },
            onRoutes = {
                if (state.routeAlternatives.isEmpty() && state.destination == null) searchExpanded = true
            },
            onSaved = onPin,
            onSettings = { settingsVisible = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = NvSpacing.Lg, vertical = NvSpacing.Sm)
        )

        state.message?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 118.dp, start = NvSpacing.Xl, end = NvSpacing.Xl),
                color = NvColors.Navy850.copy(alpha = .96f),
                shape = RoundedCornerShape(NvRadius.Pill),
                border = BorderStroke(1.dp, NvColors.Warning.copy(alpha = .5f))
            ) {
                Text(message, color = NvColors.TextPrimaryDark, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }

    if (settingsVisible) {
        ModalBottomSheet(
            onDismissRequest = { settingsVisible = false },
            containerColor = NvColors.Navy900,
            contentColor = NvColors.TextPrimaryDark
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Xl, vertical = NvSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(NvSpacing.Lg)
            ) {
                Text("تنظیمات راهنما", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("ظاهر برنامه", color = NvColors.TextSecondaryDark)
                Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(mode.title) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("نقشه آفلاین ایران")
                        Text(if (state.offlineReady) "آماده استفاده" else "هنوز دانلود نشده", color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(
                        checked = state.preferOffline,
                        onCheckedChange = { enabled ->
                            if (enabled && !state.offlineReady) viewModel.startMapDownload() else viewModel.setPreferOffline(enabled)
                        }
                    )
                }
                Button(onClick = { viewModel.toggleSatelliteMode() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.SatelliteAlt, contentDescription = null)
                    Spacer(Modifier.width(NvSpacing.Sm))
                    Text(if (state.satelliteMode) "بازگشت به نقشه عادی" else "نمای ماهواره‌ای")
                }
                Spacer(Modifier.height(NvSpacing.Xl))
            }
        }
    }
}

@Composable
private fun RahnamaMap(state: NvUiState, vm: NvViewModel, darkMode: Boolean) {
    val context = LocalContext.current
    val routes = remember(state.routeAlternatives, state.route) { state.routeAlternatives.ifEmpty { listOfNotNull(state.route) } }
    val coded = remember(state.personalPlaces, state.recentPlaces, state.destination) {
        (state.personalPlaces + state.recentPlaces + listOfNotNull(state.destination)).distinctBy { it.personalCode ?: it.code.toString() }
    }
    when (NavigationModeResolver.preferredSource(state.onlineAvailable, state.offlineReady, state.preferOffline)) {
        RouteSource.OFFLINE -> OfflineIranMap(
            context, vm.mapFile(), routes, state.selectedRouteIndex, state.traffic, state.trafficSegments,
            state.currentLocation, state.followNavigation, false, state.navigationZoomLevel, state.navigationRecenterToken,
            state.bearingDegrees, vm::pauseNavigationFollow, darkMode, Modifier.fillMaxSize()
        )
        RouteSource.ONLINE -> OnlineIranMap(
            context, routes, state.selectedRouteIndex, state.traffic, state.trafficSegments, coded, state.currentLocation,
            state.followNavigation, false, state.navigationZoomLevel, state.navigationRecenterToken, state.bearingDegrees,
            vm::pauseNavigationFollow, darkMode, state.satelliteMode, Modifier.fillMaxSize()
        )
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("نقشه در دسترس نیست", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RahnamaSearchBar(
    state: NvUiState,
    viewModel: NvViewModel,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectDestination: (ir.nv.navigation.core.Place) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = NvColors.Navy900.copy(alpha = .96f),
        shape = RoundedCornerShape(NvRadius.Card),
        border = BorderStroke(1.dp, NvColors.RouteBlue.copy(alpha = .38f)),
        shadowElevation = NvElevation.Floating
    ) {
        Column(Modifier.padding(NvSpacing.Sm)) {
            TextField(
                value = state.destinationQuery,
                onValueChange = {
                    viewModel.updateDestinationQuery(it)
                    onExpandedChange(true)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("کجا می‌خواهید بروید؟", color = NvColors.TextSecondaryDark) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "جستجو", tint = NvColors.RouteBlue) },
                trailingIcon = {
                    if (state.destinationQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.updateDestinationQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "پاک کردن", tint = NvColors.TextSecondaryDark)
                        }
                    } else {
                        Icon(Icons.Rounded.Mic, contentDescription = "جستجوی صوتی", tint = NvColors.TextSecondaryDark)
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedTextColor = NvColors.TextPrimaryDark,
                    unfocusedTextColor = NvColors.TextPrimaryDark,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            if (expanded && state.destinationSuggestions.isNotEmpty()) {
                HorizontalDivider(color = NvColors.DividerDark)
                state.destinationSuggestions.take(5).forEach { place ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelectDestination(place) }.padding(horizontal = NvSpacing.Sm, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Place, contentDescription = null, tint = NvColors.Success)
                        Spacer(Modifier.width(NvSpacing.Sm))
                        Column(Modifier.weight(1f)) {
                            Text(place.name, color = NvColors.TextPrimaryDark, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            place.address?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = NvColors.TextSecondaryDark)
                    }
                }
            }
        }
    }
}

@Composable
private fun RahnamaQuickPanel(
    onNearby: () -> Unit,
    onEmergency: () -> Unit,
    onPharmacy: () -> Unit,
    onParking: () -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NvColors.Navy900.copy(alpha = .97f),
        shape = RoundedCornerShape(NvRadius.Sheet),
        border = BorderStroke(1.dp, NvColors.RouteBlue.copy(alpha = .35f)),
        shadowElevation = NvElevation.Overlay
    ) {
        Column(Modifier.padding(NvSpacing.Md), verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("دسترسی سریع", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Black)
                TextButton(onClick = onNearby) { Text("همه اطراف من", color = NvColors.RouteBlue) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RahnamaQuickAction(Icons.Rounded.Emergency, "اورژانس", NvColors.Emergency, onEmergency)
                RahnamaQuickAction(Icons.Rounded.LocalHospital, "بیمارستان", NvColors.Info, onNearby)
                RahnamaQuickAction(Icons.Rounded.LocalPharmacy, "داروخانه", NvColors.Success, onPharmacy)
                RahnamaQuickAction(Icons.Rounded.LocalParking, "پارکینگ", NvColors.RouteBlue, onParking)
                RahnamaQuickAction(Icons.Rounded.Bookmark, "ذخیره", NvColors.Warning, onPin)
            }
        }
    }
}

@Composable
private fun RahnamaQuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp).clickable(onClick = onClick)) {
        Surface(shape = RoundedCornerShape(NvRadius.Medium), color = color.copy(alpha = .16f), border = BorderStroke(1.dp, color.copy(alpha = .45f))) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(NvSpacing.Xs))
        Text(label, color = NvColors.TextPrimaryDark, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun RahnamaRouteCard(state: NvUiState, viewModel: NvViewModel, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val selected = state.routeAlternatives.getOrNull(state.selectedRouteIndex) ?: state.route ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NvColors.Navy900.copy(alpha = .98f),
        shape = RoundedCornerShape(NvRadius.Sheet),
        border = BorderStroke(1.dp, NvColors.RouteBlue.copy(alpha = .45f)),
        shadowElevation = NvElevation.Overlay
    ) {
        Column(Modifier.padding(NvSpacing.Lg), verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.destination?.name ?: "مقصد", color = NvColors.TextPrimaryDark, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(selected.travelSeconds / 60).toInt()} دقیقه • ${String.format("%.1f", selected.distanceMeters / 1000.0)} کیلومتر", color = NvColors.TextSecondaryDark)
                }
                Button(onClick = onStart, enabled = !state.routing) {
                    Icon(Icons.Rounded.Navigation, contentDescription = null)
                    Spacer(Modifier.width(NvSpacing.Xs))
                    Text("شروع")
                }
            }
            if (state.routeAlternatives.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                    state.routeAlternatives.take(3).forEachIndexed { index, route ->
                        FilterChip(
                            selected = state.selectedRouteIndex == index,
                            onClick = { viewModel.selectRoute(index) },
                            label = { Text("${(route.travelSeconds / 60).toInt()} دقیقه") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RahnamaBottomBar(
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onRoutes: () -> Unit,
    onSaved: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NvColors.Navy900.copy(alpha = .98f),
        shape = RoundedCornerShape(NvRadius.Sheet),
        border = BorderStroke(1.dp, NvColors.DividerDark),
        shadowElevation = NvElevation.Overlay
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Sm, vertical = NvSpacing.Xs), horizontalArrangement = Arrangement.SpaceEvenly) {
            RahnamaBottomItem(Icons.Rounded.Home, "خانه", NvColors.RouteBlue, onHome)
            RahnamaBottomItem(Icons.Rounded.Search, "جستجو", NvColors.TextSecondaryDark, onSearch)
            RahnamaBottomItem(Icons.Rounded.Route, "مسیرها", NvColors.TextSecondaryDark, onRoutes)
            RahnamaBottomItem(Icons.Rounded.Bookmark, "ذخیره‌ها", NvColors.TextSecondaryDark, onSaved)
            RahnamaBottomItem(Icons.Rounded.Settings, "تنظیمات", NvColors.TextSecondaryDark, onSettings)
        }
    }
}

@Composable
private fun RahnamaBottomItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 5.dp)) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(23.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RahnamaMapButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = NvColors.Navy900.copy(alpha = .96f),
        border = BorderStroke(1.dp, if (highlight) NvColors.RouteBlue else NvColors.DividerDark),
        shadowElevation = NvElevation.Floating
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
            Icon(icon, contentDescription = description, tint = if (highlight) NvColors.RouteBlue else NvColors.TextPrimaryDark)
        }
    }
}

@Composable
private fun RahnamaStatusChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, accent: Color) {
    Surface(
        color = NvColors.Navy850.copy(alpha = .92f),
        shape = RoundedCornerShape(NvRadius.Pill),
        border = BorderStroke(1.dp, accent.copy(alpha = .35f))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, color = NvColors.TextPrimaryDark, style = MaterialTheme.typography.labelSmall)
        }
    }
}

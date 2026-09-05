package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.nv.navigation.R
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.map.OfflineIranMap
import ir.nv.navigation.map.OnlineIranMap
import ir.nv.navigation.routing.NavigationModeResolver
import ir.nv.navigation.ui.theme.AppThemeMode
import kotlin.math.roundToInt

private val V2Navy = Color(0xF2071728)
private val V2Panel = Color(0xF20A2944)
private val V2PanelHigh = Color(0xF5123857)
private val V2Cyan = Color(0xFF17D9FF)
private val V2Green = Color(0xFF35E84C)
private val V2Lime = Color(0xFFB7FF65)
private val V2Amber = Color(0xFFFFB82E)
private val V2Red = Color(0xFFFF4055)
private val V2Text = Color(0xFFF7FBFF)
private val V2Muted = Color(0xFFA7BBCB)
private val V2Outline = Color(0xFF236B91)

private enum class V2LocationAction { ORIGIN, NAVIGATE }

@Composable
fun NvPremiumV2(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showPlanner by remember { mutableStateOf(false) }
    var showRoutePanel by remember { mutableStateOf(state.route != null && !state.navigationActive) }
    var showCodeDialog by remember { mutableStateOf(false) }
    var quickPanel by remember { mutableStateOf<DashboardPanelType?>(null) }
    var selectedTab by remember { mutableStateOf(DashboardTab.NAVIGATION) }
    var pendingLocationAction by remember { mutableStateOf(V2LocationAction.ORIGIN) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            when (pendingLocationAction) {
                V2LocationAction.ORIGIN -> viewModel.useCurrentLocationAsOrigin()
                V2LocationAction.NAVIGATE -> viewModel.startNavigation()
            }
        }
    }

    fun requestLocation(action: V2LocationAction) {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            when (action) {
                V2LocationAction.ORIGIN -> viewModel.useCurrentLocationAsOrigin()
                V2LocationAction.NAVIGATE -> viewModel.startNavigation()
            }
        } else {
            pendingLocationAction = action
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    fun openPlanner(query: String? = null) {
        query?.let(viewModel::updateDestinationQuery)
        showPlanner = true
        showRoutePanel = false
        quickPanel = null
    }

    LaunchedEffect(state.route) {
        if (state.route != null && !state.navigationActive) {
            showPlanner = false
            showRoutePanel = true
            selectedTab = DashboardTab.ROUTES
        }
    }
    LaunchedEffect(state.navigationActive) {
        if (state.navigationActive) {
            showPlanner = false
            showRoutePanel = false
            quickPanel = null
            selectedTab = DashboardTab.NAVIGATION
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        PremiumMapCanvas(
            state = state,
            viewModel = viewModel,
            darkMode = darkMode,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xB3051424), Color.Transparent, Color.Transparent, Color(0xB6051424))
                    )
                )
        )

        V2Header(
            destinationName = state.destination?.name,
            weatherNotice = state.routeNotices.firstOrNull {
                it.kind == RouteNotice.Kind.WEATHER && it.distanceAheadMeters <= 10_000.0
            },
            onSearch = { openPlanner() },
            onWeather = { quickPanel = DashboardPanelType.WEATHER; showPlanner = false; showRoutePanel = false },
            onProfile = { quickPanel = DashboardPanelType.FAVORITES; showPlanner = false; showRoutePanel = false },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )

        NvDashboardServiceRail(
            satelliteMode = state.satelliteMode,
            onLayers = viewModel::toggleSatelliteMode,
            onCategory = { query -> openPlanner(query) },
            onMore = { quickPanel = DashboardPanelType.SETTINGS; showPlanner = false; showRoutePanel = false },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 7.dp, bottom = if (state.navigationActive) 32.dp else 0.dp)
        )

        if (state.navigationActive) {
            NvReferenceDrivingDashboard(
                state = state,
                onSelectRoute = viewModel::selectRoute,
                onStopNavigation = viewModel::stopNavigation,
                modifier = Modifier.matchParentSize()
            )
        } else if (showRoutePanel && state.route != null) {
            V2RouteOverview(
                state = state,
                compact = compact,
                onSelectRoute = viewModel::selectRoute,
                onStart = { requestLocation(V2LocationAction.NAVIGATE) },
                onEdit = { openPlanner() },
                onCode = { showCodeDialog = true },
                onClose = { viewModel.clearRoute(); showRoutePanel = false },
                modifier = if (compact) {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 76.dp, end = 8.dp, bottom = 84.dp)
                } else {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .width(360.dp)
                }
            )
        }

        NvDashboardBottomDock(
            selected = selectedTab,
            compact = compact,
            onSelect = { tab ->
                selectedTab = tab
                when (tab) {
                    DashboardTab.NAVIGATION -> {
                        quickPanel = null
                        if (state.route != null && !state.navigationActive) showRoutePanel = true
                    }
                    DashboardTab.SEARCH -> openPlanner()
                    DashboardTab.FAVORITES -> { quickPanel = DashboardPanelType.FAVORITES; showPlanner = false; showRoutePanel = false }
                    DashboardTab.ROUTES -> {
                        quickPanel = null
                        if (state.route != null) showRoutePanel = true else openPlanner()
                    }
                    DashboardTab.WEATHER -> { quickPanel = DashboardPanelType.WEATHER; showPlanner = false; showRoutePanel = false }
                    DashboardTab.SETTINGS -> { quickPanel = DashboardPanelType.SETTINGS; showPlanner = false; showRoutePanel = false }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
        )

        if (showPlanner) {
            V2Planner(
                state = state,
                onDismiss = { showPlanner = false },
                onOriginChange = viewModel::updateOriginQuery,
                onDestinationChange = viewModel::updateDestinationQuery,
                onOriginSelect = viewModel::selectOrigin,
                onDestinationSelect = viewModel::selectDestination,
                onUseCurrentLocation = { requestLocation(V2LocationAction.ORIGIN) },
                onSwap = viewModel::swapEndpoints,
                onRoute = { viewModel.calculateRoute() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(start = 76.dp, end = 8.dp, bottom = 84.dp)
            )
        }

        quickPanel?.let { panel ->
            NvDashboardQuickPanel(
                type = panel,
                state = state,
                themeMode = themeMode,
                onDismiss = { quickPanel = null },
                onSelectPlace = { place -> viewModel.selectDestination(place); quickPanel = null; openPlanner() },
                onThemeModeChange = onThemeModeChange,
                onToggleSatellite = viewModel::toggleSatelliteMode,
                onToggleOffline = viewModel::setPreferOffline,
                onDownloadMap = viewModel::startMapDownload,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(start = 76.dp, end = 8.dp, bottom = 84.dp)
            )
        }

        if (!state.navigationActive && (state.destination != null || state.origin != null)) {
            FilledTonalButton(
                onClick = { showCodeDialog = true },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp, bottom = 230.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = V2PanelHigh, contentColor = V2Lime)
            ) {
                Icon(Icons.Rounded.QrCode2, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("تعریف کد NV", fontWeight = FontWeight.Black)
            }
        }

        if (showCodeDialog) {
            NvCodeDialogV2(
                place = state.destination ?: state.origin,
                savedPlaces = state.personalPlaces,
                onDismiss = { showCodeDialog = false },
                onSave = { place, code -> viewModel.savePersonalCode(place, code); showCodeDialog = false },
                onDelete = viewModel::deletePersonalCode
            )
        }

        state.message?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 70.dp, start = 84.dp, end = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = V2Navy,
                border = BorderStroke(1.dp, V2Outline)
            ) {
                Text(message, color = V2Text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), maxLines = 2)
            }
        }
    }
}

@Composable
private fun PremiumMapCanvas(
    state: NvUiState,
    viewModel: NvViewModel,
    darkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapSource = NavigationModeResolver.preferredSource(
        onlineAvailable = state.onlineAvailable,
        offlineReady = state.offlineReady,
        preferOffline = state.preferOffline
    )
    when {
        mapSource == RouteSource.OFFLINE -> OfflineIranMap(
            context = context,
            mapFile = viewModel.mapFile(),
            routes = state.routeAlternatives.ifEmpty { listOfNotNull(state.route) },
            selectedRouteIndex = state.selectedRouteIndex,
            traffic = state.traffic,
            trafficSegments = state.trafficSegments,
            currentLocation = state.currentLocation,
            followLocation = state.navigationActive && state.followNavigation,
            navigationActive = state.navigationActive,
            navigationZoomLevel = state.navigationZoomLevel,
            navigationRecenterToken = state.navigationRecenterToken,
            bearingDegrees = state.bearingDegrees,
            onManualGesture = viewModel::pauseNavigationFollow,
            darkMode = darkMode,
            modifier = modifier
        )
        state.onlineAvailable -> OnlineIranMap(
            context = context,
            routes = state.routeAlternatives.ifEmpty { listOfNotNull(state.route) },
            selectedRouteIndex = state.selectedRouteIndex,
            traffic = state.traffic,
            trafficSegments = state.trafficSegments,
            codedPlaces = (state.personalPlaces + state.recentPlaces + listOfNotNull(state.origin, state.destination))
                .distinctBy { it.personalCode ?: it.code.toString() },
            currentLocation = state.currentLocation,
            followLocation = state.navigationActive && state.followNavigation,
            navigationActive = state.navigationActive,
            navigationZoomLevel = state.navigationZoomLevel,
            navigationRecenterToken = state.navigationRecenterToken,
            bearingDegrees = state.bearingDegrees,
            onManualGesture = viewModel::pauseNavigationFollow,
            darkMode = darkMode,
            satelliteMode = state.satelliteMode,
            modifier = modifier
        )
        else -> Box(modifier.background(Color(0xFF07121D)), contentAlignment = Alignment.Center) {
            Text("برای نمایش نقشه اینترنت را وصل کنید یا نقشه آفلاین ایران را دانلود کنید", color = V2Muted)
        }
    }
}

@Composable
private fun V2Header(
    destinationName: String?,
    weatherNotice: RouteNotice?,
    onSearch: () -> Unit,
    onWeather: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Surface(
            modifier = Modifier.height(58.dp),
            shape = RoundedCornerShape(19.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE0E8EF)),
            shadowElevation = 12.dp
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = "NV",
                    modifier = Modifier.size(43.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        Surface(
            modifier = Modifier.weight(1f).height(58.dp).clickable(onClick = onSearch),
            shape = RoundedCornerShape(19.dp),
            color = V2PanelHigh,
            border = BorderStroke(1.dp, V2Cyan.copy(alpha = .6f)),
            shadowElevation = 12.dp
        ) {
            Row(Modifier.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Mic, null, tint = V2Text, modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    destinationName ?: "نام یا کد مکان را جستجو کنید…",
                    color = if (destinationName == null) V2Muted else V2Text,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Rounded.Search, "جستجو", tint = V2Cyan, modifier = Modifier.size(26.dp))
            }
        }

        Surface(
            modifier = Modifier.size(58.dp).clickable(onClick = onWeather),
            shape = RoundedCornerShape(19.dp),
            color = V2PanelHigh,
            border = BorderStroke(1.dp, V2Outline),
            shadowElevation = 10.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (weatherNotice?.title?.startsWith("هشدار") == true) Icons.Rounded.Thunderstorm else Icons.Rounded.WbSunny,
                    null,
                    tint = if (weatherNotice?.title?.startsWith("هشدار") == true) V2Amber else Color(0xFFFFDE3B),
                    modifier = Modifier.size(31.dp)
                )
            }
        }

        Surface(
            modifier = Modifier.size(58.dp).clickable(onClick = onProfile),
            shape = CircleShape,
            color = V2PanelHigh,
            border = BorderStroke(1.dp, V2Outline),
            shadowElevation = 10.dp
        ) {
            Icon(Icons.Rounded.Person, "مکان‌های من", tint = V2Text, modifier = Modifier.padding(14.dp))
        }
    }
}

@Composable
private fun V2Planner(
    state: NvUiState,
    onDismiss: () -> Unit,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onOriginSelect: (Place) -> Unit,
    onDestinationSelect: (Place) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onSwap: () -> Unit,
    onRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = 620.dp),
        shape = RoundedCornerShape(24.dp),
        color = V2Navy,
        border = BorderStroke(1.dp, V2Cyan.copy(alpha = .55f)),
        shadowElevation = 20.dp
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("مسیر", color = V2Text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onSwap, enabled = state.origin != null && state.destination != null) { Icon(Icons.Rounded.SwapVert, "جابجایی", tint = V2Cyan) }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "بستن", tint = V2Muted) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AssistChip(
                    onClick = onUseCurrentLocation,
                    label = { Text("موقعیت فعلی من") },
                    leadingIcon = { Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(17.dp)) }
                )
                AssistChip(
                    onClick = { onOriginChange("") },
                    label = { Text("مبدأ دیگر") },
                    leadingIcon = { Icon(Icons.Rounded.EditLocationAlt, null, modifier = Modifier.size(17.dp)) }
                )
            }

            V2PlaceField(
                label = "مبدأ",
                value = state.originQuery,
                suggestions = state.originSuggestions,
                searching = state.originSearching,
                onChange = onOriginChange,
                onSelect = onOriginSelect
            )
            V2PlaceField(
                label = "مقصد",
                value = state.destinationQuery,
                suggestions = state.destinationSuggestions,
                searching = state.destinationSearching,
                onChange = onDestinationChange,
                onSelect = onDestinationSelect
            )

            Button(
                onClick = onRoute,
                enabled = state.origin != null && state.destination != null && !state.routing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = V2Cyan, contentColor = Color(0xFF03141F))
            ) {
                if (state.routing) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Route, null)
                Spacer(Modifier.width(7.dp))
                Text("پیدا کردن بهترین مسیر", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun V2PlaceField(
    label: String,
    value: String,
    suggestions: List<Place>,
    searching: Boolean,
    onChange: (String) -> Unit,
    onSelect: (Place) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(label) },
            leadingIcon = { Icon(if (label == "مبدأ") Icons.Rounded.TripOrigin else Icons.Rounded.Place, null, tint = V2Cyan) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searching) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = V2Cyan)
                    if (value.isNotBlank()) {
                        IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "پاک کردن", tint = V2Text) }
                    }
                }
            },
            shape = RoundedCornerShape(15.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = V2Text,
                unfocusedTextColor = V2Text,
                focusedBorderColor = V2Cyan,
                unfocusedBorderColor = V2Outline,
                focusedLabelColor = V2Cyan,
                unfocusedLabelColor = V2Muted,
                cursorColor = V2Cyan
            )
        )
        if (value.isNotBlank() && suggestions.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(13.dp), color = V2Panel, border = BorderStroke(1.dp, V2Outline.copy(alpha = .65f))) {
                Column {
                    suggestions.take(4).forEach { place ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(place) }.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Place, null, tint = V2Cyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(place.name, color = V2Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val code = place.personalCode ?: place.code.takeIf { it > 0 }?.toString()
                            if (code != null) Text("NV:$code", color = V2Lime, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V2RouteOverview(
    state: NvUiState,
    compact: Boolean,
    onSelectRoute: (Int) -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onCode: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val route = state.route ?: return
    val minutes = (route.travelSeconds / 60.0).roundToInt().coerceAtLeast(1)
    val km = route.distanceMeters / 1_000.0
    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = if (compact) 620.dp else 380.dp),
        shape = RoundedCornerShape(24.dp),
        color = V2Navy,
        border = BorderStroke(1.dp, V2Cyan.copy(alpha = .55f)),
        shadowElevation = 22.dp
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = onEdit,
                    label = { Text(if (state.routeSource == RouteSource.ONLINE) "مسیر آنلاین" else "مسیر آفلاین") },
                    leadingIcon = { Icon(Icons.Rounded.CloudDone, null, modifier = Modifier.size(16.dp)) }
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "بستن", tint = V2Muted) }
            }

            Surface(shape = RoundedCornerShape(18.dp), color = V2PanelHigh, border = BorderStroke(1.dp, V2Outline)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null, tint = V2Cyan, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.destination?.name ?: "مقصد", color = V2Text, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("مقصد انتخاب‌شده", color = V2Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    val code = state.destination?.personalCode ?: state.destination?.code?.takeIf { it > 0 }?.toString()
                    if (code != null) {
                        Surface(shape = RoundedCornerShape(11.dp), color = Color(0xFF1D3520), border = BorderStroke(1.dp, V2Lime)) {
                            Text("NV:$code", color = V2Lime, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                V2Metric("%.1f km".format(km), "مسافت")
                V2Metric("$minutes دقیقه", "زمان")
                V2Metric("${java.time.LocalTime.now().plusMinutes(minutes.toLong()).toString().take(5)}", "رسیدن")
            }

            state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.WEATHER }?.let { notice ->
                Surface(shape = RoundedCornerShape(17.dp), color = V2PanelHigh, border = BorderStroke(1.dp, V2Outline)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudDone, null, tint = V2Cyan)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(notice.title, color = V2Text, fontWeight = FontWeight.Black)
                            Text(notice.detail, color = V2Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }
            }

            if (state.routeAlternatives.size > 1) {
                Text("مسیرهای پیشنهادی", color = V2Text, fontWeight = FontWeight.Black)
                state.routeAlternatives.take(3).forEachIndexed { index, alternative ->
                    val selected = index == state.selectedRouteIndex
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectRoute(index) },
                        shape = RoundedCornerShape(15.dp),
                        color = if (selected) V2PanelHigh else V2Panel,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) V2Cyan else V2Outline.copy(alpha = .65f))
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(11.dp).clip(CircleShape).background(when (index) { 0 -> V2Green; 1 -> V2Amber; else -> V2Red }))
                            Spacer(Modifier.width(8.dp))
                            Text(if (index == 0) "مسیر پیشنهادی" else "مسیر ${index + 1}", color = V2Text, modifier = Modifier.weight(1f))
                            Text("${(alternative.travelSeconds / 60.0).roundToInt()} دقیقه", color = V2Muted)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCode, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.QrCode2, null)
                    Spacer(Modifier.width(6.dp))
                    Text("تعریف کد NV")
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.EditLocationAlt, null)
                    Spacer(Modifier.width(6.dp))
                    Text("ویرایش مسیر")
                }
            }

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = V2Cyan, contentColor = Color(0xFF02151F))
            ) {
                Icon(Icons.Rounded.Navigation, null)
                Spacer(Modifier.width(8.dp))
                Text("شروع حرکت", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun V2Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = V2Text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        Text(label, color = V2Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NvCodeDialogV2(
    place: Place?,
    savedPlaces: List<Place>,
    onDismiss: () -> Unit,
    onSave: (Place, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var code by remember(place?.code) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = V2Navy,
        titleContentColor = V2Text,
        textContentColor = V2Text,
        title = { Text("تعریف کد NV", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (place == null) {
                    Text("ابتدا یک مبدأ یا مقصد انتخاب کنید.", color = V2Muted)
                } else {
                    Text(place.name, color = V2Text, fontWeight = FontWeight.Bold)
                    Text("یک کد عددی دلخواه از ۱ تا ۹۹۹٬۹۹۹٬۹۹۹ وارد کنید. بعداً با NV:کد قابل جست‌وجو است.", color = V2Muted, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { input -> code = input.filter(Char::isDigit).take(9) },
                        singleLine = true,
                        label = { Text("کد شخصی") },
                        prefix = { Text("NV:") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = V2Text,
                            unfocusedTextColor = V2Text,
                            focusedBorderColor = V2Lime,
                            unfocusedBorderColor = V2Outline,
                            focusedLabelColor = V2Lime,
                            unfocusedLabelColor = V2Muted,
                            cursorColor = V2Lime
                        )
                    )
                }
                if (savedPlaces.isNotEmpty()) {
                    Text("کدهای ذخیره‌شده", color = V2Muted, style = MaterialTheme.typography.labelMedium)
                    savedPlaces.take(4).forEach { saved ->
                        val savedCode = saved.personalCode ?: return@forEach
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("NV:$savedCode  •  ${saved.name}", color = V2Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { onDelete(savedCode) }) { Icon(Icons.Rounded.DeleteOutline, "حذف", tint = V2Red) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (place != null && code.isNotBlank()) onSave(place, code) },
                enabled = place != null && code.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = V2Green, contentColor = Color(0xFF06140A))
            ) { Text("ذخیره کد", fontWeight = FontWeight.Black) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

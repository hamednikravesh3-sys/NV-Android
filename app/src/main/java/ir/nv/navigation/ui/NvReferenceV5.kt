package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.map.OfflineIranMap
import ir.nv.navigation.map.OnlineIranMap
import ir.nv.navigation.routing.NavigationModeResolver
import ir.nv.navigation.ui.theme.AppThemeMode

private val V5Panel = Color(0xF20A1D2D)
private val V5Cyan = Color(0xFF16D9FF)
private val V5Text = Color(0xFFF6FBFF)
private val V5Muted = Color(0xFFA7BBC8)
private val V5Green = Color(0xFF42E66A)
private enum class V5Sheet { MENU, SEARCH, ROUTES, FAVORITES, WEATHER, SETTINGS }
private enum class SearchTarget { ORIGIN, DESTINATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NvReferenceV5(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<V5Sheet?>(null) }
    var quickSearchVisible by remember { mutableStateOf(false) }
    var searchTarget by remember { mutableStateOf(SearchTarget.ORIGIN) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p ->
        if (p.values.any { it }) viewModel.startNavigation()
    }

    fun startNavigation() {
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (ok) viewModel.startNavigation() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    fun openSearch(q: String = "", target: SearchTarget = SearchTarget.DESTINATION) {
        searchTarget = target
        if (q.isNotBlank()) {
            if (target == SearchTarget.ORIGIN) viewModel.updateOriginQuery(q) else viewModel.updateDestinationQuery(q)
        }
        quickSearchVisible = true
        sheet = null
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF07121C))) {
        V5Map(state, viewModel, darkMode)

        V5QuickSearch(
            state = state,
            vm = viewModel,
            visible = quickSearchVisible,
            target = searchTarget,
            onTargetChange = { searchTarget = it },
            onOpen = {
                searchTarget = if (state.origin == null) SearchTarget.ORIGIN else SearchTarget.DESTINATION
                quickSearchVisible = true
            },
            onClose = { quickSearchVisible = false },
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 10.dp)
        )

        if (!quickSearchVisible) {
            V5RightInfoRail(
                state = state,
                onWeather = { sheet = V5Sheet.WEATHER },
                onAttraction = { sheet = V5Sheet.WEATHER },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 10.dp, top = 12.dp)
            )
        }

        if (state.navigationActive && state.route != null) {
            val maneuver = state.route?.maneuvers?.getOrNull(state.maneuverIndex)
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp).widthIn(max = 330.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xE408665D),
                border = BorderStroke(1.dp, Color(0xFF6CFFE6))
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TurnRight, null, tint = Color.White, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (state.distanceToNextManeuverMeters > 0) "${state.distanceToNextManeuverMeters.toInt()} متر" else "راهنمای مسیر", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(maneuver?.instruction ?: "ادامه مسیر", color = Color.White)
                    }
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 14.dp, bottom = 66.dp),
                shape = CircleShape,
                color = V5Panel,
                border = BorderStroke(2.dp, V5Green)
            ) {
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.speedKmh.toString(), color = V5Green, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text("km/h", color = V5Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (state.route != null) {
            val seconds = if (state.navigationActive) state.remainingSeconds else state.route?.travelSeconds ?: 0.0
            val meters = if (state.navigationActive) state.remainingDistanceMeters else state.route?.distanceMeters ?: 0.0
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 58.dp, start = 92.dp, end = 12.dp),
                shape = RoundedCornerShape(18.dp),
                color = V5Panel,
                border = BorderStroke(1.dp, V5Cyan.copy(alpha = .45f))
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    V5Stat("زمان", "${(seconds / 60).toInt()} دقیقه")
                    V5Stat("مسافت", String.format("%.1f km", meters / 1000.0))
                    FilledTonalIconButton(onClick = { if (state.navigationActive) viewModel.stopNavigation() else startNavigation() }) {
                        Icon(if (state.navigationActive) Icons.Rounded.Stop else Icons.Rounded.Navigation, null)
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp).clickable { sheet = V5Sheet.MENU },
            shape = RoundedCornerShape(18.dp),
            color = V5Panel,
            border = BorderStroke(1.dp, V5Cyan.copy(alpha = .55f))
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.KeyboardArrowUp, null, tint = V5Cyan)
                Spacer(Modifier.width(6.dp))
                Text("NV", color = V5Text, fontWeight = FontWeight.Bold)
            }
        }

        if (sheet != null) {
            ModalBottomSheet(
                onDismissRequest = { sheet = null },
                containerColor = V5Panel,
                contentColor = V5Text,
                dragHandle = { BottomSheetDefaults.DragHandle(color = V5Cyan) }
            ) {
                when (sheet) {
                    V5Sheet.MENU -> V5MenuSheet(
                        onSearch = { openSearch(target = if (state.origin == null) SearchTarget.ORIGIN else SearchTarget.DESTINATION) },
                        onRoutes = { sheet = if (state.route == null) V5Sheet.SEARCH else V5Sheet.ROUTES },
                        onFavorites = { sheet = V5Sheet.FAVORITES },
                        onWeather = { sheet = V5Sheet.WEATHER },
                        onGas = { openSearch("پمپ بنزین") },
                        onFood = { openSearch("رستوران") },
                        onHotel = { openSearch("اقامتگاه") },
                        onSight = { openSearch("جاذبه دیدنی") },
                        onSatellite = { viewModel.toggleSatelliteMode(); sheet = null },
                        onSettings = { sheet = V5Sheet.SETTINGS }
                    )
                    V5Sheet.SEARCH -> V5SearchSheet(state, viewModel) { sheet = null }
                    V5Sheet.ROUTES -> V5RoutesSheet(state, viewModel) { sheet = null }
                    V5Sheet.FAVORITES -> V5PlacesSheet(state.personalPlaces) { viewModel.selectDestination(it); sheet = V5Sheet.SEARCH }
                    V5Sheet.WEATHER -> V5NoticeSheet(state.routeNotices.map { it.title + " — " + it.detail })
                    V5Sheet.SETTINGS -> V5SettingsSheet(state, viewModel, themeMode, onThemeModeChange)
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun V5QuickSearch(
    state: NvUiState,
    vm: NvViewModel,
    visible: Boolean,
    target: SearchTarget,
    onTargetChange: (SearchTarget) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) {
        Surface(
            modifier = modifier.size(44.dp).clickable(onClick = onOpen),
            shape = CircleShape,
            color = V5Panel,
            border = BorderStroke(1.dp, V5Cyan.copy(alpha = .65f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Search, contentDescription = "جستجو", tint = V5Cyan, modifier = Modifier.size(22.dp))
            }
        }
        return
    }

    val isOrigin = target == SearchTarget.ORIGIN
    val query = if (isOrigin) state.originQuery else state.destinationQuery
    val suggestions = if (isOrigin) state.originSuggestions else state.destinationSuggestions

    Column(modifier.widthIn(min = 260.dp, max = 350.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = V5Panel,
            border = BorderStroke(1.dp, V5Cyan.copy(alpha = .5f))
        ) {
            Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                FilterChip(
                    selected = isOrigin,
                    onClick = { onTargetChange(SearchTarget.ORIGIN) },
                    label = { Text(if (state.origin != null) "مبدأ ✓" else "مبدأ") },
                    leadingIcon = { Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = !isOrigin,
                    onClick = { onTargetChange(SearchTarget.DESTINATION) },
                    label = { Text(if (state.destination != null) "مقصد ✓" else "مقصد") },
                    leadingIcon = { Icon(Icons.Rounded.Place, null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = V5Panel,
            border = BorderStroke(1.dp, V5Cyan.copy(alpha = .65f))
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { if (isOrigin) vm.updateOriginQuery(it) else vm.updateDestinationQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(if (isOrigin) "جستجوی مبدأ یا کد NV" else "جستجوی مقصد یا کد NV", color = V5Muted) },
                leadingIcon = { Icon(if (isOrigin) Icons.Rounded.MyLocation else Icons.Rounded.Place, null, tint = V5Cyan) },
                trailingIcon = {
                    IconButton(onClick = {
                        if (isOrigin) vm.updateOriginQuery("") else vm.updateDestinationQuery("")
                        onClose()
                    }) { Icon(Icons.Rounded.Close, contentDescription = "بستن", tint = V5Text) }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = V5Text,
                    unfocusedTextColor = V5Text,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = V5Cyan
                )
            )
        }

        if (isOrigin) {
            Spacer(Modifier.height(5.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    vm.useCurrentLocationAsOrigin()
                    onTargetChange(SearchTarget.DESTINATION)
                },
                shape = RoundedCornerShape(14.dp),
                color = V5Panel,
                border = BorderStroke(1.dp, V5Green.copy(alpha = .45f))
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GpsFixed, null, tint = V5Green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("استفاده از موقعیت فعلی به‌عنوان مبدأ", color = V5Text, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (query.isNotBlank() && suggestions.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = V5Panel,
                border = BorderStroke(1.dp, V5Cyan.copy(alpha = .35f))
            ) {
                LazyColumn(Modifier.heightIn(max = 210.dp)) {
                    items(suggestions.take(6)) { place ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (isOrigin) {
                                    vm.selectOrigin(place)
                                    onTargetChange(SearchTarget.DESTINATION)
                                } else {
                                    vm.selectDestination(place)
                                    onClose()
                                }
                            }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (isOrigin) Icons.Rounded.MyLocation else Icons.Rounded.Place, null, tint = V5Cyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(place.name, color = V5Text)
                                Text(if (isOrigin) "انتخاب به‌عنوان مبدأ" else "انتخاب به‌عنوان مقصد", color = V5Muted, style = MaterialTheme.typography.labelSmall)
                            }
                            Text(place.personalCode ?: place.code.toString(), color = V5Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V5RightInfoRail(
    state: NvUiState,
    onWeather: () -> Unit,
    onAttraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weather = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.WEATHER }
    val attraction = state.routeNotices.firstOrNull { it.kind == RouteNotice.Kind.ATTRACTION }
    val temperature = weather?.detail?.let { Regex("(-?\\d+)°").find(it)?.groupValues?.getOrNull(1) }

    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        V5MiniInfoChip(
            icon = Icons.Rounded.Cloud,
            text = temperature?.let { "$it°" } ?: "هوا",
            onClick = onWeather
        )
        if (attraction != null) {
            V5MiniInfoChip(
                icon = Icons.Rounded.PhotoCamera,
                text = attraction.title.take(13),
                onClick = onAttraction
            )
        }
    }
}

@Composable
private fun V5MiniInfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = V5Panel,
        border = BorderStroke(1.dp, V5Cyan.copy(alpha = .4f))
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = V5Cyan, modifier = Modifier.size(16.dp))
            Text(text, color = V5Text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun V5Map(state: NvUiState, vm: NvViewModel, dark: Boolean) {
    val context = LocalContext.current
    val source = NavigationModeResolver.preferredSource(state.onlineAvailable, state.offlineReady, state.preferOffline)
    when {
        source == RouteSource.OFFLINE -> OfflineIranMap(
            context, vm.mapFile(), state.routeAlternatives.ifEmpty { listOfNotNull(state.route) }, state.selectedRouteIndex,
            state.traffic, state.trafficSegments, state.currentLocation, state.navigationActive && state.followNavigation,
            state.navigationActive, state.navigationZoomLevel, state.navigationRecenterToken, state.bearingDegrees,
            vm::pauseNavigationFollow, dark, Modifier.fillMaxSize()
        )
        state.onlineAvailable -> OnlineIranMap(
            context, state.routeAlternatives.ifEmpty { listOfNotNull(state.route) }, state.selectedRouteIndex,
            state.traffic, state.trafficSegments,
            (state.personalPlaces + state.recentPlaces + listOfNotNull(state.origin, state.destination)).distinctBy { it.personalCode ?: it.code.toString() },
            state.currentLocation, state.navigationActive && state.followNavigation, state.navigationActive,
            state.navigationZoomLevel, state.navigationRecenterToken, state.bearingDegrees, vm::pauseNavigationFollow,
            dark, state.satelliteMode, Modifier.fillMaxSize()
        )
        else -> Box(Modifier.fillMaxSize().background(Color(0xFF102333)), contentAlignment = Alignment.Center) {
            Text("نقشه آنلاین در دسترس نیست؛ نقشه آفلاین ایران را از تنظیمات دانلود کنید", color = V5Muted, modifier = Modifier.padding(32.dp))
        }
    }
}

@Composable
private fun V5Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = V5Text, fontWeight = FontWeight.Bold)
        Text(label, color = V5Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun V5MenuSheet(
    onSearch: () -> Unit, onRoutes: () -> Unit, onFavorites: () -> Unit, onWeather: () -> Unit,
    onGas: () -> Unit, onFood: () -> Unit, onHotel: () -> Unit, onSight: () -> Unit,
    onSatellite: () -> Unit, onSettings: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("NV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text("همه ابزارها فقط هنگام نیاز باز می‌شوند", color = V5Muted)
        val rows = listOf(
            Triple("جستجوی مبدأ / مقصد", Icons.Rounded.Search, onSearch),
            Triple("مسیرهای پیشنهادی", Icons.Rounded.Route, onRoutes),
            Triple("مکان‌های ذخیره‌شده", Icons.Rounded.Bookmark, onFavorites),
            Triple("آب‌وهوا و وضعیت مسیر", Icons.Rounded.Cloud, onWeather),
            Triple("پمپ بنزین", Icons.Rounded.LocalGasStation, onGas),
            Triple("رستوران", Icons.Rounded.Restaurant, onFood),
            Triple("اقامتگاه", Icons.Rounded.Hotel, onHotel),
            Triple("جاذبه دیدنی", Icons.Rounded.PhotoCamera, onSight),
            Triple("ماهواره / نقشه", Icons.Rounded.Layers, onSatellite),
            Triple("تنظیمات", Icons.Rounded.Settings, onSettings)
        )
        rows.forEach { (title, icon, action) ->
            Surface(Modifier.fillMaxWidth().clickable(onClick = action), RoundedCornerShape(16.dp), Color(0xCC123148)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = V5Cyan)
                    Spacer(Modifier.width(14.dp))
                    Text(title, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun V5SearchSheet(state: NvUiState, vm: NvViewModel, onDone: () -> Unit) {
    val suggestions = if (state.destinationQuery.isNotBlank()) state.destinationSuggestions else state.originSuggestions
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text("مبدأ و مقصد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(state.originQuery, vm::updateOriginQuery, label = { Text("مبدأ") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { vm.updateOriginQuery("") }) { Icon(Icons.Rounded.Close, null) } })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(state.destinationQuery, vm::updateDestinationQuery, label = { Text("مقصد / کد NV") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { vm.updateDestinationQuery("") }) { Icon(Icons.Rounded.Close, null) } })
        LazyColumn(Modifier.heightIn(max = 220.dp)) {
            items(suggestions) { p ->
                Row(Modifier.fillMaxWidth().clickable { if (state.destinationQuery.isNotBlank()) vm.selectDestination(p) else vm.selectOrigin(p) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null, tint = V5Cyan)
                    Spacer(Modifier.width(8.dp))
                    Text(p.name, Modifier.weight(1f))
                    Text(p.personalCode ?: p.code.toString(), color = V5Muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::useCurrentLocationAsOrigin, modifier = Modifier.weight(1f)) { Text("موقعیت فعلی") }
            Button(onClick = { vm.calculateRoute(); onDone() }, enabled = state.origin != null && state.destination != null, modifier = Modifier.weight(1f)) { Text("محاسبه مسیر") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun V5RoutesSheet(state: NvUiState, vm: NvViewModel, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text("مسیرهای پیشنهادی", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        state.routeAlternatives.forEachIndexed { i, r ->
            Surface(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { vm.selectRoute(i); onDone() }, RoundedCornerShape(16.dp), if (i == state.selectedRouteIndex) Color(0xCC123148) else V5Panel, border = BorderStroke(1.dp, if (i == state.selectedRouteIndex) V5Cyan else V5Muted.copy(alpha = .25f))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("مسیر ${i + 1}")
                    Text("${(r.travelSeconds / 60).toInt()} دقیقه • ${String.format("%.1f", r.distanceMeters / 1000)} km")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun V5PlacesSheet(places: List<Place>, onPlace: (Place) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text("مکان‌های ذخیره‌شده", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (places.isEmpty()) Text("موردی ذخیره نشده است", color = V5Muted, modifier = Modifier.padding(vertical = 20.dp))
        places.forEach { p -> Text(p.name, Modifier.fillMaxWidth().clickable { onPlace(p) }.padding(16.dp)) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun V5NoticeSheet(lines: List<String>) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text("وضعیت مسیر", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (lines.isEmpty()) Text("اطلاعات پس از انتخاب مسیر نمایش داده می‌شود", color = V5Muted, modifier = Modifier.padding(vertical = 20.dp))
        lines.forEach { Text(it, Modifier.padding(vertical = 8.dp)) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun V5SettingsSheet(state: NvUiState, vm: NvViewModel, theme: AppThemeMode, onTheme: (AppThemeMode) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("تنظیمات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("نمای ماهواره‌ای"); Switch(state.satelliteMode, { vm.toggleSatelliteMode() }) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("اولویت نقشه آفلاین"); Switch(state.preferOffline, { vm.setPreferOffline(it) }) }
        Button(onClick = vm::startMapDownload, modifier = Modifier.fillMaxWidth()) { Text("دانلود نقشه آفلاین ایران") }
        Text("پوسته: ${theme.name}", color = V5Muted)
        Spacer(Modifier.height(24.dp))
    }
}
package ir.nv.navigation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.ui.theme.AppThemeMode

private val DashboardNavy = Color(0xF407182B)
private val DashboardPanel = Color(0xF20A2944)
private val DashboardPanelHigh = Color(0xF5123857)
private val DashboardCyan = Color(0xFF18D9FF)
private val DashboardBlue = Color(0xFF179CFF)
private val DashboardLime = Color(0xFFB7FF65)
private val DashboardText = Color(0xFFF7FBFF)
private val DashboardMuted = Color(0xFFA7BBCB)
private val DashboardOutline = Color(0xFF236B91)

enum class DashboardPanelType { FAVORITES, WEATHER, SETTINGS }

enum class DashboardTab(val title: String, val icon: ImageVector) {
    NAVIGATION("مسیریابی", Icons.Rounded.Navigation),
    SEARCH("جست‌وجو", Icons.Rounded.Search),
    FAVORITES("علاقه‌مندی‌ها", Icons.Rounded.BookmarkBorder),
    ROUTES("مسیرها", Icons.Rounded.Route),
    WEATHER("آب و هوا", Icons.Rounded.Cloud),
    SETTINGS("تنظیمات", Icons.Rounded.Settings)
}

@Composable
fun NvDashboardHeader(
    destinationName: String?,
    weatherNotice: RouteNotice?,
    compact: Boolean,
    onSearch: () -> Unit,
    onWeather: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DashboardNavy,
            border = BorderStroke(1.dp, DashboardCyan.copy(alpha = .55f)),
            shadowElevation = 14.dp
        ) {
            Row(
                Modifier.padding(horizontal = if (compact) 9.dp else 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Navigation, null, tint = DashboardCyan, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("NV", color = DashboardText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    if (!compact) Text("همراه هوشمند سفر", color = DashboardMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Surface(
            modifier = Modifier.weight(1f).clickable(onClick = onSearch),
            shape = RoundedCornerShape(18.dp),
            color = DashboardPanelHigh,
            border = BorderStroke(1.dp, DashboardCyan.copy(alpha = .5f)),
            shadowElevation = 14.dp
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Mic, null, tint = DashboardText, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    destinationName ?: "نام یا کد مکان را جست‌وجو کنید…",
                    modifier = Modifier.weight(1f),
                    color = if (destinationName == null) DashboardMuted else DashboardText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Rounded.Search, "جست‌وجو", tint = DashboardCyan, modifier = Modifier.size(25.dp))
            }
        }

        Surface(
            modifier = Modifier.clickable(onClick = onWeather),
            shape = RoundedCornerShape(17.dp),
            color = DashboardNavy,
            border = BorderStroke(1.dp, DashboardOutline),
            shadowElevation = 10.dp
        ) {
            Row(
                Modifier.padding(horizontal = if (compact) 10.dp else 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (weatherNotice?.title?.startsWith("هشدار") == true) Icons.Rounded.Thunderstorm else Icons.Rounded.WbSunny,
                    null,
                    tint = if (weatherNotice?.title?.startsWith("هشدار") == true) Color(0xFFFFB52E) else Color(0xFFFFDE3B),
                    modifier = Modifier.size(28.dp)
                )
                if (!compact) {
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.widthIn(max = 130.dp)) {
                        Text(weatherNotice?.title ?: "وضعیت هوا", color = DashboardText, fontWeight = FontWeight.Black, maxLines = 1)
                        Text(
                            weatherNotice?.detail ?: "پس از انتخاب مسیر",
                            color = DashboardMuted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.size(48.dp).clickable(onClick = onProfile),
            shape = CircleShape,
            color = DashboardPanelHigh,
            border = BorderStroke(1.dp, DashboardOutline),
            shadowElevation = 10.dp
        ) {
            Icon(Icons.Rounded.Person, "مکان‌های من", tint = DashboardText, modifier = Modifier.padding(11.dp))
        }
    }
}

@Composable
fun NvDashboardServiceRail(
    satelliteMode: Boolean,
    onLayers: () -> Unit,
    onCategory: (String) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DashboardRailAction(Icons.Rounded.Layers, "لایه‌ها", satelliteMode, onLayers)
        DashboardRailAction(Icons.Rounded.LocalGasStation, "بنزین", false) { onCategory("پمپ بنزین") }
        DashboardRailAction(Icons.Rounded.Restaurant, "رستوران", false) { onCategory("رستوران") }
        DashboardRailAction(Icons.Rounded.Hotel, "اقامتگاه", false) { onCategory("هتل و اقامتگاه") }
        DashboardRailAction(Icons.Rounded.PhotoCamera, "دیدنی", false) { onCategory("جاذبه گردشگری") }
        DashboardRailAction(Icons.Rounded.MoreHoriz, "بیشتر", false, onMore)
    }
}

@Composable
private fun DashboardRailAction(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.width(58.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = if (selected) DashboardPanelHigh else DashboardNavy,
        border = BorderStroke(1.dp, if (selected) DashboardLime else DashboardOutline.copy(alpha = .72f)),
        shadowElevation = 9.dp
    ) {
        Column(
            Modifier.padding(horizontal = 3.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(icon, label, tint = if (selected) DashboardLime else DashboardText, modifier = Modifier.size(22.dp))
            Text(label, color = DashboardText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun NvDashboardBottomDock(
    selected: DashboardTab,
    compact: Boolean,
    onSelect: (DashboardTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 27.dp, topEnd = 27.dp),
        color = DashboardNavy,
        border = BorderStroke(1.dp, DashboardOutline),
        shadowElevation = 18.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DashboardTab.entries.forEach { tab ->
                val active = tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(15.dp))
                        .clickable { onSelect(tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) DashboardCyan.copy(alpha = .14f) else Color.Transparent)
                            .padding(horizontal = if (compact) 9.dp else 14.dp, vertical = 4.dp)
                    ) {
                        Icon(tab.icon, tab.title, tint = if (active) DashboardCyan else DashboardMuted, modifier = Modifier.size(22.dp))
                    }
                    if (!compact || active) {
                        Text(
                            tab.title,
                            color = if (active) DashboardCyan else DashboardMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NvDashboardQuickPanel(
    type: DashboardPanelType,
    state: NvUiState,
    themeMode: AppThemeMode,
    onDismiss: () -> Unit,
    onSelectPlace: (Place) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onToggleSatellite: () -> Unit,
    onToggleOffline: (Boolean) -> Unit,
    onDownloadMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (type) {
        DashboardPanelType.FAVORITES -> "علاقه‌مندی‌ها و مکان‌های اخیر"
        DashboardPanelType.WEATHER -> "هوا و هشدارهای مسیر"
        DashboardPanelType.SETTINGS -> "تنظیمات سریع نقشه"
    }
    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = 620.dp),
        shape = RoundedCornerShape(24.dp),
        color = DashboardNavy,
        border = BorderStroke(1.dp, DashboardCyan.copy(alpha = .48f)),
        shadowElevation = 20.dp
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = DashboardText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(35.dp)) {
                    Icon(Icons.Rounded.Close, "بستن", tint = DashboardMuted)
                }
            }
            when (type) {
                DashboardPanelType.FAVORITES -> FavoritesPanelContent(state, onSelectPlace)
                DashboardPanelType.WEATHER -> WeatherPanelContent(state.routeNotices)
                DashboardPanelType.SETTINGS -> SettingsPanelContent(
                    state = state,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onToggleSatellite = onToggleSatellite,
                    onToggleOffline = onToggleOffline,
                    onDownloadMap = onDownloadMap
                )
            }
        }
    }
}

@Composable
private fun FavoritesPanelContent(state: NvUiState, onSelectPlace: (Place) -> Unit) {
    val places = (state.personalPlaces + state.recentPlaces).distinctBy { it.personalCode ?: it.code.toString() }.take(6)
    if (places.isEmpty()) {
        PanelEmptyState(Icons.Rounded.BookmarkBorder, "هنوز مکانی ذخیره نشده است")
    } else {
        Column(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            places.forEach { place ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectPlace(place) },
                    shape = RoundedCornerShape(14.dp),
                    color = DashboardPanel,
                    border = BorderStroke(1.dp, DashboardOutline.copy(alpha = .65f))
                ) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Place, null, tint = DashboardCyan)
                        Spacer(Modifier.width(8.dp))
                        Text(place.name, color = DashboardText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                        val code = place.personalCode ?: place.code.takeIf { it > 0 }?.toString()
                        if (code != null) Text("NV:$code", color = DashboardLime, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherPanelContent(notices: List<RouteNotice>) {
    val weather = notices.filter { it.kind == RouteNotice.Kind.WEATHER && it.distanceAheadMeters <= 10_000.0 }
    if (weather.isEmpty()) {
        PanelEmptyState(Icons.Rounded.Cloud, "برای دریافت هوای ۱۰ کیلومتر جلوتر ابتدا یک مسیر انتخاب کنید")
    } else {
        Column(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            weather.take(5).forEach { notice ->
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = DashboardPanel,
                    border = BorderStroke(1.dp, if (notice.title.startsWith("هشدار")) Color(0xFFFFB52E) else DashboardOutline)
                ) {
                    Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Cloud, null, tint = if (notice.title.startsWith("هشدار")) Color(0xFFFFB52E) else DashboardCyan)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(notice.title, color = DashboardText, fontWeight = FontWeight.Black)
                            Text(notice.detail, color = DashboardMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text("%.1f km".format(notice.distanceAheadMeters / 1_000.0), color = DashboardLime, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanelContent(
    state: NvUiState,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onToggleSatellite: () -> Unit,
    onToggleOffline: (Boolean) -> Unit,
    onDownloadMap: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("نمای روز و شب", color = DashboardMuted, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(mode.title) },
                    leadingIcon = {
                        Icon(
                            when (mode) {
                                AppThemeMode.AUTO -> Icons.Rounded.BrightnessAuto
                                AppThemeMode.DAY -> Icons.Rounded.LightMode
                                AppThemeMode.NIGHT -> Icons.Rounded.DarkMode
                            },
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DashboardCyan.copy(alpha = .2f),
                        selectedLabelColor = DashboardText,
                        labelColor = DashboardMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = themeMode == mode,
                        borderColor = DashboardOutline,
                        selectedBorderColor = DashboardCyan
                    )
                )
            }
        }
        SettingsAction(
            icon = Icons.Rounded.SatelliteAlt,
            title = "نمای ماهواره‌ای و سه‌بعدی",
            subtitle = if (state.onlineAvailable) "نمای تصویری با لایه معابر" else "فقط هنگام اتصال اینترنت",
            selected = state.satelliteMode,
            enabled = state.onlineAvailable,
            onClick = onToggleSatellite
        )
        SettingsAction(
            icon = Icons.Rounded.Map,
            title = "مسیریابی آفلاین",
            subtitle = if (state.offlineReady) "نقشه ایران آماده است" else "نقشه ایران هنوز دانلود نشده",
            selected = state.preferOffline,
            enabled = state.offlineReady,
            onClick = { onToggleOffline(!state.preferOffline) }
        )
        if (!state.offlineReady) {
            OutlinedButton(onClick = onDownloadMap, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, DashboardCyan)) {
                Icon(Icons.Rounded.Download, null, tint = DashboardCyan)
                Spacer(Modifier.width(7.dp))
                Text("دانلود نقشه کامل ایران", color = DashboardText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = DashboardPanel,
        border = BorderStroke(1.dp, if (selected) DashboardLime else DashboardOutline.copy(alpha = .7f))
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (!enabled) DashboardMuted.copy(alpha = .45f) else if (selected) DashboardLime else DashboardCyan)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = if (enabled) DashboardText else DashboardMuted, fontWeight = FontWeight.Black)
                Text(subtitle, color = DashboardMuted, style = MaterialTheme.typography.labelSmall)
            }
            Switch(
                checked = selected,
                onCheckedChange = if (enabled) { { _ -> onClick() } } else null,
                colors = SwitchDefaults.colors(checkedThumbColor = DashboardNavy, checkedTrackColor = DashboardLime)
            )
        }
    }
}

@Composable
private fun PanelEmptyState(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(DashboardPanel).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = DashboardCyan)
        Spacer(Modifier.width(9.dp))
        Text(text, color = DashboardMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

fun dashboardEdgeScrim(): Brush = Brush.verticalGradient(
    listOf(Color(0xCC03101E), Color.Transparent, Color.Transparent, Color(0xAA03101E))
)

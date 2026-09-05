package ir.nv.navigation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.core.TrafficSummary
import ir.nv.navigation.entitlement.BillingState
import ir.nv.navigation.entitlement.TrialManager
import ir.nv.navigation.map.IranPackManager
import ir.nv.navigation.data.PlaceCodes
import ir.nv.navigation.ui.theme.AppThemeMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import coil.compose.AsyncImage

private val OnlineGreen = Color(0xFF00A884)
private val OfflineAmber = Color(0xFFFFA000)
private val OriginBlue = Color(0xFF2979FF)
private val DestinationRed = Color(0xFFFF4D67)
private val NvNavy = Color(0xF2071526)
private val NvPanel = Color(0xFF0B2035)
private val NvPanelHigh = Color(0xFF102C45)
private val NvCyan = Color(0xFF18D4FF)
private val NvLime = Color(0xFFD7FF5B)
private val NvText = Color(0xFFF4FAFF)
private val NvMuted = Color(0xFF91A9BC)
private val NvOutline = Color(0xFF25445E)
private val NvInk = Color(0xFF031421)

@Composable
fun NavigationTopBar(
    state: NvUiState,
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    onOpenOfflineMaps: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shadowElevation = 5.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(if (state.onlineAvailable) OnlineGreen else OfflineAmber)
                )
                Text(
                    when {
                        state.onlineAvailable && !state.preferOffline -> "آنلاین"
                        state.offlineReady -> "آفلاین آماده"
                        else -> "بدون اتصال"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                when (val trial = state.trialState) {
                    is TrialManager.State.Trial -> Text(
                        "${trial.daysRemaining} روز رایگان",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TrialManager.State.Paid -> Text("فعال", style = MaterialTheme.typography.labelSmall, color = OnlineGreen)
                    else -> Unit
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            AppIconButton(Icons.Rounded.Map, "نقشه‌های آفلاین", onOpenOfflineMaps)
            AppIconButton(
                if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                if (darkMode) "حالت روز" else "حالت شب",
                onToggleTheme
            )
        }
    }
}

@Composable
private fun AppIconButton(icon: ImageVector, description: String, onClick: () -> Unit, dark: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (dark) NvPanel.copy(alpha = 0.96f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 5.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = description, tint = if (dark) NvText else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    state: NvUiState,
    onDismiss: () -> Unit,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onOriginSelect: (Place) -> Unit,
    onDestinationSelect: (Place) -> Unit,
    onSwap: () -> Unit,
    onRoute: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onSaveCode: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NvNavy,
        contentColor = NvText,
        dragHandle = { Box(Modifier.padding(vertical = 10.dp).width(42.dp).height(4.dp).clip(CircleShape).background(NvMuted.copy(alpha = 0.6f))) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "انتخاب مسیر",
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            SearchPanel(
                state = state,
                onOriginChange = onOriginChange,
                onDestinationChange = onDestinationChange,
                onOriginSelect = onOriginSelect,
                onDestinationSelect = onDestinationSelect,
                onSwap = onSwap,
                onRoute = onRoute,
                onUseCurrentLocation = onUseCurrentLocation,
                onSaveCode = onSaveCode
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeModeSheet(
    selected: AppThemeMode,
    resolvedDark: Boolean,
    onSelect: (AppThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NvNavy,
        contentColor = NvText,
        dragHandle = {
            Box(
                Modifier.padding(vertical = 10.dp).width(42.dp).height(4.dp)
                    .clip(CircleShape).background(NvMuted.copy(alpha = 0.6f))
            )
        }
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("نمای نقشه", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                "حالت فعال: ${if (resolvedDark) "شب" else "روز"}",
                color = NvCyan,
                fontWeight = FontWeight.Bold
            )
            AppThemeMode.entries.forEach { mode ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(mode) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected == mode) NvPanelHigh else NvPanel,
                    border = BorderStroke(1.dp, if (selected == mode) NvCyan else NvOutline)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(18.dp).clip(CircleShape).background(
                                if (selected == mode) NvCyan else NvOutline
                            )
                        )
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mode.title, color = NvText, fontWeight = FontWeight.Black)
                            Text(mode.description, color = NvMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            when (mode) {
                                AppThemeMode.AUTO -> "A"
                                AppThemeMode.DAY -> "☀"
                                AppThemeMode.NIGHT -> "☾"
                            },
                            color = if (selected == mode) NvCyan else NvMuted,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun DestinationSearchBar(
    recentPlaces: List<Place>,
    personalPlaces: List<Place>,
    onlineAvailable: Boolean,
    offlineReady: Boolean,
    onClick: () -> Unit,
    onRecentClick: (Place) -> Unit,
    modifier: Modifier = Modifier
) {
    val shortcuts = (personalPlaces + recentPlaces).distinctBy { it.personalCode ?: it.code.toString() }.take(2)
    Column(modifier.fillMaxWidth().padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.97f),
            shadowElevation = 12.dp
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF102A3C), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("نام یا کد مکان", color = Color(0xFF102A3C), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("شهر، خیابان یا کد عددی NV", color = Color(0xFF617789), style = MaterialTheme.typography.bodySmall)
                }
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (onlineAvailable) OnlineGreen else OfflineAmber))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Mic, contentDescription = "جست‌وجوی صوتی", tint = Color(0xFF29445A))
            }
        }
        shortcuts.forEachIndexed { index, place ->
            Surface(
                modifier = Modifier.fillMaxWidth(0.58f).clickable { onRecentClick(place) },
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.93f),
                shadowElevation = 5.dp
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (index == 0) Icons.Rounded.History else Icons.Rounded.Place, null, tint = Color(0xFF2A5770), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        place.name,
                        Modifier.weight(1f),
                        color = Color(0xFF122C3F),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        place.personalCode ?: place.code.takeIf { it > 0 }?.toString().orEmpty(),
                        color = Color(0xFF127B95),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
        if (shortcuts.isEmpty()) {
            Text(
                if (offlineReady) "نقشه آفلاین آماده است" else "برای شروع مقصد را جست‌وجو کنید",
                modifier = Modifier.padding(horizontal = 12.dp),
                color = Color(0xFF24475C),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SelectedRouteHeader(
    origin: Place?,
    destination: Place?,
    onEdit: () -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(22.dp),
        color = NvNavy,
        contentColor = NvText,
        shadowElevation = 7.dp
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(OriginBlue))
                Box(Modifier.size(10.dp).clip(CircleShape).background(DestinationRed))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    origin?.name ?: "مبدأ را انتخاب کنید",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NvMuted
                )
                HorizontalDivider(color = NvOutline)
                Text(
                    destination?.name ?: "مقصد را انتخاب کنید",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = NvText
                )
                destination?.let { place ->
                    val code = place.personalCode ?: place.code.takeIf { it > 0 }?.toString()
                    code?.let {
                        Text("NV $it", color = NvCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            IconButton(onClick = onSwap) {
                Icon(Icons.Rounded.SwapVert, contentDescription = "جابجایی مبدأ و مقصد", tint = NvCyan)
            }
        }
    }
}

@Composable
fun SearchPanel(
    state: NvUiState,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onOriginSelect: (Place) -> Unit,
    onDestinationSelect: (Place) -> Unit,
    onSwap: () -> Unit,
    onRoute: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onSaveCode: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = NvPanel,
            contentColor = NvText
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 7.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlaceSearchField(
                label = "مبدأ",
                placeholder = "نام، کد مکان یا انتخاب موقعیت فعلی",
                value = state.originQuery,
                color = OriginBlue,
                suggestions = state.originSuggestions,
                searching = state.originSearching,
                onValueChange = onOriginChange,
                onSelect = onOriginSelect,
                trailingIcon = {
                    IconButton(onClick = onUseCurrentLocation, enabled = !state.locating) {
                        if (state.locating) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = NvCyan)
                        } else {
                            Icon(Icons.Rounded.MyLocation, contentDescription = "موقعیت فعلی من")
                        }
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onUseCurrentLocation, enabled = !state.locating) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("استفاده از موقعیت فعلی من")
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onSwap,
                    enabled = state.origin != null && state.destination != null
                ) {
                    Icon(Icons.Rounded.SwapVert, contentDescription = "جابجایی مبدأ و مقصد", tint = NvCyan)
                }
            }
            PlaceSearchField(
                label = "مقصد",
                placeholder = "کجا می‌روید؟ نام یا کد مکان",
                value = state.destinationQuery,
                color = DestinationRed,
                suggestions = state.destinationSuggestions,
                searching = state.destinationSearching,
                onValueChange = onDestinationChange,
                onSelect = onDestinationSelect
            )
            Text(
                "مبدأ می‌تواند موقعیت فعلی شما یا هر مکان جست‌وجوشده باشد.",
                color = NvMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }

        state.searchMessage?.let { warning ->
            Text(
                warning,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        AnimatedVisibility(state.destination != null) {
            Column {
                HorizontalDivider(color = NvOutline)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onSaveCode,
                        enabled = state.destination != null,
                        colors = ButtonDefaults.textButtonColors(contentColor = NvCyan)
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("ذخیره کد")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onRoute,
                        enabled = state.origin != null && state.destination != null && !state.routing,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                        colors = ButtonDefaults.buttonColors(containerColor = NvCyan, contentColor = NvInk)
                    ) {
                        if (state.routing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(19.dp),
                                strokeWidth = 2.dp,
                                color = NvInk
                            )
                        } else {
                            Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(if (state.routing) "در حال مسیریابی" else "مسیریابی", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceSearchField(
    label: String,
    placeholder: String,
    value: String,
    color: Color,
    suggestions: List<Place>,
    searching: Boolean,
    onValueChange: (String) -> Unit,
    onSelect: (Place) -> Unit,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(color)) },
            trailingIcon = if (searching) {
                { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else trailingIcon,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = NvText,
                unfocusedTextColor = NvText,
                focusedBorderColor = NvCyan,
                unfocusedBorderColor = NvOutline,
                cursorColor = NvCyan,
                focusedLabelColor = NvCyan,
                unfocusedLabelColor = NvMuted,
                focusedPlaceholderColor = NvMuted,
                unfocusedPlaceholderColor = NvMuted,
                focusedTrailingIconColor = NvCyan,
                unfocusedTrailingIconColor = NvMuted
            )
        )
        AnimatedVisibility(suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(max = 230.dp),
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(containerColor = NvPanelHigh, contentColor = NvText),
                border = BorderStroke(1.dp, NvOutline)
            ) {
                Column {
                    suggestions.take(6).forEachIndexed { index, place ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(place) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Place,
                                contentDescription = null,
                                tint = NvCyan,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(place.name, color = NvText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(
                                    buildString {
                                        append(categoryLabel(place.category))
                                        when {
                                            !place.personalCode.isNullOrBlank() -> append("  •  کد ${place.personalCode}")
                                            place.code > 0 -> append("  •  NV:${place.code}")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NvMuted,
                                    maxLines = 1
                                )
                            }
                        }
                        if (index < suggestions.take(6).lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = NvOutline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusMessage(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 3.dp
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface)
            Spacer(Modifier.width(8.dp))
            Text(text, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun NavigationHud(
    route: Route,
    maneuverIndex: Int,
    distanceToManeuverMeters: Double,
    speedKmh: Int,
    offRoute: Boolean,
    onStop: () -> Unit
) {
    val displayManeuverIndex = route.displayManeuverIndex(maneuverIndex)
    val maneuver = route.maneuvers.getOrNull(displayManeuverIndex)
    val displayDistance = when {
        displayManeuverIndex != maneuverIndex -> maneuver?.distanceMeters
        distanceToManeuverMeters > 0.0 -> distanceToManeuverMeters
        else -> maneuver?.distanceMeters
    } ?: route.distanceMeters
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (offRoute) Color(0xFF8E2634) else NvNavy,
        contentColor = NvText,
        border = BorderStroke(1.dp, if (offRoute) Color(0xFFFF7185) else NvOutline),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = NvCyan.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, NvCyan.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = maneuverIcon(maneuver?.direction),
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(43.dp),
                    tint = NvCyan
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    formatDistance(displayDistance),
                    color = NvText,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    if (offRoute) "از مسیر خارج شده‌اید؛ مسیر جدید در حال محاسبه است"
                    else maneuver?.instruction ?: "مستقیم به سمت مقصد ادامه دهید",
                    color = NvText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!offRoute && !maneuver?.lanes.isNullOrEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        maneuver?.lanes?.forEach { lane ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NvCyan.copy(
                                    alpha = if (lane.recommended) 0.25f else 0.08f
                                )
                            ) {
                                Icon(
                                    maneuverIcon(lane.direction),
                                    contentDescription = null,
                                    modifier = Modifier.padding(4.dp).size(20.dp),
                                    tint = NvText.copy(
                                        alpha = if (lane.recommended) 1f else 0.45f
                                    )
                                )
                            }
                        }
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = NvPanelHigh,
                border = BorderStroke(2.dp, NvLime)
            ) {
                Column(
                    modifier = Modifier.size(58.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("$speedKmh", color = NvLime, fontWeight = FontWeight.Black)
                    Text("km/h", color = NvMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = onStop) {
                Text("×", color = NvText, style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

private fun Route.displayManeuverIndex(currentIndex: Int): Int {
    val current = maneuvers.getOrNull(currentIndex) ?: return currentIndex
    val normalized = current.instruction.lowercase()
    val departure = currentIndex == 0 && (
        normalized.contains("حرکت را آغاز") ||
            normalized.contains("شروع حرکت") ||
            normalized.contains("depart")
        )
    return if (departure && maneuvers.size > 1) 1 else currentIndex
}

@Composable
fun NavigationTrafficRail(
    traffic: TrafficSummary?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(start = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = NvNavy,
        contentColor = NvText,
        border = BorderStroke(1.dp, NvOutline),
        shadowElevation = 10.dp
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(10.dp).clip(RoundedCornerShape(6.dp)),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Box(Modifier.fillMaxWidth().height(25.dp).background(Color(0xFFE64045)))
                Box(Modifier.fillMaxWidth().height(25.dp).background(Color(0xFFFFB52E)))
                Box(Modifier.fillMaxWidth().height(25.dp).background(Color(0xFF64D66D)))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    when {
                        traffic == null -> "نامشخص"
                        traffic.delaySeconds > 0 -> "${(traffic.delaySeconds / 60).roundToInt()} دقیقه"
                        else -> "روان"
                    },
                    color = when {
                        traffic == null -> NvMuted
                        traffic.delaySeconds > 0 -> Color(0xFFFFB52E)
                        else -> Color(0xFF64D66D)
                    },
                    fontWeight = FontWeight.Black
                )
                Text(
                    traffic?.lengthMeters?.takeIf { it > 0 }?.let { "${formatDistance(it)} ترافیک" } ?: "وضعیت مسیر",
                    color = NvMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun NavigationWeatherCard(
    notice: RouteNotice?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(end = 12.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = NvNavy,
        contentColor = NvText,
        border = BorderStroke(1.dp, NvOutline),
        shadowElevation = 10.dp
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudDone, null, tint = NvCyan)
            Spacer(Modifier.width(7.dp))
            Column(Modifier.width(124.dp)) {
                Text("هوای ۱۰ کیلومتر جلوتر", color = NvText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    notice?.detail ?: "در حال دریافت اطلاعات واقعی",
                    color = if (notice?.title?.startsWith("هشدار") == true) Color(0xFFFFB52E) else NvMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun NavigationVehicleMarker(
    bearingDegrees: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(NvCyan.copy(alpha = 0.16f))
        )
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = NvNavy.copy(alpha = 0.95f),
            border = BorderStroke(2.dp, Color.White),
            shadowElevation = 12.dp
        ) {
            Icon(
                Icons.Rounded.Navigation,
                contentDescription = "موقعیت و جهت حرکت",
                modifier = Modifier.padding(8.dp).rotate(bearingDegrees),
                tint = NvCyan
            )
        }
    }
}

@Composable
fun DrivingZoomControls(
    zoomLevel: Int,
    automatic: Boolean,
    following: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconButton(Icons.Rounded.Add, "بزرگ‌نمایی", onZoomIn, dark = true)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NvNavy.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, NvOutline)
        ) {
            Text(
                if (automatic) "خودکار $zoomLevel×" else "$zoomLevel×",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                color = NvCyan,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black
            )
        }
        AppIconButton(Icons.Rounded.Remove, "کوچک‌نمایی", onZoomOut, dark = true)
        Surface(
            shape = CircleShape,
            color = if (following) NvCyan else NvNavy.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, if (following) NvCyan else NvOutline),
            shadowElevation = 5.dp
        ) {
            IconButton(onClick = onRecenter) {
                Icon(
                    Icons.Rounded.GpsFixed,
                    contentDescription = "بازگشت به دنبال‌کردن خودرو",
                    tint = if (following) NvInk else NvText
                )
            }
        }
    }
}

@Composable
fun FloatingNavigationControls(
    voiceEnabled: Boolean,
    darkMode: Boolean,
    onToggleVoice: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        AppIconButton(
            if (voiceEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
            if (voiceEnabled) "قطع صدای راهنما" else "فعال‌کردن صدای راهنما",
            onToggleVoice,
            dark = true
        )
        AppIconButton(Icons.Rounded.Map, "نقشه‌های آفلاین", onOpenOfflineMaps, dark = true)
        AppIconButton(
            if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            if (darkMode) "حالت روز" else "حالت شب",
            onToggleTheme,
            dark = true
        )
    }
}

@Composable
fun HomeMapControls(
    darkMode: Boolean,
    onMyLocation: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        AppIconButton(Icons.Rounded.MyLocation, "موقعیت من", onMyLocation)
        AppIconButton(Icons.Rounded.Map, "نقشه‌های آفلاین", onOpenOfflineMaps)
        AppIconButton(
            if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            if (darkMode) "حالت روز" else "حالت شب",
            onToggleTheme
        )
    }
}

@Composable
fun NvHomeDock(
    onRoute: () -> Unit,
    onCodes: () -> Unit,
    onOfflineMaps: () -> Unit,
    darkMode: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (darkMode) NvNavy else Color.White.copy(alpha = 0.98f),
        contentColor = if (darkMode) NvText else Color(0xFF102A3C),
        border = BorderStroke(1.dp, if (darkMode) NvOutline else Color(0xFFD7E2E9)),
        shadowElevation = 12.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DockAction("مسیر", Icons.Rounded.DirectionsCar, true, darkMode, onRoute)
            DockAction("کدهای من", Icons.Rounded.Save, false, darkMode, onCodes)
            DockAction("نقشه آفلاین", Icons.Rounded.Download, false, darkMode, onOfflineMaps)
            DockAction(
                if (darkMode) "حالت روز" else "حالت شب",
                if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                false,
                darkMode,
                onToggleTheme
            )
        }
    }
}

@Composable
private fun DockAction(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    dark: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = if (selected) NvCyan.copy(alpha = 0.14f) else Color.Transparent
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp).size(22.dp),
                tint = if (selected) {
                    if (dark) NvCyan else Color(0xFF007C91)
                } else if (dark) NvMuted else Color(0xFF536B7C)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                if (dark) NvCyan else Color(0xFF007C91)
            } else if (dark) NvMuted else Color(0xFF536B7C),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun RouteSummaryCard(
    route: Route,
    destination: Place?,
    alternatives: List<Route>,
    selectedRouteIndex: Int,
    source: RouteSource,
    traffic: TrafficSummary?,
    notices: List<RouteNotice>,
    insightsLoading: Boolean,
    navigationActive: Boolean,
    remainingDistanceMeters: Double,
    remainingSeconds: Double,
    onStart: () -> Unit,
    onRouteSelect: (Int) -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenCode: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseSeconds = if (navigationActive && remainingSeconds > 0) remainingSeconds else route.travelSeconds
    val shownDistance = if (navigationActive && remainingDistanceMeters > 0) {
        remainingDistanceMeters
    } else route.distanceMeters
    val totalSeconds = baseSeconds + (traffic?.delaySeconds ?: 0.0)
    val eta = Instant.now().plusSeconds(totalSeconds.toLong())
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    if (navigationActive) {
        ActiveNavigationBar(
            distanceMeters = shownDistance,
            seconds = totalSeconds,
            eta = eta,
            traffic = traffic,
            destinationCode = destination?.code?.takeIf { it > 0 },
            onStop = onStop,
            modifier = modifier
        )
        return
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp)
            .shadow(10.dp, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = NvNavy, contentColor = NvText),
        border = BorderStroke(1.dp, NvOutline)
    ) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = if (navigationActive) onStop else onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("×", style = MaterialTheme.typography.titleLarge, color = NvText)
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(12.dp), color = NvPanelHigh, border = BorderStroke(1.dp, NvOutline)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (source == RouteSource.OFFLINE) Icons.Rounded.OfflinePin else Icons.Rounded.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = if (source == RouteSource.OFFLINE) NvLime else NvCyan
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(if (source == RouteSource.OFFLINE) "مسیر آفلاین" else "مسیر آنلاین", color = NvText, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            destination?.let { place ->
                val code = place.personalCode ?: place.code.takeIf { it > 0 }?.toString()
                Surface(
                    shape = RoundedCornerShape(17.dp),
                    color = NvPanelHigh,
                    border = BorderStroke(1.dp, NvCyan.copy(alpha = .58f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(13.dp), color = NvCyan.copy(alpha = .14f)) {
                            Icon(Icons.Rounded.Place, null, tint = NvCyan, modifier = Modifier.padding(9.dp).size(25.dp))
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(place.name, color = NvText, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("مقصد انتخاب‌شده", color = NvMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        if (code != null) {
                            Surface(shape = RoundedCornerShape(11.dp), color = NvNavy, border = BorderStroke(1.dp, NvLime.copy(alpha = .7f))) {
                                Text("NV:$code", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = NvLime, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RouteMetric("مسافت", "%.1f km".format(shownDistance / 1000.0))
                RouteMetric("زمان", "${(totalSeconds / 60.0).roundToInt()} دقیقه")
                RouteMetric("رسیدن", eta)
            }
            route.maneuvers.firstOrNull { it.roadName == "اتصال مسیر خاکی" }?.let { connector ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF3A2C16),
                    border = BorderStroke(1.dp, Color(0xFFFFB52E))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.DirectionsCar, null, tint = Color(0xFFFFB52E))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("اتصال از مسیر خاکی", color = NvText, fontWeight = FontWeight.Black)
                            Text(
                                "${formatDistance(connector.distanceMeters)} تا نزدیک‌ترین جاده قابل‌مسیریابی",
                                color = NvMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            if (alternatives.size > 1) {
                Text("همه مسیرهای پیدا‌شده — برای انتخاب لمس کنید", color = NvMuted, style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    alternatives.forEachIndexed { index, candidate ->
                        RouteChoiceCard(
                            route = candidate,
                            index = index,
                            selected = index == selectedRouteIndex,
                            onClick = { onRouteSelect(index) },
                            modifier = Modifier.width(142.dp)
                        )
                    }
                }
            }
            if (traffic != null && traffic.delaySeconds > 0) {
                Text(
                    "ترافیک: %.1f کیلومتر • ${traffic.delaySeconds.div(60).roundToInt()} دقیقه تأخیر"
                        .format(traffic.lengthMeters / 1000.0),
                    color = Color(0xFFFF7185),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            notices.firstOrNull { it.kind == RouteNotice.Kind.WEATHER }?.let { notice ->
                Surface(shape = RoundedCornerShape(14.dp), color = NvPanelHigh, border = BorderStroke(1.dp, NvOutline)) {
                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = NvCyan)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(notice.title, fontWeight = FontWeight.Bold, maxLines = 1, color = NvText)
                            Text(notice.detail, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = NvMuted)
                        }
                    }
                }
            }
            val attractions = notices.filter {
                it.kind == RouteNotice.Kind.ATTRACTION && it.distanceAheadMeters <= 10_000.0
            }.take(4)
            if (attractions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("جاذبه‌های نزدیک مسیر", color = NvText, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenPlaces) { Text("مشاهده همه", color = NvCyan) }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    attractions.forEach { notice ->
                        RouteAttractionCard(notice = notice, onClick = onOpenPlaces)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenPlaces,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, NvOutline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NvCyan)
                ) {
                    if (insightsLoading) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Explore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (insightsLoading) "در حال بررسی" else "جاذبه‌ها و هوا")
                }
                OutlinedButton(
                    onClick = onOpenCode,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, NvOutline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NvText)
                ) {
                    Icon(Icons.Rounded.QrCode2, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("کد مقصد")
                }
            }
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NvCyan, contentColor = NvInk)
            ) {
                Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("شروع حرکت", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun MapCodeBadge(place: Place, modifier: Modifier = Modifier) {
    val code = place.personalCode ?: place.code.takeIf { it > 0 }?.toString() ?: return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = NvNavy,
        border = BorderStroke(1.dp, NvCyan),
        shadowElevation = 8.dp
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(place.name, color = NvText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("کد این مکان: NV:$code", color = NvCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun RouteChoiceCard(route: Route, index: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) NvPanelHigh else NvPanel,
        contentColor = NvText,
        border = BorderStroke(1.dp, if (selected) NvCyan else NvOutline),
        shadowElevation = if (selected) 8.dp else 0.dp
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (index == 0) "پیشنهادی" else "مسیر ${index + 1}", color = if (selected) NvCyan else NvMuted, style = MaterialTheme.typography.labelMedium)
            Text("${(route.travelSeconds / 60).roundToInt()} دقیقه", color = NvText, fontWeight = FontWeight.Black)
            Text("%.1f km".format(route.distanceMeters / 1000), color = NvMuted, style = MaterialTheme.typography.labelSmall)
            Box(Modifier.padding(top = 5.dp).fillMaxWidth(0.55f).height(3.dp).clip(CircleShape).background(if (selected) NvCyan else NvLime.copy(alpha = 0.55f)))
        }
    }
}

@Composable
private fun RouteAttractionCard(notice: RouteNotice, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(158.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = NvPanel,
        border = BorderStroke(1.dp, NvOutline)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!notice.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = notice.imageUrl,
                    contentDescription = notice.title,
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(11.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(shape = RoundedCornerShape(11.dp), color = NvCyan.copy(alpha = .14f)) {
                    Icon(Icons.Rounded.Explore, null, tint = NvCyan, modifier = Modifier.padding(12.dp).size(26.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(notice.title, color = NvText, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                Text("%.1f km".format(notice.distanceAheadMeters / 1_000.0), color = NvLime, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ActiveNavigationBar(
    distanceMeters: Double,
    seconds: Double,
    eta: String,
    traffic: TrafficSummary?,
    destinationCode: Long?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp),
        shape = RoundedCornerShape(25.dp),
        color = NvNavy,
        contentColor = NvText,
        border = BorderStroke(1.dp, NvOutline),
        shadowElevation = 11.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(eta, color = NvText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("زمان رسیدن", color = NvMuted, style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${(seconds / 60.0).roundToInt()} دقیقه  •  ${formatDistance(distanceMeters)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = NvText
                )
                destinationCode?.let {
                    Text(
                        "مقصد  NV:$it",
                        color = NvCyan,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    if (traffic != null && traffic.delaySeconds > 0) {
                        "${traffic.delaySeconds.div(60).roundToInt()} دقیقه تأخیر ترافیک"
                    } else {
                        "حرکت در مسیر انتخاب‌شده"
                    },
                    color = if (traffic != null && traffic.delaySeconds > 0) {
                        Color(0xFFFF7185)
                    } else {
                        NvMuted
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            FilledIconButton(
                onClick = onStop,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = NvPanelHigh, contentColor = NvText)
            ) {
                Text("×", style = MaterialTheme.typography.headlineSmall, color = NvText)
            }
        }
    }
}

private fun maneuverIcon(direction: RouteManeuver.Direction?): ImageVector = when (direction) {
    RouteManeuver.Direction.LEFT,
    RouteManeuver.Direction.SLIGHT_LEFT,
    RouteManeuver.Direction.SHARP_LEFT -> Icons.Rounded.TurnLeft
    RouteManeuver.Direction.RIGHT,
    RouteManeuver.Direction.SLIGHT_RIGHT,
    RouteManeuver.Direction.SHARP_RIGHT -> Icons.Rounded.TurnRight
    RouteManeuver.Direction.UTURN -> Icons.Rounded.UTurnLeft
    else -> Icons.Rounded.ArrowUpward
}

private fun formatDistance(meters: Double): String = when {
    meters < 1_000 -> "${meters.roundToInt()} متر"
    else -> "%.1f کیلومتر".format(meters / 1_000.0)
}

@Composable
private fun RouteMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = NvText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelMedium, color = NvMuted)
    }
}

@Composable
fun NoMapConnection(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Rounded.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(58.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("اینترنت در دسترس نیست", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("اگر نقشه ایران را دانلود کنید، NV خودکار آفلاین می‌شود", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OfflinePrompt(onOpenOfflineMaps: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = OfflineAmber)
            Spacer(Modifier.width(9.dp))
            Text("برای روزهای بدون اینترنت آماده شوید", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Button(onClick = onOpenOfflineMaps) { Text("دانلود نقشه") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsSheet(
    state: NvUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onModeChange: (Boolean) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.padding(11.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("نقشه‌های آفلاین", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("دانلود فقط با انتخاب شما انجام می‌شود", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("نقشه کامل ایران", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("نقشه، جست‌وجوی مکان و مسیریابی آفلاین واقعی", style = MaterialTheme.typography.bodyMedium)
                    Text("حجم دانلود حدود ۱٫۶۵ گیگابایت • Wi-Fi پیشنهاد می‌شود", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            when (val status = state.packStatus) {
                IranPackManager.Status.NotStarted -> {
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("دانلود نقشه کامل ایران", fontWeight = FontWeight.Bold)
                    }
                }
                IranPackManager.Status.Installing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("در حال بررسی و نصب امن نقشه…", fontWeight = FontWeight.SemiBold)
                    }
                }
                is IranPackManager.Status.Downloading -> {
                    val progress = if (status.totalBytes > 0) {
                        (status.bytes.toFloat() / status.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else null
                    if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        if (status.totalBytes > 0) "${formatBytes(status.bytes)} از ${formatBytes(status.totalBytes)}"
                        else "در حال دریافت نقشه…",
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("توقف دانلود") }
                }
                is IranPackManager.Status.Failed -> {
                    Text(status.reason, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("تلاش دوباره") }
                }
                IranPackManager.Status.Ready -> {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.OfflinePin, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("نقشه ایران آماده است", fontWeight = FontWeight.Bold)
                                Text("هنگام قطع اینترنت، تغییر حالت خودکار است", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text("حالت استفاده", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeButton("خودکار / آنلاین", selected = !state.preferOffline, modifier = Modifier.weight(1f)) {
                            onModeChange(false)
                        }
                        ModeButton("همیشه آفلاین", selected = state.preferOffline, modifier = Modifier.weight(1f)) {
                            onModeChange(true)
                        }
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("حذف نقشه آفلاین")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp)) { Text(text) }
    else OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp)) { Text(text) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlacesSheet(
    destination: Place?,
    notices: List<RouteNotice>,
    initialKind: RouteNotice.Kind? = null,
    loading: Boolean,
    onlineAvailable: Boolean,
    offlineReady: Boolean,
    onDismiss: () -> Unit,
    onShowCode: () -> Unit,
    onShare: (Place) -> Unit,
    onOpenOfflineMaps: () -> Unit
) {
    val requestedFilter = when (initialKind) {
        RouteNotice.Kind.ATTRACTION -> RoutePlaceFilter.ATTRACTIONS
        RouteNotice.Kind.SERVICE -> RoutePlaceFilter.SERVICES
        RouteNotice.Kind.WEATHER -> RoutePlaceFilter.WEATHER
        else -> RoutePlaceFilter.ALL
    }
    var filter by remember(initialKind) { mutableStateOf(requestedFilter) }
    val visibleNotices = notices.filter { notice ->
        when (filter) {
            RoutePlaceFilter.ALL -> true
            RoutePlaceFilter.ATTRACTIONS -> notice.kind == RouteNotice.Kind.ATTRACTION
            RoutePlaceFilter.SERVICES -> notice.kind == RouteNotice.Kind.SERVICE
            RoutePlaceFilter.WEATHER -> notice.kind == RouteNotice.Kind.WEATHER
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NvNavy,
        contentColor = NvText,
        dragHandle = {
            Box(Modifier.padding(vertical = 10.dp).width(42.dp).height(4.dp).clip(CircleShape).background(NvMuted.copy(alpha = 0.6f)))
        }
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("جلوتر در مسیر", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("دیدنی‌ها، خدمات و آب‌وهوای ۱۰ کیلومتر جلوتر", color = NvMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                RoutePlaceFilterButton("همه", filter == RoutePlaceFilter.ALL, Modifier.weight(1f)) {
                    filter = RoutePlaceFilter.ALL
                }
                RoutePlaceFilterButton("دیدنی", filter == RoutePlaceFilter.ATTRACTIONS, Modifier.weight(1f)) {
                    filter = RoutePlaceFilter.ATTRACTIONS
                }
                RoutePlaceFilterButton("خدمات", filter == RoutePlaceFilter.SERVICES, Modifier.weight(1f)) {
                    filter = RoutePlaceFilter.SERVICES
                }
                RoutePlaceFilterButton("هوا", filter == RoutePlaceFilter.WEATHER, Modifier.weight(1f)) {
                    filter = RoutePlaceFilter.WEATHER
                }
            }
            destination?.let { place ->
                Surface(shape = RoundedCornerShape(18.dp), color = NvPanelHigh, contentColor = NvText, border = BorderStroke(1.dp, NvOutline)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Place, null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(place.name, fontWeight = FontWeight.Black)
                            Text(if (place.code > 0) "کد عمومی NV: ${place.code}" else "مکان آنلاین", color = NvMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = onShowCode) { Icon(Icons.Rounded.QrCode2, "نمایش کد", tint = NvCyan) }
                        IconButton(onClick = { onShare(place) }, enabled = place.code > 0) { Icon(Icons.Rounded.Share, "اشتراک", tint = NvText) }
                    }
                }
            }
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = NvCyan, trackColor = NvPanelHigh)
                Text("در حال دریافت اطلاعات واقعی مسیر…", color = NvMuted, style = MaterialTheme.typography.bodySmall)
            }
            visibleNotices.take(12).forEach { notice ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (notice.kind == RouteNotice.Kind.WEATHER) {
                        Color(0xFF153B4C)
                    } else NvPanelHigh,
                    contentColor = NvText,
                    border = BorderStroke(1.dp, NvOutline)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (notice.imageUrl != null) {
                            AsyncImage(
                                model = notice.imageUrl,
                                contentDescription = "تصویر ${notice.title}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(13.dp))
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = NvPanel
                            ) {
                                Icon(
                                    when (notice.kind) {
                                        RouteNotice.Kind.ATTRACTION -> Icons.Rounded.Explore
                                        RouteNotice.Kind.SERVICE -> Icons.Rounded.DirectionsCar
                                        RouteNotice.Kind.WEATHER -> Icons.Rounded.CloudDone
                                        RouteNotice.Kind.TRAFFIC -> Icons.Rounded.Info
                                    },
                                    null,
                                    modifier = Modifier.padding(9.dp),
                                    tint = when (notice.kind) {
                                        RouteNotice.Kind.ATTRACTION -> NvLime
                                        RouteNotice.Kind.SERVICE -> NvCyan
                                        RouteNotice.Kind.WEATHER -> Color(0xFFFFB52E)
                                        RouteNotice.Kind.TRAFFIC -> Color(0xFFFF7185)
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(notice.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                            Text("${formatDistance(notice.distanceAheadMeters)} جلوتر • ${notice.detail}", color = NvMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        notice.placeCode?.let { Text("NV:$it", style = MaterialTheme.typography.labelSmall, color = NvCyan) }
                    }
                }
            }
            if (!loading && visibleNotices.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            !onlineAvailable && !offlineReady -> "برای جاذبه‌ها و خدمات، اینترنت یا نقشه آفلاین ایران لازم است."
                            filter == RoutePlaceFilter.WEATHER && !onlineAvailable -> "هواشناسی زنده فقط هنگام اتصال اینترنت نمایش داده می‌شود."
                            else -> "موردی در این دسته در ۱۰ کیلومتر جلوتر پیدا نشد."
                        },
                        color = NvMuted
                    )
                    if (!offlineReady) {
                        OutlinedButton(
                            onClick = onOpenOfflineMaps,
                            border = BorderStroke(1.dp, NvOutline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NvCyan)
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(6.dp))
                            Text("دانلود نقشه آفلاین ایران")
                        }
                    }
                }
            }
            HorizontalDivider(color = NvOutline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoutePlaceDockAction("مسیر", Icons.Rounded.DirectionsCar, filter == RoutePlaceFilter.ALL) {
                    filter = RoutePlaceFilter.ALL
                }
                RoutePlaceDockAction("دیدنی‌ها", Icons.Rounded.Explore, filter == RoutePlaceFilter.ATTRACTIONS) {
                    filter = RoutePlaceFilter.ATTRACTIONS
                }
                RoutePlaceDockAction("هشدارها", Icons.Rounded.Info, filter == RoutePlaceFilter.WEATHER) {
                    filter = RoutePlaceFilter.WEATHER
                }
                RoutePlaceDockAction("کد مقصد", Icons.Rounded.Save, false, onShowCode)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RoutePlaceFilterButton(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NvCyan, contentColor = NvInk),
            contentPadding = ButtonDefaults.ContentPadding
        ) { Text(text, maxLines = 1) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, NvOutline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NvMuted),
            contentPadding = ButtonDefaults.ContentPadding
        ) { Text(text, maxLines = 1) }
    }
}

@Composable
private fun RoutePlaceDockAction(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) NvCyan else NvMuted, modifier = Modifier.size(22.dp))
        Text(label, color = if (selected) NvCyan else NvMuted, style = MaterialTheme.typography.labelSmall)
    }
}

private enum class RoutePlaceFilter { ALL, ATTRACTIONS, SERVICES, WEATHER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalCodesSheet(
    selectedPlace: Place?,
    savedPlaces: List<Place>,
    onDismiss: () -> Unit,
    onAddCode: () -> Unit,
    onDelete: (String) -> Unit,
    onShare: (Place) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NvNavy,
        contentColor = NvText,
        dragHandle = { Box(Modifier.padding(vertical = 10.dp).width(42.dp).height(4.dp).clip(CircleShape).background(NvMuted.copy(alpha = 0.6f))) }
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("کدهای عددی من", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                "برای هر مکان یک عدد دلخواه تعریف کنید و بعداً همان عدد را در جست‌وجو وارد کنید.",
                color = NvMuted
            )
            Surface(shape = RoundedCornerShape(18.dp), color = NvPanelHigh, contentColor = NvText, border = BorderStroke(1.dp, NvOutline)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selectedPlace?.name ?: "هنوز مکانی انتخاب نشده", fontWeight = FontWeight.Bold)
                        Text(
                            if (selectedPlace == null) "ابتدا یک مکان را از جست‌وجو انتخاب کنید" else "برای این مکان کد عددی بسازید",
                            style = MaterialTheme.typography.bodySmall,
                            color = NvMuted
                        )
                    }
                    Button(
                        onClick = onAddCode,
                        colors = ButtonDefaults.buttonColors(containerColor = NvCyan, contentColor = NvInk)
                    ) { Text(if (selectedPlace == null) "انتخاب" else "تعریف کد") }
                }
            }
            if (savedPlaces.isEmpty()) {
                Text("هنوز کد شخصی ذخیره نشده است.", color = NvMuted)
            } else {
                Text("کدهای ذخیره‌شده", fontWeight = FontWeight.Black)
                savedPlaces.take(10).forEach { place ->
                    Surface(shape = RoundedCornerShape(16.dp), color = NvPanelHigh, contentColor = NvText, border = BorderStroke(1.dp, NvOutline)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(10.dp), color = NvCyan) {
                                Text(
                                    place.personalCode.orEmpty(),
                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    color = NvInk,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(Modifier.width(9.dp))
                            Text(place.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { onShare(place) }) { Icon(Icons.Rounded.Share, "اشتراک", tint = NvCyan) }
                            IconButton(onClick = { place.personalCode?.let(onDelete) }) {
                                Icon(Icons.Rounded.DeleteOutline, "حذف", tint = Color(0xFFFF7185))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun PlaceCodeDialog(
    place: Place?,
    onDismiss: () -> Unit,
    onSave: (Place, String) -> Unit,
    onShare: (Place) -> Unit
) {
    var code by remember(place) { mutableStateOf("") }
    var showQr by remember(place) { mutableStateOf(false) }
    val shareCode = place?.let { PlaceCodes.shareCode(it.code) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Save, contentDescription = null) },
        title = { Text("کد مکان NV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(place?.name ?: "ابتدا یک مکان را انتخاب کنید", fontWeight = FontWeight.Black)
                if (shareCode != null) {
                    Text("کد عمومی ثابت: ${shareCode.substringAfter(':')}")
                    Text("اشتراک: $shareCode", color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { place?.let(onShare) }) {
                            Icon(Icons.Rounded.Share, null)
                            Spacer(Modifier.width(5.dp))
                            Text("اشتراک")
                        }
                        OutlinedButton(onClick = { showQr = !showQr }) {
                            Icon(Icons.Rounded.QrCode2, null)
                            Spacer(Modifier.width(5.dp))
                            Text("QR")
                        }
                    }
                    if (showQr) NvQrCode(shareCode, Modifier.size(190.dp).align(Alignment.CenterHorizontally))
                } else if (place != null) {
                    Text("این نتیجه آنلاین است؛ کد عمومی پس از نصب نقشه آفلاین ایران در دسترس است.", style = MaterialTheme.typography.bodySmall)
                }
                val normalizedCode = ir.nv.navigation.data.PersonalCodeRules.normalize(code)
                OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        code = PlaceCodes.normalizeDigits(input).filter(Char::isDigit).take(9)
                    },
                    label = { Text("کد شخصی فقط عددی؛ مثلاً ۱۱") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = place != null,
                    supportingText = {
                        Text(if (code.isBlank() || normalizedCode != null) "عدد ۱ تا ۹۹۹٬۹۹۹٬۹۹۹" else "عدد معتبر وارد کنید")
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { place?.let { onSave(it, code) } },
                enabled = place != null && ir.nv.navigation.data.PersonalCodeRules.normalize(code) != null
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun PurchaseDialog(
    trialState: TrialManager.State,
    billingState: BillingState,
    onPurchase: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Rounded.DirectionsCar, contentDescription = null) },
        title = { Text("فعال‌سازی NV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (trialState is TrialManager.State.Tampered) "اطلاعات دوره آزمایشی قابل تأیید نیست."
                    else "دوره رایگان ۳۰ روزه به پایان رسیده است."
                )
                Text("برای ادامه مسیریابی، نسخه کامل را از Google Play فعال کنید.")
                billingState.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onPurchase, enabled = !billingState.connecting) {
                Text(billingState.formattedPrice?.let { "خرید نسخه کامل — $it" } ?: "بررسی در Google Play")
            }
        }
    )
}

private fun categoryLabel(category: String): String = when {
    category == "place:city" || category == "place:town" -> "شهر"
    category == "place:village" -> "روستا"
    category == "place:suburb" || category == "place:neighbourhood" -> "محله"
    category.startsWith("personal:") -> "مکان شخصی"
    category.startsWith("online:") -> "نتیجه آنلاین"
    category == "builtin:city" -> "شهر ایران • آماده بدون اینترنت"
    category.startsWith("tourism:") -> "دیدنی"
    category.startsWith("amenity:") -> "خدمات"
    category.startsWith("shop:") -> "فروشگاه"
    else -> "مکان"
}

private fun formatBytes(value: Long): String {
    if (value < 1024) return "$value B"
    val mb = value / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}

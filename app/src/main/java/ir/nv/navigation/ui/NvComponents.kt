package ir.nv.navigation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Save
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val OnlineGreen = Color(0xFF00A884)
private val OfflineAmber = Color(0xFFFFA000)
private val OriginBlue = Color(0xFF2979FF)
private val DestinationRed = Color(0xFFFF4D67)

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
private fun AppIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 5.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
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
    onSaveCode: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 7.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceSearchField(
                    label = "مبدأ",
                    placeholder = "از کجا حرکت می‌کنید؟",
                    value = state.originQuery,
                    color = OriginBlue,
                    suggestions = state.originSuggestions,
                    onValueChange = onOriginChange,
                    onSelect = onOriginSelect
                )
                PlaceSearchField(
                    label = "مقصد",
                    placeholder = "کجا می‌روید؟ نام یا کد مکان",
                    value = state.destinationQuery,
                    color = DestinationRed,
                    suggestions = state.destinationSuggestions,
                    onValueChange = onDestinationChange,
                    onSelect = onDestinationSelect
                )
            }
            IconButton(onClick = onSwap) {
                Icon(Icons.Rounded.SwapVert, contentDescription = "جابه‌جایی مبدأ و مقصد")
            }
        }

        AnimatedVisibility(state.origin != null || state.destination != null) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onSaveCode,
                        enabled = state.origin != null || state.destination != null
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
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        if (state.routing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(19.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
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
    onValueChange: (String) -> Unit,
    onSelect: (Place) -> Unit
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(color)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        AnimatedVisibility(suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(max = 230.dp),
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
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
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(place.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${categoryLabel(place.category)}  •  کد ${place.personalCode ?: place.code}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        if (index < suggestions.take(6).lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
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
fun NavigationHud(route: Route, onStop: () -> Unit) {
    val maneuver = route.maneuvers.firstOrNull()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 9.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
            ) {
                Icon(
                    imageVector = maneuverIcon(maneuver?.direction),
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(43.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    formatDistance(maneuver?.distanceMeters ?: route.distanceMeters),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    maneuver?.instruction ?: "مستقیم به سمت مقصد ادامه دهید",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onStop) {
                Text("×", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineMedium)
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
            onToggleVoice
        )
        AppIconButton(Icons.Rounded.Map, "نقشه‌های آفلاین", onOpenOfflineMaps)
        AppIconButton(
            if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            if (darkMode) "حالت روز" else "حالت شب",
            onToggleTheme
        )
    }
}

@Composable
fun RouteSummaryCard(
    route: Route,
    source: RouteSource,
    traffic: TrafficSummary?,
    notices: List<RouteNotice>,
    navigationActive: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSeconds = route.travelSeconds + (traffic?.delaySeconds ?: 0.0)
    val eta = Instant.now().plusSeconds(totalSeconds.toLong())
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp)
            .shadow(10.dp, RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = if (navigationActive) onStop else onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("×", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    label = { Text(if (source == RouteSource.OFFLINE) "مسیر آفلاین" else "مسیر آنلاین") },
                    leadingIcon = {
                        Icon(
                            if (source == RouteSource.OFFLINE) Icons.Rounded.OfflinePin else Icons.Rounded.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RouteMetric("مسافت", "%.1f km".format(route.distanceMeters / 1000.0))
                RouteMetric("زمان", "${(totalSeconds / 60.0).roundToInt()} دقیقه")
                RouteMetric("رسیدن", eta)
            }
            if (traffic != null && traffic.delaySeconds > 0) {
                Text(
                    "ترافیک: %.1f کیلومتر • ${traffic.delaySeconds.div(60).roundToInt()} دقیقه تأخیر"
                        .format(traffic.lengthMeters / 1000.0),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            notices.firstOrNull()?.let { notice ->
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(notice.title, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(notice.detail, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }
            }
            if (navigationActive) {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("توقف راهنمای مسیر")
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("شروع حرکت", fontWeight = FontWeight.Black)
                }
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
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
fun PlaceCodeDialog(place: Place?, onDismiss: () -> Unit, onSave: (Place, String) -> Unit) {
    var code by remember(place) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Save, contentDescription = null) },
        title = { Text("ذخیره کد شخصی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(place?.name ?: "ابتدا یک مکان را انتخاب کنید")
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("کد کوتاه؛ مثلاً HOME1") },
                    singleLine = true,
                    enabled = place != null
                )
            }
        },
        confirmButton = {
            Button(onClick = { place?.let { onSave(it, code) } }, enabled = place != null && code.trim().length >= 2) {
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

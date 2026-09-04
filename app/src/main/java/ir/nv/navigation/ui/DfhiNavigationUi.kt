package ir.nv.navigation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.nv.navigation.core.Place

private val NvCyan = Color(0xFF03C8F3)
private val NvBlue = Color(0xFF087CF0)
private val OriginLime = Color(0xFFA7E22E)

enum class NvExperience { HOME, ROUTE, DRIVE, EXPLORE }

@Composable
fun DfhiHomeSearch(
    state: NvUiState,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSearch),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            shadowElevation = 14.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = NvCyan.copy(alpha = 0.16f)) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = NvBlue
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("به کجا می‌روید؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("شهر، خیابان، مکان یا کد NV", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        if (state.onlineAvailable) "آنلاین" else if (state.offlineReady) "آفلاین" else "بدون شبکه",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            DfhiQuickChip("موقعیت من", Icons.Rounded.MyLocation, onSearch, Modifier.weight(1f))
            DfhiQuickChip("انتخاب مقصد", Icons.Rounded.Place, onSearch, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DfhiQuickChip(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 7.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = NvBlue)
            Spacer(Modifier.width(6.dp))
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DfhiRouteSheet(
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.ArrowBack, contentDescription = "بازگشت") }
                Column(Modifier.weight(1f)) {
                    Text("انتخاب مسیر", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("جست‌وجوی سریع شهر، خیابان و مکان", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(start = 7.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DfhiSearchField(
                            label = "مبدأ",
                            hint = "موقعیت فعلی یا نام خیابان",
                            value = state.originQuery,
                            accent = OriginLime,
                            suggestions = state.originSuggestions,
                            onChange = onOriginChange,
                            onSelect = onOriginSelect,
                            trailing = {
                                IconButton(onClick = onUseCurrentLocation) {
                                    if (state.locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Rounded.MyLocation, contentDescription = "موقعیت من", tint = NvBlue)
                                }
                            }
                        )
                        DfhiSearchField(
                            label = "مقصد",
                            hint = "مثلاً تهران، ولیعصر یا برج آزادی",
                            value = state.destinationQuery,
                            accent = NvCyan,
                            suggestions = state.destinationSuggestions,
                            onChange = onDestinationChange,
                            onSelect = onDestinationSelect
                        )
                    }
                    IconButton(onClick = onSwap) { Icon(Icons.Rounded.SwapVert, contentDescription = "جابه‌جایی") }
                }
            }

            if (state.origin != null || state.destination != null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSaveCode) { Text("ذخیره کد NV") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onRoute,
                        enabled = state.origin != null && state.destination != null && !state.routing,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        if (state.routing) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (state.routing) "در حال بررسی مسیر" else "نمایش مسیرها", fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun DfhiSearchField(
    label: String,
    hint: String,
    value: String,
    accent: Color,
    suggestions: List<Place>,
    onChange: (String) -> Unit,
    onSelect: (Place) -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            label = { Text(label) },
            placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Box(Modifier.size(11.dp).background(accent, CircleShape)) },
            trailingIcon = trailing
        )
        AnimatedVisibility(visible = suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(max = 260.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column {
                    suggestions.take(7).forEachIndexed { index, place ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(place) }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Place, contentDescription = null, tint = NvBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(place.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${smartCategory(place.category)} • کد ${place.personalCode ?: place.code}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        if (index < suggestions.take(7).lastIndex) HorizontalDivider(Modifier.padding(horizontal = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DfhiModeDock(
    selected: NvExperience,
    onHome: () -> Unit,
    onRoute: () -> Unit,
    onDrive: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 7.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DfhiModeItem("خانه", Icons.Rounded.Map, selected == NvExperience.HOME, onHome)
            DfhiModeItem("مسیر", Icons.Rounded.Search, selected == NvExperience.ROUTE, onRoute)
            DfhiModeItem("رانندگی", Icons.Rounded.DirectionsCar, selected == NvExperience.DRIVE, onDrive)
            DfhiModeItem("مسیرگردی", Icons.Rounded.Explore, selected == NvExperience.EXPLORE, onExplore)
        }
    }
}

@Composable
private fun DfhiModeItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = if (selected) NvCyan.copy(alpha = 0.16f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = if (selected) NvBlue else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium, color = if (selected) NvBlue else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DfhiExploreSheet(state: NvUiState, onDismiss: () -> Unit, onChooseDestination: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("جاذبه‌ها و امکانات مسیر", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("مکان‌های مهم جلوتر از مسیر با اولویت کمترین انحراف", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.route == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onChooseDestination),
                    shape = RoundedCornerShape(20.dp),
                    color = NvCyan.copy(alpha = 0.16f)
                ) { Text("ابتدا مقصد و مسیر را انتخاب کنید", modifier = Modifier.padding(18.dp), fontWeight = FontWeight.Bold) }
            } else if (state.routeNotices.isEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text("در این بخش از مسیر جاذبه ثبت‌شده‌ای پیدا نشد.", modifier = Modifier.padding(18.dp))
                }
            } else {
                state.routeNotices.take(8).forEach { notice ->
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = NvCyan.copy(alpha = 0.15f)) {
                                Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.padding(9.dp), tint = NvBlue)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(notice.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${(notice.distanceAheadMeters / 1000.0).let { String.format("%.1f", it) }} کیلومتر جلوتر", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

private fun smartCategory(category: String): String = when {
    category.contains("city") || category.contains("town") -> "شهر"
    category.contains("suburb") || category.contains("neighbour") -> "محله"
    category.contains("street") || category.contains("highway") -> "خیابان"
    category.contains("tourism") || category.contains("historic") -> "جاذبه"
    else -> "مکان"
}

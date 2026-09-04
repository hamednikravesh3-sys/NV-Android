package ir.nv.navigation.rebuild

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val NvCyan = Color(0xFF00D9FF)
private val NvNavy = Color(0xFF071423)
private val NvPanel = Color(0xFF0D1D31)

@Composable
fun NvRebuildApp(viewModel: NvRebuildViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1422), Color(0xFF02060B))))
    ) {
        FakeMapCanvas()
        when (state.screen) {
            NvScreen.HOME -> HomeD(state, viewModel)
            NvScreen.ROUTE_SELECTION -> RouteF(state, viewModel)
            NvScreen.DRIVING -> DrivingH(state, viewModel)
            NvScreen.EXPLORE -> ExploreI(state, viewModel)
        }
    }
}

@Composable
private fun FakeMapCanvas() {
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF17314A), NvNavy)))) {
        Text("NV MAP", modifier = Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.08f), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun HomeD(state: NvRebuildState, vm: NvRebuildViewModel) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopStatus(state)
        Spacer(Modifier.weight(1f))
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = Color(0xFFF7FAFC)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("کجا می‌روید؟", color = Color(0xFF0C1826), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                SmartSearchField(state, vm)
                if (state.searchResults.isNotEmpty() || state.searching) SearchResults(state, vm)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniAction("موقعیت من", Icons.Rounded.Navigation, Modifier.weight(1f)) { vm.useCurrentLocationAsOrigin() }
                    MiniAction("نقشه آفلاین", Icons.Rounded.Map, Modifier.weight(1f)) { }
                }
            }
        }
    }
}

@Composable
private fun RouteF(state: NvRebuildState, vm: NvRebuildViewModel) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::openHome) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
            Text("انتخاب مسیر", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.weight(1f))
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = NvPanel
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EndpointCard("مبدأ", state.origin?.title ?: "موقعیت فعلی من", Color(0xFF2D7DFF))
                EndpointCard("مقصد", state.destination?.title ?: "مقصد را انتخاب کنید", Color(0xFFFF4164))
                if (state.destination != null && state.routeOptions.isEmpty()) {
                    Button(onClick = vm::calculateRoutes, modifier = Modifier.fillMaxWidth()) { Text("پیدا کردن مسیرها") }
                }
                state.routeOptions.forEach { option ->
                    val selected = option.id == state.selectedRouteId
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { vm.selectRoute(option.id) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) Color(0xFF123A55) else Color(0xFF0A1626),
                        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, NvCyan) else null
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(option.label, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${(option.distanceMeters / 1000).let { String.format("%.1f", it) }} km", color = Color.White.copy(alpha = .65f))
                            }
                            Text("${(option.durationSeconds / 60).toInt()} دقیقه", color = if (selected) NvCyan else Color.White, fontWeight = FontWeight.Black)
                        }
                    }
                }
                if (state.routeOptions.isNotEmpty()) Button(onClick = vm::startDriving, modifier = Modifier.fillMaxWidth()) { Text("شروع حرکت") }
            }
        }
    }
}

@Composable
private fun DrivingH(state: NvRebuildState, vm: NvRebuildViewModel) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(12.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xE80A1728)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Navigation, null, tint = NvCyan, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("۵۰۰ متر", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("خروجی بعدی را بگیرید", color = Color.White.copy(alpha = .7f))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HudChip(Icons.Rounded.Speed, "92", "km/h", Modifier.weight(1f))
            HudChip(Icons.Rounded.Place, "24", "دقیقه", Modifier.weight(1f))
            HudChip(Icons.Rounded.DirectionsCar, "18", "km", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = vm::stopDriving, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) { Text("پایان مسیریابی") }
    }
}

@Composable
private fun ExploreI(state: NvRebuildState, vm: NvRebuildViewModel) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::openHome) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
            Text("جاذبه‌ها و امکانات مسیر", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.weight(1f))
        Surface(Modifier.fillMaxWidth().navigationBarsPadding(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = NvPanel) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("پمپ بنزین", "رستوران", "پارکینگ", "جاذبه تاریخی").forEachIndexed { i, label ->
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color(0xFF0A1626)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Explore, null, tint = NvCyan)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${i + 2}.4 کیلومتر جلوتر", color = Color.White.copy(alpha = .6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopStatus(state: NvRebuildState) {
    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xDD071423)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(if (state.online) Color(0xFF00B894) else Color(0xFFFFB020), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(if (state.online) "آنلاین" else "آفلاین", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Surface(shape = CircleShape, color = Color(0xDD071423)) { Icon(Icons.Rounded.Map, null, tint = Color.White, modifier = Modifier.padding(12.dp)) }
    }
}

@Composable
private fun SmartSearchField(state: NvRebuildState, vm: NvRebuildViewModel) {
    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = vm::search,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (state.searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
        placeholder = { Text("شهر، محله، خیابان، کوچه یا مکان") }
    )
}

@Composable
private fun SearchResults(state: NvRebuildState, vm: NvRebuildViewModel) {
    LazyColumn(Modifier.fillMaxWidth().height(250.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(state.searchResults) { result ->
            Card(modifier = Modifier.fillMaxWidth().clickable { vm.chooseDestination(result) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null, tint = Color(0xFF118AB2))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(result.title, color = Color(0xFF0A1626), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(result.subtitle.ifBlank { result.category }, color = Color(0xFF566474), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (result.source == SearchSource.ONLINE) "آنلاین" else "آفلاین", color = Color(0xFF118AB2), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun EndpointCard(label: String, value: String, dot: Color) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color(0xFF0A1626)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(dot, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column { Text(label, color = Color.White.copy(alpha = .55f)); Text(value, color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun MiniAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = Color(0xFFE9F2F8)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = Color(0xFF0A5E7A)); Spacer(Modifier.width(8.dp)); Text(label, color = Color(0xFF0A1626), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HudChip(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, unit: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Color(0xE80A1728)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = NvCyan); Text(value, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge); Text(unit, color = Color.White.copy(alpha = .6f))
        }
    }
}

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.map.OfflineIranMap
import ir.nv.navigation.map.OnlineIranMap
import ir.nv.navigation.routing.NavigationModeResolver
import ir.nv.navigation.ui.theme.AppThemeMode

private val V4Navy = Color(0xF2071829)
private val V4Panel = Color(0xF20A2841)
private val V4Cyan = Color(0xFF14D8FF)
private val V4Text = Color(0xFFF6FBFF)
private val V4Muted = Color(0xFFADC0CE)
private val V4Green = Color(0xFF50E86A)
private enum class V4Sheet { SEARCH, MODULES, ROUTES, FAVORITES, WEATHER, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NvReferenceV4(darkMode: Boolean, themeMode: AppThemeMode, onThemeModeChange: (AppThemeMode) -> Unit, viewModel: NvViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<V4Sheet?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p -> if (p.values.any { it }) viewModel.startNavigation() }
    fun startNavigation() {
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (ok) viewModel.startNavigation() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    fun search(q: String = "") { if (q.isNotBlank()) viewModel.updateDestinationQuery(q); sheet = V4Sheet.SEARCH }

    Box(Modifier.fillMaxSize().background(Color(0xFF06111C))) {
        V4Map(state, viewModel, darkMode)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xA9071829), Color.Transparent, Color.Transparent, Color(0xB9071829)))))
        V4Header(state.destination?.name, { search() }, { sheet = V4Sheet.WEATHER }, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(10.dp))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            V4RoundButton(Icons.Rounded.Apps, "ماژول‌ها") { sheet = V4Sheet.MODULES }
            V4RoundButton(Icons.Rounded.Layers, "لایه‌ها") { viewModel.toggleSatelliteMode() }
        }
        if (state.navigationActive && state.route != null) {
            val maneuver = state.route?.maneuvers?.getOrNull(state.maneuverIndex)
            Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 86.dp).widthIn(max = 310.dp), RoundedCornerShape(22.dp), Color(0xE508665D), border = BorderStroke(2.dp, Color(0xFF68FFE2))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TurnRight, null, tint = Color.White, modifier = Modifier.size(46.dp)); Spacer(Modifier.width(12.dp))
                    Column { Text(if (state.distanceToNextManeuverMeters > 0) "${state.distanceToNextManeuverMeters.toInt()} متر" else "راهنمای مسیر", color = Color.White, fontWeight = FontWeight.Bold); Text(maneuver?.instruction ?: "ادامه مسیر", color = Color.White) }
                }
            }
            Surface(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, bottom = 116.dp), CircleShape, V4Navy, border = BorderStroke(3.dp, V4Green)) {
                Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(state.speedKmh.toString(), color = V4Green, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black); Text("km/h", color = V4Text) } }
            }
        }
        if (state.route != null) {
            val seconds = if (state.navigationActive) state.remainingSeconds else state.route?.travelSeconds ?: 0.0
            val meters = if (state.navigationActive) state.remainingDistanceMeters else state.route?.distanceMeters ?: 0.0
            Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 76.dp), RoundedCornerShape(22.dp), V4Navy, border = BorderStroke(1.dp, V4Cyan.copy(.6f))) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    V4Stat("زمان", "${(seconds / 60).toInt()} دقیقه"); V4Stat("مسافت", String.format("%.1f km", meters / 1000.0))
                    TextButton(onClick = { sheet = V4Sheet.ROUTES }) { Text("مسیرها", color = V4Cyan) }
                    Button(onClick = { if (state.navigationActive) viewModel.stopNavigation() else startNavigation() }) { Text(if (state.navigationActive) "توقف" else "شروع حرکت") }
                }
            }
        }
        V4BottomBar({ if (state.route == null) search() else if (!state.navigationActive) startNavigation() }, { search() }, { sheet = V4Sheet.FAVORITES }, { sheet = if (state.route == null) V4Sheet.SEARCH else V4Sheet.ROUTES }, { sheet = V4Sheet.WEATHER }, { sheet = V4Sheet.SETTINGS }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        if (sheet != null) ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = V4Navy, contentColor = V4Text, dragHandle = { BottomSheetDefaults.DragHandle(color = V4Cyan) }) {
            when (sheet) {
                V4Sheet.SEARCH -> V4SearchSheet(state, viewModel) { sheet = null }
                V4Sheet.MODULES -> V4ModulesSheet({ search("پمپ بنزین") }, { search("رستوران") }, { search("اقامتگاه") }, { search("جاذبه دیدنی") }, { sheet = if (state.route == null) V4Sheet.SEARCH else V4Sheet.ROUTES }, { sheet = V4Sheet.WEATHER }, { sheet = V4Sheet.SETTINGS })
                V4Sheet.ROUTES -> V4RoutesSheet(state, viewModel) { sheet = null }
                V4Sheet.FAVORITES -> V4PlacesSheet("مکان‌های ذخیره‌شده", state.personalPlaces) { viewModel.selectDestination(it); sheet = V4Sheet.SEARCH }
                V4Sheet.WEATHER -> V4NoticeSheet("آب‌وهوا و وضعیت مسیر", state.routeNotices.map { it.title + " — " + it.detail })
                V4Sheet.SETTINGS -> V4SettingsSheet(state, viewModel, themeMode, onThemeModeChange)
                null -> Unit
            }
        }
    }
}

@Composable private fun V4Map(state: NvUiState, vm: NvViewModel, dark: Boolean) {
    val context = LocalContext.current
    val source = NavigationModeResolver.preferredSource(state.onlineAvailable, state.offlineReady, state.preferOffline)
    when {
        source == RouteSource.OFFLINE -> OfflineIranMap(context, vm.mapFile(), state.routeAlternatives.ifEmpty { listOfNotNull(state.route) }, state.selectedRouteIndex, state.traffic, state.trafficSegments, state.currentLocation, state.navigationActive && state.followNavigation, state.navigationActive, state.navigationZoomLevel, state.navigationRecenterToken, state.bearingDegrees, vm::pauseNavigationFollow, dark, Modifier.fillMaxSize())
        state.onlineAvailable -> OnlineIranMap(context, state.routeAlternatives.ifEmpty { listOfNotNull(state.route) }, state.selectedRouteIndex, state.traffic, state.trafficSegments, (state.personalPlaces + state.recentPlaces + listOfNotNull(state.origin, state.destination)).distinctBy { it.personalCode ?: it.code.toString() }, state.currentLocation, state.navigationActive && state.followNavigation, state.navigationActive, state.navigationZoomLevel, state.navigationRecenterToken, state.bearingDegrees, vm::pauseNavigationFollow, dark, state.satelliteMode, Modifier.fillMaxSize())
        else -> Box(Modifier.fillMaxSize().background(Color(0xFF102333)), contentAlignment = Alignment.Center) { Text("نقشه آنلاین در دسترس نیست؛ نقشه آفلاین ایران را از تنظیمات دانلود کنید", color = V4Muted, modifier = Modifier.padding(32.dp)) }
    }
}
@Composable private fun V4Header(destination:String?,onSearch:()->Unit,onWeather:()->Unit,modifier:Modifier=Modifier){Row(modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Surface(shape=RoundedCornerShape(18.dp),color=Color.White){Row(Modifier.height(56.dp).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically){Text("N",color=Color.Black,fontWeight=FontWeight.Black,style=MaterialTheme.typography.headlineMedium);Text("V",color=Color(0xFF00C83B),fontWeight=FontWeight.Black,style=MaterialTheme.typography.headlineMedium)}};Surface(Modifier.weight(1f).height(56.dp).clickable(onClick=onSearch),RoundedCornerShape(18.dp),V4Panel,border=BorderStroke(1.dp,V4Cyan)){Row(Modifier.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Mic,null);Spacer(Modifier.width(8.dp));Text(destination?:"نام یا کد مکان را جستجو کنید…",Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis);Icon(Icons.Rounded.Search,null,tint=V4Cyan)}};V4RoundButton(Icons.Rounded.WbSunny,"آب‌وهوا",onWeather)}}
@Composable private fun V4RoundButton(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,onClick:()->Unit){Surface(Modifier.size(56.dp).clickable(onClick=onClick),RoundedCornerShape(18.dp),V4Panel,border=BorderStroke(1.dp,V4Cyan.copy(.45f))){Box(contentAlignment=Alignment.Center){Icon(icon,label,tint=V4Text)}}}
@Composable private fun V4Stat(a:String,b:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(b,color=V4Text,fontWeight=FontWeight.Bold);Text(a,color=V4Muted,style=MaterialTheme.typography.labelSmall)}}
@Composable private fun V4BottomBar(onNav:()->Unit,onSearch:()->Unit,onFav:()->Unit,onRoutes:()->Unit,onWeather:()->Unit,onSettings:()->Unit,modifier:Modifier=Modifier){Surface(modifier.fillMaxWidth().height(68.dp),color=V4Navy,border=BorderStroke(1.dp,V4Cyan.copy(.5f))){Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){listOf(Icons.Rounded.Navigation to onNav,Icons.Rounded.Search to onSearch,Icons.Rounded.Bookmark to onFav,Icons.Rounded.Route to onRoutes,Icons.Rounded.Cloud to onWeather,Icons.Rounded.Settings to onSettings).forEach{(i,a)->IconButton(onClick=a){Icon(i,null,tint=V4Text)}}}}}
@Composable private fun V4ModulesSheet(onGas:()->Unit,onFood:()->Unit,onHotel:()->Unit,onSight:()->Unit,onRoutes:()->Unit,onWeather:()->Unit,onSettings:()->Unit){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("ماژول‌ها",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);listOf(Triple("پمپ بنزین",Icons.Rounded.LocalGasStation,onGas),Triple("رستوران",Icons.Rounded.Restaurant,onFood),Triple("اقامتگاه",Icons.Rounded.Hotel,onHotel),Triple("جاهای دیدنی",Icons.Rounded.PhotoCamera,onSight),Triple("مسیرها",Icons.Rounded.Route,onRoutes),Triple("آب‌وهوا",Icons.Rounded.Cloud,onWeather),Triple("تنظیمات",Icons.Rounded.Settings,onSettings)).forEach{(t,i,a)->Surface(Modifier.fillMaxWidth().clickable(onClick=a),RoundedCornerShape(16.dp),V4Panel){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(i,null,tint=V4Cyan);Spacer(Modifier.width(14.dp));Text(t,fontWeight=FontWeight.Bold)}}};Spacer(Modifier.height(18.dp))}}
@Composable private fun V4SearchSheet(state:NvUiState,vm:NvViewModel,onDone:()->Unit){val suggestions=if(state.destinationQuery.isNotBlank())state.destinationSuggestions else state.originSuggestions;Column(Modifier.fillMaxWidth().padding(20.dp)){Text("انتخاب مبدأ و مقصد",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));OutlinedTextField(state.originQuery,vm::updateOriginQuery,label={Text("مبدأ")},modifier=Modifier.fillMaxWidth(),trailingIcon={IconButton(onClick={vm.updateOriginQuery("")}){Icon(Icons.Rounded.Close,null)}});OutlinedTextField(state.destinationQuery,vm::updateDestinationQuery,label={Text("مقصد / کد NV")},modifier=Modifier.fillMaxWidth(),trailingIcon={IconButton(onClick={vm.updateDestinationQuery("")}){Icon(Icons.Rounded.Close,null)}});LazyColumn(Modifier.heightIn(max=220.dp)){items(suggestions){p->Row(Modifier.fillMaxWidth().clickable{if(state.destinationQuery.isNotBlank())vm.selectDestination(p)else vm.selectOrigin(p)}.padding(12.dp)){Icon(Icons.Rounded.Place,null,tint=V4Cyan);Spacer(Modifier.width(8.dp));Text(p.name,Modifier.weight(1f));Text(p.personalCode?:p.code.toString(),color=V4Muted)}}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=vm::useCurrentLocationAsOrigin,modifier=Modifier.weight(1f)){Text("موقعیت فعلی")};Button(onClick={vm.calculateRoute();onDone()},enabled=state.origin!=null&&state.destination!=null,modifier=Modifier.weight(1f)){Text("محاسبه مسیر")}};Spacer(Modifier.height(24.dp))}}
@Composable private fun V4RoutesSheet(state:NvUiState,vm:NvViewModel,onDone:()->Unit){Column(Modifier.fillMaxWidth().padding(20.dp)){Text("مسیرهای پیشنهادی",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);state.routeAlternatives.forEachIndexed{i,r->Surface(Modifier.fillMaxWidth().padding(vertical=5.dp).clickable{vm.selectRoute(i);onDone()},RoundedCornerShape(16.dp),if(i==state.selectedRouteIndex)V4Panel else V4Navy,border=BorderStroke(1.dp,if(i==state.selectedRouteIndex)V4Cyan else V4Muted.copy(.3f))){Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("مسیر ${i+1}");Text("${(r.travelSeconds/60).toInt()} دقیقه • ${String.format("%.1f",r.distanceMeters/1000)} km")}}};Spacer(Modifier.height(24.dp))}}
@Composable private fun V4PlacesSheet(title:String,places:List<Place>,onPlace:(Place)->Unit){Column(Modifier.fillMaxWidth().padding(20.dp)){Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);if(places.isEmpty())Text("موردی ذخیره نشده است",color=V4Muted,modifier=Modifier.padding(vertical=24.dp));places.forEach{p->Text(p.name,Modifier.fillMaxWidth().clickable{onPlace(p)}.padding(16.dp))};Spacer(Modifier.height(24.dp))}}
@Composable private fun V4NoticeSheet(title:String,lines:List<String>){Column(Modifier.fillMaxWidth().padding(20.dp)){Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);if(lines.isEmpty())Text("اطلاعات مسیر پس از انتخاب مسیر نمایش داده می‌شود",color=V4Muted,modifier=Modifier.padding(vertical=24.dp));lines.forEach{Text(it,Modifier.padding(vertical=8.dp))};Spacer(Modifier.height(24.dp))}}
@Composable private fun V4SettingsSheet(state:NvUiState,vm:NvViewModel,theme:AppThemeMode,onTheme:(AppThemeMode)->Unit){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("تنظیمات و نقشه",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("نمای ماهواره‌ای");Switch(state.satelliteMode,{vm.toggleSatelliteMode()})};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("اولویت نقشه آفلاین");Switch(state.preferOffline,{vm.setPreferOffline(it)})};Button(onClick=vm::startMapDownload,modifier=Modifier.fillMaxWidth()){Text("دانلود نقشه آفلاین ایران")};Text("پوسته: ${theme.name}",color=V4Muted);Spacer(Modifier.height(24.dp))}}

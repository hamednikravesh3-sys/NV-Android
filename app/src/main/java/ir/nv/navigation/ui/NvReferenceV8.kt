package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.nv.navigation.R
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.map.OfflineIranMap
import ir.nv.navigation.map.OnlineIranMap
import ir.nv.navigation.map.SatelliteIranMap
import ir.nv.navigation.routing.NavigationModeResolver
import ir.nv.navigation.ui.theme.AppThemeMode

private val V8Cyan = Color(0xFF14D8FF)
private val V8Green = Color(0xFF43E66B)
private val V8Gold = Color(0xFFFFB52E)
private val V8Purple = Color(0xFFBB75FF)
private val V8NightPanel = Color(0xEE071B2B)
private val V8DayPanel = Color(0xEDF5FAFD)
private val V8NightText = Color(0xFFF6FBFF)
private val V8DayText = Color(0xFF102536)
private val V8Muted = Color(0xFFA9BDC9)

private enum class V8SearchTarget { ORIGIN, DESTINATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NvReferenceV8(darkMode: Boolean, themeMode: AppThemeMode, onThemeModeChange: (AppThemeMode) -> Unit, viewModel: NvViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val panel = if (darkMode) V8NightPanel else V8DayPanel
    val text = if (darkMode) V8NightText else V8DayText
    var searchVisible by remember { mutableStateOf(state.origin == null || state.destination == null) }
    var searchTarget by remember { mutableStateOf(V8SearchTarget.ORIGIN) }
    var settingsVisible by remember { mutableStateOf(false) }
    var lastPair by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions -> if (permissions.values.any { it }) viewModel.startNavigation() }
    fun startDriving() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startNavigation() else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val pairKey = if (state.origin != null && state.destination != null) "${state.origin!!.coordinate.latitude},${state.origin!!.coordinate.longitude}->${state.destination!!.coordinate.latitude},${state.destination!!.coordinate.longitude}" else null
    LaunchedEffect(pairKey) { if (pairKey != null && pairKey != lastPair) { lastPair = pairKey; searchVisible = false; viewModel.clearRoute(); viewModel.calculateRoute() } }
    Box(Modifier.fillMaxSize().background(if (darkMode) Color.Black else Color(0xFFEAF2F6))) {
        V8Map(state, viewModel, darkMode)
        if (!state.navigationActive && (searchVisible || state.routeAlternatives.isEmpty())) V8SearchPanel(state, viewModel, searchTarget, { searchTarget = it }, { if (state.origin != null && state.destination != null) searchVisible = false }, panel, text, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(10.dp))
        if (!state.navigationActive && !searchVisible && state.routeAlternatives.isNotEmpty()) V8RouteStrip(state, viewModel, panel, text, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp))
        if (state.navigationActive) V8ManeuverHud(state, panel, text, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp))
        if (state.navigationActive) Surface(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 12.dp, bottom = 100.dp), CircleShape, panel, border = BorderStroke(4.dp, V8Green)) { Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(state.speedKmh.toString(), color = text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("km/h", color = V8Green, style = MaterialTheme.typography.labelSmall) } } }
        V8BottomBar(state.navigationActive, state.route != null, panel, text, state.satelliteMode, { if (state.navigationActive) viewModel.stopNavigation(); searchTarget = if (state.origin == null) V8SearchTarget.ORIGIN else V8SearchTarget.DESTINATION; searchVisible = true }, { if (state.navigationActive) viewModel.stopNavigation() else startDriving() }, viewModel::toggleSatelliteMode, { settingsVisible = true }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp))
        if (settingsVisible) ModalBottomSheet(onDismissRequest = { settingsVisible = false }, containerColor = panel, contentColor = text) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("تنظیمات NV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text("روز / شب", color = V8Muted); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AppThemeMode.entries.forEach { mode -> FilterChip(selected = themeMode == mode, onClick = { onThemeModeChange(mode) }, label = { Text(mode.title) }, modifier = Modifier.weight(1f)) } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("نقشه ماهواره‌ای واقعی"); Switch(checked = state.satelliteMode, onCheckedChange = { viewModel.toggleSatelliteMode() }) }; Text("Satellite از Esri World Imagery بارگذاری می‌شود.", color = V8Muted, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.height(12.dp)) } }
    }
}

@Composable private fun V8Map(state: NvUiState, vm: NvViewModel, darkMode: Boolean) {
    val context = LocalContext.current; val routes = state.routeAlternatives.ifEmpty { listOfNotNull(state.route) }; val coded = (state.personalPlaces + state.recentPlaces + listOfNotNull(state.origin, state.destination)).distinctBy { it.personalCode ?: it.code.toString() }
    if (state.satelliteMode && state.onlineAvailable) { SatelliteIranMap(context, routes, state.selectedRouteIndex, coded, state.currentLocation, state.navigationActive && state.followNavigation, state.navigationActive, state.navigationZoomLevel, state.navigationRecenterToken, state.bearingDegrees, vm::pauseNavigationFollow, Modifier.fillMaxSize()); return }
    when (NavigationModeResolver.preferredSource(state.onlineAvailable, state.offlineReady, state.preferOffline)) {
        RouteSource.OFFLINE -> OfflineIranMap(context, vm.mapFile(), routes, state.selectedRouteIndex, state.traffic, state.trafficSegments, state.currentLocation, state.navigationActive && state.followNavigation, state.navigationActive, state.navigationZoomLevel, state.navigationRecenterToken, state.bearingDegrees, vm::pauseNavigationFollow, darkMode, Modifier.fillMaxSize())
        RouteSource.ONLINE -> OnlineIranMap(context, routes, state.selectedRouteIndex, state.traffic, state.trafficSegments, coded, state.currentLocation, state.navigationActive && state.followNavigation, state.navigationActive, state.navigationZoomLevel, state.navigationRecenterToken, state.bearingDegrees, vm::pauseNavigationFollow, darkMode, false, Modifier.fillMaxSize())
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("نقشه در دسترس نیست", color = V8Muted) }
    }
}

@Composable private fun V8SearchPanel(state: NvUiState, vm: NvViewModel, target: V8SearchTarget, onTargetChange: (V8SearchTarget) -> Unit, onClose: () -> Unit, panel: Color, text: Color, modifier: Modifier = Modifier) {
    val isOrigin = target == V8SearchTarget.ORIGIN; val query = if (isOrigin) state.originQuery else state.destinationQuery; val suggestions = if (isOrigin) state.originSuggestions else state.destinationSuggestions
    Surface(modifier.fillMaxWidth(), RoundedCornerShape(20.dp), panel, border = BorderStroke(1.dp, V8Cyan.copy(alpha=.6f))) { Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { FilterChip(isOrigin,{onTargetChange(V8SearchTarget.ORIGIN)},{Text(state.origin?.name?.take(15)?:"مبدأ")},modifier=Modifier.weight(1f)); IconButton(vm::swapEndpoints){Icon(Icons.Rounded.SwapHoriz,null,tint=V8Cyan)}; FilterChip(!isOrigin,{onTargetChange(V8SearchTarget.DESTINATION)},{Text(state.destination?.name?.take(15)?:"مقصد")},modifier=Modifier.weight(1f)) }; OutlinedTextField(query,{if(isOrigin)vm.updateOriginQuery(it) else vm.updateDestinationQuery(it)},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text(if(isOrigin)"جستجوی مبدأ یا کد NV" else "جستجوی مقصد یا کد NV")},leadingIcon={Icon(Icons.Rounded.Search,null)},trailingIcon={IconButton({if(query.isNotBlank()){if(isOrigin)vm.updateOriginQuery("") else vm.updateDestinationQuery("")}else onClose()}){Icon(Icons.Rounded.Close,null)}}); if(isOrigin) TextButton({vm.useCurrentLocationAsOrigin();onTargetChange(V8SearchTarget.DESTINATION)}){Icon(Icons.Rounded.GpsFixed,null,tint=V8Green);Spacer(Modifier.width(5.dp));Text("موقعیت فعلی به عنوان مبدأ",color=text)}; if(query.isNotBlank()&&suggestions.isNotEmpty()) Column { suggestions.take(6).forEach { place -> Row(Modifier.fillMaxWidth().clickable{if(isOrigin){vm.selectOrigin(place);onTargetChange(V8SearchTarget.DESTINATION)}else{vm.selectDestination(place);onClose()}}.padding(9.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Place,null,tint=V8Cyan);Spacer(Modifier.width(7.dp));Text(place.name,color=text,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f));Text(place.personalCode?:place.code.toString(),color=V8Muted,style=MaterialTheme.typography.labelSmall)}} } } }
}

@Composable private fun V8RouteStrip(state:NvUiState,vm:NvViewModel,panel:Color,text:Color,modifier:Modifier=Modifier){Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){state.routeAlternatives.take(4).forEachIndexed{index,route->val color=v8RouteColor(index);Surface(Modifier.width(116.dp).clickable{vm.selectRoute(index)},RoundedCornerShape(16.dp),panel,border=BorderStroke(if(state.selectedRouteIndex==index)3.dp else 1.dp,color)){Column(Modifier.padding(9.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("مسیر ${index+1}",color=color,fontWeight=FontWeight.Black);Text(String.format("%.1f km",route.distanceMeters/1000.0),color=text,fontWeight=FontWeight.Bold);Text("${(route.travelSeconds/60.0).toInt()} دقیقه",color=text)}}}}}
@Composable private fun V8ManeuverHud(state:NvUiState,panel:Color,text:Color,modifier:Modifier=Modifier){val maneuver=state.route?.maneuvers?.getOrNull(state.maneuverIndex);Surface(modifier.fillMaxWidth(),RoundedCornerShape(20.dp),panel,border=BorderStroke(2.dp,V8Cyan.copy(alpha=.7f))){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(v8ManeuverIcon(maneuver?.direction),null,tint=V8Cyan,modifier=Modifier.size(48.dp));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("${state.distanceToNextManeuverMeters.toInt()} متر",color=text,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Black);Text(maneuver?.instruction?:"ادامه مسیر",color=V8Muted,maxLines=1,overflow=TextOverflow.Ellipsis)};Column(horizontalAlignment=Alignment.End){Text("${(state.remainingSeconds/60.0).toInt()} دقیقه",color=text,fontWeight=FontWeight.Bold);Text(String.format("%.1f km",state.remainingDistanceMeters/1000.0),color=V8Muted)}}}}
@Composable private fun V8CarMarker(direction:RouteManeuver.Direction?,distanceToManeuverMeters:Double,modifier:Modifier=Modifier){val signal=when(direction){RouteManeuver.Direction.LEFT,RouteManeuver.Direction.SLIGHT_LEFT,RouteManeuver.Direction.SHARP_LEFT->-1;RouteManeuver.Direction.RIGHT,RouteManeuver.Direction.SLIGHT_RIGHT,RouteManeuver.Direction.SHARP_RIGHT->1;RouteManeuver.Direction.UTURN->2;else->0};val active=signal!=0&&distanceToManeuverMeters in 0.0..1200.0;val transition=rememberInfiniteTransition(label="v8-signal");val blink by transition.animateFloat(.15f,1f,infiniteRepeatable(tween(280),repeatMode=RepeatMode.Reverse),label="v8-signal-blink");Box(modifier.width(180.dp).height(130.dp),contentAlignment=Alignment.Center){if(active&&(signal==-1||signal==2))Surface(Modifier.align(Alignment.CenterStart).size(58.dp).alpha(blink),CircleShape,V8Gold.copy(alpha=.95f),border=BorderStroke(3.dp,Color.White)){Box(contentAlignment=Alignment.Center){Icon(Icons.Rounded.ArrowBack,"راهنمای چپ",tint=Color.Black,modifier=Modifier.size(40.dp))}};if(active&&(signal==1||signal==2))Surface(Modifier.align(Alignment.CenterEnd).size(58.dp).alpha(blink),CircleShape,V8Gold.copy(alpha=.95f),border=BorderStroke(3.dp,Color.White)){Box(contentAlignment=Alignment.Center){Icon(Icons.Rounded.ArrowForward,"راهنمای راست",tint=Color.Black,modifier=Modifier.size(40.dp))}};Image(painterResource(R.drawable.nv_car_top),"خودروی NV",Modifier.width(88.dp).height(118.dp),contentScale=ContentScale.Fit);if(active)Row(Modifier.align(Alignment.BottomCenter).padding(bottom=7.dp),horizontalArrangement=Arrangement.spacedBy(25.dp)){Box(Modifier.size(12.dp).clip(CircleShape).background(if(signal==-1||signal==2)V8Gold.copy(alpha=blink)else Color.Transparent));Box(Modifier.size(12.dp).clip(CircleShape).background(if(signal==1||signal==2)V8Gold.copy(alpha=blink)else Color.Transparent))}}}

@Composable private fun V8BottomBar(navigationActive:Boolean,hasRoute:Boolean,panel:Color,text:Color,satellite:Boolean,onSearch:()->Unit,onStartStop:()->Unit,onSatellite:()->Unit,onSettings:()->Unit,modifier:Modifier=Modifier){Surface(modifier.fillMaxWidth(),RoundedCornerShape(24.dp),panel,border=BorderStroke(1.dp,V8Cyan.copy(alpha=.35f))){Row(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly){IconButton(onSearch){Icon(Icons.Rounded.Search,"جستجو",tint=text)};Button(onStartStop,enabled=hasRoute||navigationActive,shape=RoundedCornerShape(22.dp)){Icon(if(navigationActive)Icons.Rounded.Stop else Icons.Rounded.Navigation,null);Spacer(Modifier.width(5.dp));Text(if(navigationActive)"پایان" else "شروع",fontWeight=FontWeight.Bold)};IconButton(onSatellite){Icon(Icons.Rounded.SatelliteAlt,"ماهواره",tint=if(satellite)V8Green else text)};IconButton(onSettings){Icon(Icons.Rounded.Settings,"تنظیمات",tint=text)}}}}
private fun v8RouteColor(index:Int):Color=when(index%4){0->V8Cyan;1->V8Green;2->V8Gold;else->V8Purple}
private fun v8ManeuverIcon(direction:RouteManeuver.Direction?):ImageVector=when(direction){RouteManeuver.Direction.LEFT,RouteManeuver.Direction.SLIGHT_LEFT,RouteManeuver.Direction.SHARP_LEFT->Icons.Rounded.TurnLeft;RouteManeuver.Direction.RIGHT,RouteManeuver.Direction.SLIGHT_RIGHT,RouteManeuver.Direction.SHARP_RIGHT->Icons.Rounded.TurnRight;RouteManeuver.Direction.UTURN->Icons.Rounded.UturnLeft;RouteManeuver.Direction.ARRIVE->Icons.Rounded.Place;else->Icons.Rounded.Straight}

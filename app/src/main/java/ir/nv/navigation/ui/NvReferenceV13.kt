package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.data.*
import ir.nv.navigation.map.NvCodePickerMap
import ir.nv.navigation.map.NvMapInteractionBus
import ir.nv.navigation.online.OnlinePlacesService
import ir.nv.navigation.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

private val V13Panel=Color(0xF2071B2B); private val V13Cyan=Color(0xFF14D8FF); private val V13Gold=Color(0xFFFFB52E); private val V13Green=Color(0xFF43E66B); private val V13Emergency=Color(0xFFE53935)

@Composable
fun NvReferenceV13(darkMode:Boolean,themeMode:AppThemeMode,onThemeModeChange:(AppThemeMode)->Unit,viewModel:NvViewModel){
 val state by viewModel.state.collectAsState(); val context=LocalContext.current; val clipboard=LocalClipboardManager.current; val scope=rememberCoroutineScope()
 val store=remember{NvBookmarkStore(context.applicationContext)}; val allocator=remember{NvCodeAllocationService()}; val local=remember{NvLocalSequentialCodeAllocator(context.applicationContext)}; val qrManager=remember{NvQrShareManager(context)}; val nearbyService=remember{OnlinePlacesService()}
 var bookmark by remember{mutableStateOf(store.load())}; var picker by remember{mutableStateOf(false)}; var pin by remember{mutableStateOf<Coordinate?>(bookmark?.coordinate?:state.currentLocation?:state.destination?.coordinate?:state.origin?.coordinate)}
 var qrOpen by remember{mutableStateOf(false)}; var qr by remember{mutableStateOf<NvQrShareManager.SavedQr?>(null)}; var working by remember{mutableStateOf(false)}; var error by remember{mutableStateOf<String?>(null)}
 var nearbyOpen by remember{mutableStateOf(false)}; var nearbyQuery by remember{mutableStateOf("")}; var nearbyResults by remember{mutableStateOf<List<Place>>(emptyList())}; var nearbyLoading by remember{mutableStateOf(false)}; var nearbyError by remember{mutableStateOf<String?>(null)}
 var smartResults by remember{mutableStateOf<List<Place>>(emptyList())}; var smartLoading by remember{mutableStateOf(false)}
 fun savePin(){val c=pin?:return;val p=Place(-8300000000L,"سنجاق NV",c,"bookmark:pin");store.save(p);bookmark=p;qr=null;error=null;picker=false}
 val doubleTapListener=remember{ { c:Coordinate -> pin=c; val p=Place(-8300000000L,"پرچم NV",c,"bookmark:flag"); store.save(p); bookmark=p; qr=null; error=null; NvMapInteractionBus.recenterOn(c) } }
 DisposableEffect(Unit){ NvMapInteractionBus.onDoubleTap=doubleTapListener; onDispose{NvMapInteractionBus.clearListener(doubleTapListener)} }
 fun useCurrent(){state.currentLocation?.let{c->pin=c;val p=Place(-8300000000L,"موقعیت فعلی من",c,"bookmark:current");store.save(p);bookmark=p;qr=null;error=null;picker=false}}
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){p->if(p.values.any{it})viewModel.useCurrentLocationAsOrigin()}
 fun locate(){if(state.currentLocation!=null){useCurrent();viewModel.recenterNavigation();return};val ok=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;if(ok)viewModel.useCurrentLocationAsOrigin()else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
 fun nearbySearch(query:String){
  nearbyQuery=query; nearbyResults=emptyList(); nearbyError=null
  val center=state.currentLocation
  if(center==null){locate();nearbyError="ابتدا موقعیت دقیق شما دریافت می‌شود؛ دوباره دسته را لمس کنید";return}
  nearbyLoading=true
  scope.launch{val result=withContext(Dispatchers.IO){runCatching{nearbyService.searchNearby(center,query)}};result.onSuccess{nearbyResults=it;if(it.isEmpty())nearbyError="مکانی در شعاع فعلی پیدا نشد"}.onFailure{nearbyError=it.message?:"دریافت مکان‌های اطراف ناموفق بود"};nearbyLoading=false}
 }
 LaunchedEffect(state.currentLocation){if(state.currentLocation!=null&&!state.locating)pin=state.currentLocation}
 LaunchedEffect(state.destinationQuery,state.currentLocation){
  val q=state.destinationQuery.trim(); val center=state.currentLocation
  if(q.length<3||center==null){smartResults=emptyList();smartLoading=false;return@LaunchedEffect}
  delay(500)
  if(state.destinationSuggestions.isNotEmpty()){smartResults=emptyList();return@LaunchedEffect}
  smartLoading=true
  smartResults=withContext(Dispatchers.IO){runCatching{nearbyService.searchNamedNearby(center,q)}.getOrDefault(emptyList())}
  smartLoading=false
 }
 Box(Modifier.fillMaxSize()){
  NvReferenceV8(darkMode,themeMode,onThemeModeChange,viewModel)
  if(!state.navigationActive){
   // The nearby control is intentionally attached to the bottom navigation bar area, not floating mid-map.
   Surface(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end=72.dp,bottom=12.dp),color=V13Panel,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,V13Green.copy(alpha=.65f)),shadowElevation=3.dp){IconButton(onClick={nearbyOpen=true}){Icon(Icons.Rounded.NearMe,"اطراف من",tint=V13Green)}}
  }
  if(!state.navigationActive&&state.destinationQuery.trim().length>=3&&(smartLoading||smartResults.isNotEmpty())&&state.destinationSuggestions.isEmpty()){
   Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=230.dp,horizontal=18.dp).fillMaxWidth(),color=V13Panel,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,V13Cyan.copy(alpha=.55f)),shadowElevation=8.dp){Column(Modifier.padding(8.dp)){
    if(smartLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
    smartResults.take(5).forEach{place->Row(Modifier.fillMaxWidth().clickable{viewModel.selectDestination(place);smartResults=emptyList()}.padding(9.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Place,null,tint=V13Cyan);Spacer(Modifier.width(7.dp));Text(place.name,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f));state.currentLocation?.let{Text(formatNearbyDistance(nearbyDistanceMeters(it,place.coordinate)),color=Color.LightGray,style=MaterialTheme.typography.labelSmall)}}
   }}
  }
 }
 if(nearbyOpen)AlertDialog(onDismissRequest={nearbyOpen=false},containerColor=V13Panel,title={Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.NearMe,null,tint=V13Green);Spacer(Modifier.width(8.dp));Text("اطراف من",color=Color.White,fontWeight=FontWeight.Black)}},text={Column(Modifier.fillMaxWidth().heightIn(max=550.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
  Text("مکان‌های عمومی، ضروری و دیدنی بر اساس فاصله واقعی از GPS شما",color=Color.LightGray)
  val categories=listOf(
   "🚑 اورژانس" to "اورژانس بیمارستان","🏥 بیمارستان" to "بیمارستان","💊 داروخانه" to "داروخانه","🚓 پلیس" to "پلیس","🚒 آتش‌نشانی" to "آتش نشانی","🩺 درمانگاه" to "درمانگاه",
   "🚌 ترمینال" to "ترمینال پایانه","✈️ فرودگاه" to "فرودگاه","🚆 راه‌آهن" to "راه آهن","🚇 مترو" to "مترو","🚕 تاکسی" to "تاکسی","🅿️ پارکینگ" to "پارکینگ",
   "⛽ پمپ‌بنزین" to "پمپ بنزین","🔌 شارژ خودرو" to "شارژ خودرو","🍽 رستوران" to "رستوران","☕ کافه" to "کافه","🏨 هتل" to "هتل","🛒 فروشگاه" to "فروشگاه",
   "🛍 مرکز خرید" to "مرکز خرید","🏦 بانک" to "بانک","🏧 خودپرداز" to "خودپرداز","🥖 نانوایی" to "نانوایی","🏫 مدرسه" to "مدرسه","🎓 دانشگاه" to "دانشگاه",
   "🕌 مسجد" to "مسجد","📮 پست" to "پست","🚻 سرویس" to "سرویس بهداشتی","🔧 تعمیرگاه" to "تعمیرگاه","🏟 ورزشگاه" to "ورزشگاه","🎬 سینما" to "سینما",
   "🏛 موزه" to "موزه","🌳 پارک" to "پارک","📸 دیدنی" to "جاذبه گردشگری دیدنی","🏺 تاریخی" to "اثر تاریخی","🏞 طبیعت" to "طبیعت منظره","🎡 تفریحی" to "تفریح",
   "🏖 ساحل" to "ساحل","⛰ کوه/منظره" to "کوه منظره","🌉 پل دیدنی" to "پل"
  )
  categories.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){row.forEach{(label,q)->OutlinedButton(onClick={nearbySearch(q)},modifier=Modifier.weight(1f),border=BorderStroke(1.dp,if(nearbyQuery==q)V13Green else V13Cyan.copy(alpha=.4f))){Text(label,maxLines=1,overflow=TextOverflow.Ellipsis)}};if(row.size==1)Spacer(Modifier.weight(1f))}}
  if(state.locating||nearbyLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
  nearbyError?.let{Text(it,color=V13Gold)}
  nearbyResults.forEach{place->val d=state.currentLocation?.let{nearbyDistanceMeters(it,place.coordinate)};Surface(Modifier.fillMaxWidth().clickable{if(state.origin==null)locate();viewModel.selectDestination(place);nearbyOpen=false},color=Color.Black.copy(alpha=.18f),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,V13Cyan.copy(alpha=.25f))){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Place,null,tint=V13Green);Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(place.name,color=Color.White,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis);Text(d?.let{formatNearbyDistance(it)}?:"در حال دریافت فاصله",color=Color.LightGray,style=MaterialTheme.typography.labelSmall)};Icon(Icons.Rounded.Navigation,null,tint=V13Green)}}}
 }},confirmButton={TextButton(onClick={nearbyOpen=false}){Text("بستن")}})
 if(picker)Dialog(onDismissRequest={picker=false}){Surface(Modifier.fillMaxWidth().fillMaxHeight(.82f),color=V13Panel,shape=RoundedCornerShape(24.dp)){Column{
  Box(Modifier.weight(1f).fillMaxWidth()){NvCodePickerMap(context,pin,state.satelliteMode,{pin=it},Modifier.fillMaxSize())}
  pin?.let{Text("سنجاق: %.6f, %.6f".format(it.latitude,it.longitude),color=Color.White,modifier=Modifier.padding(12.dp))}
  Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={locate()},modifier=Modifier.weight(1f)){Icon(Icons.Rounded.MyLocation,null);Text("موقعیت من")};Button(onClick={savePin()},enabled=pin!=null,modifier=Modifier.weight(1f)){Icon(Icons.Rounded.Bookmark,null);Text("ثبت سنجاق")}}
 }}}
 if(qrOpen)AlertDialog(onDismissRequest={if(!working)qrOpen=false},containerColor=V13Panel,title={Text("QR و کد عددی NV",color=Color.White,fontWeight=FontWeight.Black)},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp),horizontalAlignment=Alignment.CenterHorizontally){
  bookmark?.let{Text(it.name,color=Color.White);Text("%.6f, %.6f".format(it.coordinate.latitude,it.coordinate.longitude),color=Color.LightGray)}
  qr?.let{saved->NvQrCode(saved.payload,Modifier.size(220.dp));Text("کد عددی NV",color=Color.LightGray);Surface(color=Color.Black.copy(alpha=.22f),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,V13Green.copy(alpha=.65f))){Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(saved.code,color=V13Green,fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleLarge);IconButton(onClick={clipboard.setText(AnnotatedString(saved.code))}){Icon(Icons.Rounded.ContentCopy,"کپی کد عددی",tint=V13Cyan)}}}}
  error?.let{Text(it,color=Color.Red)}
  if(qr==null)Button(onClick={val b=bookmark?:return@Button;working=true;scope.launch{val r=withContext(Dispatchers.IO){val old=b.personalCode;if(!old.isNullOrBlank())qrManager.createAndSave(old,b.name,b.coordinate)else{val a=if(allocator.isConfigured())allocator.allocateOnline(b.name,b.coordinate)else Result.success(NvCodeAllocationService.Allocation(local.nextCode(state.personalPlaces.mapNotNull{it.personalCode}),b.name,b.coordinate,false));a.fold(onSuccess={x->viewModel.savePersonalCode(b,x.code);store.attachCode(x.code);bookmark=b.copy(personalCode=x.code);qrManager.createAndSave(x.code,b.name,b.coordinate)},onFailure={Result.failure(it)})}};r.onSuccess{qr=it}.onFailure{error=it.message?:"ساخت QR ناموفق بود"};working=false}},enabled=!working,modifier=Modifier.fillMaxWidth()){Text("ساخت کد NV و QR",fontWeight=FontWeight.Black)}else Button(onClick={qr?.let(qrManager::share)},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=V13Green,contentColor=Color.Black)){Icon(Icons.Rounded.Share,null);Text(" اشتراک‌گذاری",fontWeight=FontWeight.Black)}
 }},confirmButton={},dismissButton={TextButton(onClick={if(!working)qrOpen=false}){Text("بستن")}})
}

private fun nearbyDistanceMeters(a:Coordinate,b:Coordinate):Double{val r=6371000.0;val lat1=Math.toRadians(a.latitude);val lat2=Math.toRadians(b.latitude);val dLat=lat2-lat1;val dLon=Math.toRadians(b.longitude-a.longitude);val h=sin(dLat/2).pow(2)+cos(lat1)*cos(lat2)*sin(dLon/2).pow(2);return 2*r*atan2(sqrt(h),sqrt(1-h))}
private fun formatNearbyDistance(meters:Double):String=if(meters<1000)"${meters.roundToInt()} متر" else String.format("%.1f کیلومتر",meters/1000.0)

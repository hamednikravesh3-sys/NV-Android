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
import ir.nv.navigation.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

private val V13Panel=Color(0xF2071B2B); private val V13Cyan=Color(0xFF14D8FF); private val V13Gold=Color(0xFFFFB52E); private val V13Green=Color(0xFF43E66B); private val V13Emergency=Color(0xFFE53935)

@Composable
fun NvReferenceV13(darkMode:Boolean,themeMode:AppThemeMode,onThemeModeChange:(AppThemeMode)->Unit,viewModel:NvViewModel){
 val state by viewModel.state.collectAsState(); val context=LocalContext.current; val clipboard=LocalClipboardManager.current; val scope=rememberCoroutineScope()
 val store=remember{NvBookmarkStore(context.applicationContext)}; val allocator=remember{NvCodeAllocationService()}; val local=remember{NvLocalSequentialCodeAllocator(context.applicationContext)}; val qrManager=remember{NvQrShareManager(context)}
 var bookmark by remember{mutableStateOf(store.load())}; var picker by remember{mutableStateOf(false)}; var pin by remember{mutableStateOf<Coordinate?>(bookmark?.coordinate?:state.currentLocation?:state.destination?.coordinate?:state.origin?.coordinate)}
 var qrOpen by remember{mutableStateOf(false)}; var qr by remember{mutableStateOf<NvQrShareManager.SavedQr?>(null)}; var working by remember{mutableStateOf(false)}; var error by remember{mutableStateOf<String?>(null)}
 var nearbyOpen by remember{mutableStateOf(false)}; var nearbyQuery by remember{mutableStateOf("")}
 fun savePin(){val c=pin?:return;val p=Place(-8300000000L,"سنجاق NV",c,"bookmark:pin");store.save(p);bookmark=p;qr=null;error=null;picker=false}
 val doubleTapListener=remember{ { c:Coordinate -> pin=c; val p=Place(-8300000000L,"پرچم NV",c,"bookmark:flag"); store.save(p); bookmark=p; qr=null; error=null; NvMapInteractionBus.recenterOn(c) } }
 DisposableEffect(Unit){ NvMapInteractionBus.onDoubleTap=doubleTapListener; onDispose{NvMapInteractionBus.clearListener(doubleTapListener)} }
 fun useCurrent(){state.currentLocation?.let{c->pin=c;val p=Place(-8300000000L,"موقعیت فعلی من",c,"bookmark:current");store.save(p);bookmark=p;qr=null;error=null;picker=false}}
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){p->if(p.values.any{it})viewModel.useCurrentLocationAsOrigin()}
 fun locate(){if(state.currentLocation!=null){useCurrent();viewModel.recenterNavigation();return};val ok=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;if(ok)viewModel.useCurrentLocationAsOrigin()else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}
 fun nearbySearch(query:String){nearbyQuery=query; if(state.origin==null||state.currentLocation==null) locate(); viewModel.updateDestinationQuery(query)}
 LaunchedEffect(state.currentLocation){if(state.currentLocation!=null&&!state.locating)pin=state.currentLocation}
 val nearbyResults=remember(state.destinationSuggestions,state.currentLocation,nearbyQuery){
  if(nearbyQuery.isBlank()) emptyList() else state.destinationSuggestions.sortedBy{p->state.currentLocation?.let{nearbyDistanceMeters(it,p.coordinate)}?:Double.MAX_VALUE}.take(12)
 }
 Box(Modifier.fillMaxSize()){
  NvReferenceV8(darkMode,themeMode,onThemeModeChange,viewModel)
  if(!state.navigationActive){
   Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom=72.dp),color=V13Panel,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,V13Cyan.copy(alpha=.45f)),shadowElevation=10.dp){Row(Modifier.padding(6.dp),verticalAlignment=Alignment.CenterVertically){
    IconButton(onClick={picker=true}){Icon(Icons.Rounded.LocationOn,"سنجاق",tint=V13Cyan)}
    IconButton(onClick={locate()}){Icon(Icons.Rounded.MyLocation,"موقعیت من",tint=V13Cyan)}
    IconButton(onClick={savePin()},enabled=pin!=null){Icon(if(bookmark!=null)Icons.Rounded.BookmarkAdded else Icons.Rounded.Bookmark,"ثبت سنجاق",tint=if(bookmark!=null)V13Gold else Color.White)}
    IconButton(onClick={qrOpen=true;qr=null;error=null},enabled=bookmark!=null){Icon(Icons.Rounded.QrCode2,"QR",tint=if(bookmark!=null)V13Green else Color.Gray)}
   }}
   Button(onClick={nearbyOpen=true},modifier=Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end=12.dp,bottom=144.dp),colors=ButtonDefaults.buttonColors(containerColor=V13Cyan,contentColor=Color.Black),shape=RoundedCornerShape(18.dp)){Icon(Icons.Rounded.Explore,null);Spacer(Modifier.width(6.dp));Text("اطراف من",fontWeight=FontWeight.Black)}
  }
 }
 if(nearbyOpen)AlertDialog(onDismissRequest={nearbyOpen=false},containerColor=V13Panel,title={Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Explore,null,tint=V13Cyan);Spacer(Modifier.width(8.dp));Text("همه مکان‌های نزدیک من",color=Color.White,fontWeight=FontWeight.Black)}},text={Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
  Text("مکان‌های اضطراری، عمومی، خدماتی و دیدنی را انتخاب کنید. NV نتایج را بر اساس فاصله از موقعیت فعلی مرتب می‌کند.",color=Color.LightGray)
  Text("اضطراری و درمانی",color=V13Emergency,fontWeight=FontWeight.Black)
  val emergencyCategories=listOf("🚑 اورژانس" to "اورژانس بیمارستان","🏥 بیمارستان" to "بیمارستان","💊 داروخانه" to "داروخانه","🚓 پلیس" to "پلیس","🚒 آتش‌نشانی" to "آتش نشانی","🩺 درمانگاه" to "درمانگاه")
  emergencyCategories.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){row.forEach{(label,q)->OutlinedButton(onClick={nearbySearch(q)},modifier=Modifier.weight(1f),border=BorderStroke(1.dp,if(nearbyQuery==q)V13Emergency else V13Emergency.copy(alpha=.45f))){Text(label,maxLines=1,overflow=TextOverflow.Ellipsis)}};if(row.size==1)Spacer(Modifier.weight(1f))}}
  Text("حمل‌ونقل و سفر",color=V13Cyan,fontWeight=FontWeight.Black)
  val transport=listOf("🚌 ترمینال" to "ترمینال پایانه مسافربری","✈️ فرودگاه" to "فرودگاه","🚉 ایستگاه قطار" to "ایستگاه قطار راه آهن","🚇 مترو" to "ایستگاه مترو","🚕 تاکسی" to "ایستگاه تاکسی","🅿️ پارکینگ" to "پارکینگ","⛽ پمپ بنزین" to "پمپ بنزین جایگاه سوخت","⚡ شارژ خودرو" to "ایستگاه شارژ خودرو برقی")
  transport.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){row.forEach{(label,q)->OutlinedButton(onClick={nearbySearch(q)},modifier=Modifier.weight(1f),border=BorderStroke(1.dp,if(nearbyQuery==q)V13Cyan else V13Cyan.copy(alpha=.35f))){Text(label,maxLines=1,overflow=TextOverflow.Ellipsis)}};if(row.size==1)Spacer(Modifier.weight(1f))}}
  Text("خوراک، خرید و اقامت",color=V13Gold,fontWeight=FontWeight.Black)
  val commerce=listOf("🍽 رستوران" to "رستوران","☕ کافه" to "کافه کافی شاپ","🏨 هتل" to "هتل اقامتگاه","🛒 فروشگاه" to "سوپرمارکت فروشگاه","🏬 مرکز خرید" to "مرکز خرید مجتمع تجاری","🏦 بانک" to "بانک","💳 خودپرداز" to "خودپرداز ATM","🍞 نانوایی" to "نانوایی")
  commerce.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){row.forEach{(label,q)->OutlinedButton(onClick={nearbySearch(q)},modifier=Modifier.weight(1f),border=BorderStroke(1.dp,if(nearbyQuery==q)V13Gold else V13Gold.copy(alpha=.35f))){Text(label,maxLines=1,overflow=TextOverflow.Ellipsis)}};if(row.size==1)Spacer(Modifier.weight(1f))}}
  Text("عمومی و خدماتی",color=V13Green,fontWeight=FontWeight.Black)
  val publicPlaces=listOf("🏫 مدرسه" to "مدرسه","🎓 دانشگاه" to "دانشگاه","🕌 مسجد" to "مسجد","🏛 اداره دولتی" to "اداره دولتی","📮 پست" to "اداره پست","🚻 سرویس بهداشتی" to "سرویس بهداشتی عمومی","🔧 تعمیرگاه" to "تعمیرگاه خودرو مکانیکی","💇 خدمات شخصی" to "آرایشگاه خدمات شخصی","🏟 ورزشگاه" to "ورزشگاه مجموعه ورزشی","🎬 سینما" to "سینما")
  publicPlaces.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){row.forEach{(label,q)->OutlinedButton(onClick={nearbySearch(q)},modifier=Modifier.weight(1f),border=BorderStroke(1.dp,if(nearbyQuery==q)V13Green else V13Green.copy(alpha=.35f))){Text(label,maxLines=1,overflow=TextOverflow.Ellipsis)}};if(row.size==1)Spacer(Modifier.weight(1f))}}
  Text("دیدنی و تفریحی",color=Color(0xFFBB75FF),fontWeight=FontWeight.Black)
  val tourism=listOf("🏞 جاذبه دیدنی" to "جاذبه گردشگری دیدنی","🏛 موزه" to "موزه","🏰 اثر تاریخی" to "اثر تاریخی بنای تاریخی","🌳 پارک" to "پارک بوستان","🌲 طبیعت" to "طبیعت منطقه طبیعی","🗿 یادمان" to "یادمان بنای یادبود","🎡 تفریحی" to "شهربازی مرکز تفریحی","🏖 ساحل" to "ساحل","⛰ کوه و بام" to "کوه بام منظره","🌉 پل دیدنی" to "پل تاریخی دیدنی")
  tourism.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){row.forEach{(label,q)->OutlinedButton(onClick={nearbySearch(q)},modifier=Modifier.weight(1f),border=BorderStroke(1.dp,if(nearbyQuery==q)Color(0xFFBB75FF) else Color(0xFFBB75FF).copy(alpha=.35f))){Text(label,maxLines=1,overflow=TextOverflow.Ellipsis)}};if(row.size==1)Spacer(Modifier.weight(1f))}}
  if(state.locating)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(state.destinationSearching)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(nearbyQuery.isNotBlank()&&!state.destinationSearching&&nearbyResults.isEmpty())Surface(color=V13Emergency.copy(alpha=.16f),shape=RoundedCornerShape(14.dp)){Text("برای این دسته نتیجه‌ای پیدا نشد. اینترنت/GPS را بررسی کنید یا دسته دیگری را انتخاب کنید.",color=Color.White,modifier=Modifier.padding(10.dp))}
  nearbyResults.forEach{place->val d=state.currentLocation?.let{nearbyDistanceMeters(it,place.coordinate)};Surface(Modifier.fillMaxWidth().clickable{if(state.origin==null)locate();viewModel.selectDestination(place);nearbyOpen=false},color=Color.Black.copy(alpha=.18f),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,V13Cyan.copy(alpha=.25f))){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Place,null,tint=V13Cyan);Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(place.name,color=Color.White,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis);Text(d?.let{formatNearbyDistance(it)}?:"فاصله پس از دریافت GPS نمایش داده می‌شود",color=Color.LightGray,style=MaterialTheme.typography.labelSmall)};Icon(Icons.Rounded.Navigation,null,tint=V13Green)}}}
  if(state.searchMessage!=null)Text(state.searchMessage!!,color=V13Gold)
 }},confirmButton={TextButton(onClick={nearbyOpen=false}){Text("بستن")}})
 if(picker)Dialog(onDismissRequest={picker=false}){Surface(Modifier.fillMaxWidth().fillMaxHeight(.82f),color=V13Panel,shape=RoundedCornerShape(24.dp)){Column{
  Box(Modifier.weight(1f).fillMaxWidth()){NvCodePickerMap(context,pin,state.satelliteMode,{pin=it},Modifier.fillMaxSize())}
  pin?.let{Text("سنجاق: %.6f, %.6f".format(it.latitude,it.longitude),color=Color.White,modifier=Modifier.padding(12.dp))}
  Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={locate()},modifier=Modifier.weight(1f)){Icon(Icons.Rounded.MyLocation,null);Text("موقعیت من")};Button(onClick={savePin()},enabled=pin!=null,modifier=Modifier.weight(1f)){Icon(Icons.Rounded.Bookmark,null);Text("ثبت سنجاق")}}
 }}}
 if(qrOpen)AlertDialog(onDismissRequest={if(!working)qrOpen=false},containerColor=V13Panel,title={Text("QR و کد عددی NV",color=Color.White,fontWeight=FontWeight.Black)},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp),horizontalAlignment=Alignment.CenterHorizontally){
  bookmark?.let{Text(it.name,color=Color.White);Text("%.6f, %.6f".format(it.coordinate.latitude,it.coordinate.longitude),color=Color.LightGray)}
  qr?.let{saved->
   NvQrCode(saved.payload,Modifier.size(220.dp))
   Text("کد عددی NV",color=Color.LightGray)
   Surface(color=Color.Black.copy(alpha=.22f),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,V13Green.copy(alpha=.65f))){Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(saved.code,color=V13Green,fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleLarge);IconButton(onClick={clipboard.setText(AnnotatedString(saved.code))}){Icon(Icons.Rounded.ContentCopy,"کپی کد عددی",tint=V13Cyan)}}}
  }
  error?.let{Text(it,color=Color.Red)}
  if(qr==null)Button(onClick={val b=bookmark?:return@Button;working=true;scope.launch{val r=withContext(Dispatchers.IO){val old=b.personalCode;if(!old.isNullOrBlank())qrManager.createAndSave(old,b.name,b.coordinate)else{val a=if(allocator.isConfigured())allocator.allocateOnline(b.name,b.coordinate)else Result.success(NvCodeAllocationService.Allocation(local.nextCode(state.personalPlaces.mapNotNull{it.personalCode}),b.name,b.coordinate,false));a.fold(onSuccess={x->viewModel.savePersonalCode(b,x.code);store.attachCode(x.code);bookmark=b.copy(personalCode=x.code);qrManager.createAndSave(x.code,b.name,b.coordinate)},onFailure={Result.failure(it)})}};r.onSuccess{qr=it}.onFailure{error=it.message?:"ساخت QR ناموفق بود"};working=false}},enabled=!working,modifier=Modifier.fillMaxWidth()){Text("ساخت کد NV و QR",fontWeight=FontWeight.Black)}else Button(onClick={qr?.let(qrManager::share)},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=V13Green,contentColor=Color.Black)){Icon(Icons.Rounded.Share,null);Text(" اشتراک‌گذاری",fontWeight=FontWeight.Black)}
 }},confirmButton={},dismissButton={TextButton(onClick={if(!working)qrOpen=false}){Text("بستن")}})
}

private fun nearbyDistanceMeters(a:Coordinate,b:Coordinate):Double{
 val r=6371000.0; val lat1=Math.toRadians(a.latitude); val lat2=Math.toRadians(b.latitude); val dLat=lat2-lat1; val dLon=Math.toRadians(b.longitude-a.longitude)
 val h=sin(dLat/2).pow(2)+cos(lat1)*cos(lat2)*sin(dLon/2).pow(2)
 return 2*r*atan2(sqrt(h),sqrt(1-h))
}
private fun formatNearbyDistance(meters:Double):String=if(meters<1000)"${meters.roundToInt()} متر" else String.format("%.1f کیلومتر",meters/1000.0)

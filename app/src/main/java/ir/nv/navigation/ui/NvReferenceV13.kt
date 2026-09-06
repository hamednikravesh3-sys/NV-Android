package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.data.NvBookmarkStore
import ir.nv.navigation.data.NvCodeAllocationService
import ir.nv.navigation.data.NvLocalSequentialCodeAllocator
import ir.nv.navigation.data.NvQrShareManager
import ir.nv.navigation.map.NvCodePickerMap
import ir.nv.navigation.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val V13Panel = Color(0xF2071B2B)
private val V13Cyan = Color(0xFF14D8FF)
private val V13Gold = Color(0xFFFFB52E)
private val V13Green = Color(0xFF43E66B)

@Composable
fun NvReferenceV13(darkMode: Boolean, themeMode: AppThemeMode, onThemeModeChange: (AppThemeMode) -> Unit, viewModel: NvViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bookmarkStore = remember { NvBookmarkStore(context.applicationContext) }
    val allocator = remember { NvCodeAllocationService() }
    val localAllocator = remember { NvLocalSequentialCodeAllocator(context.applicationContext) }
    val qrManager = remember { NvQrShareManager(context) }
    var bookmark by remember { mutableStateOf(bookmarkStore.load()) }
    var pickerVisible by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf<Coordinate?>(bookmark?.coordinate ?: state.currentLocation ?: state.destination?.coordinate ?: state.origin?.coordinate) }
    var qrDialog by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<NvQrShareManager.SavedQr?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun savePin() {
        val c = pin ?: return
        val p = Place(-8_300_000_000L, "سنجاق NV", c, "bookmark:pin")
        bookmarkStore.save(p); bookmark = p; qr = null; error = null; pickerVisible = false
    }
    fun useCurrentLocation() {
        state.currentLocation?.let { c -> pin = c; val p = Place(-8_300_000_000L, "موقعیت فعلی من", c, "bookmark:current"); bookmarkStore.save(p); bookmark = p; qr = null; error = null; pickerVisible = false }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) viewModel.useCurrentLocationAsOrigin()
    }
    fun requestLocation() {
        if (state.currentLocation != null) { useCurrentLocation(); viewModel.recenterNavigation(); return }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.useCurrentLocationAsOrigin() else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(state.currentLocation) { if (state.currentLocation != null && state.locating.not()) pin = state.currentLocation }

    Box(Modifier.fillMaxSize()) {
        NvReferenceV8(darkMode, themeMode, onThemeModeChange, viewModel)
        if (!state.navigationActive) {
            Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp), color = V13Panel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, V13Cyan.copy(alpha=.45f)), shadowElevation = 10.dp) {
                Row(Modifier.padding(horizontal=6.dp, vertical=3.dp), verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={ pickerVisible=true }) { Icon(Icons.Rounded.LocationOn,"سنجاق",tint=V13Cyan) }
                    IconButton(onClick={ requestLocation() }) { Icon(Icons.Rounded.MyLocation,"موقعیت من",tint=V13Cyan) }
                    IconButton(onClick={ if(pin!=null) savePin() }, enabled=pin!=null) { Icon(if(bookmark!=null) Icons.Rounded.BookmarkAdded else Icons.Rounded.Bookmark,"بوک مارک",tint=if(bookmark!=null)V13Gold else Color.White) }
                    IconButton(onClick={ qrDialog=true; qr=null; error=null }, enabled=bookmark!=null) { Icon(Icons.Rounded.QrCode2,"QR",tint=if(bookmark!=null)V13Green else Color.Gray) }
                }
            }
        }
    }

    if (pickerVisible) {
        Dialog(onDismissRequest={pickerVisible=false}) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(.82f), color=V13Panel, shape=RoundedCornerShape(24.dp)) {
                Column {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        NvCodePickerMap(context, pin, state.satelliteMode, { pin=it }, Modifier.fillMaxSize())
                    }
                    pin?.let { Text("سنجاق: %.6f, %.6f".format(it.latitude,it.longitude), color=Color.White, modifier=Modifier.padding(12.dp)) }
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={requestLocation()}, modifier=Modifier.weight(1f)) { Icon(Icons.Rounded.MyLocation,null); Text("موقعیت من") }
                        Button(onClick={savePin}, enabled=pin!=null, modifier=Modifier.weight(1f)) { Icon(Icons.Rounded.Bookmark,null); Text("ثبت سنجاق") }
                    }
                }
            }
        }
    }

    if (qrDialog) AlertDialog(
        onDismissRequest={if(!working)qrDialog=false}, containerColor=V13Panel,
        title={Text("QR مکان NV",color=Color.White,fontWeight=FontWeight.Black)},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp),horizontalAlignment=Alignment.CenterHorizontally){
            bookmark?.let { Text(it.name,color=Color.White); Text("%.6f, %.6f".format(it.coordinate.latitude,it.coordinate.longitude),color=Color.LightGray) }
            qr?.let { NvQrCode(it.payload,Modifier.size(220.dp)); Text("NV:${it.code}",color=V13Green,fontWeight=FontWeight.Black) }
            error?.let { Text(it,color=Color.Red) }
            if(qr==null) Button(onClick={
                val b=bookmark?:return@Button; working=true; scope.launch { val result=withContext(Dispatchers.IO){
                    val existing=b.personalCode
                    if(!existing.isNullOrBlank()) qrManager.createAndSave(existing,b.name,b.coordinate) else {
                        val allocation=if(allocator.isConfigured()) allocator.allocateOnline(b.name,b.coordinate) else Result.success(NvCodeAllocationService.Allocation(localAllocator.nextCode(state.personalPlaces.mapNotNull{it.personalCode}),b.name,b.coordinate,false))
                        allocation.fold(onSuccess={a->viewModel.savePersonalCode(b,a.code);bookmarkStore.attachCode(a.code);bookmark=b.copy(personalCode=a.code);qrManager.createAndSave(a.code,b.name,b.coordinate)},onFailure={Result.failure(it)})
                    }}; result.onSuccess{qr=it}.onFailure{error=it.message?:"ساخت QR ناموفق بود"};working=false }
            },enabled=!working,modifier=Modifier.fillMaxWidth()){Text("ساخت کد NV و QR",fontWeight=FontWeight.Black)}
            else Button(onClick={qr?.let(qrManager::share)},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=V13Green,contentColor=Color.Black)){Icon(Icons.Rounded.Share,null);Text(" اشتراک‌گذاری",fontWeight=FontWeight.Black)}
        }}, confirmButton={}, dismissButton={TextButton(onClick={if(!working)qrDialog=false}){Text("بستن")}}
    )
}

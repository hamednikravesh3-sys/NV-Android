package ir.nv.navigation.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.data.NvBookmarkStore
import ir.nv.navigation.data.NvCodeAllocationService
import ir.nv.navigation.data.NvLocalSequentialCodeAllocator
import ir.nv.navigation.data.NvQrShareManager
import ir.nv.navigation.map.NvCodePickerMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NvCodePanel = Color(0xF2071B2B)
private val NvCodeCyan = Color(0xFF14D8FF)
private val NvCodeGreen = Color(0xFF43E66B)

@Composable
fun NvCodeToolbarOverlay(viewModel: NvViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val store = remember { NvBookmarkStore(context.applicationContext) }
    val allocator = remember { NvCodeAllocationService() }
    val localAllocator = remember { NvLocalSequentialCodeAllocator(context.applicationContext) }
    val qrManager = remember { NvQrShareManager(context) }

    var bookmark by remember { mutableStateOf(store.load()) }
    var pickerOpen by remember { mutableStateOf(false) }
    var qrOpen by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf<Coordinate?>(bookmark?.coordinate ?: state.destination?.coordinate ?: state.currentLocation ?: state.origin?.coordinate) }
    var qr by remember { mutableStateOf<NvQrShareManager.SavedQr?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (!state.navigationActive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, bottom = 82.dp),
                color = NvCodePanel,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, NvCodeCyan.copy(alpha = .72f)),
                shadowElevation = 5.dp
            ) {
                TextButton(
                    onClick = {
                        bookmark = store.load()
                        if (bookmark == null) pickerOpen = true else qrOpen = true
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.QrCode2, "کد NV", tint = NvCodeGreen)
                    Spacer(Modifier.width(6.dp))
                    Text("کد NV", color = Color.White, fontWeight = FontWeight.Black)
                }
            }
        }
    }

    if (pickerOpen) {
        Dialog(onDismissRequest = { pickerOpen = false }) {
            Surface(
                Modifier.fillMaxWidth().fillMaxHeight(.78f),
                color = NvCodePanel,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column {
                    Text("نقطه را برای کد NV انتخاب کنید", color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.padding(14.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        NvCodePickerMap(context, pin, state.satelliteMode, { pin = it }, Modifier.fillMaxSize())
                    }
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pin = state.currentLocation ?: pin }, modifier = Modifier.weight(1f)) { Text("موقعیت من") }
                        Button(
                            onClick = {
                                val coordinate = pin ?: return@Button
                                val saved = Place(-8300000000L, "سنجاق NV", coordinate, "bookmark:pin")
                                store.save(saved)
                                bookmark = saved
                                qr = null
                                error = null
                                pickerOpen = false
                                qrOpen = true
                            },
                            enabled = pin != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.BookmarkAdd, null)
                            Text(" ثبت")
                        }
                    }
                }
            }
        }
    }

    if (qrOpen) {
        AlertDialog(
            onDismissRequest = { if (!working) qrOpen = false },
            containerColor = NvCodePanel,
            title = { Text("کد NV و QR", color = Color.White, fontWeight = FontWeight.Black) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    bookmark?.let { saved ->
                        Text(saved.name, color = Color.White)
                        Text("%.6f, %.6f".format(saved.coordinate.latitude, saved.coordinate.longitude), color = Color.LightGray)
                    }
                    qr?.let { savedQr ->
                        NvQrCode(savedQr.payload, Modifier.size(220.dp))
                        Surface(color = Color.Black.copy(alpha = .22f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, NvCodeGreen.copy(alpha = .65f))) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(savedQr.code, color = NvCodeGreen, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                                IconButton(onClick = { clipboard.setText(AnnotatedString(savedQr.code)) }) {
                                    Icon(Icons.Rounded.ContentCopy, "کپی کد NV", tint = NvCodeCyan)
                                }
                            }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (qr == null) {
                        Button(
                            onClick = {
                                val savedBookmark = bookmark ?: return@Button
                                working = true
                                error = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val existing = savedBookmark.personalCode
                                        if (!existing.isNullOrBlank()) {
                                            qrManager.createAndSave(existing, savedBookmark.name, savedBookmark.coordinate)
                                        } else {
                                            val allocation = if (allocator.isConfigured()) {
                                                allocator.allocateOnline(savedBookmark.name, savedBookmark.coordinate)
                                            } else {
                                                Result.success(NvCodeAllocationService.Allocation(localAllocator.nextCode(state.personalPlaces.mapNotNull { it.personalCode }), savedBookmark.name, savedBookmark.coordinate, false))
                                            }
                                            allocation.fold(
                                                onSuccess = { allocated ->
                                                    viewModel.savePersonalCode(savedBookmark, allocated.code)
                                                    store.attachCode(allocated.code)
                                                    bookmark = savedBookmark.copy(personalCode = allocated.code)
                                                    qrManager.createAndSave(allocated.code, savedBookmark.name, savedBookmark.coordinate)
                                                },
                                                onFailure = { Result.failure(it) }
                                            )
                                        }
                                    }
                                    result.onSuccess { qr = it }.onFailure { error = it.message ?: "ساخت کد NV ناموفق بود" }
                                    working = false
                                }
                            },
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (working) "در حال ساخت..." else "ساخت کد NV و QR", fontWeight = FontWeight.Black) }
                    } else {
                        Button(
                            onClick = { qr?.let(qrManager::share) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NvCodeGreen, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Rounded.Share, null)
                            Text(" اشتراک‌گذاری", fontWeight = FontWeight.Black)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { if (!working) qrOpen = false }) { Text("بستن") } }
        )
    }
}

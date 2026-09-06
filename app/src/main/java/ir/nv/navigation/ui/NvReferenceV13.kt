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
import ir.nv.navigation.core.Place
import ir.nv.navigation.data.NvBookmarkStore
import ir.nv.navigation.data.NvCodeAllocationService
import ir.nv.navigation.data.NvLocalSequentialCodeAllocator
import ir.nv.navigation.data.NvQrShareManager
import ir.nv.navigation.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val V13Panel = Color(0xF2071B2B)
private val V13Cyan = Color(0xFF14D8FF)
private val V13Gold = Color(0xFFFFB52E)
private val V13Green = Color(0xFF43E66B)

@Composable
fun NvReferenceV13(
    darkMode: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: NvViewModel
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bookmarkStore = remember { NvBookmarkStore(context.applicationContext) }
    val allocator = remember { NvCodeAllocationService() }
    val localAllocator = remember { NvLocalSequentialCodeAllocator(context.applicationContext) }
    val qrManager = remember { NvQrShareManager(context) }

    var bookmark by remember { mutableStateOf(bookmarkStore.load()) }
    var qrDialog by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<NvQrShareManager.SavedQr?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var locatePending by remember { mutableStateOf(false) }
    var previousOrigin by remember { mutableStateOf<Place?>(null) }

    val candidate = state.destination ?: state.currentLocation?.let {
        Place(-8_300_000_001L, "موقعیت فعلی من", it, "bookmark:current")
    } ?: state.origin

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            previousOrigin = state.origin
            locatePending = true
            viewModel.useCurrentLocationAsOrigin()
        }
    }

    fun locateMe() {
        if (state.currentLocation != null) {
            viewModel.recenterNavigation()
            return
        }
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            previousOrigin = state.origin
            locatePending = true
            viewModel.useCurrentLocationAsOrigin()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(state.currentLocation, locatePending) {
        if (locatePending && state.currentLocation != null) {
            viewModel.recenterNavigation()
            previousOrigin?.let { old ->
                if (state.origin?.coordinate != old.coordinate) viewModel.selectOrigin(old)
            }
            locatePending = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        NvReferenceV8(
            darkMode = darkMode,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            viewModel = viewModel
        )

        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(58.dp),
            color = V13Panel,
            shape = CircleShape,
            border = BorderStroke(2.dp, V13Cyan),
            shadowElevation = 8.dp
        ) {
            IconButton(onClick = ::locateMe) {
                if (state.locating) CircularProgressIndicator(Modifier.size(25.dp), strokeWidth = 3.dp, color = V13Cyan)
                else Icon(Icons.Rounded.MyLocation, "موقعیت من", tint = V13Cyan, modifier = Modifier.size(30.dp))
            }
        }

        // Bottom toolbar extension: Bookmark first, QR only after a bookmark exists.
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 72.dp),
            color = V13Panel,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, V13Cyan.copy(alpha = .45f)),
            shadowElevation = 10.dp
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        candidate?.let {
                            bookmarkStore.save(it)
                            bookmark = it
                            qr = null
                            error = null
                        }
                    },
                    enabled = candidate != null
                ) {
                    Icon(
                        if (bookmark != null) Icons.Rounded.BookmarkAdded else Icons.Rounded.Bookmark,
                        "بوک‌مارک مکان",
                        tint = if (bookmark != null) V13Gold else if (candidate != null) Color.White else Color.Gray
                    )
                }
                VerticalDivider(Modifier.height(28.dp), color = Color.White.copy(alpha = .2f))
                IconButton(
                    onClick = {
                        if (bookmark != null) {
                            qrDialog = true
                            error = null
                            qr = null
                        }
                    },
                    enabled = bookmark != null
                ) {
                    Icon(
                        Icons.Rounded.QrCode2,
                        "QR بوک‌مارک",
                        tint = if (bookmark != null) V13Green else Color.Gray
                    )
                }
            }
        }

        if (bookmark != null && !state.navigationActive) {
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 10.dp, bottom = 132.dp),
                color = V13Panel,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, V13Gold.copy(alpha = .65f))
            ) {
                Text(
                    "بوک‌مارک: ${bookmark!!.name.take(24)}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (qrDialog) {
        AlertDialog(
            onDismissRequest = { if (!working) qrDialog = false },
            containerColor = V13Panel,
            title = { Text("QR بوک‌مارک NV", color = Color.White, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val savedBookmark = bookmark
                    Text(savedBookmark?.name.orEmpty(), color = Color.White)
                    savedBookmark?.let {
                        Text("%.6f, %.6f".format(it.coordinate.latitude, it.coordinate.longitude), color = Color(0xFFB7C9D4))
                    }
                    qr?.let { saved ->
                        NvQrCode(saved.payload, Modifier.size(220.dp))
                        Text("NV:${saved.code}", color = V13Green, fontWeight = FontWeight.Black)
                    }
                    error?.let { Text(it, color = Color(0xFFFF6B78), fontWeight = FontWeight.Bold) }
                    if (qr == null) {
                        Button(
                            onClick = {
                                val savedBookmark = bookmark ?: return@Button
                                working = true
                                error = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        val existingCode = savedBookmark.personalCode
                                        if (!existingCode.isNullOrBlank()) {
                                            qrManager.createAndSave(existingCode, savedBookmark.name, savedBookmark.coordinate)
                                        } else {
                                            val allocation = if (allocator.isConfigured()) {
                                                allocator.allocateOnline(savedBookmark.name, savedBookmark.coordinate)
                                            } else {
                                                val code = localAllocator.nextCode(state.personalPlaces.mapNotNull { it.personalCode })
                                                Result.success(NvCodeAllocationService.Allocation(code, savedBookmark.name, savedBookmark.coordinate, false))
                                            }
                                            allocation.fold(
                                                onSuccess = { allocated ->
                                                    viewModel.savePersonalCode(savedBookmark, allocated.code)
                                                    bookmarkStore.attachCode(allocated.code)
                                                    val coded = savedBookmark.copy(personalCode = allocated.code)
                                                    bookmark = coded
                                                    qrManager.createAndSave(allocated.code, coded.name, coded.coordinate)
                                                },
                                                onFailure = { Result.failure(it) }
                                            )
                                        }
                                    }
                                    result.onSuccess { qr = it }.onFailure { error = it.message ?: "ساخت QR ناموفق بود" }
                                    working = false
                                }
                            },
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text("ساخت QR برای بوک‌مارک", fontWeight = FontWeight.Black)
                        }
                    } else {
                        Button(
                            onClick = { qr?.let(qrManager::share) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = V13Green, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Rounded.Share, null)
                            Spacer(Modifier.width(6.dp))
                            Text("اشتراک‌گذاری", fontWeight = FontWeight.Black)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { if (!working) qrDialog = false }) { Text("بستن") }
            }
        )
    }
}

package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.nv.navigation.core.Place
import ir.nv.navigation.data.NvCodeAllocationService
import ir.nv.navigation.data.NvQrScanner
import ir.nv.navigation.ui.theme.NvColors
import ir.nv.navigation.ui.theme.NvRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Unified QR surface: scanning and authoritative online code creation live in one place.
 * There is deliberately no manual/local code-entry flow here. New codes are allocated
 * by the central registry, whose DB uniqueness constraints are the source of truth.
 */
@Composable
fun NvQrScannerOverlay(
    viewModel: NvViewModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val registry = remember { NvCodeAllocationService() }
    var dialogOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var allocating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var generatedCode by remember { mutableStateOf<String?>(null) }
    var generatedName by remember { mutableStateOf<String?>(null) }

    fun openScanner() {
        message = null
        dialogOpen = true
    }

    fun applyScan(bitmap: Bitmap?) {
        if (bitmap == null) {
            message = "تصویری دریافت نشد"
            return
        }
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.Default) { NvQrScanner.decode(bitmap) }
            result.onSuccess { scan ->
                val stored = viewModel.state.value.personalPlaces.firstOrNull { it.personalCode == scan.code }
                val embeddedCoordinate = scan.coordinate ?: stored?.coordinate
                val resolved = if (embeddedCoordinate == null && registry.isConfigured()) {
                    withContext(Dispatchers.IO) {
                        registry.resolveOnline("NV:${scan.code}").getOrNull()
                    }
                } else null

                val place = when {
                    stored != null -> stored
                    embeddedCoordinate != null -> Place(
                        code = -8_400_000_000L,
                        name = scan.name?.takeIf { it.isNotBlank() } ?: "مکان NV ${scan.code}",
                        coordinate = embeddedCoordinate,
                        category = "nv:qr",
                        personalCode = scan.code
                    )
                    resolved != null -> resolved
                    else -> null
                }

                if (place == null) {
                    message = if (registry.isConfigured()) {
                        "کد NV ${scan.code} خوانده شد، اما در سامانه مرکزی پیدا نشد"
                    } else {
                        "کد خوانده شد، اما سامانه آنلاین NV در این نسخه تنظیم نشده است"
                    }
                } else {
                    viewModel.selectDestination(place)
                    message = "مقصد از QR تنظیم شد: ${place.name}"
                    dialogOpen = false
                }
            }.onFailure {
                message = it.message ?: "QR معتبر NV پیدا نشد"
            }
            busy = false
        }
    }

    fun allocateForCurrentPlace() {
        val place = state.destination ?: state.origin ?: state.currentLocation?.let { coordinate ->
            Place(
                code = -8_500_000_000L,
                name = "موقعیت فعلی من",
                coordinate = coordinate,
                category = "device-location"
            )
        }
        if (place == null) {
            viewModel.useCurrentLocationAsOrigin()
            message = "در حال تثبیت موقعیت دقیق؛ پس از دریافت GPS دوباره «ساخت QR» را بزنید."
            return
        }
        if (!state.onlineAvailable) {
            message = "ساخت کد یکتا فقط به‌صورت آنلاین انجام می‌شود؛ اینترنت را وصل کنید."
            return
        }
        if (!registry.isConfigured()) {
            message = "آدرس سامانه مرکزی کد NV در این Build تنظیم نشده است."
            return
        }

        allocating = true
        generatedCode = null
        generatedName = null
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                registry.allocateOnline(place.name, place.coordinate)
            }
            result.onSuccess { allocation ->
                generatedCode = allocation.code
                generatedName = allocation.name
                message = "کد آنلاین یکتا از رجیستری مرکزی دریافت شد. این کد دستی یا محلی نیست."
            }.onFailure { error ->
                message = error.message ?: "ساخت کد آنلاین ممکن نشد"
            }
            allocating = false
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }.getOrNull()
        applyScan(bitmap)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        applyScan(bitmap)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
        else message = "برای اسکن QR با دوربین، دسترسی دوربین لازم است"
    }

    if (compact) {
        FloatingActionButton(
            onClick = ::openScanner,
            modifier = modifier,
            shape = CircleShape,
            containerColor = NvColors.Navy900,
            contentColor = NvColors.RouteBlue
        ) {
            Icon(Icons.Rounded.QrCode2, contentDescription = "QR و کد NV")
        }
    } else {
        ExtendedFloatingActionButton(
            onClick = ::openScanner,
            modifier = modifier,
            icon = { Icon(Icons.Rounded.QrCode2, contentDescription = null) },
            text = { Text("QR / کد NV") }
        )
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!busy && !allocating) dialogOpen = false },
            containerColor = NvColors.Navy900,
            titleContentColor = NvColors.TextPrimaryDark,
            textContentColor = NvColors.TextPrimaryDark,
            title = { Text("QR و کد NV", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "هم اسکن و هم ساخت کد در همین بخش انجام می‌شود. کد جدید را رجیستری آنلاین NV به‌صورت یکتا تخصیص می‌دهد.",
                        color = NvColors.TextSecondaryDark,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Button(
                        onClick = ::allocateForCurrentPlace,
                        enabled = !busy && !allocating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (allocating) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(7.dp))
                            Text("در حال دریافت کد یکتا…")
                        } else {
                            Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("ساخت QR آنلاین برای این مکان")
                        }
                    }

                    generatedCode?.let { code ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = NvColors.TextPrimaryDark,
                            shape = RoundedCornerShape(NvRadius.Medium)
                        ) {
                            Column(
                                Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                NvQrCode("NV:$code", Modifier.size(190.dp))
                                Text("NV:$code", color = NvColors.Navy900, fontWeight = FontWeight.Black)
                                generatedName?.let { Text(it, color = NvColors.Navy900, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }

                    HorizontalDivider(color = NvColors.DividerDark)
                    Text("اسکن QR موجود", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                if (granted) cameraLauncher.launch(null)
                                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            enabled = !busy && !allocating,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("دوربین")
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            enabled = !busy && !allocating,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, NvColors.RouteBlue)
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("گالری")
                        }
                    }
                    if (busy) Text("در حال خواندن QR…", color = NvColors.RouteBlue)
                    message?.let {
                        Text(
                            it,
                            color = if (generatedCode != null) NvColors.Success else MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { if (!busy && !allocating) dialogOpen = false }) { Text("بستن") }
            }
        )
    }
}

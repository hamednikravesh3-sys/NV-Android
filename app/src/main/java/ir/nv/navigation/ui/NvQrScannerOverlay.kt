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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.nv.navigation.core.Place
import ir.nv.navigation.data.NvCodeAllocationService
import ir.nv.navigation.data.NvQrScanner
import ir.nv.navigation.ui.theme.NvColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NvQrScannerOverlay(
    viewModel: NvViewModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val registry = remember { NvCodeAllocationService() }
    var dialogOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

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
                        "کد NV ${scan.code} خوانده شد، اما سامانه مرکزی کد NV در این نسخه تنظیم نشده است"
                    }
                } else {
                    viewModel.selectDestination(place)
                    message = if (resolved != null) {
                        "مقصد از سامانه مرکزی NV پیدا شد: ${place.name}"
                    } else {
                        "مقصد از QR تنظیم شد: ${place.name}"
                    }
                    dialogOpen = false
                }
            }.onFailure {
                message = it.message ?: "QR معتبر NV پیدا نشد"
            }
            busy = false
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
            Icon(Icons.Rounded.QrCode2, contentDescription = "اسکن QR")
        }
    } else {
        ExtendedFloatingActionButton(
            onClick = ::openScanner,
            modifier = modifier,
            icon = { Icon(Icons.Rounded.QrCode2, contentDescription = null) },
            text = { Text("اسکن QR") }
        )
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!busy) dialogOpen = false },
            containerColor = NvColors.Navy900,
            titleContentColor = NvColors.TextPrimaryDark,
            textContentColor = NvColors.TextPrimaryDark,
            title = { Text("اسکن کد NV", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("QR را با دوربین بگیرید یا تصویر آن را از گالری انتخاب کنید.")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                if (granted) cameraLauncher.launch(null)
                                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("دوربین")
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, NvColors.RouteBlue)
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("گالری")
                        }
                    }
                    if (busy) Text("در حال خواندن QR…", color = NvColors.RouteBlue)
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { if (!busy) dialogOpen = false }) { Text("بستن") }
            }
        )
    }
}

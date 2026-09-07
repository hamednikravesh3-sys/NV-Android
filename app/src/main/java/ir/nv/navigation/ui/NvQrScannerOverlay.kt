package ir.nv.navigation.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.nv.navigation.core.Place
import ir.nv.navigation.data.NvQrScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NvQrScannerOverlay(
    viewModel: NvViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialogOpen by androidx.compose.runtime.remember { mutableStateOf(false) }
    var busy by androidx.compose.runtime.remember { mutableStateOf(false) }
    var message by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }

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
                val coordinate = scan.coordinate ?: stored?.coordinate
                if (coordinate == null) {
                    message = "کد NV ${scan.code} خوانده شد، اما این QR مختصات ندارد و کد در دستگاه ذخیره نشده است"
                } else {
                    val place = stored ?: Place(
                        code = -8_400_000_000L,
                        name = scan.name?.takeIf { it.isNotBlank() } ?: "مکان NV ${scan.code}",
                        coordinate = coordinate,
                        category = "nv:qr",
                        personalCode = scan.code
                    )
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

    ExtendedFloatingActionButton(
        onClick = {
            message = null
            dialogOpen = true
        },
        modifier = modifier,
        icon = { Icon(Icons.Rounded.QrCode2, contentDescription = null) },
        text = { Text("اسکن QR") }
    )

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { if (!busy) dialogOpen = false },
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("گالری")
                        }
                    }
                    if (busy) Text("در حال خواندن QR…", color = MaterialTheme.colorScheme.primary)
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

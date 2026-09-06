package ir.nv.navigation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.nv.navigation.map.IranPackManager

@Composable
fun OfflineMapDiagnosticsButton(
    offlineReady: Boolean,
    modifier: Modifier = Modifier
) {
    if (!offlineReady) return

    val context = LocalContext.current
    val manager = remember { IranPackManager(context.applicationContext) }
    var visible by remember { mutableStateOf(false) }

    Button(
        onClick = { visible = true },
        modifier = modifier.widthIn(max = 180.dp)
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null)
        Text(" اطلاعات نقشه")
    }

    if (visible) {
        val mapFile = manager.mapFile
        val sizeMb = if (mapFile.isFile) mapFile.length() / (1024.0 * 1024.0) else 0.0
        AlertDialog(
            onDismissRequest = { visible = false },
            title = { Text("Iran map", fontWeight = FontWeight.Black) },
            text = {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("نام بسته: Iran map")
                    Text("نام فایل واقعی: ${mapFile.name}")
                    Text("وضعیت فایل: ${if (mapFile.isFile) "پیدا شد ✓" else "پیدا نشد ✗"}")
                    if (mapFile.isFile) {
                        Text("حجم فایل نقشه: ${String.format("%.1f MB", sizeMb)}")
                    }
                    Text("مسیر کامل:", fontWeight = FontWeight.Bold)
                    Text(
                        mapFile.absolutePath,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "پوشه نصب: ${manager.installDirectory.absolutePath}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { visible = false }) {
                    Text("بستن")
                }
            }
        )
    }
}

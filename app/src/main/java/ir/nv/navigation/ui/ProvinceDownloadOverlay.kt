package ir.nv.navigation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.nv.navigation.offline.OfflinePackCatalog
import ir.nv.navigation.offline.OfflineRegionPack
import ir.nv.navigation.offline.ProvincePackDownloadManager
import kotlinx.coroutines.delay

@Composable
fun ProvinceDownloadOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { ProvincePackDownloadManager(context.applicationContext) }
    val statuses = remember { mutableStateMapOf<String, ProvincePackDownloadManager.Status>() }
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        while (true) {
            OfflinePackCatalog.provinces.forEach { pack -> statuses[pack.id] = manager.status(pack) }
            delay(1_000)
        }
    }

    Button(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Rounded.CloudDownload, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("دانلود استان")
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("نقشه آفلاین استان‌ها", fontWeight = FontWeight.Black) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("هر استان جداگانه دانلود می‌شود و دانلود پس از بسته‌شدن برنامه توسط Android ادامه پیدا می‌کند.")
                    OfflinePackCatalog.provinces.forEach { pack ->
                        ProvincePackRow(pack, statuses[pack.id] ?: manager.status(pack), manager) {
                            statuses[pack.id] = manager.status(pack)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text("بستن") } }
        )
    }
}

@Composable
private fun ProvincePackRow(
    pack: OfflineRegionPack,
    status: ProvincePackDownloadManager.Status,
    manager: ProvincePackDownloadManager,
    refresh: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(pack.title, fontWeight = FontWeight.Bold)
                Text("حدود ${pack.estimatedSizeMb} مگابایت")
            }
            when (status) {
                ProvincePackDownloadManager.Status.NotStarted -> Button(onClick = { manager.start(pack); refresh() }) { Text("دانلود") }
                is ProvincePackDownloadManager.Status.Downloading -> OutlinedButton(onClick = { manager.cancel(pack); refresh() }) { Text("لغو") }
                ProvincePackDownloadManager.Status.Downloaded -> OutlinedButton(onClick = { manager.cancel(pack); refresh() }) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("حذف")
                }
                is ProvincePackDownloadManager.Status.Failed -> Button(onClick = { manager.start(pack); refresh() }) { Text("تلاش دوباره") }
            }
        }
        when (status) {
            is ProvincePackDownloadManager.Status.Downloading -> {
                val progress = if (status.totalBytes > 0L) status.bytes.toFloat() / status.totalBytes.toFloat() else null
                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${status.bytes / (1024 * 1024)} / ${if (status.totalBytes > 0) status.totalBytes / (1024 * 1024) else 0} MB")
            }
            ProvincePackDownloadManager.Status.Downloaded -> Text("دانلود کامل شد")
            is ProvincePackDownloadManager.Status.Failed -> Text(status.reason)
            ProvincePackDownloadManager.Status.NotStarted -> Unit
        }
    }
}

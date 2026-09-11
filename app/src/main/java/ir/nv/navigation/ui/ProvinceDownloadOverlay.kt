package ir.nv.navigation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import ir.nv.navigation.map.IranPackManager
import ir.nv.navigation.offline.OfflinePackCatalog
import ir.nv.navigation.offline.OfflineRegionPack
import ir.nv.navigation.offline.ProvincePackAvailabilityService
import ir.nv.navigation.offline.ProvincePackDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ProvinceDownloadOverlay(
    iranPackStatus: IranPackManager.Status,
    onStartIranDownload: () -> Unit,
    onRetryIranDownload: () -> Unit,
    onCancelIranDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember { ProvincePackDownloadManager(context.applicationContext) }
    val availabilityService = remember { ProvincePackAvailabilityService() }
    val statuses = remember { mutableStateMapOf<String, ProvincePackDownloadManager.Status>() }
    val installFailures = remember { mutableStateMapOf<String, String>() }
    var open by remember { mutableStateOf(false) }
    var publishedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var availabilityLoaded by remember { mutableStateOf(false) }
    var availabilityError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        availabilityLoaded = false
        availabilityError = null
        withContext(Dispatchers.IO) { availabilityService.fetch() }
            .onSuccess {
                publishedIds = it.publishedPackIds
                availabilityLoaded = true
            }
            .onFailure {
                publishedIds = emptySet()
                availabilityError = it.message ?: "وضعیت انتشار بسته‌های استانی دریافت نشد"
                availabilityLoaded = true
            }
    }

    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        while (true) {
            OfflinePackCatalog.provinces.forEach { pack ->
                if (pack.id in installFailures) return@forEach
                when (val status = manager.status(pack)) {
                    ProvincePackDownloadManager.Status.Downloaded -> {
                        statuses[pack.id] = status
                        manager.installDownloaded(pack)
                            .onSuccess {
                                installFailures.remove(pack.id)
                                statuses[pack.id] = ProvincePackDownloadManager.Status.Ready
                            }
                            .onFailure {
                                val message = it.message ?: "نصب بسته استان ناموفق بود"
                                installFailures[pack.id] = message
                                statuses[pack.id] = ProvincePackDownloadManager.Status.Failed(message)
                            }
                    }
                    else -> statuses[pack.id] = status
                }
            }
            delay(1_000)
        }
    }

    Button(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Rounded.CloudDownload, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("دانلود آفلاین")
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("نقشه آفلاین", fontWeight = FontWeight.Black) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("بسته کامل ایران اکنون قابل دانلود است. بسته‌های ۳۱ استان نیز هر زمان روی سرور منتشر شوند به‌صورت مستقل فعال می‌شوند.")
                    if (!availabilityLoaded) LinearProgressIndicator(Modifier.fillMaxWidth())
                    availabilityError?.let {
                        Text("بررسی بسته‌های استانی ناموفق بود. دانلود کامل ایران همچنان در دسترس است. $it")
                    }

                    if (availabilityLoaded && publishedIds.isEmpty()) {
                        Text(
                            "هنوز هیچ بسته استانی مستقلی روی سرور منتشر نشده است؛ به‌جای دکمه‌های غیرفعال می‌توانید همین حالا بسته کامل ایران را دانلود کنید.",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IranPackFallbackRow(
                        status = iranPackStatus,
                        onStart = onStartIranDownload,
                        onRetry = onRetryIranDownload,
                        onCancel = onCancelIranDownload
                    )

                    Text("بسته‌های استانی", fontWeight = FontWeight.Black)
                    OfflinePackCatalog.provinces.forEach { pack ->
                        ProvincePackRow(
                            pack = pack,
                            status = statuses[pack.id] ?: manager.status(pack),
                            manager = manager,
                            published = availabilityLoaded && pack.id in publishedIds,
                            availabilityLoaded = availabilityLoaded,
                            onRetry = {
                                installFailures.remove(pack.id)
                                manager.deleteInstalled(pack)
                                if (publishedIds.contains(pack.id)) manager.start(pack)
                                statuses[pack.id] = manager.status(pack)
                            }
                        ) {
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
private fun IranPackFallbackRow(
    status: IranPackManager.Status,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("کل ایران", fontWeight = FontWeight.Black)
                Text("بسته رسمی منتشرشده؛ نقشه، جستجو و مسیریابی آفلاین")
            }
            when (status) {
                IranPackManager.Status.NotStarted -> Button(onClick = onStart) { Text("دانلود") }
                IranPackManager.Status.Installing -> OutlinedButton(onClick = {}, enabled = false) { Text("در حال نصب…") }
                is IranPackManager.Status.Downloading -> OutlinedButton(onClick = onCancel) { Text("لغو") }
                IranPackManager.Status.Ready -> OutlinedButton(onClick = {}, enabled = false) { Text("نصب شده") }
                is IranPackManager.Status.Failed -> Button(onClick = onRetry) { Text("تلاش دوباره") }
            }
        }
        when (status) {
            is IranPackManager.Status.Downloading -> {
                val progress = if (status.totalBytes > 0L) status.bytes.toFloat() / status.totalBytes.toFloat() else null
                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${status.bytes / (1024 * 1024)} / ${if (status.totalBytes > 0) status.totalBytes / (1024 * 1024) else 0} MB")
            }
            IranPackManager.Status.Installing -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("در حال اعتبارسنجی و نصب بسته کامل ایران")
            }
            IranPackManager.Status.Ready -> Text("نقشه آفلاین ایران آماده استفاده است")
            is IranPackManager.Status.Failed -> Text(status.reason)
            IranPackManager.Status.NotStarted -> Unit
        }
    }
}

@Composable
private fun ProvincePackRow(
    pack: OfflineRegionPack,
    status: ProvincePackDownloadManager.Status,
    manager: ProvincePackDownloadManager,
    published: Boolean,
    availabilityLoaded: Boolean,
    onRetry: () -> Unit,
    refresh: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(pack.title, fontWeight = FontWeight.Bold)
                Text("حدود ${pack.estimatedSizeMb} مگابایت")
                if (availabilityLoaded && !published && status == ProvincePackDownloadManager.Status.NotStarted) {
                    Text("بسته استانی هنوز روی سرور منتشر نشده است")
                }
            }
            when (status) {
                ProvincePackDownloadManager.Status.NotStarted -> Button(
                    onClick = { manager.start(pack); refresh() },
                    enabled = published
                ) { Text(if (published) "دانلود" else "منتظر انتشار") }

                is ProvincePackDownloadManager.Status.Downloading -> OutlinedButton(
                    onClick = { manager.cancel(pack); refresh() }
                ) { Text("لغو") }

                ProvincePackDownloadManager.Status.Downloaded -> OutlinedButton(
                    onClick = { manager.cancel(pack); refresh() }
                ) { Text("در حال نصب…") }

                ProvincePackDownloadManager.Status.Ready -> OutlinedButton(
                    onClick = { manager.deleteInstalled(pack); refresh() }
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("حذف")
                }

                is ProvincePackDownloadManager.Status.Failed -> Button(
                    onClick = onRetry,
                    enabled = published
                ) { Text(if (published) "تلاش دوباره" else "منتظر انتشار") }
            }
        }
        when (status) {
            is ProvincePackDownloadManager.Status.Downloading -> {
                val progress = if (status.totalBytes > 0L) status.bytes.toFloat() / status.totalBytes.toFloat() else null
                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${status.bytes / (1024 * 1024)} / ${if (status.totalBytes > 0) status.totalBytes / (1024 * 1024) else 0} MB")
            }
            ProvincePackDownloadManager.Status.Downloaded -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("در حال اعتبارسنجی و نصب امن بسته استان")
            }
            ProvincePackDownloadManager.Status.Ready -> Text("نصب و اعتبارسنجی کامل شد")
            is ProvincePackDownloadManager.Status.Failed -> Text(status.reason)
            ProvincePackDownloadManager.Status.NotStarted -> Unit
        }
    }
}

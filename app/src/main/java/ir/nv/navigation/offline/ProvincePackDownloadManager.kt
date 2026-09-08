package ir.nv.navigation.offline

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import ir.nv.navigation.BuildConfig
import java.io.File

/**
 * Download coordinator for province-level offline packs.
 *
 * Each province keeps an independent DownloadManager id so downloads can be resumed by Android,
 * cancelled separately and recovered after process restarts. Package installation/validation is
 * intentionally kept separate from transport so Batch 26 can own the generic install/update flow.
 */
class ProvincePackDownloadManager(private val context: Context) {
    sealed interface Status {
        data object NotStarted : Status
        data class Downloading(val bytes: Long, val totalBytes: Long) : Status
        data object Downloaded : Status
        data class Failed(val reason: String) : Status
    }

    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun start(pack: OfflineRegionPack): Long {
        require(pack.id != OfflinePackCatalog.iran.id) { "برای بسته کل ایران از IranPackManager استفاده کنید" }
        require(OfflinePackCatalog.provinces.any { it.id == pack.id }) { "استان ناشناخته است: ${pack.id}" }

        val existing = downloadId(pack.id)
        if (existing != NO_DOWNLOAD_ID) {
            when (downloadManagerStatus(existing)) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_SUCCESSFUL -> return existing
            }
            clearDownloadId(pack.id)
        }

        val target = downloadedFile(pack)
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val request = DownloadManager.Request(Uri.parse(downloadUrl(pack)))
            .setTitle("نقشه آفلاین ${pack.title}")
            .setDescription("دانلود نقشه، جستجو و داده مسیریابی استان ${pack.title}")
            .setMimeType("application/octet-stream")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                fileName(pack)
            )

        return downloads.enqueue(request).also { id ->
            prefs.edit().putLong(key(pack.id), id).apply()
        }
    }

    fun status(pack: OfflineRegionPack): Status {
        val id = downloadId(pack.id)
        if (id == NO_DOWNLOAD_ID) {
            return if (downloadedFile(pack).isFile) Status.Downloaded else Status.NotStarted
        }

        downloads.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) {
                clearDownloadId(pack.id)
                return Status.Failed("دانلود استان در سیستم پیدا نشد؛ دوباره تلاش کنید")
            }
            return when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> Status.Downloaded
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    clearDownloadId(pack.id)
                    Status.Failed(errorMessage(reason))
                }
                else -> Status.Downloading(
                    bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                )
            }
        }
        return Status.Failed("وضعیت دانلود استان قابل خواندن نیست")
    }

    fun cancel(pack: OfflineRegionPack) {
        val id = downloadId(pack.id)
        if (id != NO_DOWNLOAD_ID) runCatching { downloads.remove(id) }
        clearDownloadId(pack.id)
        downloadedFile(pack).delete()
    }

    fun downloadedFile(pack: OfflineRegionPack): File =
        File(requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)), fileName(pack))

    internal fun downloadUrl(pack: OfflineRegionPack): String =
        BuildConfig.PROVINCE_PACK_BASE_URL.trimEnd('/') + "/${fileName(pack)}"

    private fun downloadId(packId: String): Long = prefs.getLong(key(packId), NO_DOWNLOAD_ID)

    private fun clearDownloadId(packId: String) {
        prefs.edit().remove(key(packId)).apply()
    }

    private fun downloadManagerStatus(id: Long): Int? = runCatching {
        downloads.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) null
            else cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
    }.getOrNull()

    private fun errorMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "فضای ذخیره‌سازی برای نقشه استان کافی نیست"
        DownloadManager.ERROR_CANNOT_RESUME -> "دانلود استان قابل ادامه نبود؛ دوباره تلاش کنید"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "ارتباط هنگام دانلود استان قطع شد"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "فایل استان هنوز روی سرور در دسترس نیست"
        DownloadManager.ERROR_FILE_ERROR -> "ذخیره فایل استان ناموفق بود"
        else -> "دانلود نقشه استان ناموفق بود (کد $reason)"
    }

    private fun fileName(pack: OfflineRegionPack): String = "province-${pack.id}.nvpack"
    private fun key(packId: String): String = "download_id_$packId"

    private companion object {
        const val PREFS_NAME = "province_pack_downloads"
        const val NO_DOWNLOAD_ID = -1L
    }
}

package ir.nv.navigation.map

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import ir.nv.navigation.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class IranPackManager(private val context: Context) {
    sealed interface Status {
        data object NotStarted : Status
        data class Downloading(val bytes: Long, val totalBytes: Long) : Status
        data object Installing : Status
        data object Ready : Status
        data class Failed(val reason: String) : Status
    }

    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val downloadedPack = File(
        requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)),
        PACK_FILE_NAME
    )
    val installDirectory: File = context.filesDir.resolve(INSTALL_DIRECTORY)
    val mapFile: File get() = installDirectory.resolve(MAP_FILE)
    val placesFile: File get() = installDirectory.resolve(PLACES_FILE)
    val routingFile: File get() = installDirectory.resolve(ROUTING_FILE)

    fun isReady(): Boolean =
        installDirectory.resolve(MANIFEST_FILE).isFile &&
            mapFile.isFile && placesFile.isFile && routingFile.isFile

    fun startDownload(): Long {
        // Iran map is downloaded only once. Once installed successfully, all later
        // launches use the local pack until the user explicitly deletes it.
        if (isReady()) return READY_DOWNLOAD_ID

        val existing = prefs.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD_ID)
        if (existing != NO_DOWNLOAD_ID && existing != READY_DOWNLOAD_ID) {
            when (downloadStatus(existing)) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_SUCCESSFUL -> return existing
                else -> {
                    // A stale/failed DownloadManager row must not permanently block
                    // future attempts. Clean it and create a fresh request.
                    runCatching { downloads.remove(existing) }
                    prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                }
            }
        }

        downloadedPack.parentFile?.mkdirs()
        if (downloadedPack.exists()) downloadedPack.delete()

        val request = DownloadManager.Request(Uri.parse(BuildConfig.IRAN_PACK_URL))
            .setTitle(DISPLAY_NAME)
            .setDescription("Iran map — نقشه، مکان‌ها و مسیریابی آفلاین کل ایران")
            .setMimeType("application/octet-stream")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationUri(Uri.fromFile(downloadedPack))

        val id = downloads.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        return id
    }

    fun status(): Status {
        if (isReady()) return Status.Ready
        val id = prefs.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD_ID)
        if (id == NO_DOWNLOAD_ID || id == READY_DOWNLOAD_ID) return Status.NotStarted

        downloads.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) {
                prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                return Status.Failed("دانلود Iran map در سیستم پیدا نشد؛ دوباره دانلود را بزنید")
            }
            val dmStatus = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return when (dmStatus) {
                DownloadManager.STATUS_SUCCESSFUL -> Status.Installing
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    // Clear failed id so pressing download again starts immediately.
                    prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                    Status.Failed(downloadErrorMessage(reason))
                }
                else -> Status.Downloading(
                    bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                )
            }
        }
        return Status.Failed("وضعیت دانلود Iran map قابل خواندن نیست")
    }

    suspend fun installDownloadedPack(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(downloadedPack.isFile) { "فایل Iran map پیدا نشد" }
            verifyChecksum(downloadedPack)

            val staging = context.filesDir.resolve(INSTALL_DIRECTORY + "-staging")
            staging.deleteRecursively()
            check(staging.mkdirs()) { "ساخت پوشه نصب Iran map ممکن نشد" }
            unzipSafely(downloadedPack, staging)

            val required = listOf(MANIFEST_FILE, MAP_FILE, PLACES_FILE, ROUTING_FILE)
            check(required.all { staging.resolve(it).isFile }) {
                "بسته Iran map ناقص است: " + required.filterNot { staging.resolve(it).isFile }
            }
            verifyManifest(staging)

            installDirectory.deleteRecursively()
            check(staging.renameTo(installDirectory)) { "جابه‌جایی Iran map نصب‌شده ناموفق بود" }
            downloadedPack.delete()
            prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        }
    }

    fun retry() {
        cancelDownload()
        startDownload()
    }

    fun cancelDownload() {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD_ID)
        if (id != NO_DOWNLOAD_ID && id != READY_DOWNLOAD_ID) runCatching { downloads.remove(id) }
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        if (downloadedPack.exists()) downloadedPack.delete()
    }

    fun deleteInstalledPack() {
        cancelDownload()
        installDirectory.deleteRecursively()
    }

    private fun downloadStatus(id: Long): Int? {
        return runCatching {
            downloads.query(DownloadManager.Query().setFilterById(id))?.use { cursor: Cursor ->
                if (!cursor.moveToFirst()) null
                else cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            }
        }.getOrNull()
    }

    private fun downloadErrorMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "فضای ذخیره‌سازی برای Iran map کافی نیست"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "حافظه دستگاه برای ذخیره Iran map در دسترس نیست"
        DownloadManager.ERROR_CANNOT_RESUME -> "دانلود Iran map قابل ادامه نبود؛ دوباره تلاش کنید"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "ارتباط هنگام دانلود Iran map قطع شد؛ دوباره تلاش کنید"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "خطای انتقال لینک دانلود Iran map"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "سرور دانلود Iran map پاسخ نامعتبر داد"
        DownloadManager.ERROR_FILE_ERROR -> "خطا در ذخیره فایل Iran map"
        else -> "دانلود Iran map ناموفق بود (کد $reason)؛ دوباره تلاش کنید"
    }

    private fun verifyChecksum(file: File) {
        val expected = BuildConfig.IRAN_PACK_SHA256.trim().lowercase()
        if (expected.isEmpty()) return
        val actual = sha256(file)
        check(actual == expected) { "امضای SHA-256 بسته Iran map صحیح نیست" }
    }

    private fun verifyManifest(directory: File) {
        val manifest = JSONObject(directory.resolve(MANIFEST_FILE).readText())
        check(manifest.optInt("schemaVersion") == SUPPORTED_SCHEMA_VERSION) {
            "نسخه بسته Iran map پشتیبانی نمی‌شود"
        }
        val files = manifest.getJSONObject("files")
        listOf(MAP_FILE, PLACES_FILE, ROUTING_FILE).forEach { name ->
            val expected = files.getJSONObject(name).getString("sha256").lowercase()
            val actual = sha256(directory.resolve(name))
            check(actual == expected) { "فایل $name ناقص یا دستکاری شده است" }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun unzipSafely(source: File, target: File) {
        val canonicalTarget = target.canonicalFile
        ZipInputStream(FileInputStream(source).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = target.resolve(entry.name).canonicalFile
                check(output.path.startsWith(canonicalTarget.path + File.separator)) {
                    "مسیر غیرمجاز در بسته داده"
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).buffered().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "iran_pack_download"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val NO_DOWNLOAD_ID = -1L
        const val READY_DOWNLOAD_ID = -2L
        const val DISPLAY_NAME = "Iran map"
        const val PACK_FILE_NAME = "Iran map.nvpack"
        const val INSTALL_DIRECTORY = "iran-pack"
        const val MANIFEST_FILE = "manifest.json"
        const val MAP_FILE = "iran.map"
        const val PLACES_FILE = "places.db"
        const val ROUTING_FILE = "routing.db"
        const val SUPPORTED_SCHEMA_VERSION = 2
    }
}

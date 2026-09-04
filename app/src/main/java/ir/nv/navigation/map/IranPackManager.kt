package ir.nv.navigation.map

import android.app.DownloadManager
import android.content.Context
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

    fun ensureDownloadStarted(): Long {
        if (isReady()) return READY_DOWNLOAD_ID
        val existing = prefs.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD_ID)
        if (existing != NO_DOWNLOAD_ID) return existing

        downloadedPack.parentFile?.mkdirs()
        if (downloadedPack.exists()) downloadedPack.delete()
        val request = DownloadManager.Request(Uri.parse(BuildConfig.IRAN_PACK_URL))
            .setTitle("نقشه کامل ایران — NV")
            .setDescription("داده نقشه، مکان‌ها و مسیریابی آفلاین")
            .setMimeType("application/zip")
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
        if (id == NO_DOWNLOAD_ID) return Status.NotStarted
        downloads.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return Status.Failed("دانلود در سیستم پیدا نشد")
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> Status.Installing
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                    )
                    Status.Failed("خطای دانلود: " + reason)
                }
                else -> Status.Downloading(
                    bytes = cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                        )
                    ),
                    totalBytes = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    )
                )
            }
        }
        return Status.Failed("وضعیت دانلود قابل خواندن نیست")
    }

    suspend fun installDownloadedPack(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(downloadedPack.isFile) { "فایل بسته ایران پیدا نشد" }
            verifyChecksum(downloadedPack)

            val staging = context.filesDir.resolve(INSTALL_DIRECTORY + "-staging")
            staging.deleteRecursively()
            check(staging.mkdirs()) { "ساخت پوشه نصب ممکن نشد" }
            unzipSafely(downloadedPack, staging)

            val required = listOf(MANIFEST_FILE, MAP_FILE, PLACES_FILE, ROUTING_FILE)
            check(required.all { staging.resolve(it).isFile }) {
                "بسته ایران ناقص است: " + required.filterNot { staging.resolve(it).isFile }
            }
            verifyManifest(staging)

            installDirectory.deleteRecursively()
            check(staging.renameTo(installDirectory)) { "جابه‌جایی بسته نصب‌شده ناموفق بود" }
            downloadedPack.delete()
            prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        }
    }

    fun retry() {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD_ID)
        if (id != NO_DOWNLOAD_ID) downloads.remove(id)
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        if (downloadedPack.exists()) downloadedPack.delete()
        ensureDownloadStarted()
    }

    private fun verifyChecksum(file: File) {
        val expected = BuildConfig.IRAN_PACK_SHA256.trim().lowercase()
        if (expected.isEmpty()) return
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual == expected) { "امضای SHA-256 بسته ایران صحیح نیست" }
    }

    private fun verifyManifest(directory: File) {
        val manifest = JSONObject(directory.resolve(MANIFEST_FILE).readText())
        check(manifest.optInt("schemaVersion") == SUPPORTED_SCHEMA_VERSION) {
            "نسخه بسته داده پشتیبانی نمی‌شود"
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
        const val PACK_FILE_NAME = "iran.nvpack"
        const val INSTALL_DIRECTORY = "iran-pack"
        const val MANIFEST_FILE = "manifest.json"
        const val MAP_FILE = "iran.map"
        const val PLACES_FILE = "places.db"
        const val ROUTING_FILE = "routing.db"
        const val SUPPORTED_SCHEMA_VERSION = 2
    }
}

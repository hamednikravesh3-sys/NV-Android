package ir.nv.navigation.offline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Installs province packs only after validating region identity, schema and every file hash. */
class OfflineRegionPackInstaller(private val context: Context) {
    data class InstalledFiles(
        val directory: File,
        val mapFile: File,
        val placesFile: File,
        val routingFile: File,
        val manifestFile: File
    )

    fun installed(pack: OfflineRegionPack): InstalledFiles? {
        val directory = installDirectory(pack)
        val files = InstalledFiles(
            directory = directory,
            mapFile = directory.resolve("${pack.id}.map"),
            placesFile = directory.resolve(PLACES_FILE),
            routingFile = directory.resolve(ROUTING_FILE),
            manifestFile = directory.resolve(MANIFEST_FILE)
        )
        return files.takeIf {
            it.manifestFile.isFile && it.mapFile.isFile && it.mapFile.length() > 0L &&
                it.placesFile.isFile && it.routingFile.isFile
        }
    }

    suspend fun install(pack: OfflineRegionPack, source: File): Result<InstalledFiles> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(pack.id != OfflinePackCatalog.iran.id) { "برای کل ایران از نصب‌کننده ایران استفاده کنید" }
                require(OfflinePackCatalog.provinceById(pack.id) != null) { "استان ناشناخته است" }
                check(source.isFile && source.length() > 0L) { "فایل بسته استان پیدا نشد" }

                val target = installDirectory(pack)
                val staging = File(target.parentFile, target.name + "-staging")
                staging.deleteRecursively()
                check(staging.mkdirs()) { "ساخت پوشه موقت نصب استان ممکن نشد" }
                unzipSafely(source, staging)

                val manifestFile = staging.resolve(MANIFEST_FILE)
                check(manifestFile.isFile) { "manifest بسته استان وجود ندارد" }
                val manifest = JSONObject(manifestFile.readText())
                check(manifest.optInt("schemaVersion") == SUPPORTED_SCHEMA_VERSION) { "نسخه بسته استان پشتیبانی نمی‌شود" }
                check(manifest.optString("regionId") == pack.id) { "بسته دانلودشده متعلق به استان ${pack.title} نیست" }

                val mapName = "${pack.id}.map"
                val required = listOf(mapName, PLACES_FILE, ROUTING_FILE)
                check(required.all { staging.resolve(it).isFile }) {
                    "بسته استان ناقص است: ${required.filterNot { staging.resolve(it).isFile }}"
                }
                val manifestFiles = manifest.getJSONObject("files")
                required.forEach { name ->
                    val metadata = manifestFiles.getJSONObject(name)
                    val expected = metadata.getString("sha256").lowercase()
                    check(expected.matches(Regex("^[a-f0-9]{64}$"))) { "SHA-256 فایل $name نامعتبر است" }
                    val file = staging.resolve(name)
                    check(sha256(file) == expected) { "فایل $name ناقص یا دستکاری شده است" }
                    val expectedBytes = metadata.optLong("bytes", -1L)
                    if (expectedBytes >= 0L) check(file.length() == expectedBytes) { "اندازه فایل $name صحیح نیست" }
                }

                target.parentFile?.mkdirs()
                target.deleteRecursively()
                check(staging.renameTo(target)) { "جابه‌جایی بسته نصب‌شده استان ناموفق بود" }
                requireNotNull(installed(pack)) { "اعتبارسنجی پس از نصب استان ناموفق بود" }
            }
        }

    fun delete(pack: OfflineRegionPack) {
        installDirectory(pack).deleteRecursively()
    }

    private fun installDirectory(pack: OfflineRegionPack): File =
        context.filesDir.resolve("province-packs").resolve(pack.id)

    private fun unzipSafely(source: File, target: File) {
        val canonicalTarget = target.canonicalFile
        ZipInputStream(FileInputStream(source).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = target.resolve(entry.name).canonicalFile
                check(output.path.startsWith(canonicalTarget.path + File.separator)) { "مسیر غیرمجاز در بسته استان" }
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

    private companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val PLACES_FILE = "places.db"
        const val ROUTING_FILE = "routing.db"
        const val SUPPORTED_SCHEMA_VERSION = 2
    }
}

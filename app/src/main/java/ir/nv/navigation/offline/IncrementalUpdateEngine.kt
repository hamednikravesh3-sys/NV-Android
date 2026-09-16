package ir.nv.navigation.offline

import java.io.File
import java.security.MessageDigest

data class IncrementalAsset(
    val relativePath: String,
    val sha256: String,
    val sizeBytes: Long,
    val patchUrl: String? = null,
    val fullUrl: String
)

data class IncrementalUpdatePlan(
    val changed: List<IncrementalAsset>,
    val unchanged: List<IncrementalAsset>,
    val downloadBytes: Long
)

/** Plans component/patch downloads so unchanged map assets are never downloaded again. */
object IncrementalUpdateEngine {
    fun plan(installedRoot: File, remote: List<IncrementalAsset>): IncrementalUpdatePlan {
        val changed = mutableListOf<IncrementalAsset>()
        val unchanged = mutableListOf<IncrementalAsset>()
        remote.forEach { asset ->
            require(asset.relativePath.isNotBlank() && !asset.relativePath.contains(".."))
            require(asset.sha256.matches(Regex("^[a-fA-F0-9]{64}$")))
            val local = installedRoot.resolve(asset.relativePath)
            if (local.isFile && sha256(local).equals(asset.sha256, ignoreCase = true)) unchanged += asset
            else changed += asset
        }
        return IncrementalUpdatePlan(changed, unchanged, changed.sumOf { it.sizeBytes.coerceAtLeast(0) })
    }

    fun downloadUrl(asset: IncrementalAsset, patchSupported: Boolean): String =
        if (patchSupported && !asset.patchUrl.isNullOrBlank()) asset.patchUrl else asset.fullUrl

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

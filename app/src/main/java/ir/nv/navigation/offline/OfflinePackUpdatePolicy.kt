package ir.nv.navigation.offline

import java.time.Instant

/** Pure update-decision logic shared by Iran and province packs. */
object OfflinePackUpdatePolicy {
    data class InstalledVersion(
        val version: Long,
        val sha256: String,
        val installedAtEpochSeconds: Long
    )

    data class RemoteVersion(
        val version: Long,
        val sha256: String,
        val sizeBytes: Long,
        val publishedAtEpochSeconds: Long
    )

    sealed interface Decision {
        data object UpToDate : Decision
        data class UpdateAvailable(val remote: RemoteVersion) : Decision
        data class InvalidMetadata(val reason: String) : Decision
    }

    fun decide(installed: InstalledVersion?, remote: RemoteVersion): Decision {
        if (remote.version <= 0L) return Decision.InvalidMetadata("نسخه بسته نامعتبر است")
        if (remote.sizeBytes <= 0L) return Decision.InvalidMetadata("اندازه بسته نامعتبر است")
        if (!remote.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            return Decision.InvalidMetadata("SHA-256 بسته نامعتبر است")
        }
        if (remote.publishedAtEpochSeconds > Instant.now().epochSecond + MAX_CLOCK_SKEW_SECONDS) {
            return Decision.InvalidMetadata("زمان انتشار بسته نامعتبر است")
        }
        if (installed == null) return Decision.UpdateAvailable(remote)
        if (remote.version < installed.version) return Decision.UpToDate
        if (remote.version == installed.version && remote.sha256.equals(installed.sha256, ignoreCase = true)) {
            return Decision.UpToDate
        }
        return Decision.UpdateAvailable(remote)
    }

    private const val MAX_CLOCK_SKEW_SECONDS = 86_400L
}

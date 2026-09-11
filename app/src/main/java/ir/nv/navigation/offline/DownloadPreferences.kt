package ir.nv.navigation.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import java.io.File

data class DownloadPreferences(
    val wifiOnly: Boolean = true,
    val autoUpdate: Boolean = true,
    val downloadOvernight: Boolean = false
)

/** Persistent user policy for all offline-pack downloads. */
class DownloadPreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): DownloadPreferences = DownloadPreferences(
        wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true),
        autoUpdate = prefs.getBoolean(KEY_AUTO_UPDATE, true),
        downloadOvernight = prefs.getBoolean(KEY_DOWNLOAD_OVERNIGHT, false)
    )

    fun save(value: DownloadPreferences) {
        prefs.edit()
            .putBoolean(KEY_WIFI_ONLY, value.wifiOnly)
            .putBoolean(KEY_AUTO_UPDATE, value.autoUpdate)
            .putBoolean(KEY_DOWNLOAD_OVERNIGHT, value.downloadOvernight)
            .apply()
    }

    fun setWifiOnly(enabled: Boolean) = save(load().copy(wifiOnly = enabled))
    fun setAutoUpdate(enabled: Boolean) = save(load().copy(autoUpdate = enabled))
    fun setDownloadOvernight(enabled: Boolean) = save(load().copy(downloadOvernight = enabled))

    private companion object {
        const val PREFS_NAME = "offline_download_preferences"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_AUTO_UPDATE = "auto_update"
        const val KEY_DOWNLOAD_OVERNIGHT = "download_overnight"
    }
}

data class DownloadPreflight(
    val downloadSizeBytes: Long,
    val availableStorageBytes: Long,
    val mapVersion: Long,
    val lastUpdateEpochSeconds: Long?,
    val downloadType: String,
    val wifiConnected: Boolean,
    val allowed: Boolean,
    val reason: String? = null
)

class OfflineDownloadPolicy(private val context: Context) {
    fun preflight(
        target: File,
        sizeBytes: Long,
        version: Long,
        lastUpdateEpochSeconds: Long?,
        type: String,
        preferences: DownloadPreferences
    ): DownloadPreflight {
        val storage = StatFs(target.parentFile?.absolutePath ?: context.filesDir.absolutePath).availableBytes
        val wifi = wifiConnected()
        val enoughStorage = sizeBytes > 0 && storage >= sizeBytes + RESERVE_BYTES
        val networkAllowed = !preferences.wifiOnly || wifi
        val reason = when {
            sizeBytes <= 0 -> "اندازه دانلود معتبر نیست"
            !enoughStorage -> "فضای ذخیره‌سازی کافی نیست"
            !networkAllowed -> "دانلود فقط با Wi‑Fi مجاز است"
            else -> null
        }
        return DownloadPreflight(
            downloadSizeBytes = sizeBytes,
            availableStorageBytes = storage,
            mapVersion = version,
            lastUpdateEpochSeconds = lastUpdateEpochSeconds,
            downloadType = type,
            wifiConnected = wifi,
            allowed = reason == null,
            reason = reason
        )
    }

    fun wifiConnected(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private companion object { const val RESERVE_BYTES = 128L * 1024L * 1024L }
}

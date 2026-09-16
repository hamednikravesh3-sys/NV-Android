package ir.nv.navigation.privacy

import android.content.Context

data class PrivacySettingsState(
    val strictMode: Boolean = true,
    val locationHistory: Boolean = false,
    val analytics: Boolean = false,
    val communityUploads: Boolean = false,
    val cloudSync: Boolean = false
)

/** Explicit-consent privacy settings. All network-sensitive optional features default to off. */
class PrivacySettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): PrivacySettingsState = PrivacySettingsState(
        strictMode = prefs.getBoolean(KEY_STRICT, true),
        locationHistory = prefs.getBoolean(KEY_LOCATION_HISTORY, false),
        analytics = prefs.getBoolean(KEY_ANALYTICS, false),
        communityUploads = prefs.getBoolean(KEY_COMMUNITY_UPLOADS, false),
        cloudSync = prefs.getBoolean(KEY_CLOUD_SYNC, false)
    )

    fun save(value: PrivacySettingsState): PrivacySettingsState = value.copy().also { normalized ->
        prefs.edit()
            .putBoolean(KEY_STRICT, normalized.strictMode)
            .putBoolean(KEY_LOCATION_HISTORY, normalized.locationHistory)
            .putBoolean(KEY_ANALYTICS, normalized.analytics)
            .putBoolean(KEY_COMMUNITY_UPLOADS, normalized.communityUploads)
            .putBoolean(KEY_CLOUD_SYNC, normalized.cloudSync)
            .apply()
    }

    fun update(transform: (PrivacySettingsState) -> PrivacySettingsState): PrivacySettingsState =
        save(transform(load()))

    private companion object {
        const val PREFS = "nv_privacy"
        const val KEY_STRICT = "strict_mode"
        const val KEY_LOCATION_HISTORY = "location_history"
        const val KEY_ANALYTICS = "analytics"
        const val KEY_COMMUNITY_UPLOADS = "community_uploads"
        const val KEY_CLOUD_SYNC = "cloud_sync"
    }
}

object PrivacyPolicy {
    fun mayUploadCommunityReports(settings: PrivacySettingsState): Boolean = settings.communityUploads
    fun mayUseCloudSync(settings: PrivacySettingsState): Boolean = settings.cloudSync
    fun mayRecordLocationHistory(settings: PrivacySettingsState): Boolean = settings.locationHistory
    fun mayCollectAnalytics(settings: PrivacySettingsState): Boolean = settings.analytics
}

package ir.nv.navigation.account

import android.content.Context

/**
 * Persisted NV account session metadata for cloud sync.
 *
 * Access tokens are intentionally never written by this store; callers must obtain them from
 * the authenticated runtime/backend flow. Only non-secret identity/sync metadata is persisted.
 */
class NvAccountSessionStore(context: Context) {
    data class Session(
        val userId: String,
        val displayName: String,
        val email: String?,
        val lastSyncRevision: Long
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): Session? {
        val userId = prefs.getString(KEY_USER_ID, null)?.trim().orEmpty()
        if (userId.isBlank()) return null
        return Session(
            userId = userId,
            displayName = prefs.getString(KEY_DISPLAY_NAME, null)?.trim().orEmpty().ifBlank { "کاربر NV" },
            email = prefs.getString(KEY_EMAIL, null)?.trim()?.takeIf { it.isNotBlank() },
            lastSyncRevision = prefs.getLong(KEY_REVISION, 0L).coerceAtLeast(0L)
        )
    }

    fun saveIdentity(userId: String, displayName: String, email: String?) {
        require(userId.isNotBlank()) { "شناسه حساب NV خالی است" }
        prefs.edit()
            .putString(KEY_USER_ID, userId.trim())
            .putString(KEY_DISPLAY_NAME, displayName.trim().ifBlank { "کاربر NV" })
            .putString(KEY_EMAIL, email?.trim()?.takeIf { it.isNotBlank() })
            .apply()
    }

    fun updateSyncRevision(revision: Long) {
        require(revision >= 0L) { "نسخه همگام‌سازی منفی است" }
        prefs.edit().putLong(KEY_REVISION, revision).apply()
    }

    fun signOut() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS = "nv_account_session"
        const val KEY_USER_ID = "user_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_EMAIL = "email"
        const val KEY_REVISION = "sync_revision"
    }
}

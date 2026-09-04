package ir.nv.navigation.entitlement

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

class TrialManager(
    context: Context,
    private val clock: Clock = Clock.systemUTC()
) {
    sealed interface State {
        data class Trial(val daysRemaining: Long) : State
        data object Paid : State
        data object Expired : State
        data object Tampered : State
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun state(isPaid: Boolean = false): State {
        val now = clock.instant()
        val first = prefs.getLong(KEY_FIRST_USE, 0L)
        if (first == 0L) {
            if (isPaid) {
                persist(now.toEpochMilli(), now.toEpochMilli(), paid = true)
                return State.Paid
            }
            return createTrial(now)
        }

        val last = prefs.getLong(KEY_LAST_SEEN, 0L)
        val locallyPaid = prefs.getBoolean(KEY_PAID, false)
        val signature = prefs.getString(KEY_SIGNATURE, null) ?: return State.Tampered
        if (!verify(first, last, locallyPaid, signature)) return State.Tampered
        if (now.toEpochMilli() + CLOCK_ROLLBACK_TOLERANCE_MS < last) return State.Tampered
        if (isPaid || locallyPaid) {
            persist(first, now.toEpochMilli(), paid = true)
            return State.Paid
        }

        val elapsed = Duration.between(Instant.ofEpochMilli(first), now)
        if (elapsed >= TRIAL_DURATION) return State.Expired
        val daysRemaining = TRIAL_DURATION.minus(elapsed).toDays().coerceAtLeast(0) + 1
        persist(first, now.toEpochMilli(), paid = false)
        return State.Trial(daysRemaining)
    }

    private fun createTrial(now: Instant): State {
        persist(now.toEpochMilli(), now.toEpochMilli(), paid = false)
        return State.Trial(TRIAL_DURATION.toDays())
    }

    private fun persist(first: Long, last: Long, paid: Boolean) {
        prefs.edit()
            .putLong(KEY_FIRST_USE, first)
            .putLong(KEY_LAST_SEEN, last)
            .putBoolean(KEY_PAID, paid)
            .putString(KEY_SIGNATURE, sign(first, last, paid))
            .apply()
    }

    private fun sign(first: Long, last: Long, paid: Boolean): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(secretKey())
        val result = mac.doFinal("$first:$last:$paid".toByteArray())
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    private fun verify(first: Long, last: Long, paid: Boolean, signature: String): Boolean =
        runCatching {
            val expected = Base64.decode(sign(first, last, paid), Base64.NO_WRAP)
            val actual = Base64.decode(signature, Base64.NO_WRAP)
            java.security.MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(ALGORITHM, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).setDigests(KeyProperties.DIGEST_SHA256).build()
            )
            generateKey()
        }
    }

    private companion object {
        val TRIAL_DURATION: Duration = Duration.ofDays(30)
        const val CLOCK_ROLLBACK_TOLERANCE_MS = 6 * 60 * 60 * 1000L
        const val PREFS_NAME = "nv_entitlement"
        const val KEY_FIRST_USE = "first_use"
        const val KEY_LAST_SEEN = "last_seen"
        const val KEY_PAID = "paid"
        const val KEY_SIGNATURE = "signature"
        const val KEY_ALIAS = "nv_trial_hmac_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val ALGORITHM = KeyProperties.KEY_ALGORITHM_HMAC_SHA256
    }
}

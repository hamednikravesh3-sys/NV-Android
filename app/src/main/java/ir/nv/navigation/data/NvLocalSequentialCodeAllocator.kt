package ir.nv.navigation.data

import android.content.Context

class NvLocalSequentialCodeAllocator(context: Context) {
    private val prefs = context.getSharedPreferences("nv_local_code_counter", Context.MODE_PRIVATE)

    @Synchronized
    fun nextCode(existingCodes: Collection<String>): String {
        val maxExisting = existingCodes.mapNotNull { it.toLongOrNull() }.maxOrNull() ?: 0L
        val stored = prefs.getLong(KEY_LAST, 0L)
        val next = maxOf(maxExisting, stored) + 1L
        prefs.edit().putLong(KEY_LAST, next).apply()
        return next.toString()
    }

    private companion object {
        const val KEY_LAST = "last_code"
    }
}

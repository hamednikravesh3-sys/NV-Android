package ir.nv.navigation.data

import android.content.Context

/**
 * Legacy compatibility shim for older preview UI files.
 *
 * NV Code allocation is intentionally server-only. This class never allocates a
 * local/offline code; any legacy caller is forced to fail instead of creating a
 * duplicate or non-authoritative identifier.
 */
@Deprecated(
    message = "NV Code allocation is online-only; use NvCodeAllocationService",
    replaceWith = ReplaceWith("NvCodeAllocationService()")
)
class NvLocalSequentialCodeAllocator(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun nextCode(@Suppress("UNUSED_PARAMETER") existingCodes: Collection<String>): String {
        throw IllegalStateException("کد NV فقط هنگام اتصال به Registry مرکزی و به‌صورت آنلاین تعریف می‌شود")
    }
}

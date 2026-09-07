package ir.nv.navigation.data

import android.content.Context

/**
 * Legacy compatibility shim for preview UI code that still references the old allocator.
 *
 * NV Code allocation is server-only. This shim intentionally never creates a local code;
 * it only keeps older source files compilable until those references are fully removed.
 */
@Deprecated(
    message = "NV Code allocation is online-only; use NvCodeAllocationService",
    replaceWith = ReplaceWith("NvCodeAllocationService()")
)
class NvLocalSequentialCodeAllocator(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun nextCode(@Suppress("UNUSED_PARAMETER") existingCodes: Collection<String>): String {
        throw IllegalStateException("کد NV فقط هنگام اتصال آنلاین به Registry مرکزی تعریف می‌شود")
    }
}

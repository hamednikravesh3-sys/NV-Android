package ir.nv.navigation.data

import android.content.Context

/**
 * Compatibility shim for legacy reference screens.
 *
 * NV Code allocation is intentionally online-only. This type remains solely so
 * older UI references still compile; it never allocates or persists a local
 * code. Any accidental call fails closed instead of producing a duplicate or
 * device-local NV Code.
 */
@Deprecated(
    message = "NV Code allocation is online-only; use NvCodeAllocationService.allocateOnline().",
    level = DeprecationLevel.WARNING
)
class NvLocalSequentialCodeAllocator(
    @Suppress("UNUSED_PARAMETER") context: Context
) {
    fun nextCode(@Suppress("UNUSED_PARAMETER") usedCodes: Collection<Long>): Long {
        throw IllegalStateException("ساخت کد NV فقط در حالت آنلاین و از Registry مرکزی مجاز است")
    }
}

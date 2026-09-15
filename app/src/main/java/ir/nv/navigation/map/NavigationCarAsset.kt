package ir.nv.navigation.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/**
 * Embedded, downsampled copy of the user-supplied NavigationCarFeature vehicle asset.
 * Keeping it as a compact text-backed asset lets the Android build stay self-contained
 * while preserving the transparent top-view artwork.
 */
internal object NavigationCarAsset {
    fun bitmap(): Bitmap? = runCatching {
        val bytes = Base64.decode(PNG_BASE64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    private const val PNG_BASE64: String =
        "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"
}

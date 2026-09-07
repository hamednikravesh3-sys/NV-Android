package ir.nv.navigation.data

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import ir.nv.navigation.core.Coordinate
import java.net.URLDecoder

/** Decodes NV QR images and validates their payload before navigation. */
object NvQrScanner {
    data class ScannedLocation(
        val code: String,
        val coordinate: Coordinate?,
        val name: String?,
        val rawPayload: String
    )

    private val compactPattern = Regex("^NV:([0-9]+)$", RegexOption.IGNORE_CASE)
    private val locationPattern = Regex("^nv://place/([0-9]+)(?:\\?(.*))?$", RegexOption.IGNORE_CASE)

    fun decode(bitmap: Bitmap): Result<ScannedLocation> = runCatching {
        val width = bitmap.width
        val height = bitmap.height
        require(width > 0 && height > 0) { "تصویر QR نامعتبر است" }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)))
        parse(result.text)
    }

    fun parse(payload: String): ScannedLocation {
        val raw = payload.trim()
        require(raw.isNotEmpty()) { "QR خالی است" }

        compactPattern.matchEntire(raw)?.let { match ->
            return ScannedLocation(match.groupValues[1], null, null, raw)
        }

        val match = locationPattern.matchEntire(raw)
            ?: throw IllegalArgumentException("این QR متعلق به NV نیست")
        val code = match.groupValues[1]
        val query = match.groupValues.getOrElse(2) { "" }
            .split('&')
            .filter { it.isNotBlank() }
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) null
                else part.substring(0, separator) to part.substring(separator + 1)
            }
            .toMap()

        val lat = query["lat"]?.toDoubleOrNull()
        val lon = query["lon"]?.toDoubleOrNull()
        val coordinate = if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
            Coordinate(lat, lon)
        } else null
        val name = query["name"]
            ?.takeIf { it.isNotBlank() }
            ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }

        return ScannedLocation(code, coordinate, name, raw)
    }
}

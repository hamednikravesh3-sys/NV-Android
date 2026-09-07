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

        if (raw.startsWith("NV:", ignoreCase = true)) {
            val code = raw.substringAfter(':').filter(Char::isDigit)
            require(code.isNotBlank()) { "کد NV داخل QR معتبر نیست" }
            return ScannedLocation(code, null, null, raw)
        }

        require(raw.startsWith("nv://place/", ignoreCase = true)) { "این QR متعلق به NV نیست" }
        val codePart = raw.substringAfter("nv://place/").substringBefore('?')
        val code = codePart.filter(Char::isDigit)
        require(code.isNotBlank()) { "کد NV داخل QR معتبر نیست" }

        val query = raw.substringAfter('?', "")
            .split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', "")
                if (key.isBlank()) null else key to part.substringAfter('=', "")
            }
            .toMap()

        val lat = query["lat"]?.toDoubleOrNull()
        val lon = query["lon"]?.toDoubleOrNull()
        val coordinate = if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
            Coordinate(lat, lon)
        } else null
        val name = query["name"]
            ?.takeIf { it.isNotBlank() }
            ?.let { URLDecoder.decode(it, "UTF-8") }

        return ScannedLocation(code, coordinate, name, raw)
    }
}

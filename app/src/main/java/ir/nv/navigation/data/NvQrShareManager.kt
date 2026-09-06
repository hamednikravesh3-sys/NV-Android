package ir.nv.navigation.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import ir.nv.navigation.core.Coordinate
import java.io.File
import java.io.FileOutputStream

class NvQrShareManager(private val context: Context) {
    data class SavedQr(
        val file: File,
        val payload: String,
        val code: String,
        val name: String,
        val coordinate: Coordinate
    )

    fun createAndSave(code: String, name: String, coordinate: Coordinate): Result<SavedQr> = runCatching {
        val safeCode = code.filter(Char::isDigit).ifBlank { error("کد NV نامعتبر است") }
        val cleanName = name.trim().ifBlank { "NV Place $safeCode" }
        val payload = buildPayload(safeCode, cleanName, coordinate)
        val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 720, 720)
        val bitmap = matrix.toBitmap()
        val dir = File(context.filesDir, "nv_qr").apply { mkdirs() }
        val file = File(dir, "NV-$safeCode.png")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "ذخیره QR ناموفق بود" }
        }
        SavedQr(file, payload, safeCode, cleanName, coordinate)
    }

    fun share(saved: SavedQr) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            saved.file
        )
        val text = buildString {
            append("NV Code: ").append(saved.code).append('\n')
            append(saved.name).append('\n')
            append("Location: ")
                .append("%.6f".format(saved.coordinate.latitude))
                .append(", ")
                .append("%.6f".format(saved.coordinate.longitude))
            append('\n').append("geo:")
                .append(saved.coordinate.latitude)
                .append(',')
                .append(saved.coordinate.longitude)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری کد NV"))
    }

    fun existing(code: String): SavedQr? {
        val safeCode = code.filter(Char::isDigit)
        if (safeCode.isBlank()) return null
        val file = File(File(context.filesDir, "nv_qr"), "NV-$safeCode.png")
        return file.takeIf(File::exists)?.let {
            SavedQr(it, "", safeCode, "NV Place $safeCode", Coordinate(0.0, 0.0))
        }
    }

    private fun buildPayload(code: String, name: String, coordinate: Coordinate): String =
        "nv://place/$code?lat=${coordinate.latitude}&lon=${coordinate.longitude}&name=${java.net.URLEncoder.encode(name, "UTF-8")}" 

    private fun BitMatrix.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}

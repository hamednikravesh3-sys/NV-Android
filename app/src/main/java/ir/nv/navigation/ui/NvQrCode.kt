package ir.nv.navigation.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun NvQrCode(value: String, modifier: Modifier = Modifier) {
    val bitmap = remember(value) {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 360, 360)
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }
    }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR کد مکان", modifier = modifier)
}

package ir.nv.navigation.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import org.mapsforge.map.android.view.MapView

internal fun MapView.applyNightDisplay(enabled: Boolean) {
    if (!enabled) {
        setLayerType(View.LAYER_TYPE_NONE, null)
        return
    }
    val nightMatrix = ColorMatrix(
        floatArrayOf(
            -0.62f, 0f, 0f, 0f, 186f,
            0f, -0.62f, 0f, 0f, 190f,
            0f, 0f, -0.58f, 0f, 198f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val paint = Paint().apply {
        colorFilter = ColorMatrixColorFilter(nightMatrix)
    }
    setLayerType(View.LAYER_TYPE_HARDWARE, paint)
}

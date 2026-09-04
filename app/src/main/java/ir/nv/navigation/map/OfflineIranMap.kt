package ir.nv.navigation.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ir.nv.navigation.core.Route
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.File

@Composable
fun OfflineIranMap(
    context: Context,
    mapFile: File,
    route: Route?,
    modifier: Modifier = Modifier
) {
    val mapView = remember(mapFile.absolutePath) {
        val provider = OfflineTileProvider(
            SimpleRegisterReceiver(context),
            arrayOf(mapFile)
        )
        MapView(context, provider).apply {
            setUseDataConnection(false)
            setMultiTouchControls(true)
            setScrollableAreaLimitDouble(IRAN_BOUNDS)
            minZoomLevel = 4.0
            maxZoomLevel = 18.0
            controller.setZoom(5.5)
            controller.setCenter(GeoPoint(32.4279, 53.6880))
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.overlays.removeAll { it is Polyline }
            route?.takeIf { it.points.size >= 2 }?.let { result ->
                view.overlays += Polyline().apply {
                    outlinePaint.color = android.graphics.Color.rgb(0, 166, 126)
                    outlinePaint.strokeWidth = 12f
                    setPoints(result.points.map { GeoPoint(it.latitude, it.longitude) })
                }
            }
            view.invalidate()
        }
    )
}

private val IRAN_BOUNDS = BoundingBox(
    39.90,
    63.35,
    24.80,
    44.00
)

package ir.nv.navigation.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ir.nv.navigation.core.Route
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.MapPosition
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.download.TileDownloadLayer
import org.mapsforge.map.layer.download.tilesource.OpenStreetMapMapnik
import org.mapsforge.map.layer.overlay.Polyline

@Composable
fun OnlineIranMap(
    context: Context,
    route: Route?,
    modifier: Modifier = Modifier
) {
    val holder = remember { OnlineMapHolder(context) }

    DisposableEffect(holder) {
        onDispose { holder.destroy() }
    }

    AndroidView(
        factory = { holder.mapView },
        modifier = modifier,
        update = { holder.showRoute(route) }
    )
}

private class OnlineMapHolder(context: Context) {
    val mapView = MapView(context)
    private val tileCache: TileCache
    private val downloadLayer: TileDownloadLayer
    private var routeLayer: Polyline? = null
    private var renderedRoute: Route? = null

    init {
        mapView.setBuiltInZoomControls(false)
        mapView.mapScaleBar.isVisible = false
        mapView.model.mapViewPosition.zoomLevelMin = 4
        mapView.model.mapViewPosition.zoomLevelMax = 18
        mapView.model.mapViewPosition.mapPosition = MapPosition(IRAN_CENTER, 5)

        tileCache = AndroidUtil.createTileCache(
            context,
            "nv-online-map-v1",
            mapView.model.displayModel.tileSize,
            1f,
            mapView.model.frameBufferModel.overdrawFactor
        )
        OpenStreetMapMapnik.INSTANCE.setUserAgent("NV-Android/0.3 (hamednikravesh3@gmail.com)")
        downloadLayer = TileDownloadLayer(
            tileCache,
            mapView.model.mapViewPosition,
            OpenStreetMapMapnik.INSTANCE,
            AndroidGraphicFactory.INSTANCE
        )
        mapView.layerManager.layers.add(downloadLayer)
        downloadLayer.start()
    }

    fun showRoute(route: Route?) {
        if (renderedRoute === route) return
        renderedRoute = route
        routeLayer?.let { mapView.layerManager.layers.remove(it) }
        routeLayer = route?.takeIf { it.points.size >= 2 }?.let { result ->
            val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                setColor(AndroidGraphicFactory.INSTANCE.createColor(255, 20, 184, 166))
                setStrokeWidth(11f * mapView.model.displayModel.scaleFactor)
                setStyle(Style.STROKE)
            }
            Polyline(paint, AndroidGraphicFactory.INSTANCE).also { line ->
                line.setPoints(result.points.map { LatLong(it.latitude, it.longitude) })
                mapView.layerManager.layers.add(line)
                val center = result.points[result.points.lastIndex / 2]
                mapView.model.mapViewPosition.mapPosition = MapPosition(
                    LatLong(center.latitude, center.longitude),
                    routeZoom(result.distanceMeters)
                )
            }
        }
        mapView.layerManager.redrawLayers()
    }

    fun destroy() {
        mapView.destroyAll()
    }

    private fun routeZoom(distanceMeters: Double): Byte = when {
        distanceMeters < 4_000 -> 14
        distanceMeters < 15_000 -> 12
        distanceMeters < 60_000 -> 10
        distanceMeters < 250_000 -> 8
        else -> 6
    }

    private companion object {
        val IRAN_CENTER = LatLong(32.4279, 53.6880)
    }
}

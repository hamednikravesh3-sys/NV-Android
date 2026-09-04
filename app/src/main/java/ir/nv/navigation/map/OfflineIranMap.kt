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
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import java.io.File

@Composable
fun OfflineIranMap(
    context: Context,
    mapFile: File,
    route: Route?,
    modifier: Modifier = Modifier
) {
    val holder = remember(mapFile.absolutePath) { MapsforgeMapHolder(context, mapFile) }

    DisposableEffect(holder) {
        onDispose {
            holder.destroy()
        }
    }

    AndroidView(
        factory = { holder.mapView },
        modifier = modifier,
        update = { holder.showRoute(route) }
    )
}

private class MapsforgeMapHolder(context: Context, mapFile: File) {
    val mapView = MapView(context)
    private val mapData = MapFile(mapFile)
    private val tileCache: TileCache
    private var routeLayer: Polyline? = null

    init {
        mapView.setBuiltInZoomControls(false)
        mapView.mapScaleBar.isVisible = true
        mapView.model.mapViewPosition.zoomLevelMin = 4
        mapView.model.mapViewPosition.zoomLevelMax = 20
        mapView.model.mapViewPosition.mapPosition = MapPosition(IRAN_CENTER, 5)

        tileCache = AndroidUtil.createTileCache(
            context,
            "nv-iran-vector-v2",
            mapView.model.displayModel.tileSize,
            1f,
            mapView.model.frameBufferModel.overdrawFactor
        )
        val renderer = AndroidUtil.createTileRendererLayer(
            tileCache,
            mapView.model.mapViewPosition,
            mapData,
            MapsforgeThemes.MOTORIDER,
            false,
            true,
            false
        )
        mapView.layerManager.layers.add(renderer)
    }

    fun showRoute(route: Route?) {
        routeLayer?.let { mapView.layerManager.layers.remove(it) }
        routeLayer = route?.takeIf { it.points.size >= 2 }?.let { result ->
            val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                color = AndroidGraphicFactory.INSTANCE.createColor(255, 0, 166, 126)
                strokeWidth = 10f * mapView.model.displayModel.scaleFactor
                setStyle(Style.STROKE)
            }
            Polyline(paint, AndroidGraphicFactory.INSTANCE).also { line ->
                line.setPoints(result.points.map { LatLong(it.latitude, it.longitude) })
                mapView.layerManager.layers.add(line)
            }
        }
        mapView.layerManager.redrawLayers()
    }

    fun destroy() {
        mapView.destroyAll()
        mapData.close()
    }

    private companion object {
        val IRAN_CENTER = LatLong(32.4279, 53.6880)
    }
}

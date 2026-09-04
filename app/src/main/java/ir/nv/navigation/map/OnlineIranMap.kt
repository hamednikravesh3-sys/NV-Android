package ir.nv.navigation.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficSummary
import ir.nv.navigation.core.Coordinate
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
import org.mapsforge.map.layer.overlay.Circle

@Composable
fun OnlineIranMap(
    context: Context,
    routes: List<Route>,
    selectedRouteIndex: Int,
    traffic: TrafficSummary?,
    currentLocation: Coordinate?,
    followLocation: Boolean,
    navigationActive: Boolean,
    darkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val holder = remember { OnlineMapHolder(context) }

    DisposableEffect(holder) {
        onDispose { holder.destroy() }
    }

    AndroidView(
        factory = { holder.mapView },
        modifier = modifier,
        update = {
            holder.setDarkMode(darkMode)
            holder.showRoutes(routes, selectedRouteIndex, traffic)
            holder.showLocation(currentLocation, followLocation, navigationActive)
        }
    )
}

private class OnlineMapHolder(context: Context) {
    val mapView = MapView(context)
    private val tileCache: TileCache
    private val downloadLayer: TileDownloadLayer
    private val routeLayers = mutableListOf<Polyline>()
    private var locationLayer: Circle? = null
    private var renderedRoutes: List<Route> = emptyList()
    private var renderedSelectedRoute = -1
    private var renderedTraffic: TrafficSummary? = null
    private var darkMode: Boolean? = null

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
        OpenStreetMapMapnik.INSTANCE.setUserAgent("NV-Android/0.8 (hamednikravesh3@gmail.com)")
        downloadLayer = TileDownloadLayer(
            tileCache,
            mapView.model.mapViewPosition,
            OpenStreetMapMapnik.INSTANCE,
            AndroidGraphicFactory.INSTANCE
        )
        mapView.layerManager.layers.add(downloadLayer)
        downloadLayer.start()
    }

    fun showRoutes(routes: List<Route>, selectedRouteIndex: Int, traffic: TrafficSummary?) {
        if (renderedRoutes == routes && renderedSelectedRoute == selectedRouteIndex && renderedTraffic == traffic) return
        renderedRoutes = routes
        renderedSelectedRoute = selectedRouteIndex
        renderedTraffic = traffic
        routeLayers.forEach { mapView.layerManager.layers.remove(it) }
        routeLayers.clear()
        val ordered = routes.indices.sortedBy { if (it == selectedRouteIndex) 1 else 0 }
        ordered.forEach { index ->
            val result = routes[index]
            if (result.points.size < 2) return@forEach
            val color = when {
                // The selected route must remain visually stable. Traffic severity is
                // shown by the dedicated traffic rail until segment geometry exists.
                index == selectedRouteIndex -> intArrayOf(24, 212, 255)
                index % 2 == 0 -> intArrayOf(215, 255, 91)
                else -> intArrayOf(150, 160, 174)
            }
            if (index == selectedRouteIndex) {
                val glowPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                    setColor(AndroidGraphicFactory.INSTANCE.createColor(82, color[0], color[1], color[2]))
                    setStrokeWidth(24f * mapView.model.displayModel.scaleFactor)
                    setStyle(Style.STROKE)
                }
                Polyline(glowPaint, AndroidGraphicFactory.INSTANCE).also { glow ->
                    glow.setPoints(result.points.map { LatLong(it.latitude, it.longitude) })
                    mapView.layerManager.layers.add(glow)
                    routeLayers += glow
                }
            }
            val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                setColor(AndroidGraphicFactory.INSTANCE.createColor(255, color[0], color[1], color[2]))
                setStrokeWidth((if (index == selectedRouteIndex) 11f else 7f) * mapView.model.displayModel.scaleFactor)
                setStyle(Style.STROKE)
            }
            Polyline(paint, AndroidGraphicFactory.INSTANCE).also { line ->
                line.setPoints(result.points.map { LatLong(it.latitude, it.longitude) })
                mapView.layerManager.layers.add(line)
                routeLayers += line
            }
        }
        routes.getOrNull(selectedRouteIndex)?.let { selected ->
            val center = selected.points[selected.points.lastIndex / 2]
            mapView.model.mapViewPosition.mapPosition = MapPosition(LatLong(center.latitude, center.longitude), routeZoom(selected.distanceMeters))
        }
        locationLayer?.let { marker ->
            mapView.layerManager.layers.remove(marker)
            mapView.layerManager.layers.add(marker)
        }
        mapView.layerManager.redrawLayers()
    }

    fun setDarkMode(enabled: Boolean) {
        if (darkMode == enabled) return
        darkMode = enabled
        mapView.applyNightDisplay(enabled)
    }

    fun showLocation(location: Coordinate?, follow: Boolean, navigationActive: Boolean) {
        if (location == null) return
        val point = LatLong(location.latitude, location.longitude)
        val marker = locationLayer ?: createLocationMarker(point).also {
            locationLayer = it
            mapView.layerManager.layers.add(it)
        }
        marker.setLatLong(point)
        if (follow) {
            mapView.model.mapViewPosition.mapPosition = MapPosition(
                point,
                if (navigationActive) NAVIGATION_ZOOM else BROWSE_LOCATION_ZOOM
            )
        }
        mapView.layerManager.redrawLayers()
    }

    private fun createLocationMarker(point: LatLong): Circle {
        val fill = AndroidGraphicFactory.INSTANCE.createPaint().apply {
            setColor(AndroidGraphicFactory.INSTANCE.createColor(255, 24, 212, 255))
            setStyle(Style.FILL)
        }
        val stroke = AndroidGraphicFactory.INSTANCE.createPaint().apply {
            setColor(AndroidGraphicFactory.INSTANCE.createColor(255, 255, 255, 255))
            setStrokeWidth(5f * mapView.model.displayModel.scaleFactor)
            setStyle(Style.STROKE)
        }
        return Circle(point, 15f, fill, stroke)
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
        const val BROWSE_LOCATION_ZOOM: Byte = 16
        const val NAVIGATION_ZOOM: Byte = 18
    }
}

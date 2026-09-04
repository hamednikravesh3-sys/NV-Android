package ir.nv.navigation.map

import android.content.Context
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficSegment
import ir.nv.navigation.core.TrafficSummary
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.MapPosition
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.download.TileDownloadLayer
import org.mapsforge.map.layer.download.tilesource.OpenStreetMapMapnik
import org.mapsforge.map.layer.overlay.Circle
import org.mapsforge.map.layer.overlay.Polyline
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

@Composable
fun OnlineIranMap(
    context: Context,
    routes: List<Route>,
    selectedRouteIndex: Int,
    traffic: TrafficSummary?,
    trafficSegments: List<TrafficSegment>,
    currentLocation: Coordinate?,
    followLocation: Boolean,
    navigationActive: Boolean,
    navigationZoomLevel: Int,
    navigationRecenterToken: Int,
    onManualGesture: () -> Unit,
    darkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val holder = remember { OnlineMapHolder(context) }
    DisposableEffect(holder) { onDispose { holder.destroy() } }

    AndroidView(
        factory = { holder.mapView },
        modifier = modifier,
        update = {
            holder.installGestureControls(navigationActive, onManualGesture)
            holder.setDrivingPerspective(navigationActive)
            holder.setDarkMode(darkMode)
            holder.showRoutes(routes, selectedRouteIndex, traffic, trafficSegments, navigationActive)
            holder.showLocation(currentLocation, followLocation, navigationActive, navigationZoomLevel, navigationRecenterToken)
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
    private var renderedTrafficSegments: List<TrafficSegment> = emptyList()
    private var darkMode: Boolean? = null
    private var lastRecenterToken = 0
    private var mapBearing = 0f
    private var gestureStartAngle = 0f
    private var gestureStartBearing = 0f
    private var gestureStartAverageY = 0f
    private var gestureStartPitch = 0f
    private var mapPitch = 0f

    init {
        mapView.setBuiltInZoomControls(false)
        mapView.mapScaleBar.isVisible = false
        mapView.model.mapViewPosition.zoomLevelMin = 4
        mapView.model.mapViewPosition.zoomLevelMax = 19
        mapView.model.mapViewPosition.mapPosition = MapPosition(IRAN_CENTER, 5)
        mapView.cameraDistance = 12_000f
        mapView.pivotX = mapView.width / 2f
        mapView.pivotY = mapView.height * 0.72f

        tileCache = AndroidUtil.createTileCache(context, "nv-online-map-v2", mapView.model.displayModel.tileSize, 1f, mapView.model.frameBufferModel.overdrawFactor)
        OpenStreetMapMapnik.INSTANCE.setUserAgent("NV-Android/0.12")
        downloadLayer = TileDownloadLayer(tileCache, mapView.model.mapViewPosition, OpenStreetMapMapnik.INSTANCE, AndroidGraphicFactory.INSTANCE)
        mapView.layerManager.layers.add(downloadLayer)
        downloadLayer.start()
    }

    fun installGestureControls(navigationActive: Boolean, onManualGesture: () -> Unit) {
        mapView.setOnTouchListener { view, event ->
            if (event.pointerCount >= 2) {
                val angle = pointerAngle(event)
                val averageY = (event.getY(0) + event.getY(1)) / 2f
                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        gestureStartAngle = angle
                        gestureStartBearing = mapBearing
                        gestureStartAverageY = averageY
                        gestureStartPitch = mapPitch
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val delta = normalizeAngle(angle - gestureStartAngle)
                        mapBearing = normalizeAngle(gestureStartBearing + delta)
                        val pitchDelta = (gestureStartAverageY - averageY) / max(1f, view.height.toFloat()) * 85f
                        mapPitch = (gestureStartPitch + pitchDelta).coerceIn(0f, 48f)
                        applyTransform()
                        onManualGesture()
                        return@setOnTouchListener true
                    }
                }
            } else if (event.actionMasked == MotionEvent.ACTION_MOVE && navigationActive) {
                onManualGesture()
            }
            false
        }
    }

    fun setDrivingPerspective(active: Boolean) {
        if (active && mapPitch < 28f) mapPitch = 38f
        if (!active && mapPitch > 0f) mapPitch = 0f
        applyTransform()
    }

    private fun applyTransform() {
        mapView.rotation = mapBearing
        mapView.rotationX = mapPitch
        mapView.scaleX = if (mapPitch > 0f) 1.10f else 1f
        mapView.scaleY = if (mapPitch > 0f) 1.22f else 1f
        mapView.pivotX = mapView.width / 2f
        mapView.pivotY = mapView.height * 0.74f
    }

    fun showRoutes(routes: List<Route>, selectedRouteIndex: Int, traffic: TrafficSummary?, trafficSegments: List<TrafficSegment>, navigationActive: Boolean) {
        if (renderedRoutes == routes && renderedSelectedRoute == selectedRouteIndex && renderedTraffic == traffic && renderedTrafficSegments == trafficSegments) return
        renderedRoutes = routes
        renderedSelectedRoute = selectedRouteIndex
        renderedTraffic = traffic
        renderedTrafficSegments = trafficSegments
        routeLayers.forEach { mapView.layerManager.layers.remove(it) }
        routeLayers.clear()

        routes.indices.sortedBy { if (it == selectedRouteIndex) 1 else 0 }.forEach { index ->
            val route = routes[index]
            if (route.points.size < 2) return@forEach
            val color = when {
                index == selectedRouteIndex -> intArrayOf(24, 212, 255)
                index % 3 == 0 -> intArrayOf(215, 255, 91)
                index % 3 == 1 -> intArrayOf(255, 181, 46)
                else -> intArrayOf(160, 170, 185)
            }
            if (index == selectedRouteIndex) {
                addRouteLine(route, intArrayOf(3, 20, 33), 30f, 160)
                addRouteLine(route, color, 22f, 100)
            }
            addRouteLine(route, color, if (index == selectedRouteIndex) 10f else 7f, 255)
        }

        trafficSegments.forEach { segment ->
            if (segment.start == segment.end) return@forEach
            val color = when {
                segment.delaySeconds >= 600.0 -> intArrayOf(230, 64, 69)
                segment.delaySeconds >= 120.0 -> intArrayOf(255, 181, 46)
                else -> intArrayOf(100, 214, 109)
            }
            val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                setColor(AndroidGraphicFactory.INSTANCE.createColor(255, color[0], color[1], color[2]))
                setStrokeWidth(9f * mapView.model.displayModel.scaleFactor)
                setStyle(Style.STROKE)
            }
            Polyline(paint, AndroidGraphicFactory.INSTANCE).also { line ->
                line.setPoints(listOf(LatLong(segment.start.latitude, segment.start.longitude), LatLong(segment.end.latitude, segment.end.longitude)))
                mapView.layerManager.layers.add(line)
                routeLayers += line
            }
        }

        if (!navigationActive) showAllRoutesOverview(routes)
        locationLayer?.let { marker -> mapView.layerManager.layers.remove(marker); mapView.layerManager.layers.add(marker) }
        mapView.layerManager.redrawLayers()
    }

    private fun addRouteLine(route: Route, color: IntArray, width: Float, alpha: Int) {
        val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
            setColor(AndroidGraphicFactory.INSTANCE.createColor(alpha, color[0], color[1], color[2]))
            setStrokeWidth(width * mapView.model.displayModel.scaleFactor)
            setStyle(Style.STROKE)
        }
        Polyline(paint, AndroidGraphicFactory.INSTANCE).also { line ->
            line.setPoints(route.points.map { LatLong(it.latitude, it.longitude) })
            mapView.layerManager.layers.add(line)
            routeLayers += line
        }
    }

    private fun showAllRoutesOverview(routes: List<Route>) {
        val points = routes.flatMap { it.points }
        if (points.isEmpty()) return
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val center = LatLong((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)
        val span = max(maxLat - minLat, maxLon - minLon)
        val zoom: Byte = when {
            span < 0.015 -> 16
            span < 0.03 -> 15
            span < 0.07 -> 14
            span < 0.15 -> 13
            span < 0.35 -> 12
            span < 0.7 -> 11
            span < 1.5 -> 10
            span < 3.0 -> 9
            span < 6.0 -> 8
            else -> 6
        }
        mapView.model.mapViewPosition.mapPosition = MapPosition(center, zoom)
    }

    fun setDarkMode(enabled: Boolean) {
        if (darkMode == enabled) return
        darkMode = enabled
        mapView.applyNightDisplay(enabled)
    }

    fun showLocation(location: Coordinate?, follow: Boolean, navigationActive: Boolean, navigationZoomLevel: Int, recenterToken: Int) {
        if (location == null) return
        val point = LatLong(location.latitude, location.longitude)
        val marker = locationLayer ?: createLocationMarker(point).also { locationLayer = it; mapView.layerManager.layers.add(it) }
        marker.setLatLong(point)
        if (follow || (navigationActive && recenterToken != lastRecenterToken)) {
            mapView.model.mapViewPosition.mapPosition = MapPosition(point, if (navigationActive) navigationZoomLevel.coerceIn(16, 19).toByte() else BROWSE_LOCATION_ZOOM)
            lastRecenterToken = recenterToken
        }
        mapView.layerManager.redrawLayers()
    }

    private fun createLocationMarker(point: LatLong): Circle {
        val fill = AndroidGraphicFactory.INSTANCE.createPaint().apply { setColor(AndroidGraphicFactory.INSTANCE.createColor(255, 24, 212, 255)); setStyle(Style.FILL) }
        val stroke = AndroidGraphicFactory.INSTANCE.createPaint().apply { setColor(AndroidGraphicFactory.INSTANCE.createColor(255, 255, 255, 255)); setStrokeWidth(5f * mapView.model.displayModel.scaleFactor); setStyle(Style.STROKE) }
        return Circle(point, 16f, fill, stroke)
    }

    private fun pointerAngle(event: MotionEvent): Float = Math.toDegrees(atan2((event.getY(1) - event.getY(0)).toDouble(), (event.getX(1) - event.getX(0)).toDouble())).toFloat()
    private fun normalizeAngle(value: Float): Float {
        var result = value
        while (result > 180f) result -= 360f
        while (result < -180f) result += 360f
        return result
    }

    fun destroy() { downloadLayer.stop(); mapView.destroyAll() }

    private companion object {
        val IRAN_CENTER = LatLong(32.4279, 53.6880)
        const val BROWSE_LOCATION_ZOOM: Byte = 16
    }
}

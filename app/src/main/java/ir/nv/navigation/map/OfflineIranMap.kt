package ir.nv.navigation.map

import android.content.Context
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficSummary
import ir.nv.navigation.core.TrafficSegment
import ir.nv.navigation.core.Coordinate
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.MapPosition
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.overlay.Circle
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import java.io.File
import kotlin.math.atan2

@Composable
fun OfflineIranMap(
    context: Context,
    mapFile: File,
    routes: List<Route>,
    selectedRouteIndex: Int,
    traffic: TrafficSummary?,
    trafficSegments: List<TrafficSegment>,
    currentLocation: Coordinate?,
    followLocation: Boolean,
    navigationActive: Boolean,
    navigationZoomLevel: Int,
    navigationRecenterToken: Int,
    bearingDegrees: Float,
    onManualGesture: () -> Unit,
    darkMode: Boolean,
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
        update = {
            it.setOnTouchListener { _, event ->
                holder.handleRotationGesture(event)
                if (navigationActive && event.actionMasked == MotionEvent.ACTION_MOVE) {
                    onManualGesture()
                }
                false
            }
            holder.setDarkMode(darkMode)
            holder.setPerspective(navigationActive)
            holder.showRoutes(routes, selectedRouteIndex, traffic, trafficSegments)
            holder.showLocation(
                currentLocation,
                followLocation,
                navigationActive,
                navigationZoomLevel,
                navigationRecenterToken,
                bearingDegrees
            )
        }
    )
}

private class MapsforgeMapHolder(context: Context, mapFile: File) {
    val mapView = MapView(context)
    private val mapData = MapFile(mapFile)
    private val tileCache: TileCache
    private val routeLayers = mutableListOf<Polyline>()
    private var locationLayer: Circle? = null
    private var renderedRoutes: List<Route> = emptyList()
    private var renderedSelectedRoute = -1
    private var renderedTraffic: TrafficSummary? = null
    private var renderedTrafficSegments: List<TrafficSegment> = emptyList()
    private var darkMode: Boolean? = null
    private var lastRecenterToken = 0
    private var lastGestureAngle: Float? = null

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

    fun showRoutes(
        routes: List<Route>,
        selectedRouteIndex: Int,
        traffic: TrafficSummary?,
        trafficSegments: List<TrafficSegment>
    ) {
        if (renderedRoutes == routes && renderedSelectedRoute == selectedRouteIndex &&
            renderedTraffic == traffic && renderedTrafficSegments == trafficSegments
        ) return
        renderedRoutes = routes
        renderedSelectedRoute = selectedRouteIndex
        renderedTraffic = traffic
        renderedTrafficSegments = trafficSegments
        routeLayers.forEach { mapView.layerManager.layers.remove(it) }
        routeLayers.clear()
        val ordered = routes.indices.sortedBy { if (it == selectedRouteIndex) 1 else 0 }
        ordered.forEach { index ->
            val result = routes[index]
            if (result.points.size < 2) return@forEach
            val routeColor = when {
                // Keep the active route cyan; the traffic rail carries delay severity.
                index == selectedRouteIndex -> intArrayOf(24, 212, 255)
                index % 2 == 0 -> intArrayOf(215, 255, 91)
                else -> intArrayOf(150, 160, 174)
            }
            if (index == selectedRouteIndex) {
                val glowPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                    color = AndroidGraphicFactory.INSTANCE.createColor(82, routeColor[0], routeColor[1], routeColor[2])
                    strokeWidth = 24f * mapView.model.displayModel.scaleFactor
                    setStyle(Style.STROKE)
                }
                Polyline(glowPaint, AndroidGraphicFactory.INSTANCE).also { glow ->
                    glow.setPoints(result.points.map { LatLong(it.latitude, it.longitude) })
                    mapView.layerManager.layers.add(glow)
                    routeLayers += glow
                }
            }
            val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                color = AndroidGraphicFactory.INSTANCE.createColor(255, routeColor[0], routeColor[1], routeColor[2])
                strokeWidth = (if (index == selectedRouteIndex) 11f else 7f) * mapView.model.displayModel.scaleFactor
                setStyle(Style.STROKE)
            }
            Polyline(paint, AndroidGraphicFactory.INSTANCE).also { line ->
                line.setPoints(result.points.map { LatLong(it.latitude, it.longitude) })
                mapView.layerManager.layers.add(line)
                routeLayers += line
            }
        }
        routes.getOrNull(selectedRouteIndex)?.takeIf {
            it.points.size >= 2 && it.maneuvers.firstOrNull()?.roadName == "اتصال مسیر خاکی"
        }?.let { route ->
            val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                color = AndroidGraphicFactory.INSTANCE.createColor(255, 255, 181, 46)
                strokeWidth = 9f * mapView.model.displayModel.scaleFactor
                setStyle(Style.STROKE)
            }
            Polyline(paint, AndroidGraphicFactory.INSTANCE).also { connector ->
                connector.setPoints(
                    route.points.take(2).map { LatLong(it.latitude, it.longitude) }
                )
                mapView.layerManager.layers.add(connector)
                routeLayers += connector
            }
        }
        trafficSegments.forEach { segment ->
            if (segment.start == segment.end) return@forEach
            val routeColor = when {
                segment.delaySeconds >= 600.0 -> intArrayOf(230, 64, 69)
                segment.delaySeconds >= 120.0 -> intArrayOf(255, 181, 46)
                else -> intArrayOf(100, 214, 109)
            }
            val paint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                color = AndroidGraphicFactory.INSTANCE.createColor(
                    255,
                    routeColor[0],
                    routeColor[1],
                    routeColor[2]
                )
                strokeWidth = 9f * mapView.model.displayModel.scaleFactor
                setStyle(Style.STROKE)
            }
            Polyline(paint, AndroidGraphicFactory.INSTANCE).also { line ->
                line.setPoints(
                    listOf(
                        LatLong(segment.start.latitude, segment.start.longitude),
                        LatLong(segment.end.latitude, segment.end.longitude)
                    )
                )
                mapView.layerManager.layers.add(line)
                routeLayers += line
            }
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

    fun showLocation(
        location: Coordinate?,
        follow: Boolean,
        navigationActive: Boolean,
        navigationZoomLevel: Int,
        recenterToken: Int,
        bearingDegrees: Float
    ) {
        if (location == null) return
        val point = LatLong(location.latitude, location.longitude)
        val marker = locationLayer ?: createLocationMarker(point).also {
            locationLayer = it
            mapView.layerManager.layers.add(it)
        }
        marker.setLatLong(point)
        if (follow || (navigationActive && recenterToken != lastRecenterToken)) {
            if (navigationActive && bearingDegrees.isFinite()) {
                mapView.rotation = -bearingDegrees
            }
            mapView.model.mapViewPosition.mapPosition = MapPosition(
                point,
                if (navigationActive) navigationZoomLevel.coerceIn(15, 19).toByte() else BROWSE_LOCATION_ZOOM
            )
            lastRecenterToken = recenterToken
        }
        mapView.layerManager.redrawLayers()
    }

    fun handleRotationGesture(event: MotionEvent) {
        if (event.pointerCount < 2 || event.actionMasked == MotionEvent.ACTION_POINTER_UP ||
            event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            lastGestureAngle = null
            return
        }
        val angle = Math.toDegrees(
            atan2(
                (event.getY(1) - event.getY(0)).toDouble(),
                (event.getX(1) - event.getX(0)).toDouble()
            )
        ).toFloat()
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            lastGestureAngle?.let { previous -> mapView.rotation += angle - previous }
        }
        lastGestureAngle = angle
    }

    fun setPerspective(navigationActive: Boolean) {
        mapView.pivotY = mapView.height.toFloat()
        mapView.rotationX = if (navigationActive) OFFLINE_NAVIGATION_TILT else 0f
        val scale = if (navigationActive || mapView.rotation != 0f) OFFLINE_ROTATION_SCALE else 1f
        mapView.scaleX = scale
        mapView.scaleY = scale
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
        mapData.close()
    }

    private companion object {
        val IRAN_CENTER = LatLong(32.4279, 53.6880)
        const val BROWSE_LOCATION_ZOOM: Byte = 16
        const val OFFLINE_NAVIGATION_TILT = 18f
        const val OFFLINE_ROTATION_SCALE = 1.32f
    }
}

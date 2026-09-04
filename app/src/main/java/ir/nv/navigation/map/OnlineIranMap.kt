package ir.nv.navigation.map

import android.content.Context
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficSegment
import ir.nv.navigation.core.TrafficSummary
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

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
    val holder = remember { MapLibreIranHolder(context.applicationContext, onManualGesture) }
    DisposableEffect(holder) { onDispose { holder.destroy() } }

    AndroidView(
        factory = { holder.mapView },
        modifier = modifier,
        update = {
            holder.update(
                routes = routes,
                selectedRouteIndex = selectedRouteIndex,
                trafficSegments = trafficSegments,
                currentLocation = currentLocation,
                followLocation = followLocation,
                navigationActive = navigationActive,
                navigationZoomLevel = navigationZoomLevel,
                navigationRecenterToken = navigationRecenterToken,
                darkMode = darkMode
            )
        }
    )
}

private class MapLibreIranHolder(
    context: Context,
    private val onManualGesture: () -> Unit
) {
    val mapView: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var currentDarkMode: Boolean? = null
    private var pending: RenderState = RenderState()
    private var lastOverviewKey = ""
    private var lastRecenterToken = -1
    private var lastNavigationActive = false

    init {
        MapLibre.getInstance(context)
        mapView = MapView(context)
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.isRotateGesturesEnabled = true
            readyMap.uiSettings.isTiltGesturesEnabled = true
            readyMap.uiSettings.isZoomGesturesEnabled = true
            readyMap.uiSettings.isScrollGesturesEnabled = true
            readyMap.uiSettings.isCompassEnabled = true
            readyMap.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onManualGesture()
            }
            loadStyle(pending.darkMode)
        }
    }

    fun update(
        routes: List<Route>,
        selectedRouteIndex: Int,
        trafficSegments: List<TrafficSegment>,
        currentLocation: Coordinate?,
        followLocation: Boolean,
        navigationActive: Boolean,
        navigationZoomLevel: Int,
        navigationRecenterToken: Int,
        darkMode: Boolean
    ) {
        pending = RenderState(
            routes,
            selectedRouteIndex,
            trafficSegments,
            currentLocation,
            followLocation,
            navigationActive,
            navigationZoomLevel,
            navigationRecenterToken,
            darkMode
        )
        if (currentDarkMode != darkMode) loadStyle(darkMode) else render()
    }

    private fun loadStyle(darkMode: Boolean) {
        val readyMap = map ?: return
        currentDarkMode = darkMode
        style = null
        val styleUrl = if (darkMode) DARK_STYLE else DAY_STYLE
        readyMap.setStyle(Style.Builder().fromUri(styleUrl)) { loaded ->
            style = loaded
            lastOverviewKey = ""
            render()
        }
    }

    private fun render() {
        val readyMap = map ?: return
        val loadedStyle = style ?: return
        renderRoutes(loadedStyle, pending.routes, pending.selectedRouteIndex)
        renderTraffic(loadedStyle, pending.trafficSegments)
        renderLocation(loadedStyle, pending.currentLocation)

        if (pending.navigationActive) {
            val location = pending.currentLocation
            if (location != null && (pending.followLocation || pending.navigationRecenterToken != lastRecenterToken || !lastNavigationActive)) {
                val existingBearing = readyMap.cameraPosition.bearing
                val camera = CameraPosition.Builder()
                    .target(LatLng(location.latitude, location.longitude))
                    .zoom(pending.navigationZoomLevel.coerceIn(16, 19).toDouble())
                    .bearing(existingBearing)
                    .tilt(58.0)
                    .padding(0.0, 190.0, 0.0, 260.0)
                    .build()
                readyMap.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 500)
                lastRecenterToken = pending.navigationRecenterToken
            }
        } else if (pending.routes.isNotEmpty()) {
            fitAllRoutes(readyMap, pending.routes)
        } else if (pending.currentLocation != null && pending.followLocation) {
            val location = pending.currentLocation!!
            readyMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(location.latitude, location.longitude))
                        .zoom(16.0)
                        .tilt(0.0)
                        .build()
                ),
                350
            )
        }
        lastNavigationActive = pending.navigationActive
    }

    private fun renderRoutes(style: Style, routes: List<Route>, selectedIndex: Int) {
        removeLayers(style, ROUTE_LAYER_PREFIX, ROUTE_SOURCE_PREFIX, 20)
        routes.forEachIndexed { index, route ->
            if (route.points.size < 2) return@forEachIndexed
            val sourceId = "$ROUTE_SOURCE_PREFIX$index"
            val geometry = lineGeoJson(route.points)
            style.addSource(GeoJsonSource(sourceId, geometry))

            if (index == selectedIndex) {
                val casingId = "$ROUTE_LAYER_PREFIX${index}_casing"
                style.addLayer(
                    LineLayer(casingId, sourceId).withProperties(
                        lineColor(Color.rgb(4, 23, 39)),
                        lineWidth(17f),
                        lineOpacity(0.88f)
                    )
                )
            }

            val color = when {
                index == selectedIndex -> Color.rgb(24, 212, 255)
                index % 3 == 0 -> Color.rgb(215, 255, 91)
                index % 3 == 1 -> Color.rgb(255, 181, 46)
                else -> Color.rgb(170, 181, 194)
            }
            style.addLayer(
                LineLayer("$ROUTE_LAYER_PREFIX$index", sourceId).withProperties(
                    lineColor(color),
                    lineWidth(if (index == selectedIndex) 9f else 6f),
                    lineOpacity(if (index == selectedIndex) 1f else 0.82f)
                )
            )
        }
    }

    private fun renderTraffic(style: Style, segments: List<TrafficSegment>) {
        removeLayers(style, TRAFFIC_LAYER_PREFIX, TRAFFIC_SOURCE_PREFIX, 100)
        segments.forEachIndexed { index, segment ->
            val sourceId = "$TRAFFIC_SOURCE_PREFIX$index"
            style.addSource(GeoJsonSource(sourceId, lineGeoJson(listOf(segment.start, segment.end))))
            val color = when {
                segment.delaySeconds >= 600.0 -> Color.rgb(230, 64, 69)
                segment.delaySeconds >= 120.0 -> Color.rgb(255, 181, 46)
                else -> Color.rgb(100, 214, 109)
            }
            style.addLayer(
                LineLayer("$TRAFFIC_LAYER_PREFIX$index", sourceId).withProperties(
                    lineColor(color), lineWidth(7f), lineOpacity(0.95f)
                )
            )
        }
    }

    private fun renderLocation(style: Style, location: Coordinate?) {
        style.removeLayer(LOCATION_LAYER)
        style.removeSource(LOCATION_SOURCE)
        if (location == null) return
        val point = JSONObject()
            .put("type", "Feature")
            .put("geometry", JSONObject()
                .put("type", "Point")
                .put("coordinates", JSONArray().put(location.longitude).put(location.latitude)))
            .put("properties", JSONObject())
            .toString()
        style.addSource(GeoJsonSource(LOCATION_SOURCE, point))
        style.addLayer(
            CircleLayer(LOCATION_LAYER, LOCATION_SOURCE).withProperties(
                circleRadius(8f),
                circleColor(Color.rgb(24, 212, 255)),
                circleStrokeColor(Color.WHITE),
                circleStrokeWidth(3f)
            )
        )
    }

    private fun fitAllRoutes(map: MapLibreMap, routes: List<Route>) {
        val key = routes.joinToString("|") { "${it.points.size}:${it.distanceMeters.toLong()}:${it.travelSeconds.toLong()}" }
        if (key == lastOverviewKey) return
        val builder = LatLngBounds.Builder()
        var count = 0
        routes.forEach { route ->
            route.points.forEach { point ->
                builder.include(LatLng(point.latitude, point.longitude))
                count++
            }
        }
        if (count < 2) return
        val bounds = builder.build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 70, 150, 70, 300), 550)
        lastOverviewKey = key
    }

    private fun removeLayers(style: Style, layerPrefix: String, sourcePrefix: String, max: Int) {
        repeat(max) { index ->
            style.removeLayer("$layerPrefix${index}_casing")
            style.removeLayer("$layerPrefix$index")
            style.removeSource("$sourcePrefix$index")
        }
    }

    private fun lineGeoJson(points: List<Coordinate>): String {
        val coordinates = JSONArray()
        points.forEach { point -> coordinates.put(JSONArray().put(point.longitude).put(point.latitude)) }
        return JSONObject()
            .put("type", "Feature")
            .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coordinates))
            .put("properties", JSONObject())
            .toString()
    }

    fun destroy() {
        runCatching { mapView.onPause() }
        runCatching { mapView.onStop() }
        runCatching { mapView.onDestroy() }
    }

    private data class RenderState(
        val routes: List<Route> = emptyList(),
        val selectedRouteIndex: Int = 0,
        val trafficSegments: List<TrafficSegment> = emptyList(),
        val currentLocation: Coordinate? = null,
        val followLocation: Boolean = false,
        val navigationActive: Boolean = false,
        val navigationZoomLevel: Int = 18,
        val navigationRecenterToken: Int = 0,
        val darkMode: Boolean = false
    )

    private companion object {
        const val DAY_STYLE = "https://tiles.openfreemap.org/styles/liberty"
        const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
        const val ROUTE_LAYER_PREFIX = "nv-route-layer-"
        const val ROUTE_SOURCE_PREFIX = "nv-route-source-"
        const val TRAFFIC_LAYER_PREFIX = "nv-traffic-layer-"
        const val TRAFFIC_SOURCE_PREFIX = "nv-traffic-source-"
        const val LOCATION_SOURCE = "nv-location-source"
        const val LOCATION_LAYER = "nv-location-layer"
    }
}

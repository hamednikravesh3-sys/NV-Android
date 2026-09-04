package ir.nv.navigation.map

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficSegment
import ir.nv.navigation.core.TrafficSummary
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Online vector map with genuine camera pitch, two-finger rotation and 3D buildings. */
@Composable
fun OnlineIranMap(
    context: Context,
    routes: List<Route>,
    selectedRouteIndex: Int,
    traffic: TrafficSummary?,
    trafficSegments: List<TrafficSegment>,
    codedPlaces: List<Place>,
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
    val holder = remember { VectorMapHolder(context.applicationContext) }

    DisposableEffect(holder) {
        onDispose(holder::destroy)
    }

    AndroidView(
        factory = { holder.mapView },
        modifier = modifier,
        update = { view ->
            view.setOnTouchListener { _, event ->
                if (navigationActive && (
                        event.actionMasked == MotionEvent.ACTION_MOVE ||
                            event.actionMasked == MotionEvent.ACTION_POINTER_DOWN
                        )
                ) {
                    onManualGesture()
                }
                false
            }
            holder.update(
                routes = routes,
                selectedRouteIndex = selectedRouteIndex,
                trafficSegments = trafficSegments,
                codedPlaces = codedPlaces,
                currentLocation = currentLocation,
                followLocation = followLocation,
                navigationActive = navigationActive,
                navigationZoomLevel = navigationZoomLevel,
                navigationRecenterToken = navigationRecenterToken,
                bearingDegrees = bearingDegrees,
                darkMode = darkMode
            )
        }
    )
}

private class VectorMapHolder(context: Context) {
    val mapView: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private val routeSources = mutableListOf<GeoJsonSource>()
    private val routeLayers = mutableListOf<LineLayer>()
    private val routeGlowLayers = mutableListOf<LineLayer>()
    private val trafficSources = mutableListOf<GeoJsonSource>()
    private val trafficLayers = mutableListOf<LineLayer>()
    private var locationSource: GeoJsonSource? = null
    private var placeSource: GeoJsonSource? = null
    private var darkMode = false
    private var appliedDarkMode: Boolean? = null
    private var lastRecenterToken = -1
    private var renderedRoutes: List<Route> = emptyList()
    private var renderedSelectedRoute = -1
    private var renderedTraffic: List<TrafficSegment> = emptyList()
    private var renderedPlaces: List<Place> = emptyList()
    private var currentLocation: Coordinate? = null
    private var followLocation = false
    private var navigationActive = false
    private var navigationZoomLevel = 18
    private var navigationRecenterToken = 0
    private var bearingDegrees = 0f

    init {
        MapLibre.getInstance(context)
        mapView = MapView(context)
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.uiSettings.apply {
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isCompassEnabled = true
                isAttributionEnabled = true
                isLogoEnabled = false
            }
            loadStyle()
        }
    }

    fun update(
        routes: List<Route>,
        selectedRouteIndex: Int,
        trafficSegments: List<TrafficSegment>,
        codedPlaces: List<Place>,
        currentLocation: Coordinate?,
        followLocation: Boolean,
        navigationActive: Boolean,
        navigationZoomLevel: Int,
        navigationRecenterToken: Int,
        bearingDegrees: Float,
        darkMode: Boolean
    ) {
        val routesChanged = routes != renderedRoutes || selectedRouteIndex != renderedSelectedRoute
        renderedRoutes = routes
        renderedSelectedRoute = selectedRouteIndex
        renderedTraffic = trafficSegments
        renderedPlaces = codedPlaces.distinctBy { it.code }.take(MAX_CODE_LABELS)
        this.currentLocation = currentLocation
        this.followLocation = followLocation
        this.navigationActive = navigationActive
        this.navigationZoomLevel = navigationZoomLevel
        this.navigationRecenterToken = navigationRecenterToken
        this.bearingDegrees = bearingDegrees
        this.darkMode = darkMode
        if (appliedDarkMode != darkMode) {
            loadStyle()
            return
        }
        renderRoutes()
        renderTraffic()
        renderPlaces()
        renderLocation()
        updateCamera(frameRoute = routesChanged)
    }

    private fun loadStyle() {
        val readyMap = map ?: return
        appliedDarkMode = darkMode
        style = null
        readyMap.setStyle(if (darkMode) DARK_STYLE_URL else DAY_STYLE_URL) { loadedStyle ->
            style = loadedStyle
            setupThreeDimensionalBuildings(loadedStyle)
            setupDynamicLayers(loadedStyle)
            renderRoutes()
            renderTraffic()
            renderPlaces()
            renderLocation()
            updateCamera(frameRoute = renderedRoutes.isNotEmpty())
        }
    }

    private fun setupThreeDimensionalBuildings(loadedStyle: Style) {
        if (loadedStyle.getLayer(BUILDING_LAYER_ID) != null || loadedStyle.getSource(OPEN_MAP_TILES_SOURCE) == null) return
        val buildings = FillExtrusionLayer(BUILDING_LAYER_ID, OPEN_MAP_TILES_SOURCE).apply {
            sourceLayer = "building"
            minZoom = 15f
            setFilter(
                Expression.all(
                    Expression.has("render_height"),
                    Expression.has("render_min_height")
                )
            )
            setProperties(
                PropertyFactory.fillExtrusionColor(if (darkMode) Color.rgb(40, 61, 79) else Color.LTGRAY),
                PropertyFactory.fillExtrusionHeight(Expression.get("render_height")),
                PropertyFactory.fillExtrusionBase(Expression.get("render_min_height")),
                PropertyFactory.fillExtrusionOpacity(0.88f)
            )
        }
        loadedStyle.addLayer(buildings)
    }

    private fun setupDynamicLayers(loadedStyle: Style) {
        routeSources.clear()
        routeLayers.clear()
        routeGlowLayers.clear()
        repeat(MAX_ROUTE_LAYERS) { index ->
            val source = GeoJsonSource("nv-route-source-$index", emptyFeatures())
            val glow = LineLayer("nv-route-glow-$index", source.id).withProperties(
                PropertyFactory.lineColor(Color.argb(100, 24, 212, 255)),
                PropertyFactory.lineWidth(18f),
                PropertyFactory.lineOpacity(0f)
            )
            val layer = LineLayer("nv-route-line-$index", source.id).withProperties(
                PropertyFactory.lineColor(Color.rgb(140, 151, 166)),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineOpacity(0f)
            )
            loadedStyle.addSource(source)
            loadedStyle.addLayer(glow)
            loadedStyle.addLayer(layer)
            routeSources += source
            routeGlowLayers += glow
            routeLayers += layer
        }
        trafficSources.clear()
        trafficLayers.clear()
        repeat(MAX_TRAFFIC_LAYERS) { index ->
            val source = GeoJsonSource("nv-traffic-source-$index", emptyFeatures())
            val layer = LineLayer("nv-traffic-line-$index", source.id).withProperties(
                PropertyFactory.lineColor(Color.rgb(100, 214, 109)),
                PropertyFactory.lineWidth(8f),
                PropertyFactory.lineOpacity(0f)
            )
            loadedStyle.addSource(source)
            loadedStyle.addLayer(layer)
            trafficSources += source
            trafficLayers += layer
        }

        locationSource = GeoJsonSource(LOCATION_SOURCE_ID, emptyFeatures()).also(loadedStyle::addSource)
        loadedStyle.addLayer(
            CircleLayer(LOCATION_LAYER_ID, LOCATION_SOURCE_ID).withProperties(
                PropertyFactory.circleColor(Color.rgb(24, 212, 255)),
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(3f)
            )
        )

        placeSource = GeoJsonSource(PLACE_SOURCE_ID, emptyFeatures()).also(loadedStyle::addSource)
        loadedStyle.addLayer(
            CircleLayer(PLACE_CIRCLE_LAYER_ID, PLACE_SOURCE_ID).withProperties(
                PropertyFactory.circleColor(Color.rgb(6, 30, 52)),
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeColor(Color.rgb(24, 212, 255)),
                PropertyFactory.circleStrokeWidth(2f)
            )
        )
        loadedStyle.addLayer(
            SymbolLayer(PLACE_LABEL_LAYER_ID, PLACE_SOURCE_ID).withProperties(
                PropertyFactory.textField(Expression.get("label")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor(Color.WHITE),
                PropertyFactory.textHaloColor(Color.rgb(6, 22, 39)),
                PropertyFactory.textHaloWidth(2f),
                PropertyFactory.textOffset(arrayOf(0f, 1.35f)),
                PropertyFactory.textAllowOverlap(true)
            )
        )
    }

    private fun renderRoutes() {
        if (style == null) return
        routeSources.forEachIndexed { index, source ->
            val route = renderedRoutes.getOrNull(index)
            source.setGeoJson(route?.toFeatureCollection() ?: emptyFeatures())
            val selected = index == renderedSelectedRoute && route != null
            val routeColor = when {
                selected -> Color.rgb(24, 212, 255)
                index % 2 == 0 -> Color.rgb(215, 255, 91)
                else -> Color.rgb(150, 160, 174)
            }
            routeLayers[index].setProperties(
                PropertyFactory.lineColor(routeColor),
                PropertyFactory.lineWidth(if (selected) 10f else 6f),
                PropertyFactory.lineOpacity(if (route == null) 0f else 0.96f)
            )
            routeGlowLayers[index].setProperties(
                PropertyFactory.lineColor(Color.argb(100, 24, 212, 255)),
                PropertyFactory.lineOpacity(if (selected) 0.8f else 0f)
            )
        }
    }

    private fun renderTraffic() {
        if (style == null) return
        trafficSources.forEachIndexed { index, source ->
            val segment = renderedTraffic.getOrNull(index)
            source.setGeoJson(segment?.toFeatureCollection() ?: emptyFeatures())
            val color = when {
                segment == null -> Color.TRANSPARENT
                segment.delaySeconds >= 600.0 -> Color.rgb(230, 64, 69)
                segment.delaySeconds >= 120.0 -> Color.rgb(255, 181, 46)
                else -> Color.rgb(100, 214, 109)
            }
            trafficLayers[index].setProperties(
                PropertyFactory.lineColor(color),
                PropertyFactory.lineOpacity(if (segment == null) 0f else 1f)
            )
        }
    }

    private fun renderPlaces() {
        val features = renderedPlaces.map { place ->
            Feature.fromGeometry(
                Point.fromLngLat(place.coordinate.longitude, place.coordinate.latitude)
            ).also { feature ->
                val code = place.personalCode ?: place.code.takeIf { it > 0 }?.toString() ?: "GPS"
                feature.addStringProperty("label", "${place.name}  •  NV:$code")
            }
        }
        placeSource?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun renderLocation() {
        val point = currentLocation?.let {
            Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))
        }
        locationSource?.setGeoJson(
            if (point == null) emptyFeatures() else FeatureCollection.fromFeature(point)
        )
    }

    private fun updateCamera(frameRoute: Boolean) {
        val readyMap = map ?: return
        if (frameRoute && !navigationActive) {
            renderedRoutes.getOrNull(renderedSelectedRoute)?.takeIf { it.points.isNotEmpty() }?.let { route ->
                val center = route.points[route.points.lastIndex / 2]
                val position = CameraPosition.Builder()
                    .target(LatLng(center.latitude, center.longitude))
                    .zoom(routeZoom(route.distanceMeters))
                    .tilt(BROWSE_TILT)
                    .bearing(readyMap.cameraPosition.bearing)
                    .build()
                readyMap.easeCamera(CameraUpdateFactory.newCameraPosition(position), CAMERA_ANIMATION_MS)
                return
            }
        }
        val location = currentLocation ?: return
        val mustRecenter = navigationRecenterToken != lastRecenterToken
        if (!followLocation && !mustRecenter) return
        lastRecenterToken = navigationRecenterToken
        val position = CameraPosition.Builder()
            .target(LatLng(location.latitude, location.longitude))
            .zoom(if (navigationActive) navigationZoomLevel.toDouble() else HOME_ZOOM)
            .tilt(if (navigationActive) NAVIGATION_TILT else BROWSE_TILT)
            .bearing(if (navigationActive && bearingDegrees.isFinite()) bearingDegrees.toDouble() else readyMap.cameraPosition.bearing)
            .build()
        readyMap.easeCamera(CameraUpdateFactory.newCameraPosition(position), CAMERA_ANIMATION_MS)
    }

    fun destroy() {
        mapView.onPause()
        mapView.onStop()
        mapView.onDestroy()
    }

    private fun Route.toFeatureCollection(): FeatureCollection = FeatureCollection.fromFeature(
        Feature.fromGeometry(
            LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })
        )
    )

    private fun TrafficSegment.toFeatureCollection(): FeatureCollection = FeatureCollection.fromFeature(
        Feature.fromGeometry(
            LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(start.longitude, start.latitude),
                    Point.fromLngLat(end.longitude, end.latitude)
                )
            )
        )
    )

    private fun routeZoom(distanceMeters: Double): Double = when {
        distanceMeters < 4_000 -> 14.5
        distanceMeters < 15_000 -> 12.5
        distanceMeters < 60_000 -> 10.5
        distanceMeters < 250_000 -> 8.5
        else -> 6.5
    }

    private fun emptyFeatures(): FeatureCollection = FeatureCollection.fromFeatures(emptyList())

    private companion object {
        // OpenFreeMap's documented mobile style URL. NV adds its own building
        // extrusion layer below instead of relying on the removed /styles/3d URL.
        const val DAY_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        const val DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
        const val OPEN_MAP_TILES_SOURCE = "openmaptiles"
        const val BUILDING_LAYER_ID = "nv-building-3d"
        const val LOCATION_SOURCE_ID = "nv-location-source"
        const val LOCATION_LAYER_ID = "nv-location-layer"
        const val PLACE_SOURCE_ID = "nv-place-source"
        const val PLACE_CIRCLE_LAYER_ID = "nv-place-circle-layer"
        const val PLACE_LABEL_LAYER_ID = "nv-place-label-layer"
        const val MAX_ROUTE_LAYERS = 8
        const val MAX_TRAFFIC_LAYERS = 12
        const val MAX_CODE_LABELS = 12
        const val CAMERA_ANIMATION_MS = 650
        const val HOME_ZOOM = 16.5
        const val BROWSE_TILT = 42.0
        const val NAVIGATION_TILT = 58.0
    }
}

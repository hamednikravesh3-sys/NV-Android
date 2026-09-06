package ir.nv.navigation.map

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@Composable
fun SatelliteIranMap(
    context: Context,
    routes: List<Route>,
    selectedRouteIndex: Int,
    codedPlaces: List<Place>,
    currentLocation: Coordinate?,
    followLocation: Boolean,
    navigationActive: Boolean,
    navigationZoomLevel: Int,
    navigationRecenterToken: Int,
    bearingDegrees: Float,
    onManualGesture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val holder = remember { SatelliteMapHolder(context.applicationContext) }
    val detector = remember(holder) {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                holder.placeFlagAtScreen(e.x, e.y)?.let(NvMapInteractionBus::emitDoubleTap)
                return true
            }
        })
    }
    DisposableEffect(holder) { onDispose(holder::destroy) }
    AndroidView(
        factory = { holder.mapView },
        modifier = modifier,
        update = { view ->
            view.setOnTouchListener { _, event ->
                detector.onTouchEvent(event)
                if (navigationActive && (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN)) onManualGesture()
                false
            }
            holder.update(routes, selectedRouteIndex, codedPlaces, currentLocation, followLocation, navigationActive, navigationZoomLevel, navigationRecenterToken, bearingDegrees)
        }
    )
}

private class SatelliteMapHolder(context: Context) {
    val mapView: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private val routeSources = mutableListOf<GeoJsonSource>()
    private val routeLayers = mutableListOf<LineLayer>()
    private val glowLayers = mutableListOf<LineLayer>()
    private var placeSource: GeoJsonSource? = null
    private var flagSource: GeoJsonSource? = null
    private var routes: List<Route> = emptyList()
    private var selectedRouteIndex = 0
    private var codedPlaces: List<Place> = emptyList()
    private var currentLocation: Coordinate? = null
    private var followLocation = false
    private var navigationActive = false
    private var navigationZoomLevel = 18
    private var recenterToken = 0
    private var lastRecenterToken = -1
    private var bearingDegrees = 0f

    init {
        MapLibre.getInstance(context)
        mapView = MapView(context)
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        mapView.setBackgroundColor(Color.rgb(14, 18, 22))
        mapView.getMapAsync { ready ->
            map = ready
            ready.uiSettings.apply {
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isCompassEnabled = true
                isAttributionEnabled = true
                isLogoEnabled = false
            }
            ready.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(IRAN_CENTER).zoom(5.2).build()))
            loadSatelliteStyle()
        }
    }

    private fun loadSatelliteStyle() {
        val ready = map ?: return
        ready.setStyle(Style.Builder().fromJson(SATELLITE_STYLE_JSON)) { loaded ->
            style = loaded
            setupLayers(loaded)
            renderRoutes()
            renderPlaces()
            updateCamera(routes.isNotEmpty())
        }
    }

    private fun setupLayers(loaded: Style) {
        if (loaded.getLayer(BACKGROUND_ID) == null) loaded.addLayerBelow(BackgroundLayer(BACKGROUND_ID).withProperties(PropertyFactory.backgroundColor(Color.rgb(14,18,22))), SAT_LAYER_ID)
        routeSources.clear(); routeLayers.clear(); glowLayers.clear()
        repeat(MAX_ROUTE_LAYERS) { index ->
            val source = GeoJsonSource("nv-sat-route-source-$index", emptyFeatures())
            val glow = LineLayer("nv-sat-route-glow-$index", source.id).withProperties(PropertyFactory.lineColor(routeColor(index)), PropertyFactory.lineWidth(24f), PropertyFactory.lineOpacity(0f))
            val line = LineLayer("nv-sat-route-line-$index", source.id).withProperties(PropertyFactory.lineColor(routeColor(index)), PropertyFactory.lineWidth(9f), PropertyFactory.lineOpacity(0f))
            loaded.addSource(source); loaded.addLayer(glow); loaded.addLayer(line)
            routeSources += source; glowLayers += glow; routeLayers += line
        }
        flagSource = GeoJsonSource(FLAG_SOURCE_ID, emptyFeatures()).also(loaded::addSource)
        loaded.addLayer(SymbolLayer(FLAG_LAYER_ID, FLAG_SOURCE_ID).withProperties(PropertyFactory.textField("🚩"), PropertyFactory.textSize(34f), PropertyFactory.textAllowOverlap(true), PropertyFactory.textIgnorePlacement(true)))
        placeSource = GeoJsonSource(PLACE_SOURCE_ID, emptyFeatures()).also(loaded::addSource)
        loaded.addLayer(CircleLayer(PLACE_CIRCLE_LAYER_ID, PLACE_SOURCE_ID).withProperties(PropertyFactory.circleColor(Color.rgb(5,27,49)), PropertyFactory.circleRadius(7f), PropertyFactory.circleStrokeColor(Color.rgb(20,216,255)), PropertyFactory.circleStrokeWidth(2f)))
        loaded.addLayer(SymbolLayer(PLACE_LABEL_LAYER_ID, PLACE_SOURCE_ID).withProperties(PropertyFactory.textField(Expression.get("label")), PropertyFactory.textSize(12f), PropertyFactory.textColor(Color.WHITE), PropertyFactory.textHaloColor(Color.rgb(5,20,35)), PropertyFactory.textHaloWidth(2f), PropertyFactory.textOffset(arrayOf(0f,1.3f)), PropertyFactory.textAllowOverlap(true)))
    }

    fun update(routes: List<Route>, selectedRouteIndex: Int, codedPlaces: List<Place>, currentLocation: Coordinate?, followLocation: Boolean, navigationActive: Boolean, navigationZoomLevel: Int, navigationRecenterToken: Int, bearingDegrees: Float) {
        val routeChanged = routes != this.routes || selectedRouteIndex != this.selectedRouteIndex
        this.routes = routes; this.selectedRouteIndex = selectedRouteIndex; this.codedPlaces = codedPlaces.take(30); this.currentLocation = currentLocation; this.followLocation = followLocation; this.navigationActive = navigationActive; this.navigationZoomLevel = navigationZoomLevel; this.recenterToken = navigationRecenterToken; this.bearingDegrees = bearingDegrees
        renderRoutes(); renderPlaces(); updateCamera(routeChanged)
    }

    private fun renderRoutes() {
        if (style == null) return
        routeSources.forEachIndexed { index, source ->
            val route = routes.getOrNull(index)
            source.setGeoJson(route?.toFeatureCollection() ?: emptyFeatures())
            val selected = route != null && index == selectedRouteIndex
            routeLayers[index].setProperties(PropertyFactory.lineColor(routeColor(index)), PropertyFactory.lineWidth(if(selected)11f else 7f), PropertyFactory.lineOffset(if(selected)0f else alternativeOffset(index)), PropertyFactory.lineOpacity(if(route==null)0f else .98f))
            glowLayers[index].setProperties(PropertyFactory.lineColor(routeColor(index)), PropertyFactory.lineWidth(25f), PropertyFactory.lineOpacity(if(selected).48f else 0f))
        }
    }

    private fun renderPlaces() {
        val features = codedPlaces.map { p -> Feature.fromGeometry(Point.fromLngLat(p.coordinate.longitude,p.coordinate.latitude)).also { f -> f.addStringProperty("label", "${p.name} • NV:${p.personalCode ?: p.code.takeIf{it>0}?.toString() ?: "GPS"}") } }
        placeSource?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun placeFlagAtScreen(x: Float, y: Float): Coordinate? {
        val ready = map ?: return null
        val ll = ready.projection.fromScreenLocation(PointF(x,y))
        val c = Coordinate(ll.latitude,ll.longitude)
        flagSource?.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(c.longitude,c.latitude))))
        return c
    }

    private fun updateCamera(frameRoute: Boolean) {
        val ready = map ?: return
        if (frameRoute && !navigationActive) {
            routes.getOrNull(selectedRouteIndex)?.takeIf{it.points.isNotEmpty()}?.let { route ->
                val center=route.points[route.points.lastIndex/2]
                ready.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(center.latitude,center.longitude)).zoom(routeZoom(route.distanceMeters)).tilt(42.0).build()),650)
                return
            }
        }
        val loc=currentLocation?:return
        val mustRecenter=recenterToken!=lastRecenterToken
        if(!followLocation&&!mustRecenter)return
        lastRecenterToken=recenterToken
        ready.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(loc.latitude,loc.longitude)).zoom(if(navigationActive)navigationZoomLevel.toDouble() else 16.5).tilt(if(navigationActive)58.0 else 42.0).bearing(if(navigationActive&&bearingDegrees.isFinite())bearingDegrees.toDouble() else ready.cameraPosition.bearing).build()),550)
    }

    private fun Route.toFeatureCollection()=FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points.map{Point.fromLngLat(it.longitude,it.latitude)})))
    private fun routeZoom(d:Double)=when{d<4000->14.5;d<15000->12.5;d<60000->10.5;d<250000->8.5;else->6.5}
    private fun emptyFeatures()=FeatureCollection.fromFeatures(emptyList())
    private fun routeColor(i:Int)=when(i%4){0->Color.rgb(20,216,255);1->Color.rgb(67,230,107);2->Color.rgb(255,181,46);else->Color.rgb(187,117,255)}
    private fun alternativeOffset(i:Int)=when(i%4){0->-5f;1->5f;2->-10f;else->10f}
    fun destroy(){mapView.onPause();mapView.onStop();mapView.onDestroy()}

    private companion object {
        val IRAN_CENTER=LatLng(32.4279,53.6880)
        const val MAX_ROUTE_LAYERS=8
        const val PLACE_SOURCE_ID="nv-sat-place-source"; const val PLACE_CIRCLE_LAYER_ID="nv-sat-place-circle"; const val PLACE_LABEL_LAYER_ID="nv-sat-place-label"
        const val FLAG_SOURCE_ID="nv-sat-flag-source"; const val FLAG_LAYER_ID="nv-sat-flag-layer"; const val BACKGROUND_ID="nv-sat-background"; const val SAT_LAYER_ID="nv-world-imagery"
        const val SATELLITE_STYLE_JSON="""{
          "version":8,
          "name":"NV Satellite Online",
          "sources":{
            "esri-world-imagery":{
              "type":"raster",
              "tiles":["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
              "tileSize":256,
              "scheme":"xyz",
              "bounds":[44.0,24.0,64.0,40.0],
              "minzoom":0,
              "maxzoom":19,
              "attribution":"Imagery © Esri, Maxar, Earthstar Geographics"
            }
          },
          "layers":[{"id":"nv-world-imagery","type":"raster","source":"esri-world-imagery","minzoom":0,"maxzoom":24,"paint":{"raster-opacity":1.0,"raster-fade-duration":0}}]
        }"""
    }
}

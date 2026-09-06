package ir.nv.navigation.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ir.nv.navigation.core.Coordinate
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** Full map picker used to assign an NV code to any coordinate. Long-press selects the point. */
@Composable
fun NvCodePickerMap(
    context: Context,
    initial: Coordinate?,
    satellite: Boolean,
    onPointSelected: (Coordinate) -> Unit,
    modifier: Modifier = Modifier
) {
    val holder = remember { NvCodePickerHolder(context.applicationContext, initial, satellite, onPointSelected) }
    DisposableEffect(holder) { onDispose(holder::destroy) }
    AndroidView(factory = { holder.mapView }, modifier = modifier)
}

private class NvCodePickerHolder(
    context: Context,
    initial: Coordinate?,
    satellite: Boolean,
    private val onPointSelected: (Coordinate) -> Unit
) {
    val mapView: MapView

    init {
        MapLibre.getInstance(context)
        mapView = MapView(context)
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        mapView.getMapAsync { map ->
            map.uiSettings.apply {
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = true
                isZoomGesturesEnabled = true
                isScrollGesturesEnabled = true
                isCompassEnabled = true
                isAttributionEnabled = true
                isLogoEnabled = false
            }
            val center = initial?.let { LatLng(it.latitude, it.longitude) } ?: IRAN_CENTER
            map.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(center).zoom(if (initial == null) 5.2 else 16.0).build()
                )
            )
            if (satellite) map.setStyle(Style.Builder().fromJson(SATELLITE_STYLE))
            else map.setStyle(DAY_STYLE)

            map.addOnMapLongClickListener { latLng ->
                onPointSelected(Coordinate(latLng.latitude, latLng.longitude))
                true
            }
        }
    }

    fun destroy() {
        mapView.onPause()
        mapView.onStop()
        mapView.onDestroy()
    }

    private companion object {
        val IRAN_CENTER = LatLng(32.4279, 53.6880)
        const val DAY_STYLE = "https://tiles.openfreemap.org/styles/liberty"
        const val SATELLITE_STYLE = """{
          "version":8,
          "name":"NV Satellite Picker",
          "sources":{
            "esri":{
              "type":"raster",
              "tiles":[
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
                "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
              ],
              "tileSize":256,
              "maxzoom":19,
              "attribution":"Tiles © Esri, Maxar, Earthstar Geographics"
            }
          },
          "layers":[{"id":"sat","type":"raster","source":"esri","paint":{"raster-opacity":1.0,"raster-fade-duration":0}}]
        }"""
    }
}

package ir.nv.navigation.map

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ir.nv.navigation.core.Route
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OnlineIranMap(route: Route?, modifier: Modifier = Modifier) {
    val html = remember {
        """
        <!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no'>
        <link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>
        <style>html,body,#map{height:100%;margin:0;background:#e9eef3}.leaflet-control-attribution{font-size:9px}</style>
        </head><body><div id='map'></div>
        <script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>
        <script>
        const map=L.map('map',{zoomControl:false}).setView([32.4279,53.6880],5);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);
        let routeLine=null;
        function showRoute(points){
          if(routeLine){map.removeLayer(routeLine);routeLine=null;}
          if(!points||points.length<2)return;
          routeLine=L.polyline(points,{weight:7,opacity:0.9}).addTo(map);
          map.fitBounds(routeLine.getBounds(),{padding:[35,35]});
        }
        </script></body></html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadDataWithBaseURL("https://nv.local/", html, "text/html", "UTF-8", null)
            }
        },
        update = { web ->
            val array = JSONArray()
            route?.points?.forEach { p ->
                array.put(JSONArray().put(p.latitude).put(p.longitude))
            }
            web.evaluateJavascript("if(window.showRoute){showRoute(${JSONObject.quote(array.toString())} && JSON.parse(${JSONObject.quote(array.toString())}));}", null)
        }
    )
}

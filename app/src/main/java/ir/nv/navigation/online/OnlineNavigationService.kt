package ir.nv.navigation.online

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

class OnlineNavigationService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 12): List<Place> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.toString())
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&countrycodes=ir&limit=$limit&q=$encoded"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NV-Android/0.3 (offline-capable navigation)")
            .header("Accept-Language", "fa,en")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string().orEmpty()
            val array = JSONArray(body)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val lat = item.optString("lat").toDoubleOrNull() ?: continue
                    val lon = item.optString("lon").toDoubleOrNull() ?: continue
                    val osmId = item.optLong("osm_id", i.toLong() + 1L)
                    val type = item.optString("type", "place")
                    val category = when (type) {
                        "city", "town", "village", "suburb", "neighbourhood" -> "place:$type"
                        else -> "online:$type"
                    }
                    val display = item.optString("display_name").ifBlank { q }
                    val shortName = display.substringBefore(',').trim().ifBlank { display }
                    add(
                        Place(
                            code = -(osmId.absoluteValue + 1L),
                            name = shortName,
                            coordinate = Coordinate(lat, lon),
                            category = category
                        )
                    )
                }
            }
        }
    }

    suspend fun route(origin: Coordinate, destination: Coordinate): Route? = withContext(Dispatchers.IO) {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
            "${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}" +
            "?overview=full&geometries=geojson&steps=false&alternatives=false"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NV-Android/0.3")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val root = JSONObject(response.body?.string().orEmpty())
            val routes = root.optJSONArray("routes") ?: return@withContext null
            if (routes.length() == 0) return@withContext null
            val first = routes.getJSONObject(0)
            val coords = first.getJSONObject("geometry").getJSONArray("coordinates")
            val points = ArrayList<Coordinate>(coords.length())
            for (i in 0 until coords.length()) {
                val pair = coords.getJSONArray(i)
                points += Coordinate(pair.getDouble(1), pair.getDouble(0))
            }
            Route(
                points = points,
                edgeIds = emptyList(),
                distanceMeters = first.optDouble("distance", 0.0),
                travelSeconds = first.optDouble("duration", 0.0)
            )
        }
    }
}
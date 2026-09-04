package ir.nv.navigation.online

import ir.nv.navigation.BuildConfig
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.routing.RouteInsightEngine
import ir.nv.navigation.routing.RoutePointSampler
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Online POIs for the first 10 km; the installed Iran pack remains the offline source. */
class OnlinePlacesService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(18, TimeUnit.SECONDS)
        .build()
) {
    fun noticesAhead(route: Route): List<RouteNotice> {
        val samples = SAMPLE_DISTANCES.mapNotNull { RoutePointSampler.pointAhead(route, it) }
            .distinctBy { "%.4f,%.4f".format(it.coordinate.latitude, it.coordinate.longitude) }
        if (samples.isEmpty()) return emptyList()
        val query = buildString {
            append("[out:json][timeout:12];(")
            samples.forEach { sample ->
                val lat = sample.coordinate.latitude
                val lon = sample.coordinate.longitude
                append("nwr(around:2500,$lat,$lon)[\"name\"][\"tourism\"];")
                append("nwr(around:2500,$lat,$lon)[\"name\"][\"historic\"];")
                append("nwr(around:2500,$lat,$lon)[\"name\"][\"natural\"];")
                append("nwr(around:2500,$lat,$lon)[\"name\"][\"amenity\"~\"^(fuel|parking|hospital|clinic|pharmacy|restaurant|cafe|toilets)$\"];")
            }
            append(");out center tags 120;")
        }
        val request = Request.Builder()
            .url(BuildConfig.PLACES_API_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "NV-Android/${BuildConfig.VERSION_NAME}")
            .post(FormBody.Builder().add("data", query).build())
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Places HTTP ${response.code}" }
            val elements = JSONObject(requireNotNull(response.body).string()).optJSONArray("elements")
                ?: return emptyList()
            val places = buildList {
                for (index in 0 until elements.length()) {
                    val element = elements.optJSONObject(index) ?: continue
                    val tags = element.optJSONObject("tags") ?: continue
                    val center = element.optJSONObject("center")
                    val latitude = if (element.has("lat")) element.optDouble("lat") else center?.optDouble("lat")
                    val longitude = if (element.has("lon")) element.optDouble("lon") else center?.optDouble("lon")
                    if (latitude == null || longitude == null || latitude.isNaN() || longitude.isNaN()) continue
                    val category = category(tags) ?: continue
                    val name = tags.optString("name:fa").ifBlank { tags.optString("name") }
                    if (name.isBlank()) continue
                    add(
                        Place(
                            code = -element.optLong("id", index.toLong() + 1L),
                            name = name,
                            coordinate = Coordinate(latitude, longitude),
                            category = category
                        )
                    )
                }
            }
            return RouteInsightEngine.placesAhead(
                route = route,
                places = places.distinctBy { Triple(it.name, it.coordinate.latitude, it.coordinate.longitude) },
                limit = 12,
                maxAheadMeters = MAX_AHEAD_METERS
            )
        }
    }

    private fun category(tags: JSONObject): String? = when {
        tags.has("tourism") -> "tourism:${tags.optString("tourism")}"
        tags.has("historic") -> "historic:${tags.optString("historic")}"
        tags.has("natural") -> "natural:${tags.optString("natural")}"
        tags.has("amenity") -> "amenity:${tags.optString("amenity")}"
        else -> null
    }

    private companion object {
        val SAMPLE_DISTANCES = listOf(1_000.0, 4_000.0, 7_000.0, 10_000.0)
        const val MAX_AHEAD_METERS = 10_000.0
    }
}

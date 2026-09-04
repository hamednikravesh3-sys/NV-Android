package ir.nv.navigation.online

import ir.nv.navigation.BuildConfig
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

class OnlineNavigationService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val searchCache = object : LinkedHashMap<String, List<Place>>(80, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Place>>?): Boolean = size > 80
    }

    suspend fun search(query: String, limit: Int = 20): List<Place> = withContext(Dispatchers.IO) {
        val q = normalizeQuery(query)
        if (q.length < 2) return@withContext emptyList()
        synchronized(searchCache) { searchCache[q]?.let { return@withContext it } }

        val generic = runCatching { photonSearch(q, limit) }.getOrDefault(emptyList())
        val roadQuery = looksLikeRoadQuery(q)
        val roadResults = if (roadQuery) {
            runCatching { photonSearch(q, limit, osmTag = "highway") }.getOrDefault(emptyList())
        } else emptyList()

        // Photon sometimes resolves Persian addresses better when the country is explicit.
        val iranQualified = if ((generic + roadResults).size < 6 && !q.contains("ایران")) {
            runCatching { photonSearch("$q ایران", limit) }.getOrDefault(emptyList())
        } else emptyList()

        val combined = (generic + roadResults + iranQualified)
            .filter { isInsideIran(it.coordinate) }
            .distinctBy { placeIdentity(it) }
            .sortedWith(
                compareBy<Place> { searchScore(it, q) }
                    .thenBy { it.name.length }
                    .thenBy { it.code }
            )
            .take(limit)

        synchronized(searchCache) { searchCache[q] = combined }
        combined
    }

    private fun photonSearch(query: String, limit: Int, osmTag: String? = null): List<Place> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val tag = osmTag?.let { "&osm_tag=${URLEncoder.encode(it, StandardCharsets.UTF_8.toString())}" }.orEmpty()
        val url = BuildConfig.GEOCODING_API_URL.toHttpUrlString() +
            "?q=$encoded&limit=$limit&lang=fa&bbox=44.0,24.0,64.0,40.0$tag"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Accept-Language", "fa-IR,fa;q=0.95,en;q=0.7")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("جست‌وجوی آنلاین: HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@use emptyList()
            parsePhoton(JSONObject(body), query)
        }
    }

    suspend fun route(origin: Coordinate, destination: Coordinate): Route = withContext(Dispatchers.IO) {
        val providers = listOf(
            BuildConfig.ROUTING_API_URL,
            BuildConfig.ROUTING_FALLBACK_API_URL
        ).map { it.toHttpUrlString() }.distinct()
        val failures = mutableListOf<String>()

        providers.forEach { provider ->
            val url = provider + "/route/v1/driving/" +
                "${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}" +
                "?overview=full&geometries=geojson&steps=true&alternatives=true"
            val result = runCatching {
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    parseRoute(JSONObject(response.body?.string().orEmpty()))
                }
            }
            result.getOrNull()?.let { return@withContext it }
            failures += result.exceptionOrNull()?.message ?: "پاسخ نامعتبر"
        }
        throw IOException("هیچ سرویس مسیر آنلاینی پاسخ نداد: ${failures.joinToString("، ")}")
    }

    private fun parsePhoton(root: JSONObject, fallbackName: String): List<Place> {
        val features = root.optJSONArray("features") ?: return emptyList()
        return buildList {
            for (index in 0 until features.length()) {
                val feature = features.optJSONObject(index) ?: continue
                val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                if (coordinates.length() < 2) continue
                val longitude = coordinates.optDouble(0, Double.NaN)
                val latitude = coordinates.optDouble(1, Double.NaN)
                if (!latitude.isFinite() || !longitude.isFinite()) continue

                val properties = feature.optJSONObject("properties") ?: JSONObject()
                val osmId = properties.optLong("osm_id", index.toLong() + 1)
                val primary = properties.optString("name")
                    .ifBlank { properties.optString("street") }
                    .ifBlank { properties.optString("district") }
                    .ifBlank { properties.optString("city") }
                    .ifBlank { properties.optString("county") }
                    .ifBlank { fallbackName }
                val city = properties.optString("city")
                    .ifBlank { properties.optString("town") }
                    .ifBlank { properties.optString("village") }
                    .ifBlank { properties.optString("county") }
                val district = properties.optString("district")
                    .ifBlank { properties.optString("locality") }
                val state = properties.optString("state")
                val contextualName = buildList {
                    add(primary)
                    if (district.isNotBlank() && !district.equals(primary, true)) add(district)
                    if (city.isNotBlank() && !city.equals(primary, true) && !city.equals(district, true)) add(city)
                    if (state.isNotBlank() && size < 3 && !state.equals(city, true)) add(state)
                }.joinToString("، ")

                val osmKey = properties.optString("osm_key", "online")
                val osmValue = properties.optString("osm_value", "place")
                add(
                    Place(
                        code = -(osmId.absoluteValue + 1L),
                        name = contextualName,
                        coordinate = Coordinate(latitude, longitude),
                        category = "$osmKey:$osmValue"
                    )
                )
            }
        }
    }

    private fun searchScore(place: Place, query: String): Int {
        val name = normalizeQuery(place.name)
        val category = place.category.lowercase()
        var score = when {
            name == query -> 0
            name.startsWith(query) -> 10
            name.contains(query) -> 20
            query.split(' ').all { token -> name.contains(token) } -> 30
            else -> 60
        }
        score += when {
            category.startsWith("place:city") || category.startsWith("place:town") -> 0
            category.contains("highway") || category.contains("street") -> 2
            category.startsWith("place:suburb") || category.startsWith("place:neighbourhood") -> 4
            category.startsWith("place:village") -> 6
            category.startsWith("amenity:") || category.startsWith("tourism:") -> 8
            else -> 12
        }
        return score
    }

    private fun placeIdentity(place: Place): String {
        val lat = (place.coordinate.latitude * 100_000).toLong()
        val lon = (place.coordinate.longitude * 100_000).toLong()
        return "${normalizeQuery(place.name)}|$lat|$lon"
    }

    private fun looksLikeRoadQuery(query: String): Boolean {
        val words = listOf(
            "خیابان", "خ ", "کوچه", "بلوار", "میدان", "جاده", "بزرگراه", "اتوبان",
            "راه", "چهارراه", "street", "road", "avenue", "boulevard", "highway", "alley"
        )
        return words.any { query.contains(it, ignoreCase = true) }
    }

    private fun isInsideIran(coordinate: Coordinate): Boolean =
        coordinate.latitude in 24.0..40.0 && coordinate.longitude in 44.0..64.0

    private fun normalizeQuery(value: String): String = value
        .trim()
        .lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace('ة', 'ه')
        .replace("\u200c", " ")
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace(Regex("\\s+"), " ")

    private fun parseRoute(root: JSONObject): Route {
        if (root.optString("code") != "Ok") {
            throw IOException(root.optString("message", "مسیر پیدا نشد"))
        }
        val routes = root.optJSONArray("routes") ?: throw IOException("پاسخ مسیر خالی است")
        if (routes.length() == 0) throw IOException("برای این دو نقطه مسیر پیدا نشد")
        val first = routes.getJSONObject(0)
        val coordinates = first.getJSONObject("geometry").getJSONArray("coordinates")
        val points = buildList {
            for (index in 0 until coordinates.length()) {
                val pair = coordinates.getJSONArray(index)
                add(Coordinate(pair.getDouble(1), pair.getDouble(0)))
            }
        }
        if (points.size < 2) throw IOException("هندسه مسیر نامعتبر است")
        return Route(
            points = points,
            edgeIds = emptyList(),
            distanceMeters = first.optDouble("distance", 0.0),
            travelSeconds = first.optDouble("duration", 0.0),
            maneuvers = parseManeuvers(first)
        )
    }

    private fun parseManeuvers(route: JSONObject): List<RouteManeuver> {
        val legs = route.optJSONArray("legs") ?: return emptyList()
        return buildList {
            for (legIndex in 0 until legs.length()) {
                val steps = legs.getJSONObject(legIndex).optJSONArray("steps") ?: continue
                for (stepIndex in 0 until steps.length()) {
                    val step = steps.getJSONObject(stepIndex)
                    val maneuver = step.optJSONObject("maneuver") ?: continue
                    val type = maneuver.optString("type")
                    val modifier = maneuver.optString("modifier")
                    val roadName = step.optString("name").takeIf { it.isNotBlank() }
                    val direction = maneuverDirection(type, modifier)
                    val location = maneuver.optJSONArray("location")
                    val coordinate = if (location != null && location.length() >= 2) {
                        Coordinate(location.getDouble(1), location.getDouble(0))
                    } else null
                    add(
                        RouteManeuver(
                            instruction = maneuverInstruction(type, direction, roadName),
                            roadName = roadName,
                            distanceMeters = step.optDouble("distance", 0.0),
                            direction = direction,
                            coordinate = coordinate
                        )
                    )
                }
            }
        }
    }

    private fun maneuverDirection(type: String, modifier: String): RouteManeuver.Direction = when {
        type == "arrive" -> RouteManeuver.Direction.ARRIVE
        modifier == "uturn" -> RouteManeuver.Direction.UTURN
        modifier == "sharp left" -> RouteManeuver.Direction.SHARP_LEFT
        modifier == "slight left" -> RouteManeuver.Direction.SLIGHT_LEFT
        modifier == "left" -> RouteManeuver.Direction.LEFT
        modifier == "sharp right" -> RouteManeuver.Direction.SHARP_RIGHT
        modifier == "slight right" -> RouteManeuver.Direction.SLIGHT_RIGHT
        modifier == "right" -> RouteManeuver.Direction.RIGHT
        else -> RouteManeuver.Direction.STRAIGHT
    }

    private fun maneuverInstruction(
        type: String,
        direction: RouteManeuver.Direction,
        roadName: String?
    ): String {
        val action = when {
            type == "depart" -> "حرکت را آغاز کنید"
            direction == RouteManeuver.Direction.ARRIVE -> "به مقصد می‌رسید"
            direction == RouteManeuver.Direction.UTURN -> "دور بزنید"
            direction == RouteManeuver.Direction.SHARP_LEFT -> "به چپ تند بپیچید"
            direction == RouteManeuver.Direction.SLIGHT_LEFT -> "کمی به چپ بروید"
            direction == RouteManeuver.Direction.LEFT -> "به چپ بپیچید"
            direction == RouteManeuver.Direction.SHARP_RIGHT -> "به راست تند بپیچید"
            direction == RouteManeuver.Direction.SLIGHT_RIGHT -> "کمی به راست بروید"
            direction == RouteManeuver.Direction.RIGHT -> "به راست بپیچید"
            type == "roundabout" || type == "rotary" -> "وارد میدان شوید"
            else -> "مستقیم ادامه دهید"
        }
        return roadName?.let { "$action، سپس وارد $it شوید" } ?: action
    }

    private fun String.toHttpUrlString(): String = trim().trimEnd('/')

    private companion object {
        const val USER_AGENT = "NV-Android/0.5 (hamednikravesh3@gmail.com)"
    }
}

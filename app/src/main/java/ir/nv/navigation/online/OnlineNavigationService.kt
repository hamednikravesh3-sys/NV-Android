package ir.nv.navigation.online

import ir.nv.navigation.BuildConfig
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.data.PlaceCodes
import ir.nv.navigation.routing.RouteDecisionEngine
import ir.nv.navigation.traffic.LiveTrafficService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

class OnlineNavigationService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val liveTraffic = LiveTrafficService()
    private val searchCache = object : LinkedHashMap<String, List<Place>>(80, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Place>>?): Boolean = size > 80
    }

    suspend fun search(query: String, limit: Int = 20): List<Place> = withContext(Dispatchers.IO) {
        val onlineIdentity = PlaceCodes.publicCode(query)?.let(PlaceCodes::onlineIdentity)
        if (onlineIdentity != null) {
            return@withContext searchNominatimIdentity(onlineIdentity).take(limit)
        }
        val q = normalizeQuery(query)
        if (q.length < 2) return@withContext emptyList()
        synchronized(searchCache) { searchCache[q]?.let { return@withContext it } }

        val failures = mutableListOf<String>()
        val generic = runCatching { searchPhoton(q, limit) }
            .onFailure { failures += "Photon: ${it.message}" }
            .getOrDefault(emptyList())
        val roads = if (looksLikeRoadQuery(q)) {
            runCatching { searchPhoton(q, limit, osmTag = "highway") }
                .onFailure { failures += "Photon roads: ${it.message}" }
                .getOrDefault(emptyList())
        } else emptyList()
        val iranQualified = if ((generic + roads).size < 6 && !q.contains("ایران")) {
            runCatching { searchPhoton("$q ایران", limit) }.getOrDefault(emptyList())
        } else emptyList()
        val photonResults = generic + roads + iranQualified
        val fallback = if (photonResults.size < 4) {
            runCatching { searchNominatim(q, limit) }
                .onFailure { failures += "Nominatim: ${it.message}" }
                .getOrDefault(emptyList())
        } else emptyList()
        val results = (photonResults + fallback)
            .filter { isInsideIran(it.coordinate) }
            .distinctBy(::placeIdentity)
            .sortedWith(compareBy<Place> { searchScore(it, q) }.thenBy { it.name.length }.thenBy { it.code })
            .take(limit)
        if (results.isEmpty() && failures.isNotEmpty()) throw IOException("سرویس جست‌وجو پاسخ نداد")
        synchronized(searchCache) { searchCache[q] = results }
        results
    }

    private fun searchPhoton(query: String, limit: Int, osmTag: String? = null): List<Place> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val tag = osmTag?.let {
            "&osm_tag=${URLEncoder.encode(it, StandardCharsets.UTF_8.toString())}"
        }.orEmpty()
        val url = BuildConfig.GEOCODING_API_URL.toHttpUrlString() +
            "?q=$encoded&limit=$limit&lang=fa&bbox=44.0,24.0,64.0,40.0$tag"
        val request = Request.Builder().url(url).searchHeaders().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            parsePhoton(JSONObject(response.body?.string().orEmpty()), query)
        }
    }

    private fun searchNominatim(query: String, limit: Int): List<Place> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = BuildConfig.GEOCODING_FALLBACK_API_URL.toHttpUrlString() +
            "/search?q=$encoded&format=jsonv2&addressdetails=1&countrycodes=ir" +
            "&limit=$limit&accept-language=fa,en"
        val request = Request.Builder().url(url).searchHeaders().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            parseNominatim(JSONArray(response.body?.string().orEmpty()))
        }
    }

    private fun searchNominatimIdentity(identity: PlaceCodes.OnlineIdentity): List<Place> {
        val prefix = when (identity.osmType) {
            "node" -> "N"
            "way" -> "W"
            else -> "R"
        }
        val url = BuildConfig.GEOCODING_FALLBACK_API_URL.toHttpUrlString() +
            "/lookup?osm_ids=$prefix${identity.osmId}&format=jsonv2&addressdetails=1&accept-language=fa,en"
        val request = Request.Builder().url(url).searchHeaders().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            parseNominatim(JSONArray(response.body?.string().orEmpty()))
        }
    }

    private fun Request.Builder.searchHeaders(): Request.Builder =
        header("User-Agent", USER_AGENT)
            .header("From", "hamednikravesh3@gmail.com")
            .header("Accept", "application/json")
            .header("Accept-Language", "fa,en")

    suspend fun route(origin: Coordinate, destination: Coordinate): Route = routes(origin, destination).first()

    suspend fun routes(origin: Coordinate, destination: Coordinate): List<Route> = withContext(Dispatchers.IO) {
        val providers = listOf(
            BuildConfig.ROUTING_API_URL,
            BuildConfig.ROUTING_FALLBACK_API_URL
        ).map { it.toHttpUrlString() }.distinct()
        val failures = mutableListOf<String>()

        providers.forEach { provider ->
            val url = provider + "/route/v1/driving/" +
                "${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}" +
                "?overview=full&geometries=geojson&steps=true&alternatives=3"
            val result = runCatching {
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    parseRoutes(JSONObject(response.body?.string().orEmpty()))
                }
            }
            result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { candidates ->
                return@withContext rankRoutes(candidates)
            }
            failures += result.exceptionOrNull()?.message ?: "پاسخ نامعتبر"
        }
        throw IOException("هیچ سرویس مسیر آنلاینی پاسخ نداد: ${failures.joinToString("، ")}")
    }

    private fun rankRoutes(routes: List<Route>): List<Route> {
        if (routes.size <= 1) return routes
        val traffic = if (liveTraffic.isConfigured()) {
            routes.map { route -> runCatching { liveTraffic.summary(route) }.getOrNull() }
        } else emptyList()
        return RouteDecisionEngine.rank(routes, traffic)
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
                val osmType = properties.optString("osm_type", "node")
                val primaryName = properties.optString("name")
                    .ifBlank { properties.optString("street") }
                    .ifBlank { properties.optString("district") }
                    .ifBlank { properties.optString("city") }
                    .ifBlank { properties.optString("county") }
                    .ifBlank { fallbackName }
                val city = properties.optString("city")
                    .ifBlank { properties.optString("town") }
                    .ifBlank { properties.optString("village") }
                    .ifBlank { properties.optString("county") }
                val district = properties.optString("district").ifBlank { properties.optString("locality") }
                val state = properties.optString("state")
                val name = buildList {
                    add(primaryName)
                    if (district.isNotBlank() && !district.equals(primaryName, true)) add(district)
                    if (city.isNotBlank() && !city.equals(primaryName, true) && !city.equals(district, true)) add(city)
                    if (state.isNotBlank() && size < 3 && !state.equals(city, true)) add(state)
                }.joinToString("، ")
                val osmKey = properties.optString("osm_key", "online")
                val osmValue = properties.optString("osm_value", "place")
                add(
                    Place(
                        code = PlaceCodes.onlineCode(osmType, osmId.absoluteValue)
                            ?: -(osmId.absoluteValue + 1L),
                        name = name,
                        coordinate = Coordinate(latitude, longitude),
                        category = "$osmKey:$osmValue"
                    )
                )
            }
        }
    }

    private fun parseNominatim(values: JSONArray): List<Place> = buildList {
        for (index in 0 until values.length()) {
            val value = values.optJSONObject(index) ?: continue
            val latitude = value.optString("lat").toDoubleOrNull() ?: continue
            val longitude = value.optString("lon").toDoubleOrNull() ?: continue
            if (latitude !in 24.0..40.0 || longitude !in 44.0..64.0) continue
            val osmId = value.optLong("osm_id", index.toLong() + 1)
            val osmType = value.optString("osm_type", "node")
            val displayName = value.optString("display_name")
                .split(',')
                .map(String::trim)
                .filter(String::isNotBlank)
                .take(3)
                .joinToString("، ")
            add(
                Place(
                    code = PlaceCodes.onlineCode(osmType, osmId.absoluteValue)
                        ?: -(osmId.absoluteValue + NOMINATIM_CODE_OFFSET),
                    name = displayName.ifBlank { "نتیجه جست‌وجو" },
                    coordinate = Coordinate(latitude, longitude),
                    category = "online:${value.optString("type", "place")}"
                )
            )
        }
    }

    private fun searchScore(place: Place, query: String): Int {
        val name = normalizeQuery(place.name)
        val category = place.category.lowercase()
        var score = when {
            name == query -> 0
            name.startsWith(query) -> 10
            name.contains(query) -> 20
            query.split(' ').all(name::contains) -> 30
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
        val latitude = (place.coordinate.latitude * 100_000).toLong()
        val longitude = (place.coordinate.longitude * 100_000).toLong()
        return "${normalizeQuery(place.name)}|$latitude|$longitude"
    }

    private fun looksLikeRoadQuery(query: String): Boolean = listOf(
        "خیابان", "خ ", "کوچه", "بلوار", "میدان", "جاده", "بزرگراه", "اتوبان",
        "راه", "چهارراه", "street", "road", "avenue", "boulevard", "highway", "alley"
    ).any { query.contains(it, ignoreCase = true) }

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

    private fun parseRoutes(root: JSONObject): List<Route> {
        if (root.optString("code") != "Ok") {
            throw IOException(root.optString("message", "مسیر پیدا نشد"))
        }
        val routes = root.optJSONArray("routes") ?: throw IOException("پاسخ مسیر خالی است")
        if (routes.length() == 0) throw IOException("برای این دو نقطه مسیر پیدا نشد")
        return buildList {
            for (index in 0 until routes.length()) {
                add(parseRoute(routes.getJSONObject(index)))
            }
        }
    }

    private fun parseRoute(value: JSONObject): Route {
        val coordinates = value.getJSONObject("geometry").getJSONArray("coordinates")
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
            distanceMeters = value.optDouble("distance", 0.0),
            travelSeconds = value.optDouble("duration", 0.0),
            maneuvers = parseManeuvers(value)
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
                            coordinate = coordinate,
                            lanes = parseLanes(step)
                        )
                    )
                }
            }
        }
    }

    private fun parseLanes(step: JSONObject): List<RouteManeuver.Lane> {
        val intersections = step.optJSONArray("intersections") ?: return emptyList()
        for (intersectionIndex in 0 until intersections.length()) {
            val lanes = intersections.optJSONObject(intersectionIndex)?.optJSONArray("lanes") ?: continue
            if (lanes.length() == 0) continue
            return buildList {
                for (laneIndex in 0 until lanes.length().coerceAtMost(6)) {
                    val lane = lanes.optJSONObject(laneIndex) ?: continue
                    val indications = lane.optJSONArray("indications")
                    val indication = indications?.optString(0).orEmpty()
                    add(
                        RouteManeuver.Lane(
                            direction = maneuverDirection("turn", indication),
                            recommended = lane.optBoolean("valid", lane.optBoolean("active", false))
                        )
                    )
                }
            }
        }
        return emptyList()
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
        const val USER_AGENT = "NV-Android/0.6 (contact: hamednikravesh3@gmail.com)"
        const val NOMINATIM_CODE_OFFSET = 4_000_000_000L
    }
}

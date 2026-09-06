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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Online POIs for route notices and nearby discovery. */
class OnlinePlacesService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    fun searchNearby(center: Coordinate, query: String, radiusMeters: Int = 18000, limit: Int = 40): List<Place> {
        val selectors = nearbySelectors(query)
        if (selectors.isEmpty()) return emptyList()
        val radius = radiusMeters.coerceIn(1000, 50000)
        val overpass = buildString {
            append("[out:json][timeout:18];(")
            selectors.forEach { selector ->
                append("nwr(around:$radius,${center.latitude},${center.longitude})")
                append(selector)
                append(";")
            }
            append(");out center tags $limit;")
        }
        return requestPlaces(overpass)
            .distinctBy { Triple(it.name, (it.coordinate.latitude * 10000).toInt(), (it.coordinate.longitude * 10000).toInt()) }
            .sortedBy { distanceSquared(center, it.coordinate) }
            .take(limit)
    }

    fun searchNamedNearby(center: Coordinate, query: String, radiusMeters: Int = 30000, limit: Int = 30): List<Place> {
        val clean = query.trim().replace("\"", "").take(80)
        if (clean.length < 2) return emptyList()
        val radius = radiusMeters.coerceIn(3000, 50000)
        val escaped = clean.replace("\\", "\\\\").replace("'", "\\'")
        val words = escaped.split(Regex("\\s+")).filter { it.length >= 2 }.take(4)
        val regex = words.joinToString(".*") { Regex.escape(it) }.ifBlank { Regex.escape(escaped) }
        val overpass = "[out:json][timeout:18];(nwr(around:$radius,${center.latitude},${center.longitude})[\"name\"~\"$regex\",i];nwr(around:$radius,${center.latitude},${center.longitude})[\"name:fa\"~\"$regex\",i];);out center tags $limit;"
        return requestPlaces(overpass)
            .distinctBy { Triple(it.name, (it.coordinate.latitude * 10000).toInt(), (it.coordinate.longitude * 10000).toInt()) }
            .sortedBy { distanceSquared(center, it.coordinate) }
            .take(limit)
    }

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
        val places = requestPlaces(query)
        val placesByTitle = places.associateBy(Place::displayName)
        return RouteInsightEngine.placesAhead(
            route = route,
            places = places,
            limit = 12,
            maxAheadMeters = MAX_AHEAD_METERS
        ).map { notice ->
            notice.copy(imageUrl = placesByTitle[notice.title]?.let { null })
        }
    }

    private fun requestPlaces(overpass: String): List<Place> {
        val request = Request.Builder()
            .url(BuildConfig.PLACES_API_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "NV-Android/${BuildConfig.VERSION_NAME}")
            .post(FormBody.Builder().add("data", overpass).build())
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Places HTTP ${response.code}" }
            val elements = JSONObject(requireNotNull(response.body).string()).optJSONArray("elements") ?: return emptyList()
            return buildList {
                for (index in 0 until elements.length()) {
                    val element = elements.optJSONObject(index) ?: continue
                    val tags = element.optJSONObject("tags") ?: continue
                    val center = element.optJSONObject("center")
                    val latitude = if (element.has("lat")) element.optDouble("lat") else center?.optDouble("lat")
                    val longitude = if (element.has("lon")) element.optDouble("lon") else center?.optDouble("lon")
                    if (latitude == null || longitude == null || latitude.isNaN() || longitude.isNaN()) continue
                    val name = tags.optString("name:fa").ifBlank { tags.optString("name") }.ifBlank { tags.optString("brand") }
                    if (name.isBlank()) continue
                    add(
                        Place(
                            code = -element.optLong("id", index.toLong() + 1L),
                            name = name,
                            coordinate = Coordinate(latitude, longitude),
                            category = category(tags) ?: "poi"
                        )
                    )
                }
            }
        }
    }

    private fun nearbySelectors(query: String): List<String> {
        val q = query.lowercase().replace('ي','ی').replace('ك','ک')
        return when {
            q.contains("اورژانس") || q.contains("بیمارستان") -> listOf("[\"amenity\"~\"^(hospital|clinic)$\"]", "[\"healthcare\"~\"^(hospital|clinic)$\"]")
            q.contains("داروخانه") -> listOf("[\"amenity\"=\"pharmacy\"]")
            q.contains("پلیس") -> listOf("[\"amenity\"=\"police\"]")
            q.contains("آتش") -> listOf("[\"amenity\"=\"fire_station\"]")
            q.contains("درمانگاه") || q.contains("کلینیک") -> listOf("[\"amenity\"=\"clinic\"]", "[\"healthcare\"=\"clinic\"]")
            q.contains("ترمینال") || q.contains("پایانه") -> listOf("[\"amenity\"=\"bus_station\"]", "[\"public_transport\"=\"station\"][\"bus\"=\"yes\"]")
            q.contains("فرودگاه") -> listOf("[\"aeroway\"~\"^(aerodrome|terminal)$\"]")
            q.contains("راه آهن") || q.contains("قطار") -> listOf("[\"railway\"=\"station\"]")
            q.contains("مترو") -> listOf("[\"railway\"=\"station\"][\"station\"=\"subway\"]", "[\"subway\"=\"yes\"]")
            q.contains("تاکسی") -> listOf("[\"amenity\"=\"taxi\"]")
            q.contains("پارکینگ") -> listOf("[\"amenity\"=\"parking\"]")
            q.contains("پمپ") || q.contains("سوخت") -> listOf("[\"amenity\"=\"fuel\"]")
            q.contains("شارژ") -> listOf("[\"amenity\"=\"charging_station\"]")
            q.contains("رستوران") -> listOf("[\"amenity\"=\"restaurant\"]")
            q.contains("کافه") -> listOf("[\"amenity\"=\"cafe\"]")
            q.contains("هتل") || q.contains("اقامت") -> listOf("[\"tourism\"~\"^(hotel|guest_house|hostel)$\"]")
            q.contains("فروشگاه") -> listOf("[\"shop\"]")
            q.contains("مرکز خرید") -> listOf("[\"shop\"=\"mall\"]", "[\"building\"=\"retail\"]")
            q.contains("بانک") -> listOf("[\"amenity\"=\"bank\"]")
            q.contains("خودپرداز") -> listOf("[\"amenity\"=\"atm\"]")
            q.contains("نانوایی") -> listOf("[\"shop\"=\"bakery\"]")
            q.contains("مدرسه") -> listOf("[\"amenity\"=\"school\"]")
            q.contains("دانشگاه") -> listOf("[\"amenity\"=\"university\"]")
            q.contains("مسجد") -> listOf("[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"]")
            q.contains("پست") -> listOf("[\"amenity\"=\"post_office\"]")
            q.contains("سرویس") || q.contains("دستشویی") -> listOf("[\"amenity\"=\"toilets\"]")
            q.contains("تعمیرگاه") -> listOf("[\"shop\"=\"car_repair\"]")
            q.contains("ورزشگاه") -> listOf("[\"leisure\"~\"^(stadium|sports_centre)$\"]")
            q.contains("سینما") -> listOf("[\"amenity\"=\"cinema\"]")
            q.contains("موزه") -> listOf("[\"tourism\"=\"museum\"]")
            q.contains("پارک") -> listOf("[\"leisure\"=\"park\"]")
            q.contains("تاریخی") || q.contains("اثر") -> listOf("[\"historic\"]")
            q.contains("طبیعت") || q.contains("کوه") || q.contains("منظره") -> listOf("[\"natural\"]", "[\"tourism\"=\"viewpoint\"]")
            q.contains("تفریح") -> listOf("[\"leisure\"]", "[\"tourism\"=\"attraction\"]")
            q.contains("ساحل") -> listOf("[\"natural\"=\"beach\"]")
            q.contains("پل") -> listOf("[\"bridge\"=\"yes\"][\"name\"]")
            q.contains("دیدنی") || q.contains("گردش") || q.contains("جاذبه") -> listOf("[\"tourism\"]", "[\"historic\"]", "[\"natural\"]")
            else -> listOf("[\"name\"]")
        }
    }

    private fun category(tags: JSONObject): String? = when {
        tags.has("tourism") -> "tourism:${tags.optString("tourism")}"
        tags.has("historic") -> "historic:${tags.optString("historic")}"
        tags.has("natural") -> "natural:${tags.optString("natural")}"
        tags.has("amenity") -> "amenity:${tags.optString("amenity")}"
        tags.has("shop") -> "shop:${tags.optString("shop")}"
        tags.has("leisure") -> "leisure:${tags.optString("leisure")}"
        tags.has("railway") -> "railway:${tags.optString("railway")}"
        tags.has("aeroway") -> "aeroway:${tags.optString("aeroway")}"
        else -> null
    }

    private fun distanceSquared(a: Coordinate, b: Coordinate): Double {
        val dx = a.longitude - b.longitude
        val dy = a.latitude - b.latitude
        return dx * dx + dy * dy
    }

    private companion object {
        val SAMPLE_DISTANCES = listOf(1_000.0, 4_000.0, 7_000.0, 10_000.0)
        const val MAX_AHEAD_METERS = 10_000.0
    }
}

package ir.nv.navigation.online

import ir.nv.navigation.BuildConfig
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.routing.RouteInsightEngine
import ir.nv.navigation.routing.RoutePointSampler
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Online POIs for route notices and nearby discovery. Google Places is preferred when configured; OSM remains the resilient fallback. */
class OnlinePlacesService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    fun googleConfigured(): Boolean = BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank()

    fun searchNearby(center: Coordinate, query: String, radiusMeters: Int = 18000, limit: Int = 40): List<Place> {
        val google = if (googleConfigured()) {
            runCatching { requestGoogleNearby(center, query, radiusMeters, limit) }.getOrDefault(emptyList())
        } else emptyList()

        val osm = runCatching { requestOsmNearby(center, query, radiusMeters, limit) }.getOrDefault(emptyList())
        val merged = (google + osm)
            .distinctBy { Triple(normalizeName(it.name), (it.coordinate.latitude * 10000).toInt(), (it.coordinate.longitude * 10000).toInt()) }
            .sortedBy { distanceSquared(center, it.coordinate) }
            .take(limit)

        if (merged.isNotEmpty()) return merged
        if (googleConfigured()) throw IllegalStateException("Google Places و سرویس پشتیبان OSM فعلاً نتیجه‌ای برنگرداندند")
        throw IllegalStateException("سرویس مکان‌های اطراف فعلاً نتیجه‌ای برنگرداند؛ برای Google Places کلید API لازم است")
    }

    private fun requestOsmNearby(center: Coordinate, query: String, radiusMeters: Int, limit: Int): List<Place> {
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
    }

    private fun requestGoogleNearby(center: Coordinate, query: String, radiusMeters: Int, limit: Int): List<Place> {
        val types = googleTypes(query)
        if (types.isEmpty()) return emptyList()
        val body = JSONObject().apply {
            put("includedTypes", JSONArray(types.take(8)))
            put("maxResultCount", limit.coerceIn(1, 20))
            put("rankPreference", "DISTANCE")
            put("locationRestriction", JSONObject().apply {
                put("circle", JSONObject().apply {
                    put("center", JSONObject().apply {
                        put("latitude", center.latitude)
                        put("longitude", center.longitude)
                    })
                    put("radius", radiusMeters.coerceIn(500, 50000).toDouble())
                })
            })
            put("languageCode", "fa")
            put("regionCode", "IR")
        }
        val request = Request.Builder()
            .url(BuildConfig.GOOGLE_PLACES_API_URL)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Goog-Api-Key", BuildConfig.GOOGLE_MAPS_API_KEY)
            .header("X-Goog-FieldMask", "places.id,places.displayName,places.location,places.primaryType")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful) { "Google Places HTTP ${response.code}" }
            val places = JSONObject(raw).optJSONArray("places") ?: return emptyList()
            return buildList {
                for (i in 0 until places.length()) {
                    val item = places.optJSONObject(i) ?: continue
                    val location = item.optJSONObject("location") ?: continue
                    val lat = location.optDouble("latitude", Double.NaN)
                    val lon = location.optDouble("longitude", Double.NaN)
                    if (lat.isNaN() || lon.isNaN()) continue
                    val name = item.optJSONObject("displayName")?.optString("text").orEmpty().trim()
                    if (name.isBlank()) continue
                    val id = item.optString("id").hashCode().toLong()
                    add(
                        Place(
                            code = -9_000_000_000L - kotlin.math.abs(id),
                            name = name,
                            coordinate = Coordinate(lat, lon),
                            category = "google:${item.optString("primaryType", "poi")}"
                        )
                    )
                }
            }
        }
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
        return RouteInsightEngine.placesAhead(route, places, limit = 12, maxAheadMeters = MAX_AHEAD_METERS)
    }

    private fun requestPlaces(overpass: String): List<Place> {
        val endpoints = listOf(
            BuildConfig.PLACES_API_URL,
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
        ).distinct()
        var lastReason = "سرویس مکان‌های اطراف پاسخ نداد"
        for (endpoint in endpoints) {
            val result = runCatching {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .header("User-Agent", "NV-Android/${BuildConfig.VERSION_NAME}")
                    .post(FormBody.Builder().add("data", overpass).build())
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 429 || response.code in 500..599) throw IllegalStateException("HTTP ${response.code}")
                    check(response.isSuccessful) { "Places HTTP ${response.code}" }
                    val elements = JSONObject(requireNotNull(response.body).string()).optJSONArray("elements")
                        ?: return@use emptyList<Place>()
                    buildList {
                        for (index in 0 until elements.length()) {
                            val element = elements.optJSONObject(index) ?: continue
                            val tags = element.optJSONObject("tags") ?: continue
                            val c = element.optJSONObject("center")
                            val latitude = if (element.has("lat")) element.optDouble("lat") else c?.optDouble("lat")
                            val longitude = if (element.has("lon")) element.optDouble("lon") else c?.optDouble("lon")
                            if (latitude == null || longitude == null || latitude.isNaN() || longitude.isNaN()) continue
                            val name = tags.optString("name:fa").ifBlank { tags.optString("name") }.ifBlank { tags.optString("brand") }
                            if (name.isBlank()) continue
                            add(Place(code = -element.optLong("id", index.toLong() + 1L), name = name, coordinate = Coordinate(latitude, longitude), category = category(tags) ?: "poi"))
                        }
                    }
                }
            }
            result.onSuccess { return it }.onFailure { lastReason = it.message ?: lastReason }
        }
        throw IllegalStateException("سرویس OSM موقتاً شلوغ است؛ دوباره تلاش کنید ($lastReason)")
    }

    private fun googleTypes(query: String): List<String> {
        val q = normalizeName(query)
        return when {
            q.contains("اورژانس") || q.contains("بیمارستان") -> listOf("hospital")
            q.contains("داروخانه") -> listOf("pharmacy")
            q.contains("پلیس") -> listOf("police")
            q.contains("آتش") -> listOf("fire_station")
            q.contains("پزشک") || q.contains("دکتر") -> listOf("doctor")
            q.contains("دندان") -> listOf("dentist")
            q.contains("آزمایشگاه") -> listOf("medical_lab")
            q.contains("دامپزشک") -> listOf("veterinary_care")
            q.contains("ترمینال") || q.contains("پایانه") || q.contains("اتوبوس") -> listOf("bus_station")
            q.contains("فرودگاه") -> listOf("airport")
            q.contains("راه آهن") || q.contains("قطار") -> listOf("train_station")
            q.contains("مترو") -> listOf("subway_station")
            q.contains("تاکسی") -> listOf("taxi_stand")
            q.contains("پارکینگ") -> listOf("parking")
            q.contains("پمپ") || q.contains("سوخت") -> listOf("gas_station")
            q.contains("شارژ") -> listOf("electric_vehicle_charging_station")
            q.contains("رستوران") -> listOf("restaurant")
            q.contains("فست") -> listOf("fast_food_restaurant")
            q.contains("کافه") -> listOf("cafe")
            q.contains("هتل") || q.contains("اقامت") -> listOf("hotel")
            q.contains("سوپر") -> listOf("supermarket")
            q.contains("فروشگاه") -> listOf("store")
            q.contains("مرکز خرید") -> listOf("shopping_mall")
            q.contains("بانک") -> listOf("bank")
            q.contains("خودپرداز") -> listOf("atm")
            q.contains("نانوایی") -> listOf("bakery")
            q.contains("مدرسه") -> listOf("school")
            q.contains("دانشگاه") -> listOf("university")
            q.contains("مسجد") -> listOf("mosque")
            q.contains("پست") -> listOf("post_office")
            q.contains("سرویس") || q.contains("دستشویی") -> listOf("public_bathroom")
            q.contains("تعمیرگاه") -> listOf("car_repair")
            q.contains("کارواش") -> listOf("car_wash")
            q.contains("ورزشگاه") -> listOf("stadium")
            q.contains("سینما") -> listOf("movie_theater")
            q.contains("موزه") -> listOf("museum")
            q.contains("پارک") -> listOf("park")
            q.contains("تفریح") || q.contains("شهربازی") -> listOf("amusement_park")
            q.contains("دیدنی") || q.contains("گردش") || q.contains("جاذبه") || q.contains("تاریخی") || q.contains("طبیعت") -> listOf("tourist_attraction")
            else -> emptyList()
        }
    }

    private fun nearbySelectors(query: String): List<String> {
        val q = normalizeName(query)
        return when {
            q.contains("اورژانس") || q.contains("بیمارستان") -> listOf("[\"amenity\"~\"^(hospital|clinic)$\"]", "[\"healthcare\"~\"^(hospital|clinic)$\"]")
            q.contains("داروخانه") -> listOf("[\"amenity\"=\"pharmacy\"]")
            q.contains("پلیس") -> listOf("[\"amenity\"=\"police\"]")
            q.contains("آتش") -> listOf("[\"amenity\"=\"fire_station\"]")
            q.contains("پزشک") || q.contains("دکتر") -> listOf("[\"amenity\"=\"doctors\"]", "[\"healthcare\"=\"doctor\"]")
            q.contains("دندان") -> listOf("[\"amenity\"=\"dentist\"]", "[\"healthcare\"=\"dentist\"]")
            q.contains("آزمایشگاه") -> listOf("[\"healthcare\"=\"laboratory\"]")
            q.contains("دامپزشک") -> listOf("[\"amenity\"=\"veterinary\"]")
            q.contains("درمانگاه") || q.contains("کلینیک") -> listOf("[\"amenity\"=\"clinic\"]", "[\"healthcare\"=\"clinic\"]")
            q.contains("ترمینال") || q.contains("پایانه") -> listOf("[\"amenity\"=\"bus_station\"]", "[\"public_transport\"=\"station\"][\"bus\"=\"yes\"]")
            q.contains("ایستگاه اتوبوس") -> listOf("[\"highway\"=\"bus_stop\"]", "[\"public_transport\"=\"platform\"][\"bus\"=\"yes\"]")
            q.contains("فرودگاه") -> listOf("[\"aeroway\"~\"^(aerodrome|terminal)$\"]")
            q.contains("راه آهن") || q.contains("قطار") -> listOf("[\"railway\"=\"station\"]")
            q.contains("مترو") -> listOf("[\"railway\"=\"station\"][\"station\"=\"subway\"]", "[\"subway\"=\"yes\"]")
            q.contains("تاکسی") -> listOf("[\"amenity\"=\"taxi\"]")
            q.contains("پارکینگ") -> listOf("[\"amenity\"=\"parking\"]")
            q.contains("پمپ") || q.contains("سوخت") -> listOf("[\"amenity\"=\"fuel\"]")
            q.contains("شارژ") -> listOf("[\"amenity\"=\"charging_station\"]")
            q.contains("رستوران") -> listOf("[\"amenity\"=\"restaurant\"]")
            q.contains("فست") -> listOf("[\"amenity\"=\"fast_food\"]")
            q.contains("کافه") -> listOf("[\"amenity\"=\"cafe\"]")
            q.contains("هتل") || q.contains("اقامت") -> listOf("[\"tourism\"~\"^(hotel|guest_house|hostel)$\"]")
            q.contains("سوپر") -> listOf("[\"shop\"~\"^(supermarket|convenience)$\"]")
            q.contains("فروشگاه") -> listOf("[\"shop\"]")
            q.contains("مرکز خرید") -> listOf("[\"shop\"=\"mall\"]", "[\"building\"=\"retail\"]")
            q.contains("بانک") -> listOf("[\"amenity\"=\"bank\"]")
            q.contains("خودپرداز") -> listOf("[\"amenity\"=\"atm\"]")
            q.contains("نانوایی") -> listOf("[\"shop\"=\"bakery\"]")
            q.contains("مدرسه") -> listOf("[\"amenity\"=\"school\"]")
            q.contains("دانشگاه") -> listOf("[\"amenity\"=\"university\"]")
            q.contains("مسجد") -> listOf("[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"]")
            q.contains("پست") -> listOf("[\"amenity\"=\"post_office\"]")
            q.contains("کتابخانه") -> listOf("[\"amenity\"=\"library\"]")
            q.contains("سرویس") || q.contains("دستشویی") -> listOf("[\"amenity\"=\"toilets\"]")
            q.contains("تعمیرگاه") -> listOf("[\"shop\"=\"car_repair\"]")
            q.contains("کارواش") -> listOf("[\"amenity\"=\"car_wash\"]")
            q.contains("لاستیک") -> listOf("[\"shop\"=\"tyres\"]")
            q.contains("آرایشگاه") -> listOf("[\"shop\"=\"hairdresser\"]")
            q.contains("خشکشویی") -> listOf("[\"shop\"~\"^(laundry|dry_cleaning)$\"]")
            q.contains("ورزشگاه") -> listOf("[\"leisure\"~\"^(stadium|sports_centre)$\"]")
            q.contains("استخر") -> listOf("[\"leisure\"=\"swimming_pool\"]")
            q.contains("سینما") -> listOf("[\"amenity\"=\"cinema\"]")
            q.contains("موزه") -> listOf("[\"tourism\"=\"museum\"]")
            q.contains("پارک") -> listOf("[\"leisure\"=\"park\"]")
            q.contains("شهربازی") -> listOf("[\"tourism\"=\"theme_park\"]", "[\"leisure\"=\"amusement_arcade\"]")
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

    private fun normalizeName(value: String): String = value.lowercase().replace('ي', 'ی').replace('ك', 'ک').replace("‌", " ").trim()

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

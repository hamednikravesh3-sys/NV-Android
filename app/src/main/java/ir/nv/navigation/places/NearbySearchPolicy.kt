package ir.nv.navigation.places

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class NearbyScope(val title: String) {
    AROUND_ME("اطراف من"),
    ALONG_ROUTE("در طول مسیر"),
    NEAR_DESTINATION("نزدیک مقصد"),
    NEAR_ORIGIN("نزدیک مبدأ")
}

enum class NearbyCategory(
    val id: String,
    val title: String,
    val query: String,
    val offlineCategoryPatterns: List<String>
) {
    EMERGENCY("emergency", "اورژانس", "اورژانس", listOf("amenity:hospital", "amenity:clinic", "healthcare:hospital", "healthcare:clinic", "emergency:%")),
    HOSPITAL("hospital", "بیمارستان", "بیمارستان", listOf("amenity:hospital", "healthcare:hospital", "amenity:clinic", "healthcare:clinic")),
    PHARMACY("pharmacy", "داروخانه", "داروخانه", listOf("amenity:pharmacy", "healthcare:pharmacy")),
    POLICE("police", "پلیس", "پلیس", listOf("amenity:police")),
    FIRE("fire", "آتش‌نشانی", "آتش نشانی", listOf("amenity:fire_station")),
    RESCUE("rescue", "امداد و نجات", "امداد و نجات", listOf("emergency:%", "amenity:rescue_station")),
    PARKING("parking", "پارکینگ", "پارکینگ", listOf("amenity:parking", "amenity:parking_entrance")),
    FUEL("fuel", "سوخت", "پمپ بنزین", listOf("amenity:fuel")),
    EV("ev", "شارژ خودرو برقی", "شارژ خودرو", listOf("amenity:charging_station")),
    PARKS("parks", "پارک", "پارک", listOf("leisure:park", "leisure:garden")),
    RECREATION("recreation", "تفریح", "تفریح", listOf("leisure:%", "tourism:theme_park", "tourism:attraction")),
    RESTAURANTS("restaurants", "رستوران", "رستوران", listOf("amenity:restaurant", "amenity:fast_food")),
    CAFE("cafe", "کافه", "کافه", listOf("amenity:cafe")),
    HOTEL("hotel", "هتل و اقامت", "هتل", listOf("tourism:hotel", "tourism:guest_house", "tourism:hostel")),
    SHOPPING("shopping", "خرید", "مرکز خرید", listOf("shop:%", "building:retail")),
    CINEMA("cinema", "سینما", "سینما", listOf("amenity:cinema")),
    ATTRACTIONS("attractions", "جاذبه‌ها", "جاذبه گردشگری", listOf("tourism:%", "historic:%", "natural:%")),
    REPAIR("repair", "تعمیرگاه", "تعمیرگاه", listOf("shop:car_repair", "amenity:car_repair")),
    BANK("bank", "بانک", "بانک", listOf("amenity:bank")),
    ATM("atm", "خودپرداز", "خودپرداز", listOf("amenity:atm")),
    SERVICES("services", "خدمات", "خدمات", listOf("amenity:post_office", "amenity:toilets", "amenity:library", "amenity:car_wash", "shop:laundry", "shop:dry_cleaning"));

    companion object {
        fun fromId(value: String): NearbyCategory? = entries.firstOrNull { it.id == value }
    }
}

object NearbySearchPolicy {
    val radiusStepsMeters: List<Int> = listOf(5_000, 10_000, 25_000, 50_000, 100_000)

    fun requireSupportedRadius(radiusMeters: Int): Int {
        require(radiusMeters in radiusStepsMeters) {
            "Nearby radius must be one of ${radiusStepsMeters.joinToString()} metres"
        }
        return radiusMeters
    }

    fun progressiveRadiiMeters(selectedRadiusMeters: Int): List<Int> {
        requireSupportedRadius(selectedRadiusMeters)
        return radiusStepsMeters.takeWhile { it <= selectedRadiusMeters }
    }

    fun filterWithinRadius(center: Coordinate, places: List<Place>, radiusMeters: Int): List<Place> {
        requireSupportedRadius(radiusMeters)
        return places.mapNotNull { place ->
            val distance = distanceMeters(center, place.coordinate)
            place.takeIf { distance <= radiusMeters + 1.0 }?.copy(distance = distance)
        }.sortedBy { it.distance ?: Double.MAX_VALUE }
    }

    fun filterAlongRoute(route: Route, places: List<Place>, radiusMeters: Int): List<Place> {
        requireSupportedRadius(radiusMeters)
        if (route.points.size < 2) return emptyList()
        return places.mapNotNull { place ->
            val distance = distanceToRouteMeters(route, place.coordinate)
            place.takeIf { distance <= radiusMeters + 1.0 }?.copy(distance = distance)
        }.sortedBy { it.distance ?: Double.MAX_VALUE }
    }

    fun distanceMeters(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(min(1.0, h)))
    }

    fun distanceToRouteMeters(route: Route, point: Coordinate): Double {
        if (route.points.isEmpty()) return Double.POSITIVE_INFINITY
        if (route.points.size == 1) return distanceMeters(route.points.first(), point)
        return route.points.zipWithNext().minOf { (start, end) -> distanceToSegmentMeters(start, end, point) }
    }

    private fun distanceToSegmentMeters(start: Coordinate, end: Coordinate, point: Coordinate): Double {
        val meanLatitude = Math.toRadians((start.latitude + end.latitude + point.latitude) / 3.0)
        val metersPerLongitude = METERS_PER_DEGREE * cos(meanLatitude).let { kotlin.math.abs(it).coerceAtLeast(0.01) }
        val ax = start.longitude * metersPerLongitude
        val ay = start.latitude * METERS_PER_DEGREE
        val bx = end.longitude * metersPerLongitude
        val by = end.latitude * METERS_PER_DEGREE
        val px = point.longitude * metersPerLongitude
        val py = point.latitude * METERS_PER_DEGREE
        val dx = bx - ax
        val dy = by - ay
        val denominator = dx * dx + dy * dy
        val fraction = if (denominator <= 0.01) 0.0 else
            (((px - ax) * dx + (py - ay) * dy) / denominator).coerceIn(0.0, 1.0)
        val projectedX = ax + fraction * dx
        val projectedY = ay + fraction * dy
        val x = px - projectedX
        val y = py - projectedY
        return sqrt(x * x + y * y)
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val METERS_PER_DEGREE = 111_320.0
}

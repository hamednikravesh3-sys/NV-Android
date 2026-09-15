package ir.nv.navigation.routing

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.TrafficSegment
import ir.nv.navigation.core.TrafficSummary
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object RouteInsightEngine {
    private data class Candidate(
        val notice: RouteNotice,
        val progressMeters: Double,
        val offsetMeters: Double,
        val attraction: Boolean,
        val score: Double
    )

    fun placesAhead(
        route: Route,
        places: List<Place>,
        limit: Int = 8,
        maxAheadMeters: Double = DEFAULT_MAX_AHEAD_METERS
    ): List<RouteNotice> {
        if (route.points.size < 2) return emptyList()
        val candidates = places.mapNotNull { place ->
            val projection = project(route.points, place.coordinate) ?: return@mapNotNull null
            if (projection.offsetMeters > PLACE_CORRIDOR_METERS) return@mapNotNull null
            if (projection.progressMeters !in MIN_AHEAD_METERS..maxAheadMeters) return@mapNotNull null

            val service = isService(place.category)
            val attraction = !service
            val detail = buildDetail(place.category, projection.offsetMeters, attraction)
            val score = if (attraction) attractionScore(place.category, projection) else projection.progressMeters
            Candidate(
                notice = RouteNotice(
                    title = place.displayName,
                    detail = detail,
                    distanceAheadMeters = projection.progressMeters,
                    kind = if (service) RouteNotice.Kind.SERVICE else RouteNotice.Kind.ATTRACTION,
                    placeCode = place.code.takeIf { it > 0 }
                ),
                progressMeters = projection.progressMeters,
                offsetMeters = projection.offsetMeters,
                attraction = attraction,
                score = score
            )
        }

        val services = candidates.filterNot { it.attraction }
            .sortedBy { it.progressMeters }
            .take((limit / 2).coerceAtLeast(2))
        val attractions = candidates.filter { it.attraction }
            .sortedWith(compareBy<Candidate> { it.score }.thenBy { it.progressMeters })
            .distinctBy { attractionBucket(it) }
            .take((limit - services.size).coerceAtLeast(0))

        return (services + attractions)
            .distinctBy { Triple(it.notice.title, it.notice.kind, (it.progressMeters / 1_000).toInt()) }
            .sortedBy { it.progressMeters }
            .take(limit)
            .map { it.notice }
    }

    fun attractionsAhead(route: Route, places: List<Place>, limit: Int = 6): List<RouteNotice> =
        placesAhead(route, places, limit * 2)
            .filter { it.kind == RouteNotice.Kind.ATTRACTION }
            .take(limit)

    fun summarizeTraffic(segments: List<TrafficSegment>): TrafficSummary = TrafficSummary(
        lengthMeters = segments.sumOf { it.lengthMeters.coerceAtLeast(0.0) },
        delaySeconds = segments.sumOf { it.delaySeconds.coerceAtLeast(0.0) }
    )

    private data class Projection(val progressMeters: Double, val offsetMeters: Double)

    private fun project(route: List<Coordinate>, point: Coordinate): Projection? {
        var best: Projection? = null
        var completed = 0.0
        route.zipWithNext().forEach { (start, end) ->
            val segmentLength = haversine(start, end)
            if (segmentLength <= 0.01) return@forEach
            val meanLatitude = Math.toRadians((start.latitude + end.latitude + point.latitude) / 3.0)
            val metersPerLongitude = METERS_PER_DEGREE * cos(meanLatitude)
            val ax = start.longitude * metersPerLongitude
            val ay = start.latitude * METERS_PER_DEGREE
            val bx = end.longitude * metersPerLongitude
            val by = end.latitude * METERS_PER_DEGREE
            val px = point.longitude * metersPerLongitude
            val py = point.latitude * METERS_PER_DEGREE
            val dx = bx - ax
            val dy = by - ay
            val fraction = (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy))
                .coerceIn(0.0, 1.0)
            val offset = sqrt(
                (px - (ax + fraction * dx)) * (px - (ax + fraction * dx)) +
                    (py - (ay + fraction * dy)) * (py - (ay + fraction * dy))
            )
            val candidate = Projection(completed + fraction * segmentLength, offset)
            if (best == null || candidate.offsetMeters < requireNotNull(best).offsetMeters) {
                best = candidate
            }
            completed += segmentLength
        }
        return best
    }

    private fun attractionScore(category: String, projection: Projection): Double {
        val categoryPenalty = when {
            category.startsWith("historic:") -> 0.0
            category.startsWith("tourism:viewpoint") -> 50.0
            category.startsWith("tourism:attraction") -> 80.0
            category.startsWith("tourism:museum") -> 120.0
            category.startsWith("natural:") -> 160.0
            category.startsWith("tourism:") -> 220.0
            else -> 350.0
        }
        // A short detour is more useful than a famous-looking category several km off-route.
        return projection.offsetMeters * 1.8 + projection.progressMeters * 0.015 + categoryPenalty
    }

    private fun buildDetail(category: String, offsetMeters: Double, attraction: Boolean): String {
        val base = categoryLabel(category)
        if (!attraction) return base
        val detour = when {
            offsetMeters < 250 -> "در امتداد مسیر"
            offsetMeters < 1_000 -> "حدود ${offsetMeters.roundToInt()} متر از مسیر"
            else -> "حدود ${"%.1f".format(offsetMeters / 1_000.0)} کیلومتر از مسیر"
        }
        return "$base • $detour"
    }

    private fun attractionBucket(candidate: Candidate): String {
        val category = candidate.notice.detail.substringBefore(" • ")
        val distanceBand = (candidate.progressMeters / 20_000.0).toInt()
        return "$category:$distanceBand"
    }

    private fun categoryLabel(value: String): String = when {
        value.startsWith("tourism:viewpoint") -> "چشم‌انداز پیشنهادی NV"
        value.startsWith("tourism:museum") -> "موزه پیشنهادی NV"
        value.startsWith("tourism:") -> "جاذبه گردشگری پیشنهادی NV"
        value.startsWith("historic:") -> "مکان تاریخی پیشنهادی NV"
        value.startsWith("natural:") -> "دیدنی طبیعی پیشنهادی NV"
        value == "amenity:fuel" -> "جایگاه سوخت"
        value == "amenity:parking" -> "پارکینگ"
        value == "amenity:hospital" || value == "amenity:clinic" -> "مرکز درمانی"
        value == "amenity:pharmacy" -> "داروخانه"
        value == "amenity:restaurant" || value == "amenity:cafe" -> "غذا و استراحت"
        value == "amenity:toilets" -> "سرویس بهداشتی"
        value.startsWith("shop:") -> "خدمات مسیر"
        else -> "دیدنی پیشنهادی مسیر"
    }

    private fun isService(category: String): Boolean =
        category.startsWith("amenity:") || category.startsWith("shop:")

    private fun haversine(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(min(1.0, h)))
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val METERS_PER_DEGREE = 111_320.0
    private const val PLACE_CORRIDOR_METERS = 3_000.0
    private const val MIN_AHEAD_METERS = 500.0
    private const val DEFAULT_MAX_AHEAD_METERS = 100_000.0
}

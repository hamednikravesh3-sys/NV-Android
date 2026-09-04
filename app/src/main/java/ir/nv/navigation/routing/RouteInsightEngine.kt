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
import kotlin.math.sin
import kotlin.math.sqrt

object RouteInsightEngine {
    fun attractionsAhead(route: Route, places: List<Place>, limit: Int = 6): List<RouteNotice> {
        if (route.points.size < 2) return emptyList()
        return places.mapNotNull { place ->
            val projection = project(route.points, place.coordinate) ?: return@mapNotNull null
            if (projection.offsetMeters > ATTRACTION_CORRIDOR_METERS) return@mapNotNull null
            if (projection.progressMeters !in MIN_AHEAD_METERS..MAX_AHEAD_METERS) return@mapNotNull null
            RouteNotice(
                title = place.displayName,
                detail = categoryLabel(place.category),
                distanceAheadMeters = projection.progressMeters,
                kind = RouteNotice.Kind.ATTRACTION,
                placeCode = place.code.takeIf { it > 0 }
            )
        }.sortedBy { it.distanceAheadMeters }.take(limit)
    }

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

    private fun categoryLabel(value: String): String = when {
        value.startsWith("tourism:") -> "جاذبه گردشگری"
        value.startsWith("historic:") -> "مکان تاریخی"
        value.startsWith("natural:") -> "دیدنی طبیعی"
        else -> "دیدنی مسیر"
    }

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
    private const val ATTRACTION_CORRIDOR_METERS = 3_000.0
    private const val MIN_AHEAD_METERS = 500.0
    private const val MAX_AHEAD_METERS = 100_000.0
}

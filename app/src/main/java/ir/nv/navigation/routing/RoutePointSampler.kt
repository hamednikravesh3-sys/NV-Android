package ir.nv.navigation.routing

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Finds a real coordinate a requested road-distance ahead on the route polyline. */
object RoutePointSampler {
    data class Sample(val coordinate: Coordinate, val distanceAheadMeters: Double)

    fun pointAhead(route: Route, requestedMeters: Double): Sample? {
        if (route.points.size < 2 || requestedMeters < 0.0) return null
        var completed = 0.0
        route.points.zipWithNext().forEach { (start, end) ->
            val segmentLength = haversine(start, end)
            if (segmentLength <= 0.01) return@forEach
            if (completed + segmentLength >= requestedMeters) {
                val fraction = ((requestedMeters - completed) / segmentLength).coerceIn(0.0, 1.0)
                return Sample(
                    coordinate = Coordinate(
                        latitude = start.latitude + (end.latitude - start.latitude) * fraction,
                        longitude = start.longitude + (end.longitude - start.longitude) * fraction
                    ),
                    distanceAheadMeters = requestedMeters
                )
            }
            completed += segmentLength
        }
        return Sample(route.points.last(), completed)
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
}

package ir.nv.navigation.routing

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class RouteProgress(
    val maneuverIndex: Int,
    val distanceToManeuverMeters: Double,
    val remainingDistanceMeters: Double,
    val remainingSeconds: Double,
    val offRoute: Boolean
)

object RouteProgressEngine {
    fun calculate(route: Route, location: Coordinate): RouteProgress? {
        if (route.points.size < 2) return null
        val cumulative = DoubleArray(route.points.size)
        for (index in 1 until route.points.size) {
            cumulative[index] = cumulative[index - 1] + distance(route.points[index - 1], route.points[index])
        }
        val nearest = nearestSegment(route.points, cumulative, location) ?: return null
        val geometryLength = cumulative.last().coerceAtLeast(1.0)
        val progressRatio = (nearest.distanceAlongMeters / geometryLength).coerceIn(0.0, 1.0)
        val remainingDistance = route.distanceMeters * (1.0 - progressRatio)
        val remainingSeconds = route.travelSeconds * (1.0 - progressRatio)

        val maneuverDistances = route.maneuvers.map { maneuver ->
            val point = maneuver.coordinate ?: return@map Double.POSITIVE_INFINITY
            nearestSegment(route.points, cumulative, point)?.distanceAlongMeters ?: Double.POSITIVE_INFINITY
        }
        val maneuverIndex = maneuverDistances.indices.firstOrNull {
            maneuverDistances[it] > nearest.distanceAlongMeters + MANEUVER_PASSED_TOLERANCE_METERS
        } ?: route.maneuvers.lastIndex.coerceAtLeast(0)
        val maneuverDistance = maneuverDistances.getOrNull(maneuverIndex)
            ?.takeIf { it.isFinite() }
            ?.minus(nearest.distanceAlongMeters)
            ?.coerceAtLeast(0.0)
            ?: remainingDistance

        return RouteProgress(
            maneuverIndex = maneuverIndex,
            distanceToManeuverMeters = maneuverDistance,
            remainingDistanceMeters = remainingDistance,
            remainingSeconds = remainingSeconds,
            offRoute = nearest.distanceFromRouteMeters > OFF_ROUTE_METERS
        )
    }

    private fun nearestSegment(
        points: List<Coordinate>,
        cumulative: DoubleArray,
        location: Coordinate
    ): SegmentMatch? {
        if (points.size < 2) return null
        val referenceLatitude = Math.toRadians(location.latitude)
        var best: SegmentMatch? = null
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            val startX = Math.toRadians(start.longitude - location.longitude) *
                EARTH_RADIUS_METERS * cos(referenceLatitude)
            val startY = Math.toRadians(start.latitude - location.latitude) * EARTH_RADIUS_METERS
            val endX = Math.toRadians(end.longitude - location.longitude) *
                EARTH_RADIUS_METERS * cos(referenceLatitude)
            val endY = Math.toRadians(end.latitude - location.latitude) * EARTH_RADIUS_METERS
            val deltaX = endX - startX
            val deltaY = endY - startY
            val lengthSquared = deltaX * deltaX + deltaY * deltaY
            val fraction = if (lengthSquared <= 0.0) {
                0.0
            } else {
                (-(startX * deltaX + startY * deltaY) / lengthSquared).coerceIn(0.0, 1.0)
            }
            val projectionX = startX + fraction * deltaX
            val projectionY = startY + fraction * deltaY
            val distanceFromRoute = sqrt(projectionX * projectionX + projectionY * projectionY)
            val segmentMeters = cumulative[index + 1] - cumulative[index]
            val match = SegmentMatch(
                distanceFromRouteMeters = distanceFromRoute,
                distanceAlongMeters = cumulative[index] + segmentMeters * fraction
            )
            if (best == null || match.distanceFromRouteMeters < best.distanceFromRouteMeters) {
                best = match
            }
        }
        return best
    }

    private fun distance(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(min(1.0, h)))
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val OFF_ROUTE_METERS = 75.0
    private const val MANEUVER_PASSED_TOLERANCE_METERS = 15.0

    private data class SegmentMatch(
        val distanceFromRouteMeters: Double,
        val distanceAlongMeters: Double
    )
}

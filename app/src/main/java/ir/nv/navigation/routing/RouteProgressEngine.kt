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
        val nearestIndex = route.points.indices.minByOrNull { distance(location, route.points[it]) } ?: return null
        val offset = distance(location, route.points[nearestIndex])
        val geometryLength = cumulative.last().coerceAtLeast(1.0)
        val progressRatio = (cumulative[nearestIndex] / geometryLength).coerceIn(0.0, 1.0)
        val remainingDistance = route.distanceMeters * (1.0 - progressRatio)
        val remainingSeconds = route.travelSeconds * (1.0 - progressRatio)

        val maneuverDistances = route.maneuvers.map { maneuver ->
            val point = maneuver.coordinate ?: return@map Double.POSITIVE_INFINITY
            val pointIndex = route.points.indices.minByOrNull { distance(point, route.points[it]) } ?: 0
            cumulative[pointIndex]
        }
        val maneuverIndex = maneuverDistances.indices.firstOrNull {
            maneuverDistances[it] > cumulative[nearestIndex] + MANEUVER_PASSED_TOLERANCE_METERS
        } ?: route.maneuvers.lastIndex.coerceAtLeast(0)
        val maneuverDistance = maneuverDistances.getOrNull(maneuverIndex)
            ?.takeIf { it.isFinite() }
            ?.minus(cumulative[nearestIndex])
            ?.coerceAtLeast(0.0)
            ?: remainingDistance

        return RouteProgress(
            maneuverIndex = maneuverIndex,
            distanceToManeuverMeters = maneuverDistance,
            remainingDistanceMeters = remainingDistance,
            remainingSeconds = remainingSeconds,
            offRoute = offset > OFF_ROUTE_METERS
        )
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
    private const val OFF_ROUTE_METERS = 80.0
    private const val MANEUVER_PASSED_TOLERANCE_METERS = 15.0
}

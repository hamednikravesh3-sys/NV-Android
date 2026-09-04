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

    /** Returns route geometry beginning at the driver's closest projected point. */
    fun remainingRoute(route: Route, location: Coordinate): Route? {
        if (route.points.size < 2) return null
        var bestIndex = -1
        var bestFraction = 0.0
        var bestDistance = Double.POSITIVE_INFINITY
        route.points.zipWithNext().forEachIndexed { index, (start, end) ->
            val meanLatitude = Math.toRadians((start.latitude + end.latitude + location.latitude) / 3.0)
            val metersPerLongitude = 111_320.0 * cos(meanLatitude)
            val ax = start.longitude * metersPerLongitude
            val ay = start.latitude * 111_320.0
            val bx = end.longitude * metersPerLongitude
            val by = end.latitude * 111_320.0
            val px = location.longitude * metersPerLongitude
            val py = location.latitude * 111_320.0
            val dx = bx - ax
            val dy = by - ay
            val squared = dx * dx + dy * dy
            val fraction = if (squared <= 0.01) 0.0 else
                (((px - ax) * dx + (py - ay) * dy) / squared).coerceIn(0.0, 1.0)
            val projectedX = ax + fraction * dx
            val projectedY = ay + fraction * dy
            val offset = sqrt((px - projectedX) * (px - projectedX) + (py - projectedY) * (py - projectedY))
            if (offset < bestDistance) {
                bestDistance = offset
                bestIndex = index
                bestFraction = fraction
            }
        }
        if (bestIndex < 0) return null
        val start = route.points[bestIndex]
        val end = route.points[bestIndex + 1]
        val projected = Coordinate(
            start.latitude + (end.latitude - start.latitude) * bestFraction,
            start.longitude + (end.longitude - start.longitude) * bestFraction
        )
        val remainingPoints = (listOf(projected) + route.points.drop(bestIndex + 1)).distinct()
        if (remainingPoints.size < 2) return null
        val geometryMeters = remainingPoints.zipWithNext().sumOf { (a, b) -> haversine(a, b) }
        val originalGeometry = route.points.zipWithNext().sumOf { (a, b) -> haversine(a, b) }.coerceAtLeast(1.0)
        val ratio = (geometryMeters / originalGeometry).coerceIn(0.0, 1.0)
        return route.copy(
            points = remainingPoints,
            distanceMeters = route.distanceMeters * ratio,
            travelSeconds = route.travelSeconds * ratio
        )
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

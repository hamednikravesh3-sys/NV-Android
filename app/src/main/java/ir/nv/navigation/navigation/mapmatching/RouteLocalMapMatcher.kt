package ir.nv.navigation.navigation.mapmatching

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import kotlin.math.cos
import kotlin.math.sqrt

/** Lightweight route-local matcher used when remote Valhalla matching is unavailable or weak. */
object RouteLocalMapMatcher {
    fun match(
        route: Route,
        sample: RawLocationSample,
        maxSnapDistanceMeters: Double = 100.0
    ): MatchedLocation? {
        if (route.points.size < 2) return null
        val referenceLatitude = Math.toRadians(sample.coordinate.latitude)
        var best: Projection? = null
        for ((start, end) in route.points.zipWithNext()) {
            val projection = project(start, end, sample.coordinate, referenceLatitude)
            if (best == null || projection.distanceMeters < best.distanceMeters) best = projection
        }
        val resolved = best ?: return null
        if (resolved.distanceMeters > maxSnapDistanceMeters) return null
        val distanceConfidence = (1.0 - resolved.distanceMeters / maxSnapDistanceMeters).coerceIn(0.0, 1.0)
        val accuracyConfidence = when {
            !sample.accuracyMeters.isFinite() -> .35
            sample.accuracyMeters <= 10f -> .95
            sample.accuracyMeters <= 25f -> .80
            sample.accuracyMeters <= 50f -> .60
            else -> .40
        }
        val confidence = (distanceConfidence * .65 + accuracyConfidence * .35).coerceIn(.2, .95)
        return MatchedLocation(resolved.coordinate, confidence)
    }

    private fun project(start: Coordinate, end: Coordinate, point: Coordinate, referenceLatitude: Double): Projection {
        val metersPerLon = METERS_PER_DEGREE * cos(referenceLatitude).let { kotlin.math.abs(it).coerceAtLeast(.01) }
        val ax = (start.longitude - point.longitude) * metersPerLon
        val ay = (start.latitude - point.latitude) * METERS_PER_DEGREE
        val bx = (end.longitude - point.longitude) * metersPerLon
        val by = (end.latitude - point.latitude) * METERS_PER_DEGREE
        val dx = bx - ax
        val dy = by - ay
        val denom = dx * dx + dy * dy
        val t = if (denom <= .01) 0.0 else (-(ax * dx + ay * dy) / denom).coerceIn(0.0, 1.0)
        val px = ax + dx * t
        val py = ay + dy * t
        val snapped = Coordinate(
            latitude = start.latitude + (end.latitude - start.latitude) * t,
            longitude = start.longitude + (end.longitude - start.longitude) * t
        )
        return Projection(snapped, sqrt(px * px + py * py))
    }

    private data class Projection(val coordinate: Coordinate, val distanceMeters: Double)
    private const val METERS_PER_DEGREE = 111_320.0
}

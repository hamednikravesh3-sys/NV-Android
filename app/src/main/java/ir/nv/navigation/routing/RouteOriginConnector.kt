package ir.nv.navigation.routing

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Preserves the real GPS origin when a routing engine snaps it to a mapped road.
 * This is especially useful on short unmapped dirt-road approaches: NV displays
 * and tracks the connection to the routable road instead of pretending that the
 * trip starts at the snapped point.
 */
object RouteOriginConnector {
    fun attach(origin: Coordinate, route: Route): Route {
        val snappedStart = route.points.firstOrNull() ?: return route
        val connectorMeters = distance(origin, snappedStart)
        if (connectorMeters <= MIN_CONNECTOR_METERS || connectorMeters > MAX_CONNECTOR_METERS) {
            return route
        }

        val connector = RouteManeuver(
            instruction = "تا اتصال به جاده ادامه دهید",
            roadName = "اتصال مسیر خاکی",
            distanceMeters = connectorMeters,
            direction = RouteManeuver.Direction.STRAIGHT,
            coordinate = snappedStart
        )
        val remainingManeuvers = route.maneuvers.ifEmpty {
            listOf(
                RouteManeuver(
                    instruction = "به مقصد می‌رسید",
                    roadName = null,
                    distanceMeters = route.distanceMeters,
                    direction = RouteManeuver.Direction.ARRIVE,
                    coordinate = route.points.last()
                )
            )
        }

        return route.copy(
            points = listOf(origin) + route.points,
            distanceMeters = route.distanceMeters + connectorMeters,
            travelSeconds = route.travelSeconds + connectorMeters / DIRT_ROAD_SPEED_METERS_PER_SECOND,
            maneuvers = listOf(connector) + remainingManeuvers
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
    private const val MIN_CONNECTOR_METERS = 20.0
    private const val MAX_CONNECTOR_METERS = 12_000.0
    private const val DIRT_ROAD_SPEED_METERS_PER_SECOND = 5.56 // 20 km/h
}

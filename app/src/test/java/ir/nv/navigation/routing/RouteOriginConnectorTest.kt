package ir.nv.navigation.routing

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteOriginConnectorTest {
    private val snappedStart = Coordinate(35.7000, 51.4000)
    private val route = Route(
        points = listOf(snappedStart, Coordinate(35.7100, 51.4000)),
        edgeIds = emptyList(),
        distanceMeters = 1_110.0,
        travelSeconds = 180.0,
        maneuvers = listOf(
            RouteManeuver(
                instruction = "حرکت را آغاز کنید",
                roadName = null,
                distanceMeters = 1_110.0,
                direction = RouteManeuver.Direction.STRAIGHT,
                coordinate = snappedStart
            )
        )
    )

    @Test
    fun adds_connection_from_unmapped_dirt_road_to_snapped_route() {
        val realOrigin = Coordinate(35.6970, 51.3970)
        val connected = RouteOriginConnector.attach(realOrigin, route)

        assertEquals(realOrigin, connected.points.first())
        assertEquals("تا اتصال به جاده ادامه دهید", connected.maneuvers.first().instruction)
        assertTrue(connected.distanceMeters > route.distanceMeters)
        assertTrue(connected.travelSeconds > route.travelSeconds)
    }

    @Test
    fun leaves_route_unchanged_when_origin_is_already_on_road() {
        val connected = RouteOriginConnector.attach(Coordinate(35.70001, 51.40001), route)
        assertSame(route, connected)
    }
}

package ir.nv.navigation.routing

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProgressEngineTest {
    private val route = Route(
        points = listOf(
            Coordinate(35.7000, 51.4000),
            Coordinate(35.7050, 51.4000),
            Coordinate(35.7100, 51.4000)
        ),
        edgeIds = emptyList(),
        distanceMeters = 1_110.0,
        travelSeconds = 180.0,
        maneuvers = listOf(
            RouteManeuver(
                instruction = "به راست بپیچید",
                roadName = null,
                distanceMeters = 555.0,
                direction = RouteManeuver.Direction.RIGHT,
                coordinate = Coordinate(35.7050, 51.4000)
            )
        )
    )

    @Test
    fun progress_reduces_remaining_distance() {
        val progress = requireNotNull(RouteProgressEngine.calculate(route, Coordinate(35.7040, 51.4000)))
        assertTrue(progress.remainingDistanceMeters < route.distanceMeters)
        assertFalse(progress.offRoute)
    }

    @Test
    fun detects_position_far_from_route() {
        val progress = requireNotNull(RouteProgressEngine.calculate(route, Coordinate(35.7040, 51.4100)))
        assertTrue(progress.offRoute)
    }
}

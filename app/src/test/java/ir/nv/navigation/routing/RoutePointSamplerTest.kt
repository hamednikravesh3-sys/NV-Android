package ir.nv.navigation.routing

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePointSamplerTest {
    @Test
    fun `samples ten kilometres along route instead of using a percentage`() {
        val route = Route(
            points = listOf(Coordinate(35.0, 51.0), Coordinate(35.0, 51.25)),
            edgeIds = emptyList(),
            distanceMeters = 23_000.0,
            travelSeconds = 1_200.0
        )

        val sample = requireNotNull(RoutePointSampler.pointAhead(route, 10_000.0))

        assertEquals(10_000.0, sample.distanceAheadMeters, 0.01)
        assertTrue(sample.coordinate.longitude in 51.09..51.13)
    }

    @Test
    fun `uses destination and actual distance when route is shorter`() {
        val end = Coordinate(35.0, 51.01)
        val route = Route(
            points = listOf(Coordinate(35.0, 51.0), end),
            edgeIds = emptyList(),
            distanceMeters = 900.0,
            travelSeconds = 90.0
        )

        val sample = requireNotNull(RoutePointSampler.pointAhead(route, 10_000.0))

        assertEquals(end, sample.coordinate)
        assertTrue(sample.distanceAheadMeters in 850.0..1_050.0)
    }
}

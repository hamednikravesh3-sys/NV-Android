package ir.nv.navigation.routing

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteInsightEngineTest {
    private val route = Route(
        points = listOf(Coordinate(35.0, 51.0), Coordinate(35.1, 51.0)),
        edgeIds = listOf(1),
        distanceMeters = 11_100.0,
        travelSeconds = 600.0
    )

    @Test
    fun keepsOnlyAttractionsAheadAndNearCorridor() {
        val candidates = listOf(
            Place(1, "کاخ", Coordinate(35.05, 51.005), "historic:palace"),
            Place(2, "دور", Coordinate(35.05, 51.2), "tourism:attraction"),
            Place(3, "پشت", Coordinate(34.99, 51.0), "natural:peak")
        )
        val result = RouteInsightEngine.attractionsAhead(route, candidates)
        assertEquals(listOf("کاخ — 1"), result.map { it.title })
        assertTrue(result.single().distanceAheadMeters > 5_000)
    }

    @Test
    fun sumsTrafficLengthAndDelay() {
        val summary = RouteInsightEngine.summarizeTraffic(
            listOf(
                TrafficSegment(route.points[0], route.points[1], 1_200.0, 180.0),
                TrafficSegment(route.points[0], route.points[1], 800.0, 60.0)
            )
        )
        assertEquals(2_000.0, summary.lengthMeters, 0.01)
        assertEquals(240.0, summary.delaySeconds, 0.01)
    }
}

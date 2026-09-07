package ir.nv.navigation.navigation

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.core.TrafficSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteComparisonEngineTest {
    private val engine = RouteComparisonEngine()

    @Test
    fun firstRankedCandidateIsNvRecommended() {
        val recommended = candidate(10_000.0, 900.0, 60.0, 0.12)
        val alternative = candidate(9_500.0, 1_050.0, 0.0, 0.28)

        val result = engine.compare(listOf(recommended, alternative))

        assertEquals(2, result.size)
        assertTrue(result[0].recommended)
        assertEquals("پیشنهاد NV", result[0].labelFa)
        assertFalse(result[1].recommended)
        assertEquals("مسیر 2", result[1].labelFa)
    }

    @Test
    fun comparisonUsesTrafficAdjustedTravelTime() {
        val recommended = candidate(10_000.0, 900.0, 120.0, 0.10)
        val alternative = candidate(10_500.0, 1_000.0, 180.0, 0.20)

        val result = engine.compare(listOf(recommended, alternative))

        assertEquals(1_020.0, result[0].effectiveTravelSeconds, 0.001)
        assertEquals(1_180.0, result[1].effectiveTravelSeconds, 0.001)
        assertEquals(160.0, result[1].extraTravelSeconds, 0.001)
        assertEquals(500.0, result[1].extraDistanceMeters, 0.001)
    }

    @Test
    fun comparisonNeverExposesMoreThanFourRoutes() {
        val routes = (0 until 6).map { candidate(10_000.0 + it, 900.0 + it, 0.0, it / 10.0) }
        assertEquals(4, engine.compare(routes).size)
    }

    private fun candidate(
        distanceMeters: Double,
        travelSeconds: Double,
        trafficDelay: Double,
        score: Double
    ) = RouteCandidate(
        route = Route(
            points = listOf(Coordinate(35.7, 51.4), Coordinate(35.8, 51.5)),
            edgeIds = emptyList(),
            distanceMeters = distanceMeters,
            travelSeconds = travelSeconds
        ),
        source = RouteSource.ONLINE,
        traffic = TrafficSummary(distanceMeters, trafficDelay),
        score = score
    )
}

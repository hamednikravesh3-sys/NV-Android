package ir.nv.navigation.routing

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDecisionEngineTest {
    private val points = listOf(
        Coordinate(35.7000, 51.4000),
        Coordinate(35.7100, 51.4200)
    )

    @Test
    fun heavyTrafficCanMoveNominallyFasterRouteBehindCleanerAlternative() {
        val nominallyFast = route(distanceMeters = 10_000.0, travelSeconds = 600.0)
        val cleanerAlternative = route(distanceMeters = 11_000.0, travelSeconds = 660.0)

        val ranked = RouteDecisionEngine.rank(
            routes = listOf(nominallyFast, cleanerAlternative),
            traffic = listOf(
                TrafficSummary(lengthMeters = 4_000.0, delaySeconds = 500.0),
                TrafficSummary(lengthMeters = 0.0, delaySeconds = 0.0)
            )
        )

        assertEquals(cleanerAlternative, ranked.first())
    }

    @Test
    fun withoutTrafficDataFasterShorterRouteRemainsPreferred() {
        val efficient = route(distanceMeters = 8_000.0, travelSeconds = 480.0)
        val slowerLonger = route(distanceMeters = 12_000.0, travelSeconds = 780.0)

        val evaluations = RouteDecisionEngine.evaluate(listOf(efficient, slowerLonger))
        val ranked = evaluations.sortedBy { it.score }.map { it.route }

        assertEquals(efficient, ranked.first())
        assertTrue(evaluations.all { it.score in 0.0..1.0 })
    }

    private fun route(distanceMeters: Double, travelSeconds: Double) = Route(
        points = points,
        edgeIds = emptyList(),
        distanceMeters = distanceMeters,
        travelSeconds = travelSeconds
    )
}

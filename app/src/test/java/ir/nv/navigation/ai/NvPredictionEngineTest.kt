package ir.nv.navigation.ai

import ir.nv.navigation.ai.route.NvPredictiveRouteOptimizer
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.core.TrafficSummary
import ir.nv.navigation.navigation.RouteCandidate
import ir.nv.navigation.navigation.RouteIntelligenceContext
import ir.nv.navigation.navigation.RouteProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NvPredictionEngineTest {
    private fun route(seconds: Double, distance: Double = 10_000.0) = Route(
        points = listOf(Coordinate(35.7, 51.4), Coordinate(35.8, 51.5)),
        edgeIds = emptyList(),
        distanceMeters = distance,
        travelSeconds = seconds
    )

    @Test
    fun adaptiveEtaBlendsLiveAndHistoricalDelay() = runTest {
        val predictor = AdaptiveEtaPredictor(historicalDelayRatio = 0.20)
        val result = predictor.predict(route(1_000.0), TrafficSummary(10_000.0, 300.0))
        assertEquals(1_280.0, result.seconds, 0.001)
        assertTrue(result.confidence >= 0.89)
    }

    @Test
    fun historicalTrafficPredictorUsesWeightedSamples() = runTest {
        val predictor = HistoricalTrafficPredictor(
            listOf(
                HistoricalTrafficPredictor.Sample(30, 0.20, 2.0),
                HistoricalTrafficPredictor.Sample(25, 0.10, 1.0),
                HistoricalTrafficPredictor.Sample(90, 1.00, 1.0)
            )
        )
        val result = requireNotNull(predictor.predict(route(900.0), 30))
        assertTrue(result.delaySeconds > 0.0)
        assertEquals(30, result.horizonMinutes)
        assertTrue(result.confidence in 0.0..1.0)
    }

    @Test
    fun predictiveOptimizerPrefersLowerExpectedArrival() = runTest {
        val engine = NvRoutePredictionEngine(
            etaPredictor = AdaptiveEtaPredictor(),
            trafficPredictor = HistoricalTrafficPredictor(
                listOf(HistoricalTrafficPredictor.Sample(30, 0.10))
            )
        )
        val optimizer = NvPredictiveRouteOptimizer(engine)
        val slow = RouteCandidate(route(1_400.0), RouteSource.ONLINE, score = 0.2)
        val fast = RouteCandidate(route(900.0), RouteSource.ONLINE, score = 0.3)
        val ranked = optimizer.optimize(
            listOf(slow, fast),
            RouteIntelligenceContext(profile = RouteProfile.SMART),
            horizonMinutes = 30
        )
        assertEquals(900.0, ranked.first().route.travelSeconds, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun predictionRejectsNegativeHorizon() = runTest {
        HistoricalTrafficPredictor(emptyList()).predict(route(100.0), -1)
    }
}

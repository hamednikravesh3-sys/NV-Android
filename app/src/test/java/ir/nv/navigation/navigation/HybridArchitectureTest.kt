package ir.nv.navigation.navigation

import ir.nv.navigation.ai.route.NvAdaptiveRouteRanker
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.core.TrafficSummary
import ir.nv.navigation.search.HybridSearchEngine
import ir.nv.navigation.search.PlaceSearchProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridArchitectureTest {
    private val origin = Coordinate(35.7, 51.4)
    private val destination = Coordinate(35.8, 51.5)

    @Test
    fun onlineFailureFallsBackToOfflineWithoutBreakingNavigation() = runBlocking {
        val offlineRoute = route(1_000.0, 100.0)
        val coordinator = HybridRouteCoordinator(
            onlineProvider = RouteProvider { error("online unavailable") },
            offlineProvider = RouteProvider { listOf(offlineRoute) },
            trafficProvider = TrafficProvider { null },
            ranker = NvAdaptiveRouteRanker()
        )

        val plan = coordinator.plan(
            RouteRequest(origin, destination, onlineAvailable = true, offlineAvailable = true)
        )

        assertEquals(RouteSource.OFFLINE, plan.selected?.source)
        assertTrue(plan.fallbackUsed)
        assertEquals(offlineRoute, plan.selected?.route)
    }

    @Test
    fun smartRankingPenalizesLargeTrafficDelay() {
        val fastButBlocked = RouteCandidate(
            route = route(5_000.0, 500.0),
            source = RouteSource.ONLINE,
            traffic = TrafficSummary(2_000.0, 500.0)
        )
        val cleaner = RouteCandidate(
            route = route(5_500.0, 620.0),
            source = RouteSource.ONLINE,
            traffic = TrafficSummary(300.0, 20.0)
        )
        val ranked = NvAdaptiveRouteRanker().rank(
            listOf(fastButBlocked, cleaner),
            RouteIntelligenceContext(RouteProfile.SMART)
        )
        assertEquals(cleaner.route, ranked.first().route)
    }

    @Test
    fun realRiskSignalsCanMoveRiskyRouteBehindSaferAlternative() {
        val risky = RouteCandidate(
            route = route(4_900.0, 540.0),
            source = RouteSource.ONLINE,
            signals = RouteSignals(
                accidentRiskPenalty = 1.0,
                weatherPenalty = 0.9,
                restrictionPenalty = 0.8
            )
        )
        val safer = RouteCandidate(
            route = route(5_100.0, 555.0),
            source = RouteSource.ONLINE,
            signals = RouteSignals(
                accidentRiskPenalty = 0.05,
                weatherPenalty = 0.0,
                restrictionPenalty = 0.0
            )
        )
        val ranked = NvAdaptiveRouteRanker().rank(
            listOf(risky, safer),
            RouteIntelligenceContext(RouteProfile.SMART, avoidRisk = true)
        )
        assertEquals(safer.route, ranked.first().route)
    }

    @Test
    fun missingExternalSignalsRemainUnknownInsteadOfBeingFabricated() = runBlocking {
        val candidateRoute = route(2_000.0, 200.0)
        val coordinator = HybridRouteCoordinator(
            onlineProvider = RouteProvider { listOf(candidateRoute) },
            offlineProvider = RouteProvider { emptyList() },
            trafficProvider = TrafficProvider { null },
            ranker = NvAdaptiveRouteRanker()
        )
        val candidate = coordinator.plan(
            RouteRequest(origin, destination, onlineAvailable = true)
        ).selected ?: error("route missing")

        assertNull(candidate.signals.roadQualityPenalty)
        assertNull(candidate.signals.accidentRiskPenalty)
        assertNull(candidate.signals.weatherPenalty)
        assertNull(candidate.signals.restrictionPenalty)
    }

    @Test
    fun reroutingRequiresMeaningfulBenefitAndCooldown() {
        val policy = ContinuousReroutePolicy()
        val now = 1_000_000L

        val tinySaving = policy.evaluate(
            nowMillis = now,
            lastRerouteMillis = 0L,
            offRoute = false,
            currentRouteBlocked = false,
            previousTrafficDelaySeconds = 30.0,
            currentTrafficDelaySeconds = 40.0,
            currentRemainingSeconds = 1_000.0,
            bestAlternativeSeconds = 900.0
        )
        assertFalse(tinySaving.shouldReroute)

        val meaningful = policy.evaluate(
            nowMillis = now,
            lastRerouteMillis = 0L,
            offRoute = false,
            currentRouteBlocked = false,
            previousTrafficDelaySeconds = 30.0,
            currentTrafficDelaySeconds = 40.0,
            currentRemainingSeconds = 1_200.0,
            bestAlternativeSeconds = 900.0
        )
        assertTrue(meaningful.shouldReroute)
        assertEquals(ContinuousReroutePolicy.Reason.BETTER_ROUTE, meaningful.reason)
    }

    @Test
    fun hybridSearchCombinesOfflineAndOnlineResults() = runBlocking {
        val local = Place(1, "Milad Tower", origin, "attraction")
        val remote = Place(2, "Milad Cafe", destination, "cafe")
        val engine = HybridSearchEngine(
            offline = PlaceSearchProvider { listOf(local) },
            online = PlaceSearchProvider { listOf(remote) }
        )
        val result = engine.search("Milad", onlineAvailable = true, preferOffline = false)
        assertEquals(listOf(local, remote), result)
    }

    private fun route(distance: Double, seconds: Double) = Route(
        points = listOf(origin, destination),
        edgeIds = emptyList(),
        distanceMeters = distance,
        travelSeconds = seconds
    )
}

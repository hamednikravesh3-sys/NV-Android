package ir.nv.navigation.navigation

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.TrafficReport
import ir.nv.navigation.core.TrafficSegment
import ir.nv.navigation.core.TrafficSummary
import ir.nv.navigation.navigation.guidance.GuidanceEngine
import ir.nv.navigation.navigation.guidance.GuidancePhase
import ir.nv.navigation.places.AheadEngine
import ir.nv.navigation.traffic.CongestionLevel
import ir.nv.navigation.traffic.TrafficSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Batch13To18NavigationTest {
    @Test
    fun `guidance produces phase aware Persian voice text`() {
        val maneuver = RouteManeuver(
            instruction = "Turn right",
            roadName = "خیابان آزادی",
            distanceMeters = 500.0,
            direction = RouteManeuver.Direction.RIGHT
        )
        val route = Route(
            points = listOf(Coordinate(35.0, 51.0), Coordinate(35.01, 51.01)),
            edgeIds = emptyList(),
            distanceMeters = 1_000.0,
            travelSeconds = 120.0,
            maneuvers = listOf(maneuver)
        )
        val engine = GuidanceEngine()
        val snapshot = requireNotNull(engine.snapshot(route, 0, 250.0))
        assertEquals(GuidancePhase.APPROACH, snapshot.phase)
        assertTrue(snapshot.voiceInstructionFa.contains("آماده باشید"))
        assertTrue(snapshot.voiceInstructionFa.contains("به راست بپیچید"))
        assertTrue(snapshot.voiceInstructionFa.contains("خیابان آزادی"))
    }

    @Test
    fun `reroute traffic increase needs a useful replacement`() {
        val policy = ContinuousReroutePolicy(
            minimumTimeSavingSeconds = 180.0,
            trafficIncreaseThresholdSeconds = 120.0,
            cooldownMillis = 0L,
            minimumTrafficSavingSeconds = 60.0
        )
        val noUsefulAlternative = policy.evaluate(
            nowMillis = 100_000L,
            lastRerouteMillis = 0L,
            offRoute = false,
            currentRouteBlocked = false,
            previousTrafficDelaySeconds = 20.0,
            currentTrafficDelaySeconds = 200.0,
            currentRemainingSeconds = 900.0,
            bestAlternativeSeconds = 870.0
        )
        assertFalse(noUsefulAlternative.shouldReroute)

        val usefulAlternative = policy.evaluate(
            nowMillis = 100_000L,
            lastRerouteMillis = 0L,
            offRoute = false,
            currentRouteBlocked = false,
            previousTrafficDelaySeconds = 20.0,
            currentTrafficDelaySeconds = 200.0,
            currentRemainingSeconds = 900.0,
            bestAlternativeSeconds = 760.0
        )
        assertTrue(usefulAlternative.shouldReroute)
        assertEquals(ContinuousReroutePolicy.Reason.TRAFFIC_INCREASE, usefulAlternative.reason)
        assertEquals(140.0, usefulAlternative.estimatedSavingSeconds, 0.01)
    }

    @Test
    fun `traffic snapshot classifies live congestion and freshness`() {
        val report = TrafficReport(
            summary = TrafficSummary(lengthMeters = 2_000.0, delaySeconds = 240.0),
            segments = listOf(
                TrafficSegment(
                    start = Coordinate(35.0, 51.0),
                    end = Coordinate(35.01, 51.01),
                    lengthMeters = 2_000.0,
                    delaySeconds = 240.0
                )
            )
        )
        val snapshot = TrafficSnapshot(live = report, generatedAtMillis = 1_000L)
        assertEquals(CongestionLevel.HEAVY, snapshot.congestionLevel())
        assertTrue(snapshot.isFresh(nowMillis = 60_000L, maxAgeMillis = 120_000L))
        assertFalse(snapshot.isFresh(nowMillis = 200_000L, maxAgeMillis = 120_000L))
    }

    @Test
    fun `smart POI ranking keeps emergency service ahead of ordinary service`() {
        val engine = AheadEngine(maxItems = 5, maxDistanceMeters = 10_000.0)
        val ordinary = RouteNotice(
            title = "پارکینگ عمومی",
            detail = "",
            distanceAheadMeters = 300.0,
            kind = RouteNotice.Kind.SERVICE
        )
        val emergency = RouteNotice(
            title = "بیمارستان مرکزی",
            detail = "",
            distanceAheadMeters = 900.0,
            kind = RouteNotice.Kind.SERVICE
        )
        val ranked = engine.rank(listOf(ordinary, emergency))
        assertEquals("بیمارستان مرکزی", ranked.first().title)
    }
}

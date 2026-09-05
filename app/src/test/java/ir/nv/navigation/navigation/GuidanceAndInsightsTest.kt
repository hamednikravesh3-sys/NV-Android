package ir.nv.navigation.navigation

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.TrafficSummary
import ir.nv.navigation.navigation.guidance.GuidanceEngine
import ir.nv.navigation.places.AheadEngine
import ir.nv.navigation.places.RouteInsightsEngine
import ir.nv.navigation.places.RouteNoticeProvider
import ir.nv.navigation.places.RouteTrafficProvider
import ir.nv.navigation.places.RouteTrafficSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceAndInsightsTest {
    private val route = Route(
        points = listOf(Coordinate(35.70, 51.40), Coordinate(35.71, 51.41)),
        edgeIds = listOf(1L),
        distanceMeters = 1_500.0,
        travelSeconds = 180.0,
        maneuvers = listOf(
            RouteManeuver(
                instruction = "به راست بپیچید",
                roadName = "خیابان آزادی",
                distanceMeters = 500.0,
                direction = RouteManeuver.Direction.RIGHT,
                lanes = listOf(
                    RouteManeuver.Lane(RouteManeuver.Direction.STRAIGHT, false),
                    RouteManeuver.Lane(RouteManeuver.Direction.RIGHT, true)
                )
            ),
            RouteManeuver(
                instruction = "به مقصد رسیدید",
                roadName = null,
                distanceMeters = 0.0,
                direction = RouteManeuver.Direction.ARRIVE
            )
        )
    )

    @Test
    fun guidancePreservesLaneRecommendationAndArrivalState() {
        val engine = GuidanceEngine()
        val turn = requireNotNull(engine.snapshot(route, 0, 320.0))
        assertEquals(RouteManeuver.Direction.RIGHT, turn.direction)
        assertEquals(320.0, turn.distanceToTurnMeters, 0.01)
        assertTrue(turn.lanes.last().recommended)
        assertFalse(turn.arrival)

        val arrival = requireNotNull(engine.snapshot(route, 1, 20.0))
        assertTrue(arrival.arrival)
        assertEquals("به مقصد رسیدید", arrival.instruction)
    }

    @Test
    fun aheadEngineKeepsOnlyRelevantForwardItemsAndPrioritizesSafety() {
        val ranked = AheadEngine(maxItems = 8, maxDistanceMeters = 20_000.0).rank(
            listOf(
                notice("جاذبه", 2_000.0, RouteNotice.Kind.ATTRACTION),
                notice("هوا", 7_000.0, RouteNotice.Kind.WEATHER),
                notice("ترافیک", 12_000.0, RouteNotice.Kind.TRAFFIC),
                notice("خیلی دور", 25_000.0, RouteNotice.Kind.SERVICE),
                notice("پشت سر", -100.0, RouteNotice.Kind.SERVICE)
            )
        )
        assertEquals(listOf("ترافیک", "هوا", "جاذبه"), ranked.map { it.title })
    }

    @Test
    fun routeInsightsSurviveOnlineProviderFailureAndKeepOfflineData() = runBlocking {
        val engine = RouteInsightsEngine(
            offlinePlaces = RouteNoticeProvider {
                listOf(notice("پمپ بنزین", 2_500.0, RouteNotice.Kind.SERVICE))
            },
            onlinePlaces = RouteNoticeProvider { error("online places unavailable") },
            weather = RouteNoticeProvider {
                listOf(notice("بارندگی", 10_000.0, RouteNotice.Kind.WEATHER))
            },
            traffic = RouteTrafficProvider {
                RouteTrafficSnapshot(TrafficSummary(1_200.0, 240.0))
            }
        )

        val result = engine.load(route, onlineAvailable = true)
        assertTrue(result.notices.any { it.title == "پمپ بنزین" })
        assertTrue(result.notices.any { it.title == "بارندگی" })
        assertEquals(240.0, result.traffic?.delaySeconds ?: 0.0, 0.01)
    }

    private fun notice(title: String, distance: Double, kind: RouteNotice.Kind) = RouteNotice(
        title = title,
        detail = title,
        distanceAheadMeters = distance,
        kind = kind
    )
}

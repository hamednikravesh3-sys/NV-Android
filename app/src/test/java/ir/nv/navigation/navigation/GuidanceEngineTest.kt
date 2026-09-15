package ir.nv.navigation.navigation

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver
import ir.nv.navigation.navigation.guidance.GuidanceEngine
import ir.nv.navigation.navigation.guidance.GuidancePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceEngineTest {
    private val engine = GuidanceEngine()

    @Test
    fun exposesApproachPhaseAndNextInstruction() {
        val route = routeWithManeuvers()
        val snapshot = requireNotNull(engine.snapshot(route, 0, 120.0))

        assertEquals(GuidancePhase.APPROACH, snapshot.phase)
        assertEquals("به راست بپیچید", snapshot.instruction)
        assertEquals("به مسیر مستقیم ادامه دهید", snapshot.nextInstruction)
        assertTrue(snapshot.lanes.first().recommended)
    }

    @Test
    fun marksImmediateTurnAndArrival() {
        val route = routeWithManeuvers()
        assertEquals(GuidancePhase.NOW, engine.snapshot(route, 0, 25.0)?.phase)
        assertEquals(GuidancePhase.ARRIVAL, engine.snapshot(route, 2, 15.0)?.phase)
    }

    private fun routeWithManeuvers() = Route(
        points = listOf(Coordinate(35.7, 51.4), Coordinate(35.8, 51.5)),
        edgeIds = emptyList(),
        distanceMeters = 2_000.0,
        travelSeconds = 300.0,
        maneuvers = listOf(
            RouteManeuver(
                instruction = "به راست بپیچید",
                roadName = "خیابان نمونه",
                distanceMeters = 120.0,
                direction = RouteManeuver.Direction.RIGHT,
                lanes = listOf(RouteManeuver.Lane(RouteManeuver.Direction.RIGHT, true))
            ),
            RouteManeuver(
                instruction = "به مسیر مستقیم ادامه دهید",
                roadName = "بلوار نمونه",
                distanceMeters = 1_500.0,
                direction = RouteManeuver.Direction.STRAIGHT
            ),
            RouteManeuver(
                instruction = "به مقصد رسیدید",
                roadName = null,
                distanceMeters = 0.0,
                direction = RouteManeuver.Direction.ARRIVE
            )
        )
    )
}

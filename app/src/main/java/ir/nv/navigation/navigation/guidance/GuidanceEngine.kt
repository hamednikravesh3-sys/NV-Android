package ir.nv.navigation.navigation.guidance

import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver

data class GuidanceSnapshot(
    val maneuverIndex: Int,
    val instruction: String,
    val roadName: String?,
    val direction: RouteManeuver.Direction,
    val distanceToTurnMeters: Double,
    val lanes: List<RouteManeuver.Lane>,
    val arrival: Boolean
)

class GuidanceEngine {
    fun snapshot(
        route: Route,
        maneuverIndex: Int,
        distanceToTurnMeters: Double
    ): GuidanceSnapshot? {
        val maneuver = route.maneuvers.getOrNull(maneuverIndex) ?: return null
        return GuidanceSnapshot(
            maneuverIndex = maneuverIndex,
            instruction = maneuver.instruction,
            roadName = maneuver.roadName,
            direction = maneuver.direction,
            distanceToTurnMeters = distanceToTurnMeters.coerceAtLeast(0.0),
            lanes = maneuver.lanes,
            arrival = maneuver.direction == RouteManeuver.Direction.ARRIVE
        )
    }
}

package ir.nv.navigation.navigation.guidance

import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver

enum class GuidancePhase {
    PREPARE,
    APPROACH,
    NOW,
    ARRIVAL
}

data class GuidanceSnapshot(
    val maneuverIndex: Int,
    val instruction: String,
    val roadName: String?,
    val direction: RouteManeuver.Direction,
    val distanceToTurnMeters: Double,
    val lanes: List<RouteManeuver.Lane>,
    val arrival: Boolean,
    val phase: GuidancePhase,
    val nextInstruction: String?
)

class GuidanceEngine {
    fun snapshot(
        route: Route,
        maneuverIndex: Int,
        distanceToTurnMeters: Double
    ): GuidanceSnapshot? {
        val maneuver = route.maneuvers.getOrNull(maneuverIndex) ?: return null
        val distance = distanceToTurnMeters.coerceAtLeast(0.0)
        val arrival = maneuver.direction == RouteManeuver.Direction.ARRIVE
        return GuidanceSnapshot(
            maneuverIndex = maneuverIndex,
            instruction = maneuver.instruction,
            roadName = maneuver.roadName,
            direction = maneuver.direction,
            distanceToTurnMeters = distance,
            lanes = maneuver.lanes,
            arrival = arrival,
            phase = phaseFor(distance, arrival),
            nextInstruction = route.maneuvers.getOrNull(maneuverIndex + 1)?.instruction
        )
    }

    fun phaseFor(distanceMeters: Double, arrival: Boolean = false): GuidancePhase {
        if (arrival) return GuidancePhase.ARRIVAL
        return when {
            distanceMeters <= NOW_THRESHOLD_METERS -> GuidancePhase.NOW
            distanceMeters <= APPROACH_THRESHOLD_METERS -> GuidancePhase.APPROACH
            else -> GuidancePhase.PREPARE
        }
    }

    companion object {
        const val NOW_THRESHOLD_METERS = 45.0
        const val APPROACH_THRESHOLD_METERS = 280.0
    }
}

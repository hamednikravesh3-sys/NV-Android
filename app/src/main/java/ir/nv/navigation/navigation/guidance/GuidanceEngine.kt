package ir.nv.navigation.navigation.guidance

import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteManeuver
import kotlin.math.roundToInt

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
    val nextInstruction: String?,
    val voiceInstructionFa: String
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
        val phase = phaseFor(distance, arrival)
        return GuidanceSnapshot(
            maneuverIndex = maneuverIndex,
            instruction = maneuver.instruction,
            roadName = maneuver.roadName,
            direction = maneuver.direction,
            distanceToTurnMeters = distance,
            lanes = maneuver.lanes,
            arrival = arrival,
            phase = phase,
            nextInstruction = route.maneuvers.getOrNull(maneuverIndex + 1)?.instruction,
            voiceInstructionFa = voiceInstruction(maneuver, distance, phase)
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

    fun voiceInstruction(
        maneuver: RouteManeuver,
        distanceMeters: Double,
        phase: GuidancePhase = phaseFor(distanceMeters, maneuver.direction == RouteManeuver.Direction.ARRIVE)
    ): String {
        if (phase == GuidancePhase.ARRIVAL) return "به مقصد رسیدید"
        val action = directionPhrase(maneuver.direction)
        val road = maneuver.roadName?.trim()?.takeIf { it.isNotEmpty() }?.let { " به $it" }.orEmpty()
        return when (phase) {
            GuidancePhase.PREPARE -> "${distancePhrase(distanceMeters)} دیگر، $action$road"
            GuidancePhase.APPROACH -> "آماده باشید؛ ${distancePhrase(distanceMeters)} دیگر، $action$road"
            GuidancePhase.NOW -> "$action$road"
            GuidancePhase.ARRIVAL -> "به مقصد رسیدید"
        }
    }

    private fun directionPhrase(direction: RouteManeuver.Direction): String = when (direction) {
        RouteManeuver.Direction.STRAIGHT -> "مستقیم ادامه دهید"
        RouteManeuver.Direction.SLIGHT_LEFT -> "کمی به چپ بروید"
        RouteManeuver.Direction.LEFT -> "به چپ بپیچید"
        RouteManeuver.Direction.SHARP_LEFT -> "تند به چپ بپیچید"
        RouteManeuver.Direction.SLIGHT_RIGHT -> "کمی به راست بروید"
        RouteManeuver.Direction.RIGHT -> "به راست بپیچید"
        RouteManeuver.Direction.SHARP_RIGHT -> "تند به راست بپیچید"
        RouteManeuver.Direction.UTURN -> "دور بزنید"
        RouteManeuver.Direction.ARRIVE -> "به مقصد برسید"
    }

    private fun distancePhrase(distanceMeters: Double): String {
        val meters = distanceMeters.coerceAtLeast(0.0)
        return if (meters < 1000.0) {
            "${(meters / 10.0).roundToInt() * 10} متر"
        } else {
            val tenths = (meters / 100.0).roundToInt() / 10.0
            if (tenths % 1.0 == 0.0) "${tenths.toInt()} کیلومتر" else "$tenths کیلومتر"
        }
    }

    companion object {
        const val NOW_THRESHOLD_METERS = 45.0
        const val APPROACH_THRESHOLD_METERS = 280.0
    }
}

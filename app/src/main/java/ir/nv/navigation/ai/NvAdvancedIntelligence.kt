package ir.nv.navigation.ai

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import kotlin.math.abs

data class DestinationVisit(val place: Place, val epochMillis: Long)

data class ParkingCandidate(val place: Place, val occupancyProbability: Double, val walkingMeters: Double)

data class PersonalizedRoutingProfile(
    val timePriority: Double = 0.5,
    val ecoPriority: Double = 0.5,
    val highwayPreference: Double = 0.5,
    val tollAversion: Double = 0.5,
    val riskAversion: Double = 0.7
)

class DestinationPredictionEngine {
    fun predict(visits: List<DestinationVisit>, nowMillis: Long, limit: Int = 5): List<Place> {
        if (visits.isEmpty()) return emptyList()
        val day = 24L * 60L * 60L * 1000L
        return visits.groupBy { it.place.personalCode ?: it.place.code.toString() }
            .map { (_, group) ->
                val score = group.sumOf { visit -> 1.0 / (1.0 + ((nowMillis - visit.epochMillis).coerceAtLeast(0L) / day).toDouble()) }
                group.last().place to score
            }.sortedByDescending { it.second }.take(limit).map { it.first }
    }
}

class ParkingPredictionEngine {
    fun rank(candidates: List<ParkingCandidate>): List<ParkingCandidate> = candidates.sortedBy { c ->
        val occupancy = c.occupancyProbability.coerceIn(0.0, 1.0)
        occupancy * 0.7 + (c.walkingMeters.coerceAtLeast(0.0) / 2_000.0).coerceAtMost(1.0) * 0.3
    }
}

class AccidentRiskPredictionEngine {
    data class Signal(
        val rainOrSnow: Boolean,
        val night: Boolean,
        val speedKmh: Double,
        val roadQualityPenalty: Double,
        val historicalAccidentRate: Double
    )

    fun risk(signal: Signal): Double {
        var risk = signal.historicalAccidentRate.coerceIn(0.0, 1.0) * 0.45
        risk += signal.roadQualityPenalty.coerceIn(0.0, 1.0) * 0.20
        if (signal.rainOrSnow) risk += 0.15
        if (signal.night) risk += 0.10
        risk += ((signal.speedKmh - 70.0).coerceAtLeast(0.0) / 100.0).coerceAtMost(0.10)
        return risk.coerceIn(0.0, 1.0)
    }
}

class PersonalizedRoutingEngine {
    fun learn(previous: PersonalizedRoutingProfile, acceptedFastRoute: Boolean, acceptedEcoRoute: Boolean): PersonalizedRoutingProfile {
        val step = 0.05
        return previous.copy(
            timePriority = (previous.timePriority + if (acceptedFastRoute) step else -step / 3).coerceIn(0.0, 1.0),
            ecoPriority = (previous.ecoPriority + if (acceptedEcoRoute) step else -step / 3).coerceIn(0.0, 1.0)
        )
    }
}

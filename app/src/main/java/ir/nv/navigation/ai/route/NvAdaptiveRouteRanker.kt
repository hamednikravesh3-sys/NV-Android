package ir.nv.navigation.ai.route

import ir.nv.navigation.navigation.RouteCandidate
import ir.nv.navigation.navigation.RouteIntelligenceContext
import ir.nv.navigation.navigation.RouteProfile
import ir.nv.navigation.navigation.RouteRanker

class NvAdaptiveRouteRanker : RouteRanker {
    override fun rank(
        candidates: List<RouteCandidate>,
        context: RouteIntelligenceContext
    ): List<RouteCandidate> {
        if (candidates.size <= 1) return candidates

        val raw = candidates.map { candidate ->
            val route = candidate.route
            val delay = candidate.traffic?.delaySeconds?.coerceAtLeast(0.0) ?: 0.0
            val effectiveTime = route.travelSeconds.coerceAtLeast(0.0) + delay
            val distance = route.distanceMeters.coerceAtLeast(0.0)
            val energy = energyIndex(distance, effectiveTime)
            Raw(candidate, effectiveTime, delay, distance, energy)
        }

        val timeRange = Range.of(raw.map { it.effectiveTime })
        val trafficRange = Range.of(raw.map { it.trafficDelay })
        val distanceRange = Range.of(raw.map { it.distance })
        val energyRange = Range.of(raw.map { it.energy })
        val weights = weightsFor(context)

        return raw.map { row ->
            val signals = row.candidate.signals.normalized()
            var score = weights.time * timeRange.normalize(row.effectiveTime) +
                weights.traffic * trafficRange.normalize(row.trafficDelay) +
                weights.distance * distanceRange.normalize(row.distance) +
                weights.energy * energyRange.normalize(row.energy)
            var activeWeight = weights.time + weights.traffic + weights.distance + weights.energy

            fun addSignal(value: Double?, weight: Double) {
                if (value != null) {
                    score += weight * value
                    activeWeight += weight
                }
            }

            addSignal(signals.roadQualityPenalty, ROAD_QUALITY_WEIGHT)
            addSignal(signals.accidentRiskPenalty, ACCIDENT_RISK_WEIGHT)
            addSignal(signals.weatherPenalty, WEATHER_WEIGHT)
            addSignal(signals.restrictionPenalty, RESTRICTION_WEIGHT)

            row.candidate.copy(score = score / activeWeight.coerceAtLeast(1e-9))
        }.sortedBy { it.score }
    }

    private fun weightsFor(context: RouteIntelligenceContext): Weights {
        var weights = when (context.profile) {
            RouteProfile.FASTEST -> Weights(0.70, 0.20, 0.07, 0.03)
            RouteProfile.SHORTEST -> Weights(0.20, 0.10, 0.65, 0.05)
            RouteProfile.LOW_TRAFFIC -> Weights(0.35, 0.50, 0.10, 0.05)
            RouteProfile.ECO -> Weights(0.25, 0.10, 0.15, 0.50)
            RouteProfile.SCENIC -> Weights(0.30, 0.10, 0.25, 0.35)
            RouteProfile.AVOID_TOLL,
            RouteProfile.AVOID_HIGHWAY,
            RouteProfile.SMART -> Weights(0.45, 0.25, 0.15, 0.15)
        }

        val timePriority = context.userTimePriority.coerceIn(0.0, 1.0)
        val ecoPriority = context.userEcoPriority.coerceIn(0.0, 1.0)
        weights = weights.copy(
            time = weights.time + 0.15 * (timePriority - 0.5),
            energy = weights.energy + 0.15 * (ecoPriority - 0.5)
        )

        if (context.electricVehicle) {
            weights = weights.copy(
                energy = weights.energy + 0.15,
                time = (weights.time - 0.10).coerceAtLeast(0.05)
            )
        }
        if (context.rainOrSnow && context.avoidRisk) {
            weights = weights.copy(
                time = (weights.time - 0.08).coerceAtLeast(0.05),
                energy = weights.energy + 0.08
            )
        }
        return weights.nonNegative().normalized()
    }

    private fun energyIndex(distanceMeters: Double, travelSeconds: Double): Double {
        val seconds = travelSeconds.coerceAtLeast(1.0)
        val distanceKm = distanceMeters / 1_000.0
        val speedMps = distanceMeters / seconds
        val ratio = speedMps / REFERENCE_SPEED_MPS
        return distanceKm * (1.0 + AERODYNAMIC_FACTOR * ratio * ratio)
    }

    private data class Raw(
        val candidate: RouteCandidate,
        val effectiveTime: Double,
        val trafficDelay: Double,
        val distance: Double,
        val energy: Double
    )

    private data class Weights(
        val time: Double,
        val traffic: Double,
        val distance: Double,
        val energy: Double
    ) {
        fun nonNegative() = Weights(
            time.coerceAtLeast(0.0),
            traffic.coerceAtLeast(0.0),
            distance.coerceAtLeast(0.0),
            energy.coerceAtLeast(0.0)
        )

        fun normalized(): Weights {
            val sum = (time + traffic + distance + energy).coerceAtLeast(1e-9)
            return Weights(time / sum, traffic / sum, distance / sum, energy / sum)
        }
    }

    private data class Range(val min: Double, val max: Double) {
        fun normalize(value: Double): Double {
            val span = max - min
            return if (span <= 1e-9) 0.0 else ((value - min) / span).coerceIn(0.0, 1.0)
        }

        companion object {
            fun of(values: List<Double>) = Range(values.minOrNull() ?: 0.0, values.maxOrNull() ?: 0.0)
        }
    }

    private companion object {
        const val REFERENCE_SPEED_MPS = 13.89
        const val AERODYNAMIC_FACTOR = 0.35
        const val ROAD_QUALITY_WEIGHT = 0.12
        const val ACCIDENT_RISK_WEIGHT = 0.35
        const val WEATHER_WEIGHT = 0.20
        const val RESTRICTION_WEIGHT = 0.55
    }
}

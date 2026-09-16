package ir.nv.navigation.ai.route

import ir.nv.navigation.ai.NvRoutePredictionEngine
import ir.nv.navigation.navigation.RouteCandidate
import ir.nv.navigation.navigation.RouteIntelligenceContext

/**
 * Final predictive optimization pass for SMART routing.
 * Lower score remains better. The existing adaptive score is preserved and adjusted using
 * predicted ETA and near-future traffic so the route recommendation can react before congestion.
 */
class NvPredictiveRouteOptimizer(
    private val predictionEngine: NvRoutePredictionEngine
) {
    suspend fun optimize(
        candidates: List<RouteCandidate>,
        context: RouteIntelligenceContext,
        horizonMinutes: Int = 30
    ): List<RouteCandidate> {
        if (candidates.size <= 1) return candidates
        require(horizonMinutes >= 0) { "افق بهینه‌سازی منفی است" }

        val predicted = candidates.map { candidate ->
            val prediction = predictionEngine.predict(candidate.route, candidate.traffic, horizonMinutes)
            val eta = prediction.eta.seconds.coerceAtLeast(1.0)
            val futureDelay = prediction.futureTraffic?.delaySeconds?.coerceAtLeast(0.0) ?: 0.0
            Predicted(candidate, eta, futureDelay)
        }
        val etaMin = predicted.minOf { it.eta }
        val etaMax = predicted.maxOf { it.eta }
        val delayMin = predicted.minOf { it.futureDelay }
        val delayMax = predicted.maxOf { it.futureDelay }

        val predictiveWeight = when {
            context.profile.name == "SMART" -> 0.45
            context.profile.name == "LOW_TRAFFIC" -> 0.55
            context.profile.name == "FASTEST" -> 0.40
            else -> 0.25
        }

        return predicted.map { row ->
            val base = row.candidate.score?.coerceAtLeast(0.0) ?: 0.5
            val etaPenalty = normalize(row.eta, etaMin, etaMax)
            val trafficPenalty = normalize(row.futureDelay, delayMin, delayMax)
            val predictionPenalty = 0.65 * etaPenalty + 0.35 * trafficPenalty
            row.candidate.copy(
                score = (1.0 - predictiveWeight) * base + predictiveWeight * predictionPenalty
            )
        }.sortedBy { it.score }
    }

    private fun normalize(value: Double, min: Double, max: Double): Double =
        if (max - min <= 1e-9) 0.0 else ((value - min) / (max - min)).coerceIn(0.0, 1.0)

    private data class Predicted(
        val candidate: RouteCandidate,
        val eta: Double,
        val futureDelay: Double
    )
}

package ir.nv.navigation.navigation

import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteSource
import kotlin.math.roundToInt

/**
 * Produces stable, UI-ready comparison data for the ranked route candidates.
 * Candidate index 0 is the NV recommended route because HybridRouteCoordinator
 * returns candidates in ranker order.
 */
class RouteComparisonEngine {
    data class Comparison(
        val index: Int,
        val route: Route,
        val source: RouteSource,
        val recommended: Boolean,
        val score: Double?,
        val distanceMeters: Double,
        val travelSeconds: Double,
        val trafficDelaySeconds: Double,
        val effectiveTravelSeconds: Double,
        val extraDistanceMeters: Double,
        val extraTravelSeconds: Double,
        val labelFa: String
    ) {
        val distanceKm: Double get() = distanceMeters / 1_000.0
        val travelMinutes: Int get() = (effectiveTravelSeconds / 60.0).roundToInt().coerceAtLeast(0)
    }

    fun compare(candidates: List<RouteCandidate>): List<Comparison> {
        if (candidates.isEmpty()) return emptyList()
        val baseline = candidates.first()
        val baselineDistance = baseline.route.distanceMeters.coerceAtLeast(0.0)
        val baselineEffective = effectiveSeconds(baseline)
        return candidates.take(MAX_ROUTES).mapIndexed { index, candidate ->
            val route = candidate.route
            val effective = effectiveSeconds(candidate)
            Comparison(
                index = index,
                route = route,
                source = candidate.source,
                recommended = index == 0,
                score = candidate.score,
                distanceMeters = route.distanceMeters.coerceAtLeast(0.0),
                travelSeconds = route.travelSeconds.coerceAtLeast(0.0),
                trafficDelaySeconds = candidate.traffic?.delaySeconds?.coerceAtLeast(0.0) ?: 0.0,
                effectiveTravelSeconds = effective,
                extraDistanceMeters = (route.distanceMeters - baselineDistance).coerceAtLeast(0.0),
                extraTravelSeconds = (effective - baselineEffective).coerceAtLeast(0.0),
                labelFa = if (index == 0) "پیشنهاد NV" else "مسیر ${index + 1}"
            )
        }
    }

    private fun effectiveSeconds(candidate: RouteCandidate): Double =
        candidate.route.travelSeconds.coerceAtLeast(0.0) +
            (candidate.traffic?.delaySeconds?.coerceAtLeast(0.0) ?: 0.0)

    private companion object {
        const val MAX_ROUTES = 4
    }
}

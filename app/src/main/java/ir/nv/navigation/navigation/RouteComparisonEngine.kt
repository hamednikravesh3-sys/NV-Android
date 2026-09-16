package ir.nv.navigation.navigation

import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteSource
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

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
        val usesToll: Boolean,
        val usesHighway: Boolean,
        val usesFerry: Boolean,
        val estimatedEnergyIndex: Double?,
        val roadQualityScore: Double?,
        val etaEpochMillis: Long,
        val labelFa: String
    ) {
        val distanceKm: Double get() = distanceMeters / 1_000.0
        val travelMinutes: Int get() = (effectiveTravelSeconds / 60.0).roundToInt().coerceAtLeast(0)
        fun etaText(zoneId: ZoneId = ZoneId.systemDefault()): String {
            val time = Instant.ofEpochMilli(etaEpochMillis).atZone(zoneId)
            return "%02d:%02d".format(time.hour, time.minute)
        }
        val statusFa: String get() = buildList {
            if (usesToll) add("عوارض")
            if (usesHighway) add("بزرگراه")
            if (usesFerry) add("شناور")
            roadQualityScore?.let { add("کیفیت جاده ${(it.coerceIn(0.0, 1.0) * 100).roundToInt()}٪") }
        }.joinToString(" • ").ifBlank { "مسیر عادی" }
    }

    fun compare(candidates: List<RouteCandidate>, nowMillis: Long = System.currentTimeMillis()): List<Comparison> {
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
                usesToll = route.usesToll,
                usesHighway = route.usesHighway,
                usesFerry = route.usesFerry,
                estimatedEnergyIndex = route.estimatedEnergyIndex,
                roadQualityScore = route.roadQualityScore,
                etaEpochMillis = nowMillis + (effective * 1_000.0).toLong(),
                labelFa = if (index == 0) "پیشنهاد NV" else "مسیر ${index + 1}"
            )
        }
    }

    private fun effectiveSeconds(candidate: RouteCandidate): Double =
        candidate.route.travelSeconds.coerceAtLeast(0.0) +
            (candidate.traffic?.delaySeconds?.coerceAtLeast(0.0) ?: 0.0)

    private companion object { const val MAX_ROUTES = 4 }
}

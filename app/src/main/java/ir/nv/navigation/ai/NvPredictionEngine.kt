package ir.nv.navigation.ai

import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficSummary
import kotlin.math.abs

/** Lightweight on-device prediction used when no server-side model is available. */
class AdaptiveEtaPredictor(
    private val historicalDelayRatio: Double = 0.0
) : EtaPredictor {
    override suspend fun predict(route: Route, traffic: TrafficSummary?): EtaPrediction {
        val base = route.travelSeconds.coerceAtLeast(0.0)
        val liveDelay = traffic?.delaySeconds?.coerceAtLeast(0.0) ?: 0.0
        val historicalDelay = base * historicalDelayRatio.coerceIn(0.0, 2.0)
        val blendedDelay = if (traffic == null) historicalDelay else 0.8 * liveDelay + 0.2 * historicalDelay
        val confidence = when {
            traffic != null && historicalDelayRatio > 0.0 -> 0.90
            traffic != null -> 0.82
            historicalDelayRatio > 0.0 -> 0.68
            else -> 0.58
        }
        return EtaPrediction(seconds = base + blendedDelay, confidence = confidence)
    }
}

class HistoricalTrafficPredictor(
    samples: List<Sample>
) : TrafficPredictor {
    data class Sample(
        val horizonMinutes: Int,
        val delayRatio: Double,
        val weight: Double = 1.0
    )

    private val validSamples = samples.filter {
        it.horizonMinutes >= 0 && it.delayRatio >= 0.0 && it.weight > 0.0 &&
            it.delayRatio.isFinite() && it.weight.isFinite()
    }

    override suspend fun predict(route: Route, horizonMinutes: Int): TrafficPrediction? {
        require(horizonMinutes >= 0) { "افق پیش‌بینی ترافیک منفی است" }
        if (validSamples.isEmpty()) return null
        val nearest = validSamples.sortedBy { abs(it.horizonMinutes - horizonMinutes) }.take(8)
        val weighted = nearest.sumOf { it.delayRatio * it.weight } / nearest.sumOf { it.weight }
        val delay = route.travelSeconds.coerceAtLeast(0.0) * weighted.coerceIn(0.0, 3.0)
        val confidence = (0.45 + 0.06 * nearest.size).coerceAtMost(0.90)
        return TrafficPrediction(delaySeconds = delay, horizonMinutes = horizonMinutes, confidence = confidence)
    }
}

class NvRoutePredictionEngine(
    private val etaPredictor: EtaPredictor,
    private val trafficPredictor: TrafficPredictor
) {
    data class Prediction(
        val eta: EtaPrediction,
        val futureTraffic: TrafficPrediction?
    )

    suspend fun predict(route: Route, currentTraffic: TrafficSummary?, horizonMinutes: Int): Prediction {
        require(route.travelSeconds >= 0.0 && route.distanceMeters >= 0.0) { "مسیر برای پیش‌بینی معتبر نیست" }
        val eta = etaPredictor.predict(route, currentTraffic)
            ?: EtaPrediction(route.travelSeconds + (currentTraffic?.delaySeconds ?: 0.0), 0.50)
        return Prediction(
            eta = eta.copy(confidence = eta.confidence.coerceIn(0.0, 1.0)),
            futureTraffic = trafficPredictor.predict(route, horizonMinutes)?.let {
                it.copy(confidence = it.confidence.coerceIn(0.0, 1.0), delaySeconds = it.delaySeconds.coerceAtLeast(0.0))
            }
        )
    }
}

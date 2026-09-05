package ir.nv.navigation.ai

import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficSummary

data class EtaPrediction(
    val seconds: Double,
    val confidence: Double
)

data class TrafficPrediction(
    val delaySeconds: Double,
    val horizonMinutes: Int,
    val confidence: Double
)

fun interface EtaPredictor {
    suspend fun predict(route: Route, traffic: TrafficSummary?): EtaPrediction?
}

fun interface TrafficPredictor {
    suspend fun predict(route: Route, horizonMinutes: Int): TrafficPrediction?
}

class HeuristicEtaPredictor : EtaPredictor {
    override suspend fun predict(route: Route, traffic: TrafficSummary?): EtaPrediction {
        val delay = traffic?.delaySeconds?.coerceAtLeast(0.0) ?: 0.0
        return EtaPrediction(
            seconds = route.travelSeconds.coerceAtLeast(0.0) + delay,
            confidence = if (traffic == null) 0.60 else 0.80
        )
    }
}

class NoOpTrafficPredictor : TrafficPredictor {
    override suspend fun predict(route: Route, horizonMinutes: Int): TrafficPrediction? = null
}

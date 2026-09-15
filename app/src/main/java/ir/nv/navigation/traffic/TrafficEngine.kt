package ir.nv.navigation.traffic

import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficReport

enum class CongestionLevel { UNKNOWN, FREE, MODERATE, HEAVY, SEVERE, CLOSED }

data class TrafficSnapshot(
    val live: TrafficReport? = null,
    val historicalDelaySeconds: Double? = null,
    val incidentCount: Int = 0,
    val roadClosed: Boolean = false,
    val generatedAtMillis: Long = System.currentTimeMillis()
) {
    fun isFresh(nowMillis: Long = System.currentTimeMillis(), maxAgeMillis: Long = 120_000L): Boolean =
        nowMillis >= generatedAtMillis && nowMillis - generatedAtMillis <= maxAgeMillis

    fun effectiveDelaySeconds(): Double = when {
        roadClosed -> Double.POSITIVE_INFINITY
        live != null -> live.summary.delaySeconds.coerceAtLeast(0.0)
        historicalDelaySeconds != null -> historicalDelaySeconds.coerceAtLeast(0.0)
        else -> 0.0
    }

    fun congestionLevel(): CongestionLevel {
        if (roadClosed) return CongestionLevel.CLOSED
        val delay = effectiveDelaySeconds()
        if (!delay.isFinite()) return CongestionLevel.CLOSED
        val liveLength = live?.summary?.lengthMeters?.coerceAtLeast(1.0)
        if (liveLength == null && historicalDelaySeconds == null) return CongestionLevel.UNKNOWN
        val delayPerKm = if (liveLength != null) delay / (liveLength / 1000.0) else delay
        return when {
            delayPerKm < 30.0 -> CongestionLevel.FREE
            delayPerKm < 90.0 -> CongestionLevel.MODERATE
            delayPerKm < 180.0 -> CongestionLevel.HEAVY
            else -> CongestionLevel.SEVERE
        }
    }
}

interface TrafficEngine {
    suspend fun snapshot(route: Route): TrafficSnapshot
}

class TomTomTrafficEngine(
    private val liveService: LiveTrafficService = LiveTrafficService()
) : TrafficEngine {
    override suspend fun snapshot(route: Route): TrafficSnapshot = TrafficSnapshot(
        live = runCatching { liveService.report(route) }.getOrNull()
    )
}

class NoOpTrafficEngine : TrafficEngine {
    override suspend fun snapshot(route: Route): TrafficSnapshot = TrafficSnapshot()
}

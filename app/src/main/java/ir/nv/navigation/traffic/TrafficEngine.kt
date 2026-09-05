package ir.nv.navigation.traffic

import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficReport

data class TrafficSnapshot(
    val live: TrafficReport? = null,
    val historicalDelaySeconds: Double? = null,
    val incidentCount: Int = 0,
    val roadClosed: Boolean = false,
    val generatedAtMillis: Long = System.currentTimeMillis()
)

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

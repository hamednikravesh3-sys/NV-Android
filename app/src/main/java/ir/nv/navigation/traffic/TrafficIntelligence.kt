package ir.nv.navigation.traffic

import ir.nv.navigation.core.Coordinate
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

enum class IncidentKind { ACCIDENT, ROAD_CLOSURE, CONSTRUCTION, EVENT, USER_REPORT }

data class TrafficIncident(
    val id: String,
    val kind: IncidentKind,
    val coordinate: Coordinate,
    val severity: Int,
    val startsAtMillis: Long,
    val endsAtMillis: Long? = null,
    val description: String? = null
) {
    fun activeAt(nowMillis: Long): Boolean =
        nowMillis >= startsAtMillis && (endsAtMillis == null || nowMillis <= endsAtMillis)
}

data class HistoricalTrafficSample(
    val dayOfWeek: DayOfWeek,
    val minuteOfDay: Int,
    val averageSpeedKmh: Double,
    val freeFlowSpeedKmh: Double
)

class HistoricalTrafficModel(private val samples: List<HistoricalTrafficSample>) {
    fun expectedDelayRatio(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Double? {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        val minute = time.hour * 60 + time.minute
        val candidates = samples.filter { it.dayOfWeek == time.dayOfWeek }
            .sortedBy { kotlin.math.abs(it.minuteOfDay - minute) }
            .take(6)
        if (candidates.isEmpty()) return null
        val ratios = candidates.mapNotNull { s ->
            if (s.averageSpeedKmh <= 0.0 || s.freeFlowSpeedKmh <= 0.0) null
            else (s.freeFlowSpeedKmh / s.averageSpeedKmh - 1.0).coerceIn(0.0, 5.0)
        }
        return ratios.takeIf { it.isNotEmpty() }?.average()
    }
}

class IncidentTrafficEngine {
    fun active(incidents: List<TrafficIncident>, nowMillis: Long = System.currentTimeMillis()): List<TrafficIncident> =
        incidents.filter { it.activeAt(nowMillis) }.sortedByDescending { it.severity }

    fun routeBlocked(incidents: List<TrafficIncident>, nowMillis: Long = System.currentTimeMillis()): Boolean =
        active(incidents, nowMillis).any { it.kind == IncidentKind.ROAD_CLOSURE && it.severity >= 2 }

    fun penalty(incidents: List<TrafficIncident>, nowMillis: Long = System.currentTimeMillis()): Double =
        active(incidents, nowMillis).sumOf { incident ->
            when (incident.kind) {
                IncidentKind.ROAD_CLOSURE -> 1.0
                IncidentKind.ACCIDENT -> 0.55
                IncidentKind.CONSTRUCTION -> 0.35
                IncidentKind.EVENT -> 0.25
                IncidentKind.USER_REPORT -> 0.20
            } * incident.severity.coerceIn(1, 5)
        }.coerceAtMost(10.0)
}

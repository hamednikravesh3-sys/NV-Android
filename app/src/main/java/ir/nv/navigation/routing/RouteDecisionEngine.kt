package ir.nv.navigation.routing

import ir.nv.navigation.core.Route
import ir.nv.navigation.core.TrafficSummary

/**
 * Ranks route alternatives using a balanced multi-objective score.
 *
 * The time objective is traffic-adjusted effective travel time, while traffic
 * delay also remains a separate reliability penalty. Distance and a
 * speed-sensitive energy proxy complete the multi-objective score.
 */
object RouteDecisionEngine {
    data class Evaluation(
        val route: Route,
        val trafficDelaySeconds: Double,
        val effectiveTravelSeconds: Double,
        val energyIndex: Double,
        val score: Double
    )

    fun rank(
        routes: List<Route>,
        traffic: List<TrafficSummary?> = emptyList()
    ): List<Route> = evaluate(routes, traffic).sortedBy(Evaluation::score).map(Evaluation::route)

    fun evaluate(
        routes: List<Route>,
        traffic: List<TrafficSummary?> = emptyList()
    ): List<Evaluation> {
        if (routes.isEmpty()) return emptyList()
        val rows = routes.mapIndexed { index, route ->
            val delay = traffic.getOrNull(index)?.delaySeconds?.coerceAtLeast(0.0) ?: 0.0
            val baseTime = route.travelSeconds.coerceAtLeast(0.0)
            Raw(
                route = route,
                effectiveTravelSeconds = baseTime + delay,
                trafficDelaySeconds = delay,
                distanceMeters = route.distanceMeters.coerceAtLeast(0.0),
                energyIndex = energyIndex(route)
            )
        }
        val timeRange = Range.of(rows.map(Raw::effectiveTravelSeconds))
        val trafficRange = Range.of(rows.map(Raw::trafficDelaySeconds))
        val distanceRange = Range.of(rows.map(Raw::distanceMeters))
        val energyRange = Range.of(rows.map(Raw::energyIndex))

        return rows.map { row ->
            Evaluation(
                route = row.route,
                trafficDelaySeconds = row.trafficDelaySeconds,
                effectiveTravelSeconds = row.effectiveTravelSeconds,
                energyIndex = row.energyIndex,
                score = TIME_WEIGHT * timeRange.normalize(row.effectiveTravelSeconds) +
                    TRAFFIC_WEIGHT * trafficRange.normalize(row.trafficDelaySeconds) +
                    DISTANCE_WEIGHT * distanceRange.normalize(row.distanceMeters) +
                    ENERGY_WEIGHT * energyRange.normalize(row.energyIndex)
            )
        }
    }

    private fun energyIndex(route: Route): Double {
        val seconds = route.travelSeconds.coerceAtLeast(1.0)
        val distanceKm = route.distanceMeters.coerceAtLeast(0.0) / 1_000.0
        val averageSpeedMetersPerSecond = (route.distanceMeters / seconds).coerceAtLeast(0.0)
        val speedRatio = averageSpeedMetersPerSecond / REFERENCE_SPEED_METERS_PER_SECOND
        return distanceKm * (1.0 + AERODYNAMIC_FACTOR * speedRatio * speedRatio)
    }

    private data class Raw(
        val route: Route,
        val effectiveTravelSeconds: Double,
        val trafficDelaySeconds: Double,
        val distanceMeters: Double,
        val energyIndex: Double
    )

    private data class Range(val min: Double, val max: Double) {
        fun normalize(value: Double): Double {
            val span = max - min
            return if (span <= EPSILON) 0.0 else ((value - min) / span).coerceIn(0.0, 1.0)
        }

        companion object {
            fun of(values: List<Double>): Range = Range(
                min = values.minOrNull() ?: 0.0,
                max = values.maxOrNull() ?: 0.0
            )
        }
    }

    private const val TIME_WEIGHT = 0.45
    private const val TRAFFIC_WEIGHT = 0.25
    private const val DISTANCE_WEIGHT = 0.15
    private const val ENERGY_WEIGHT = 0.15
    private const val REFERENCE_SPEED_METERS_PER_SECOND = 13.89
    private const val AERODYNAMIC_FACTOR = 0.35
    private const val EPSILON = 1e-9
}

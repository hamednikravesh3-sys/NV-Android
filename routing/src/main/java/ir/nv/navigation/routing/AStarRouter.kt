package ir.nv.navigation.routing

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.RoadEdge
import ir.nv.navigation.core.Route
import ir.nv.navigation.navigation.CustomRoutePreferences
import ir.nv.navigation.navigation.RouteProfile
import java.util.PriorityQueue
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AStarRouter(private val graph: RoutingGraph) {
    private data class State(val nodeId: Long, val incomingEdgeId: Long?)
    private data class QueueEntry(val state: State, val score: Double) : Comparable<QueueEntry> {
        override fun compareTo(other: QueueEntry): Int = score.compareTo(other.score)
    }
    private data class Previous(val state: State, val edge: RoadEdge)

    fun route(origin: Coordinate, destination: Coordinate): Route? =
        route(origin, destination, RouteProfile.SMART)

    fun route(
        origin: Coordinate,
        destination: Coordinate,
        profile: RouteProfile,
        custom: CustomRoutePreferences = CustomRoutePreferences()
    ): Route? = routeAvoiding(origin, destination, emptySet(), profile, custom.normalized())

    fun routes(origin: Coordinate, destination: Coordinate, limit: Int = 3): List<Route> =
        routes(origin, destination, RouteProfile.SMART, CustomRoutePreferences(), limit)

    fun routes(
        origin: Coordinate,
        destination: Coordinate,
        profile: RouteProfile,
        custom: CustomRoutePreferences = CustomRoutePreferences(),
        limit: Int = 4
    ): List<Route> {
        val normalized = custom.normalized()
        val primary = routeAvoiding(origin, destination, emptySet(), profile, normalized) ?: return emptyList()
        if (limit <= 1 || primary.edgeIds.size < 2) return listOf(primary)
        val attempts = (limit * 2).coerceAtMost(MAX_ALTERNATIVE_ATTEMPTS)
        val avoidIndices = (1..attempts).map {
            (primary.edgeIds.size * it / (attempts + 1)).coerceIn(0, primary.edgeIds.lastIndex)
        }.distinct()
        val candidates = avoidIndices.mapNotNull { index ->
            routeAvoiding(origin, destination, setOf(primary.edgeIds[index]), profile, normalized)
        }.filter { it.travelSeconds <= primary.travelSeconds * MAX_ALTERNATIVE_TIME_FACTOR }
            .distinctBy(Route::edgeIds)
            .sortedBy { routeObjectiveScore(it, profile, normalized) }
        return (listOf(primary) + candidates).distinctBy(Route::edgeIds).take(limit.coerceIn(1, 4))
    }

    private fun routeAvoiding(
        origin: Coordinate,
        destination: Coordinate,
        bannedEdgeIds: Set<Long>,
        profile: RouteProfile,
        custom: CustomRoutePreferences
    ): Route? {
        val startNode = graph.nearestNode(origin) ?: return null
        val goalNode = graph.nearestNode(destination) ?: return null
        val start = State(startNode, null)
        val frontier = PriorityQueue<QueueEntry>()
        val best = mutableMapOf(start to 0.0)
        val previous = mutableMapOf<State, Previous>()
        frontier += QueueEntry(start, heuristic(startNode, goalNode, profile, custom))
        var goal: State? = null
        while (frontier.isNotEmpty()) {
            val current = frontier.remove().state
            val currentCost = best[current] ?: continue
            if (current.nodeId == goalNode) { goal = current; break }
            for (edge in graph.outgoing(current.nodeId)) {
                if (edge.id in bannedEdgeIds || excluded(edge, profile, custom)) continue
                if (!graph.isTurnAllowed(current.nodeId, current.incomingEdgeId, edge.id)) continue
                val next = State(edge.toNode, edge.id)
                val nextCost = currentCost + edgeObjectiveCost(edge, profile, custom)
                if (nextCost < (best[next] ?: Double.POSITIVE_INFINITY)) {
                    best[next] = nextCost
                    previous[next] = Previous(current, edge)
                    frontier += QueueEntry(next, nextCost + heuristic(edge.toNode, goalNode, profile, custom))
                }
            }
        }
        return goal?.let { reconstruct(start, it, previous) }
    }

    private fun excluded(edge: RoadEdge, profile: RouteProfile, custom: CustomRoutePreferences): Boolean = when (profile) {
        RouteProfile.AVOID_TOLL -> edge.toll
        RouteProfile.AVOID_HIGHWAY -> edge.isHighway
        RouteProfile.AVOID_FERRY -> edge.ferry
        RouteProfile.CUSTOM -> (custom.avoidToll && edge.toll) || (custom.avoidHighway && edge.isHighway) || (custom.avoidFerry && edge.ferry)
        else -> false
    }

    private fun edgeObjectiveCost(edge: RoadEdge, profile: RouteProfile, custom: CustomRoutePreferences): Double {
        val travel = edge.travelSeconds.coerceAtLeast(MIN_EDGE_SECONDS)
        val distance = edge.distanceMeters.coerceAtLeast(0.0)
        val distanceSeconds = distance / REFERENCE_SPEED_METERS_PER_SECOND
        val speed = (distance / travel).coerceIn(0.0, MAX_EXPECTED_SPEED_METERS_PER_SECOND)
        val energy = (distance / 1_000.0) * BASE_ENERGY_EQUIVALENT_SECONDS_PER_KM *
            (1.0 + ENERGY_SPEED_FACTOR * speed / REFERENCE_SPEED_METERS_PER_SECOND * speed / REFERENCE_SPEED_METERS_PER_SECOND)
        val roadPenalty = (1.0 - edge.roadQualityScore) * ROAD_QUALITY_PENALTY_SECONDS_PER_KM * (distance / 1_000.0)
        val weights = weights(profile, custom)
        return weights.time * travel + weights.distance * distanceSeconds + weights.energy * energy + weights.quality * roadPenalty
    }

    private fun routeObjectiveScore(route: Route, profile: RouteProfile, custom: CustomRoutePreferences): Double {
        val travel = route.travelSeconds.coerceAtLeast(MIN_EDGE_SECONDS)
        val distance = route.distanceMeters.coerceAtLeast(0.0)
        val distanceSeconds = distance / REFERENCE_SPEED_METERS_PER_SECOND
        val energy = route.estimatedEnergyIndex ?: (distance / 1_000.0)
        val qualityPenalty = (1.0 - (route.roadQualityScore ?: 0.7)) * ROAD_QUALITY_PENALTY_SECONDS_PER_KM * (distance / 1_000.0)
        val w = weights(profile, custom)
        return w.time * travel + w.distance * distanceSeconds + w.energy * energy * BASE_ENERGY_EQUIVALENT_SECONDS_PER_KM + w.quality * qualityPenalty
    }

    private data class Weights(val time: Double, val distance: Double, val energy: Double, val quality: Double)
    private fun weights(profile: RouteProfile, custom: CustomRoutePreferences): Weights = when (profile) {
        RouteProfile.FASTEST -> Weights(0.82, 0.10, 0.05, 0.03)
        RouteProfile.SHORTEST -> Weights(0.18, 0.72, 0.05, 0.05)
        RouteProfile.ECO -> Weights(0.30, 0.10, 0.52, 0.08)
        RouteProfile.SCENIC -> Weights(0.30, 0.15, 0.15, 0.40)
        RouteProfile.LOW_TRAFFIC -> Weights(0.70, 0.12, 0.10, 0.08)
        RouteProfile.CUSTOM -> Weights(custom.timeWeight, custom.distanceWeight, custom.energyWeight, custom.roadQualityWeight)
        else -> Weights(0.60, 0.18, 0.14, 0.08)
    }

    private fun reconstruct(start: State, goal: State, previous: Map<State, Previous>): Route {
        val edges = mutableListOf<RoadEdge>()
        var cursor = goal
        while (cursor != start) {
            val step = requireNotNull(previous[cursor])
            edges += step.edge
            cursor = step.state
        }
        edges.reverse()
        val nodeIds = buildList { add(start.nodeId); edges.forEach { add(it.toNode) } }
        val totalDistance = edges.sumOf { it.distanceMeters }
        val totalSeconds = edges.sumOf { it.travelSeconds }
        val quality = if (edges.isEmpty()) null else edges.sumOf { it.roadQualityScore * it.distanceMeters } / totalDistance.coerceAtLeast(1.0)
        val avgSpeed = totalDistance / totalSeconds.coerceAtLeast(1.0)
        val energyIndex = (totalDistance / 1_000.0) * (1.0 + ENERGY_SPEED_FACTOR *
            (avgSpeed / REFERENCE_SPEED_METERS_PER_SECOND) * (avgSpeed / REFERENCE_SPEED_METERS_PER_SECOND))
        return Route(
            points = nodeIds.map(graph::coordinate),
            edgeIds = edges.map { it.id },
            distanceMeters = totalDistance,
            travelSeconds = totalSeconds,
            estimatedEnergyIndex = energyIndex,
            usesToll = edges.any { it.toll },
            usesHighway = edges.any { it.isHighway },
            usesFerry = edges.any { it.ferry },
            roadQualityScore = quality,
            speedLimitsKmh = edges.mapNotNull { it.speedLimitKmh }.distinct()
        )
    }

    private fun heuristic(from: Long, to: Long, profile: RouteProfile, custom: CustomRoutePreferences): Double {
        val distance = haversine(graph.coordinate(from), graph.coordinate(to))
        val w = weights(profile, custom)
        return w.time * distance / MAX_EXPECTED_SPEED_METERS_PER_SECOND + w.distance * distance / REFERENCE_SPEED_METERS_PER_SECOND
    }

    private fun haversine(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude); val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1; val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val MAX_EXPECTED_SPEED_METERS_PER_SECOND = 55.56
        const val REFERENCE_SPEED_METERS_PER_SECOND = 13.89
        const val MIN_EDGE_SECONDS = 0.1
        const val BASE_ENERGY_EQUIVALENT_SECONDS_PER_KM = 45.0
        const val ENERGY_SPEED_FACTOR = 0.35
        const val ROAD_QUALITY_PENALTY_SECONDS_PER_KM = 40.0
        const val MAX_ALTERNATIVE_ATTEMPTS = 8
        const val MAX_ALTERNATIVE_TIME_FACTOR = 1.8
    }
}

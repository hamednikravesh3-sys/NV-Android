package ir.nv.navigation.routing

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.RoadEdge
import ir.nv.navigation.core.Route
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
        routeAvoiding(origin, destination, emptySet())

    /**
     * Returns the best balanced path plus practical detours. The primary route
     * is optimized with a multi-objective cost rather than travel time alone.
     * The public Route still reports physical distance and ETA independently.
     */
    fun routes(origin: Coordinate, destination: Coordinate, limit: Int = 3): List<Route> {
        val primary = route(origin, destination) ?: return emptyList()
        if (limit <= 1 || primary.edgeIds.size < 2) return listOf(primary)
        val attempts = (limit * 2).coerceAtMost(MAX_ALTERNATIVE_ATTEMPTS)
        val avoidIndices = (1..attempts).map {
            (primary.edgeIds.size * it / (attempts + 1)).coerceIn(0, primary.edgeIds.lastIndex)
        }.distinct()
        val candidates = avoidIndices.mapNotNull { index ->
            routeAvoiding(origin, destination, setOf(primary.edgeIds[index]))
        }.filter { it.travelSeconds <= primary.travelSeconds * MAX_ALTERNATIVE_TIME_FACTOR }
            .distinctBy(Route::edgeIds)
            .sortedBy(::routeObjectiveScore)

        return (listOf(primary) + candidates)
            .distinctBy(Route::edgeIds)
            .take(limit)
    }

    private fun routeAvoiding(
        origin: Coordinate,
        destination: Coordinate,
        bannedEdgeIds: Set<Long>
    ): Route? {
        val startNode = graph.nearestNode(origin) ?: return null
        val goalNode = graph.nearestNode(destination) ?: return null
        val start = State(startNode, null)
        val frontier = PriorityQueue<QueueEntry>()
        val best = mutableMapOf(start to 0.0)
        val previous = mutableMapOf<State, Previous>()
        frontier += QueueEntry(start, heuristic(startNode, goalNode))

        var goal: State? = null
        while (frontier.isNotEmpty()) {
            val currentEntry = frontier.remove()
            val current = currentEntry.state
            val currentCost = best[current] ?: continue
            if (current.nodeId == goalNode) {
                goal = current
                break
            }

            for (edge in graph.outgoing(current.nodeId)) {
                if (edge.id in bannedEdgeIds) continue
                if (!graph.isTurnAllowed(current.nodeId, current.incomingEdgeId, edge.id)) continue
                val next = State(edge.toNode, edge.id)
                val nextCost = currentCost + edgeObjectiveCost(edge)
                if (nextCost < (best[next] ?: Double.POSITIVE_INFINITY)) {
                    best[next] = nextCost
                    previous[next] = Previous(current, edge)
                    frontier += QueueEntry(next, nextCost + heuristic(edge.toNode, goalNode))
                }
            }
        }

        return goal?.let { reconstruct(start, it, previous) }
    }

    /**
     * Converts time, distance and a speed-sensitive energy estimate to one
     * seconds-like cost. Time intentionally remains dominant for navigation.
     */
    private fun edgeObjectiveCost(edge: RoadEdge): Double {
        val travelSeconds = edge.travelSeconds.coerceAtLeast(MIN_EDGE_SECONDS)
        val distanceMeters = edge.distanceMeters.coerceAtLeast(0.0)
        val distanceEquivalentSeconds = distanceMeters / REFERENCE_SPEED_METERS_PER_SECOND
        val speedMetersPerSecond = (distanceMeters / travelSeconds)
            .coerceIn(0.0, MAX_EXPECTED_SPEED_METERS_PER_SECOND)
        val aerodynamicFactor = 1.0 + ENERGY_SPEED_FACTOR *
            (speedMetersPerSecond / REFERENCE_SPEED_METERS_PER_SECOND) *
            (speedMetersPerSecond / REFERENCE_SPEED_METERS_PER_SECOND)
        val energyEquivalentSeconds =
            (distanceMeters / 1_000.0) * BASE_ENERGY_EQUIVALENT_SECONDS_PER_KM * aerodynamicFactor

        return TIME_WEIGHT * travelSeconds +
            DISTANCE_WEIGHT * distanceEquivalentSeconds +
            ENERGY_WEIGHT * energyEquivalentSeconds
    }

    private fun routeObjectiveScore(route: Route): Double {
        val travelSeconds = route.travelSeconds.coerceAtLeast(MIN_EDGE_SECONDS)
        val distanceMeters = route.distanceMeters.coerceAtLeast(0.0)
        val averageSpeed = (distanceMeters / travelSeconds)
            .coerceIn(0.0, MAX_EXPECTED_SPEED_METERS_PER_SECOND)
        val distanceEquivalentSeconds = distanceMeters / REFERENCE_SPEED_METERS_PER_SECOND
        val aerodynamicFactor = 1.0 + ENERGY_SPEED_FACTOR *
            (averageSpeed / REFERENCE_SPEED_METERS_PER_SECOND) *
            (averageSpeed / REFERENCE_SPEED_METERS_PER_SECOND)
        val energyEquivalentSeconds =
            (distanceMeters / 1_000.0) * BASE_ENERGY_EQUIVALENT_SECONDS_PER_KM * aerodynamicFactor
        return TIME_WEIGHT * travelSeconds +
            DISTANCE_WEIGHT * distanceEquivalentSeconds +
            ENERGY_WEIGHT * energyEquivalentSeconds
    }

    private fun reconstruct(
        start: State,
        goal: State,
        previous: Map<State, Previous>
    ): Route {
        val edges = mutableListOf<RoadEdge>()
        var cursor = goal
        while (cursor != start) {
            val step = requireNotNull(previous[cursor])
            edges += step.edge
            cursor = step.state
        }
        edges.reverse()
        val nodeIds = buildList {
            add(start.nodeId)
            edges.forEach { add(it.toNode) }
        }
        return Route(
            points = nodeIds.map(graph::coordinate),
            edgeIds = edges.map { it.id },
            distanceMeters = edges.sumOf { it.distanceMeters },
            travelSeconds = edges.sumOf { it.travelSeconds }
        )
    }

    /** Admissible lower bound for the multi-objective cost. */
    private fun heuristic(from: Long, to: Long): Double {
        val distanceMeters = haversine(graph.coordinate(from), graph.coordinate(to))
        val minimumTravelSeconds = distanceMeters / MAX_EXPECTED_SPEED_METERS_PER_SECOND
        val distanceEquivalentSeconds = distanceMeters / REFERENCE_SPEED_METERS_PER_SECOND
        val minimumEnergyEquivalentSeconds =
            (distanceMeters / 1_000.0) * BASE_ENERGY_EQUIVALENT_SECONDS_PER_KM
        return TIME_WEIGHT * minimumTravelSeconds +
            DISTANCE_WEIGHT * distanceEquivalentSeconds +
            ENERGY_WEIGHT * minimumEnergyEquivalentSeconds
    }

    private fun haversine(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val MAX_EXPECTED_SPEED_METERS_PER_SECOND = 55.56
        const val REFERENCE_SPEED_METERS_PER_SECOND = 13.89
        const val MIN_EDGE_SECONDS = 0.1
        const val TIME_WEIGHT = 0.65
        const val DISTANCE_WEIGHT = 0.20
        const val ENERGY_WEIGHT = 0.15
        const val BASE_ENERGY_EQUIVALENT_SECONDS_PER_KM = 45.0
        const val ENERGY_SPEED_FACTOR = 0.35
        const val MAX_ALTERNATIVE_ATTEMPTS = 6
        const val MAX_ALTERNATIVE_TIME_FACTOR = 1.8
    }
}

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

    /** Returns the fastest path plus practical detours created by avoiding key edges. */
    fun routes(origin: Coordinate, destination: Coordinate, limit: Int = 3): List<Route> {
        val primary = route(origin, destination) ?: return emptyList()
        if (limit <= 1 || primary.edgeIds.size < 2) return listOf(primary)
        val attempts = (limit * 2).coerceAtMost(MAX_ALTERNATIVE_ATTEMPTS)
        val avoidIndices = (1..attempts).map {
            (primary.edgeIds.lastIndex * it / (attempts + 1)).coerceIn(0, primary.edgeIds.lastIndex)
        }.distinct()
        val candidates = avoidIndices.mapNotNull { index ->
            routeAvoiding(origin, destination, setOf(primary.edgeIds[index]))
        }.filter { it.travelSeconds <= primary.travelSeconds * MAX_ALTERNATIVE_TIME_FACTOR }
        return (listOf(primary) + candidates)
            .distinctBy(Route::edgeIds)
            .sortedBy(Route::travelSeconds)
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
            val current = frontier.remove().state
            val currentCost = best[current] ?: continue
            if (current.nodeId == goalNode) {
                goal = current
                break
            }

            for (edge in graph.outgoing(current.nodeId)) {
                if (edge.id in bannedEdgeIds) continue
                if (!graph.isTurnAllowed(current.nodeId, current.incomingEdgeId, edge.id)) continue
                val next = State(edge.toNode, edge.id)
                val nextCost = currentCost + edge.travelSeconds
                if (nextCost < (best[next] ?: Double.POSITIVE_INFINITY)) {
                    best[next] = nextCost
                    previous[next] = Previous(current, edge)
                    frontier += QueueEntry(next, nextCost + heuristic(edge.toNode, goalNode))
                }
            }
        }

        return goal?.let { reconstruct(start, it, previous) }
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

    private fun heuristic(from: Long, to: Long): Double {
        val distanceMeters = haversine(graph.coordinate(from), graph.coordinate(to))
        return distanceMeters / MAX_EXPECTED_SPEED_METERS_PER_SECOND
    }

    private fun haversine(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val MAX_EXPECTED_SPEED_METERS_PER_SECOND = 55.56
        const val MAX_ALTERNATIVE_ATTEMPTS = 6
        const val MAX_ALTERNATIVE_TIME_FACTOR = 1.8
    }
}

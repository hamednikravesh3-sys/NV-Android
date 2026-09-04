package ir.nv.navigation.routing

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.RoadEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AStarRouterTest {
    private val coordinates = mapOf(
        1L to Coordinate(35.000, 51.000),
        2L to Coordinate(35.001, 51.001),
        3L to Coordinate(35.002, 51.001),
        4L to Coordinate(35.002, 51.002)
    )
    private val edges = listOf(
        RoadEdge(12, 1, 2, 200.0, 20.0, "A"),
        RoadEdge(24, 2, 4, 200.0, 20.0, "B"),
        RoadEdge(23, 2, 3, 150.0, 15.0, "C"),
        RoadEdge(34, 3, 4, 150.0, 15.0, "D")
    )

    @Test
    fun forbiddenTurnIsNeverUsed() {
        val graph = MemoryGraph(
            coordinates = coordinates,
            edges = edges,
            forbiddenTurns = setOf(Triple(2L, 12L, 24L))
        )
        val route = AStarRouter(graph).route(
            coordinates.getValue(1),
            coordinates.getValue(4)
        )
        assertEquals(listOf(12L, 23L, 34L), route?.edgeIds)
        assertEquals(500.0, route?.distanceMeters ?: 0.0, 0.001)
    }

    @Test
    fun directedEdgesEnforceOneWayRoads() {
        val graph = MemoryGraph(coordinates, edges, emptySet())
        val reverseRoute = AStarRouter(graph).route(
            coordinates.getValue(4),
            coordinates.getValue(1)
        )
        assertNull(reverseRoute)
    }

    @Test
    fun returnsSelectableAlternativePaths() {
        val graph = MemoryGraph(coordinates, edges, emptySet())
        val routes = AStarRouter(graph).routes(
            coordinates.getValue(1),
            coordinates.getValue(4),
            limit = 3
        )
        assertEquals(2, routes.size)
        assertEquals(listOf(12L, 24L), routes[0].edgeIds)
        assertEquals(listOf(12L, 23L, 34L), routes[1].edgeIds)
    }
}

private class MemoryGraph(
    private val coordinates: Map<Long, Coordinate>,
    private val edges: List<RoadEdge>,
    private val forbiddenTurns: Set<Triple<Long, Long, Long>>
) : RoutingGraph {
    override fun nearestNode(point: Coordinate): Long? =
        coordinates.entries.firstOrNull { it.value == point }?.key

    override fun coordinate(nodeId: Long): Coordinate = coordinates.getValue(nodeId)

    override fun outgoing(nodeId: Long): List<RoadEdge> =
        edges.filter { it.fromNode == nodeId }

    override fun isTurnAllowed(
        viaNode: Long,
        incomingEdgeId: Long?,
        outgoingEdgeId: Long
    ): Boolean =
        incomingEdgeId == null ||
            Triple(viaNode, incomingEdgeId, outgoingEdgeId) !in forbiddenTurns
}

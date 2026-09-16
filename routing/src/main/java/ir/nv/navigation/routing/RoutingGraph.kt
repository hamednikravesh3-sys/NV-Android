package ir.nv.navigation.routing

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.RoadEdge

interface RoutingGraph {
    fun nearestNode(point: Coordinate): Long?
    fun coordinate(nodeId: Long): Coordinate
    fun outgoing(nodeId: Long): List<RoadEdge>
    fun isTurnAllowed(viaNode: Long, incomingEdgeId: Long?, outgoingEdgeId: Long): Boolean
}

package ir.nv.navigation.core

data class Coordinate(val latitude: Double, val longitude: Double)

data class Place(
    val code: Long,
    val name: String,
    val coordinate: Coordinate,
    val category: String
) {
    val displayName: String get() = "$name — $code"
}

data class RoadEdge(
    val id: Long,
    val fromNode: Long,
    val toNode: Long,
    val distanceMeters: Double,
    val travelSeconds: Double,
    val roadName: String?
)

data class Route(
    val points: List<Coordinate>,
    val edgeIds: List<Long>,
    val distanceMeters: Double,
    val travelSeconds: Double
)

data class TrafficSegment(
    val start: Coordinate,
    val end: Coordinate,
    val lengthMeters: Double,
    val delaySeconds: Double
)

data class TrafficSummary(
    val lengthMeters: Double,
    val delaySeconds: Double
)

data class RouteNotice(
    val title: String,
    val detail: String,
    val distanceAheadMeters: Double,
    val kind: Kind
) {
    enum class Kind { ATTRACTION, WEATHER, TRAFFIC }
}

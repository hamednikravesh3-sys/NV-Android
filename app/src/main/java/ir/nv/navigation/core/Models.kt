package ir.nv.navigation.core

data class Coordinate(val latitude: Double, val longitude: Double)

data class Place(
    val code: Long,
    val name: String,
    val coordinate: Coordinate,
    val category: String,
    val personalCode: String? = null
) {
    val displayName: String
        get() = if (personalCode.isNullOrBlank()) "$name — $code" else "$name — $personalCode"
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
    val travelSeconds: Double,
    val maneuvers: List<RouteManeuver> = emptyList()
)

enum class RouteSource { NONE, ONLINE, OFFLINE }

data class RouteManeuver(
    val instruction: String,
    val roadName: String?,
    val distanceMeters: Double,
    val direction: Direction,
    val coordinate: Coordinate? = null
) {
    enum class Direction {
        STRAIGHT,
        SLIGHT_LEFT,
        LEFT,
        SHARP_LEFT,
        SLIGHT_RIGHT,
        RIGHT,
        SHARP_RIGHT,
        UTURN,
        ARRIVE
    }
}

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
    val kind: Kind,
    val placeCode: Long? = null
) {
    enum class Kind { ATTRACTION, WEATHER, TRAFFIC }
}

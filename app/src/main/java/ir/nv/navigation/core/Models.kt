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
        get() = when {
            !personalCode.isNullOrBlank() -> "$name — $personalCode"
            code > 0 -> "$name — $code"
            else -> name
        }
}

data class RoadEdge(
    val id: Long,
    val fromNode: Long,
    val toNode: Long,
    val distanceMeters: Double,
    val travelSeconds: Double,
    val roadName: String?,
    val speedLimitKmh: Int? = null,
    val highwayClass: String? = null,
    val toll: Boolean = false,
    val ferry: Boolean = false,
    val surface: String? = null,
    val laneCount: Int? = null
) {
    val isHighway: Boolean get() = highwayClass in setOf("motorway", "motorway_link", "trunk", "trunk_link")
    val roadQualityScore: Double get() = when (surface?.lowercase()) {
        "asphalt", "concrete", "concrete:plates", "paved" -> 1.0
        "paving_stones", "sett", "compacted" -> 0.75
        "fine_gravel", "gravel" -> 0.55
        "dirt", "earth", "ground", "sand", "mud" -> 0.30
        else -> 0.70
    }
}

data class Route(
    val points: List<Coordinate>,
    val edgeIds: List<Long>,
    val distanceMeters: Double,
    val travelSeconds: Double,
    val maneuvers: List<RouteManeuver> = emptyList(),
    val estimatedEnergyIndex: Double? = null,
    val usesToll: Boolean = false,
    val usesHighway: Boolean = false,
    val usesFerry: Boolean = false,
    val roadQualityScore: Double? = null,
    val speedLimitsKmh: List<Int> = emptyList()
)

enum class RouteSource { NONE, ONLINE, OFFLINE }

data class RouteManeuver(
    val instruction: String,
    val roadName: String?,
    val distanceMeters: Double,
    val direction: Direction,
    val coordinate: Coordinate? = null,
    val lanes: List<Lane> = emptyList(),
    val exitNumber: String? = null,
    val junctionName: String? = null,
    val speedLimitKmh: Int? = null
) {
    data class Lane(val direction: Direction, val recommended: Boolean)

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

data class TrafficReport(
    val summary: TrafficSummary,
    val segments: List<TrafficSegment>
)

data class RouteNotice(
    val title: String,
    val detail: String,
    val distanceAheadMeters: Double,
    val kind: Kind,
    val placeCode: Long? = null,
    val imageUrl: String? = null
) {
    enum class Kind { ATTRACTION, SERVICE, WEATHER, TRAFFIC }
}

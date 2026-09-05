package ir.nv.navigation.places

import ir.nv.navigation.core.RouteNotice

class AheadEngine(
    private val maxItems: Int = 8,
    private val maxDistanceMeters: Double = 20_000.0
) {
    fun rank(notices: List<RouteNotice>): List<RouteNotice> = notices
        .asSequence()
        .filter { it.distanceAheadMeters in 0.0..maxDistanceMeters }
        .distinctBy { Triple(it.kind, it.title, (it.distanceAheadMeters / 250.0).toInt()) }
        .sortedWith(
            compareBy<RouteNotice> { priority(it.kind) }
                .thenBy { it.distanceAheadMeters }
        )
        .take(maxItems)
        .toList()

    private fun priority(kind: RouteNotice.Kind): Int = when (kind) {
        RouteNotice.Kind.TRAFFIC -> 0
        RouteNotice.Kind.WEATHER -> 1
        RouteNotice.Kind.SERVICE -> 2
        RouteNotice.Kind.ATTRACTION -> 3
    }
}

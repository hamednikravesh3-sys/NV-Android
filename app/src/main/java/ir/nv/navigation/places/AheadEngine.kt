package ir.nv.navigation.places

import ir.nv.navigation.core.RouteNotice

class AheadEngine(
    private val maxItems: Int = 8,
    private val maxDistanceMeters: Double = 20_000.0
) {
    fun rank(notices: List<RouteNotice>): List<RouteNotice> = notices
        .asSequence()
        .filter { it.distanceAheadMeters in 0.0..maxDistanceMeters }
        .distinctBy { Triple(it.kind, normalizedTitle(it.title), (it.distanceAheadMeters / 250.0).toInt()) }
        .sortedWith(
            compareBy<RouteNotice> { smartPriority(it) }
                .thenBy { it.distanceAheadMeters }
                .thenBy { normalizedTitle(it.title) }
        )
        .take(maxItems)
        .toList()

    private fun smartPriority(notice: RouteNotice): Int {
        val title = normalizedTitle(notice.title)
        if (notice.kind == RouteNotice.Kind.TRAFFIC) return 0
        if (notice.kind == RouteNotice.Kind.WEATHER) return 1
        if (notice.kind == RouteNotice.Kind.SERVICE) {
            return when {
                EMERGENCY_WORDS.any(title::contains) -> 2
                FUEL_WORDS.any(title::contains) -> 3
                PARKING_WORDS.any(title::contains) -> 4
                else -> 5
            }
        }
        return 6
    }

    private fun normalizedTitle(value: String): String = value
        .trim()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .lowercase()

    private companion object {
        val EMERGENCY_WORDS = listOf("اورژانس", "بیمارستان", "درمانگاه", "داروخانه", "پلیس", "آتش")
        val FUEL_WORDS = listOf("پمپ", "بنزین", "سوخت", "شارژ")
        val PARKING_WORDS = listOf("پارکینگ", "توقفگاه")
    }
}

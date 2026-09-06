package ir.nv.navigation.search

import ir.nv.navigation.core.Place
import ir.nv.navigation.data.PersianText

fun interface PlaceSearchProvider {
    suspend fun search(query: String): List<Place>
}

data class HybridSearchResult(
    val items: List<Place>,
    val onlineAttempted: Boolean,
    val onlineFailed: Boolean
)

class HybridSearchEngine(
    private val offline: PlaceSearchProvider,
    private val online: PlaceSearchProvider
) {
    suspend fun search(
        query: String,
        onlineAvailable: Boolean,
        preferOffline: Boolean,
        limit: Int = 30
    ): List<Place> = searchDetailed(query, onlineAvailable, preferOffline, limit).items

    suspend fun searchDetailed(
        query: String,
        onlineAvailable: Boolean,
        preferOffline: Boolean,
        limit: Int = 30
    ): HybridSearchResult {
        val clean = normalize(query)
        if (clean.isEmpty()) return HybridSearchResult(emptyList(), false, false)

        val variants = expandQuery(clean)
        val local = variants
            .flatMap { variant -> runCatching { offline.search(variant) }.getOrDefault(emptyList()) }

        if (!onlineAvailable || preferOffline) {
            return HybridSearchResult(rankAndDeduplicate(local, clean, limit), false, false)
        }

        var onlineFailed = false
        val remote = buildList {
            variants.take(MAX_ONLINE_VARIANTS).forEach { variant ->
                val result = runCatching { online.search(variant) }
                if (result.isFailure) onlineFailed = true
                addAll(result.getOrDefault(emptyList()))
            }
        }

        return HybridSearchResult(
            items = rankAndDeduplicate(local + remote, clean, limit),
            onlineAttempted = true,
            onlineFailed = onlineFailed && remote.isEmpty()
        )
    }

    private fun rankAndDeduplicate(values: List<Place>, query: String, limit: Int): List<Place> = values
        .distinctBy {
            Triple(
                normalize(it.name),
                (it.coordinate.latitude * 10_000).toInt(),
                (it.coordinate.longitude * 10_000).toInt()
            )
        }
        // Kotlin's sortedWith is stable. Equal relevance therefore preserves provider order:
        // exact/local data stays ahead of an equally relevant remote result, while genuinely
        // better fuzzy matches can still move upward.
        .sortedWith(compareBy<Place> { smartScore(it, query) })
        .take(limit)

    private fun smartScore(place: Place, query: String): Double {
        val name = normalize(place.name)
        val category = normalize(place.category.orEmpty())
        if (name == query) return 0.0
        if (name.startsWith(query)) return 0.05
        if (name.contains(query)) return 0.1

        val queryTokens = query.split(' ').filter(String::isNotBlank)
        val nameTokens = name.split(' ').filter(String::isNotBlank)
        val tokenHits = queryTokens.count { q -> nameTokens.any { n -> n == q || n.startsWith(q) || n.contains(q) } }
        val tokenPenalty = 1.0 - tokenHits.toDouble() / queryTokens.size.coerceAtLeast(1)

        val edit = normalizedEditDistance(name, query)
        val categoryBonus = if (queryTokens.any { category.contains(it) }) -0.08 else 0.0
        return 0.25 + tokenPenalty * 0.45 + edit * 0.45 + categoryBonus
    }

    private fun expandQuery(query: String): List<String> {
        val variants = linkedSetOf(query)
        val words = query.split(' ').filter(String::isNotBlank)

        words.forEachIndexed { index, word ->
            val replacements = synonymGroups.firstOrNull { word in it }
                ?.filterNot { it == word }
                .orEmpty()
            replacements.take(3).forEach { replacement ->
                val copy = words.toMutableList()
                copy[index] = replacement
                variants += copy.joinToString(" ")
            }
        }

        if (words.size <= 3) {
            categoryHints.entries.firstOrNull { (key, _) -> query.contains(key) }?.value?.forEach { hint ->
                variants += "$query $hint"
            }
        }
        return variants.take(MAX_QUERY_VARIANTS)
    }

    private fun normalizedEditDistance(a: String, b: String): Double {
        if (a == b) return 0.0
        if (a.isEmpty() || b.isEmpty()) return 1.0
        val left = if (a.length > MAX_EDIT_TEXT) a.take(MAX_EDIT_TEXT) else a
        val right = if (b.length > MAX_EDIT_TEXT) b.take(MAX_EDIT_TEXT) else b
        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        return previous[right.length].toDouble() / maxOf(left.length, right.length).coerceAtLeast(1)
    }

    private fun normalize(value: String): String = PersianText.normalize(value)
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .lowercase()

    private companion object {
        const val MAX_QUERY_VARIANTS = 8
        const val MAX_ONLINE_VARIANTS = 4
        const val MAX_EDIT_TEXT = 64

        val synonymGroups = listOf(
            setOf("ترمینال", "پایانه", "مسافربری", "پایانه مسافربری"),
            setOf("فرودگاه", "ایرپورت", "airport"),
            setOf("بیمارستان", "درمانگاه", "کلینیک", "مرکز درمانی"),
            setOf("پمپ بنزین", "جایگاه سوخت", "بنزین", "سوخت"),
            setOf("دانشگاه", "دانشکده", "پردیس"),
            setOf("هتل", "مسافرخانه", "اقامتگاه"),
            setOf("رستوران", "غذاخوری", "فست فود"),
            setOf("فروشگاه", "مرکز خرید", "مجتمع تجاری", "بازار"),
            setOf("پارکینگ", "توقفگاه"),
            setOf("ایستگاه", "استیشن", "station")
        )

        val categoryHints = mapOf(
            "ترمینال" to listOf("bus station", "amenity bus_station"),
            "پایانه" to listOf("bus station", "terminal"),
            "فرودگاه" to listOf("airport", "aerodrome"),
            "بیمارستان" to listOf("hospital"),
            "پمپ بنزین" to listOf("fuel"),
            "دانشگاه" to listOf("university"),
            "هتل" to listOf("hotel")
        )
    }
}

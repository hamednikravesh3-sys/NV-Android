package ir.nv.navigation.search

import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.data.NvCodeAllocationService
import ir.nv.navigation.data.PersianText
import ir.nv.navigation.data.PlaceCodes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

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
    private val online: PlaceSearchProvider,
    private val nvCodeService: NvCodeAllocationService = NvCodeAllocationService()
) {
    suspend fun search(
        query: String,
        onlineAvailable: Boolean,
        preferOffline: Boolean,
        limit: Int = 30,
        reference: Coordinate? = null
    ): List<Place> = searchDetailed(query, onlineAvailable, preferOffline, limit, reference).items

    suspend fun searchDetailed(
        query: String,
        onlineAvailable: Boolean,
        preferOffline: Boolean,
        limit: Int = 30,
        reference: Coordinate? = null
    ): HybridSearchResult {
        val clean = normalize(stripSearchIntent(query))
        if (clean.isEmpty()) return HybridSearchResult(emptyList(), false, false)

        val variants = expandQuery(clean)
        val local = variants
            .flatMap { variant -> runCatching { offline.search(variant) }.getOrDefault(emptyList()) }

        val publicCode = PlaceCodes.publicCode(query)
        val explicitNvCode = publicCode != null && PlaceCodes.isExplicitNvCode(query)
        val centralCode = publicCode != null && (PlaceCodes.isRegistryCode(publicCode) || explicitNvCode)
        if (centralCode) {
            // An explicit NV: code is an instruction to resolve the shared registry identity.
            // Try it even during the immediate/local phase so legacy low-number codes are not
            // blocked by the ViewModel's generic online-search gate. Failures remain non-fatal.
            val mayResolveRegistry = nvCodeService.isConfigured() &&
                (onlineAvailable || explicitNvCode) &&
                (!preferOffline || explicitNvCode)
            val registryResult = if (mayResolveRegistry) nvCodeService.resolveOnline(query) else Result.success(null)
            val registryPlace = registryResult.getOrNull()
            if (registryPlace != null || mayResolveRegistry) {
                return HybridSearchResult(
                    items = if (registryPlace != null) listOf(registryPlace) else rankAndDeduplicate(local, clean, limit, reference),
                    onlineAttempted = mayResolveRegistry,
                    onlineFailed = registryResult.isFailure
                )
            }
            return HybridSearchResult(rankAndDeduplicate(local, clean, limit, reference), false, false)
        }

        if (!onlineAvailable || preferOffline) {
            return HybridSearchResult(rankAndDeduplicate(local, clean, limit, reference), false, false)
        }

        val onlineResults = withTimeoutOrNull(ONLINE_SEARCH_BUDGET_MS) {
            coroutineScope {
                variants.take(MAX_ONLINE_VARIANTS)
                    .map { variant -> async { runCatching { online.search(variant) } } }
                    .awaitAll()
            }
        }
        val timedOut = onlineResults == null
        val completed = onlineResults.orEmpty()
        val remote = completed.flatMap { it.getOrDefault(emptyList()) }
        val onlineFailed = timedOut || completed.any { it.isFailure }

        return HybridSearchResult(
            items = rankAndDeduplicate(local + remote, clean, limit, reference),
            onlineAttempted = true,
            onlineFailed = onlineFailed && remote.isEmpty()
        )
    }

    private fun rankAndDeduplicate(
        values: List<Place>,
        query: String,
        limit: Int,
        reference: Coordinate?
    ): List<Place> = values
        .distinctBy {
            Triple(
                normalize(it.name),
                (it.coordinate.latitude * 10_000).toInt(),
                (it.coordinate.longitude * 10_000).toInt()
            )
        }
        .sortedWith(compareBy<Place> { smartScore(it, query, reference) })
        .take(limit)

    private fun smartScore(place: Place, query: String, reference: Coordinate?): Double {
        val textScore = textScore(place, query)
        val proximityPenalty = reference?.let {
            // Text relevance remains dominant. Distance only resolves otherwise
            // similar candidates so "بیمارستان" prefers a useful nearby result.
            (distanceMeters(it, place.coordinate) / 50_000.0).coerceIn(0.0, 1.0) * PROXIMITY_WEIGHT
        } ?: 0.0
        return textScore + proximityPenalty
    }

    private fun textScore(place: Place, query: String): Double {

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

    private fun stripSearchIntent(value: String): String {
        var result = value
        val noise = listOf(
            "نزدیک ترین", "نزدیک‌ترین", "نزدیکترین", "نزدیک من", "اطراف من", "همین اطراف",
            "بهترین", "الان باز", "باز الان", "منو ببر", "مرا ببر", "مسیر بده", "مسیریابی کن",
            "از اینجا", "از موقعیت من", "لطفا", "لطفاً"
        )
        noise.forEach { result = result.replace(it, " ", ignoreCase = true) }
        return result.replace(Regex("\\s+"), " ").trim().ifBlank { value }
    }

    private fun distanceMeters(a: Coordinate, b: Coordinate): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(min(1.0, h)))
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
        const val MAX_ONLINE_VARIANTS = 2
        const val MAX_EDIT_TEXT = 64
        const val ONLINE_SEARCH_BUDGET_MS = 3_000L
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val PROXIMITY_WEIGHT = 0.18

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

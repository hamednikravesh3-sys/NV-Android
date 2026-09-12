package ir.nv.navigation.places

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.online.OnlinePlacesService
import ir.nv.navigation.search.HybridSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

fun interface NearbyPlaceProvider {
    fun search(center: Coordinate, category: NearbyCategory, radiusMeters: Int, limit: Int): List<Place>
}

data class PlaceSearchContext(
    val currentLocation: Coordinate? = null,
    val origin: Coordinate? = null,
    val destination: Coordinate? = null,
    val route: Route? = null,
    val onlineAvailable: Boolean = false,
    val preferOffline: Boolean = false
)

data class NearbySearchRequest(
    val category: NearbyCategory,
    val scope: NearbyScope = NearbyScope.AROUND_ME,
    val radiusMeters: Int = 25_000,
    val limit: Int = 40
) {
    init {
        NearbySearchPolicy.requireSupportedRadius(radiusMeters)
        require(limit in 1..100) { "Nearby limit must be between 1 and 100" }
    }
}

/**
 * Single place boundary for text search and nearby discovery. The production constructor
 * delegates to HybridSearchEngine (offline + Photon/Nominatim) and OnlinePlacesService
 * (Google Places + OSM/Overpass). Provider injection keeps ranking/radius behavior unit-testable.
 */
class UnifiedPlaceRepository internal constructor(
    private val textSearch: suspend (String, Boolean, Boolean, Int) -> List<Place>,
    private val onlineNearby: NearbyPlaceProvider,
    private val offlineNearby: NearbyPlaceProvider? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    constructor(
        hybridSearchEngine: HybridSearchEngine,
        onlinePlacesService: OnlinePlacesService,
        offlineNearby: NearbyPlaceProvider? = null
    ) : this(
        textSearch = { query, onlineAvailable, preferOffline, limit ->
            hybridSearchEngine.search(query, onlineAvailable, preferOffline, limit)
        },
        onlineNearby = NearbyPlaceProvider { center, category, radius, limit ->
            onlinePlacesService.searchNearby(center, category.query, radius, limit)
        },
        offlineNearby = offlineNearby
    )

    private data class CacheEntry(val createdAt: Long, val values: List<Place>)
    private val nearbyCache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun search(
        query: String,
        onlineAvailable: Boolean,
        preferOffline: Boolean,
        limit: Int = 30
    ): List<Place> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()
        return normalizeAndRank(textSearch(clean, onlineAvailable, preferOffline, limit), limit)
    }

    suspend fun nearby(request: NearbySearchRequest, context: PlaceSearchContext): List<Place> = withContext(Dispatchers.IO) {
        val key = cacheKey(request, context)
        nearbyCache[key]?.takeIf { nowMillis() - it.createdAt <= CACHE_TTL_MS }?.let { return@withContext it.values }

        val centers = centersFor(request.scope, context)
        if (centers.isEmpty()) return@withContext emptyList()

        for (radius in NearbySearchPolicy.progressiveRadiiMeters(request.radiusMeters)) {
            val candidates = buildList {
                centers.forEach { center ->
                    offlineNearby?.let { provider ->
                        addAll(runCatching { provider.search(center, request.category, radius, request.limit) }.getOrDefault(emptyList()))
                    }
                    if (context.onlineAvailable && !context.preferOffline) {
                        addAll(runCatching { onlineNearby.search(center, request.category, radius, request.limit) }.getOrDefault(emptyList()))
                    }
                }
            }

            val bounded = when (request.scope) {
                NearbyScope.ALONG_ROUTE -> context.route?.let { NearbySearchPolicy.filterAlongRoute(it, candidates, radius) }.orEmpty()
                else -> anchorFor(request.scope, context)?.let { NearbySearchPolicy.filterWithinRadius(it, candidates, radius) }.orEmpty()
            }
            val ranked = normalizeAndRank(bounded, request.limit)
            if (ranked.isNotEmpty()) {
                nearbyCache[key] = CacheEntry(nowMillis(), ranked)
                trimCache()
                return@withContext ranked
            }
        }
        emptyList()
    }

    fun clearCache() = nearbyCache.clear()

    private fun centersFor(scope: NearbyScope, context: PlaceSearchContext): List<Coordinate> = when (scope) {
        NearbyScope.AROUND_ME -> listOfNotNull(context.currentLocation)
        NearbyScope.NEAR_ORIGIN -> listOfNotNull(context.origin)
        NearbyScope.NEAR_DESTINATION -> listOfNotNull(context.destination)
        NearbyScope.ALONG_ROUTE -> context.route?.let(::sampleRouteCenters).orEmpty()
    }

    private fun anchorFor(scope: NearbyScope, context: PlaceSearchContext): Coordinate? = when (scope) {
        NearbyScope.AROUND_ME -> context.currentLocation
        NearbyScope.NEAR_ORIGIN -> context.origin
        NearbyScope.NEAR_DESTINATION -> context.destination
        NearbyScope.ALONG_ROUTE -> null
    }

    private fun sampleRouteCenters(route: Route, maxSamples: Int = 6): List<Coordinate> {
        if (route.points.isEmpty()) return emptyList()
        if (route.points.size <= maxSamples) return route.points.distinct()
        val last = route.points.lastIndex
        return (0 until maxSamples)
            .map { sample -> route.points[(sample * last.toDouble() / (maxSamples - 1)).roundToInt()] }
            .distinct()
    }

    private fun normalizeAndRank(values: List<Place>, limit: Int): List<Place> = values
        .asSequence()
        .filter(::isValid)
        .map { place -> place.copy(confidence = place.confidence.coerceIn(0.0, 1.0)) }
        .distinctBy(::identity)
        .sortedWith(
            compareByDescending<Place> { it.isOpen == true }
                .thenByDescending { it.confidence }
                .thenByDescending { it.rating ?: -1.0 }
                .thenBy { it.distance ?: Double.MAX_VALUE }
                .thenBy { normalize(it.name) }
        )
        .take(limit.coerceIn(1, 100))
        .toList()

    private fun isValid(place: Place): Boolean =
        place.name.isNotBlank() &&
            place.coordinate.latitude in -90.0..90.0 &&
            place.coordinate.longitude in -180.0..180.0

    private fun identity(place: Place): String = buildString {
        append(normalize(place.name))
        append('|')
        append((place.coordinate.latitude * 10_000).roundToInt())
        append('|')
        append((place.coordinate.longitude * 10_000).roundToInt())
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace('ة', 'ه')
        .replace("\u200c", " ")
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace(Regex("\\s+"), " ")

    private fun cacheKey(request: NearbySearchRequest, context: PlaceSearchContext): String {
        val anchor = anchorFor(request.scope, context)
        val routeKey = context.route?.let { route ->
            val first = route.points.firstOrNull()
            val last = route.points.lastOrNull()
            "${first?.latitude}:${first?.longitude}:${last?.latitude}:${last?.longitude}:${route.points.size}"
        }.orEmpty()
        return listOf(
            request.category.id,
            request.scope.name,
            request.radiusMeters,
            request.limit,
            anchor?.latitude?.let { (it * 1000).roundToInt() },
            anchor?.longitude?.let { (it * 1000).roundToInt() },
            routeKey,
            context.onlineAvailable,
            context.preferOffline
        ).joinToString(":")
    }

    private fun trimCache() {
        val now = nowMillis()
        nearbyCache.entries.removeIf { now - it.value.createdAt > CACHE_TTL_MS }
        if (nearbyCache.size > MAX_CACHE_ENTRIES) {
            nearbyCache.entries.sortedBy { it.value.createdAt }
                .take(nearbyCache.size - MAX_CACHE_ENTRIES)
                .forEach { nearbyCache.remove(it.key) }
        }
    }

    private companion object {
        const val CACHE_TTL_MS = 120_000L
        const val MAX_CACHE_ENTRIES = 48
    }
}

package ir.nv.navigation.search

import ir.nv.navigation.core.Place

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
        val clean = query.trim()
        if (clean.isEmpty()) return HybridSearchResult(emptyList(), false, false)

        val local = runCatching { offline.search(clean) }.getOrDefault(emptyList())
        if (!onlineAvailable || preferOffline) {
            return HybridSearchResult(deduplicate(local, limit), false, false)
        }

        val remoteResult = runCatching { online.search(clean) }
        val remote = remoteResult.getOrDefault(emptyList())
        return HybridSearchResult(
            items = deduplicate(local + remote, limit),
            onlineAttempted = true,
            onlineFailed = remoteResult.isFailure
        )
    }

    private fun deduplicate(values: List<Place>, limit: Int): List<Place> = values
        .distinctBy {
            Triple(
                it.name.trim().lowercase(),
                (it.coordinate.latitude * 10_000).toInt(),
                (it.coordinate.longitude * 10_000).toInt()
            )
        }
        .take(limit)
}

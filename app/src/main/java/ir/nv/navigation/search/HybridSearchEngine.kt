package ir.nv.navigation.search

import ir.nv.navigation.core.Place

fun interface PlaceSearchProvider {
    suspend fun search(query: String): List<Place>
}

class HybridSearchEngine(
    private val offline: PlaceSearchProvider,
    private val online: PlaceSearchProvider
) {
    suspend fun search(
        query: String,
        onlineAvailable: Boolean,
        preferOffline: Boolean,
        limit: Int = 30
    ): List<Place> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()

        val local = runCatching { offline.search(clean) }.getOrDefault(emptyList())
        if (!onlineAvailable || preferOffline) return deduplicate(local, limit)

        val remote = runCatching { online.search(clean) }.getOrDefault(emptyList())
        return deduplicate(local + remote, limit)
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

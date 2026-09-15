package ir.nv.navigation.places

import android.content.Context
import ir.nv.navigation.data.PlaceRepository
import ir.nv.navigation.map.IranPackManager
import ir.nv.navigation.online.OnlinePlacesService
import java.io.Closeable

/**
 * Application-facing Nearby boundary used by the Rahnama presentation layer.
 *
 * It owns the provider lifecycle so Compose never opens the offline SQLite database or
 * constructs provider adapters directly. The installed Iran pack is detected lazily and
 * re-opened when its backing places database changes. UnifiedPlaceRepository remains the
 * single place for progressive radii, hard radius filtering, ranking, dedupe and cache.
 */
class NearbySearchCoordinator(context: Context) : Closeable {
    private val packManager = IranPackManager(context.applicationContext)
    private val onlinePlaces = OnlinePlacesService()
    private val lock = Any()

    @Volatile
    private var offlinePlaces: PlaceRepository? = null
    private var offlineSignature: String? = null

    private val repository = UnifiedPlaceRepository(
        textSearch = { _, _, _, _ -> emptyList() },
        onlineNearby = NearbyPlaceProvider { center, category, radiusMeters, limit ->
            onlinePlaces.searchNearby(center, category.query, radiusMeters, limit)
        },
        offlineNearby = NearbyPlaceProvider { center, category, radiusMeters, limit ->
            offlineRepository()?.nearby(
                center = center,
                categoryPatterns = category.offlineCategoryPatterns,
                radiusMeters = radiusMeters,
                limit = limit
            ).orEmpty()
        }
    )

    suspend fun nearby(request: NearbySearchRequest, context: PlaceSearchContext) =
        repository.nearby(request, context)

    fun invalidate() {
        repository.clearCache()
        synchronized(lock) {
            offlinePlaces?.close()
            offlinePlaces = null
            offlineSignature = null
        }
    }

    private fun offlineRepository(): PlaceRepository? {
        if (!packManager.isReady()) {
            if (offlinePlaces != null) invalidate()
            return null
        }
        val file = packManager.placesFile
        if (!file.isFile || file.length() <= 0L) return null
        val signature = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        offlinePlaces?.takeIf { offlineSignature == signature }?.let { return it }

        return synchronized(lock) {
            offlinePlaces?.takeIf { offlineSignature == signature } ?: runCatching {
                offlinePlaces?.close()
                PlaceRepository(file).also {
                    offlinePlaces = it
                    offlineSignature = signature
                    repository.clearCache()
                }
            }.getOrElse {
                offlinePlaces = null
                offlineSignature = null
                null
            }
        }
    }

    override fun close() = invalidate()
}

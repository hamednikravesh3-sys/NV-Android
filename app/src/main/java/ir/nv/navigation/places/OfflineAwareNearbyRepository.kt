package ir.nv.navigation.places

import android.content.Context
import ir.nv.navigation.data.PlaceRepository
import ir.nv.navigation.map.IranPackManager
import ir.nv.navigation.online.OnlinePlacesService
import java.io.Closeable

/**
 * Lifecycle-aware Nearby gateway for the Rahnama UI. It keeps the product policy in
 * UnifiedPlaceRepository while opening the installed POI database only when a valid
 * offline pack exists. Online and offline providers therefore share one ranking,
 * dedupe and radius boundary.
 */
class OfflineAwareNearbyRepository(context: Context) : Closeable {
    private val packManager = IranPackManager(context.applicationContext)
    private val onlinePlaces = OnlinePlacesService()

    @Volatile
    private var offlinePlaces: PlaceRepository? = null

    private val repository = UnifiedPlaceRepository(
        textSearch = { _, _, _, _ -> emptyList() },
        onlineNearby = NearbyPlaceProvider { center, category, radiusMeters, limit ->
            onlinePlaces.searchNearby(center, category.query, radiusMeters, limit)
        },
        offlineNearby = NearbyPlaceProvider { center, category, radiusMeters, limit ->
            ensureOfflinePlaces()?.nearby(
                center = center,
                categoryPatterns = category.offlineCategoryPatterns,
                radiusMeters = radiusMeters,
                limit = limit
            ).orEmpty()
        }
    )

    suspend fun nearby(request: NearbySearchRequest, context: PlaceSearchContext) =
        repository.nearby(request, context)

    @Synchronized
    private fun ensureOfflinePlaces(): PlaceRepository? {
        if (!packManager.isReady()) {
            offlinePlaces?.close()
            offlinePlaces = null
            return null
        }
        offlinePlaces?.let { return it }
        return runCatching { PlaceRepository(packManager.placesFile) }
            .getOrNull()
            .also { offlinePlaces = it }
    }

    override fun close() {
        repository.clearCache()
        synchronized(this) {
            offlinePlaces?.close()
            offlinePlaces = null
        }
    }
}

package ir.nv.navigation.application

import android.content.Context
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import ir.nv.navigation.places.NearbyCategory
import ir.nv.navigation.places.NearbyScope
import ir.nv.navigation.places.NearbySearchCoordinator
import ir.nv.navigation.places.NearbySearchRequest
import ir.nv.navigation.places.PlaceSearchContext
import java.io.Closeable

/** Application boundary for Nearby so presentation code never owns provider/database lifecycle. */
class NearbyDiscoveryUseCase(context: Context) : Closeable {
    private val coordinator = NearbySearchCoordinator(context.applicationContext)

    suspend operator fun invoke(
        request: NearbySearchRequest,
        context: PlaceSearchContext
    ): List<Place> = coordinator.nearby(request, context)

    override fun close() = coordinator.close()
}

class EmergencySearchUseCase(
    private val nearby: NearbyDiscoveryUseCase
) {
    suspend fun search(
        category: NearbyCategory,
        center: Coordinate,
        onlineAvailable: Boolean,
        preferOffline: Boolean,
        radiusMeters: Int = 25_000,
        limit: Int = 20
    ): List<Place> {
        require(category in EMERGENCY_CATEGORIES) { "Unsupported emergency category: ${category.id}" }
        return nearby(
            NearbySearchRequest(
                category = category,
                scope = NearbyScope.AROUND_ME,
                radiusMeters = radiusMeters,
                limit = limit
            ),
            PlaceSearchContext(
                currentLocation = center,
                onlineAvailable = onlineAvailable,
                preferOffline = preferOffline
            )
        )
    }

    private companion object {
        val EMERGENCY_CATEGORIES = setOf(
            NearbyCategory.EMERGENCY,
            NearbyCategory.HOSPITAL,
            NearbyCategory.POLICE,
            NearbyCategory.FIRE,
            NearbyCategory.RESCUE
        )
    }
}

class DestinationParkingUseCase(
    private val nearby: NearbyDiscoveryUseCase
) {
    suspend fun search(
        destination: Coordinate,
        onlineAvailable: Boolean,
        preferOffline: Boolean,
        radiusMeters: Int = 5_000,
        limit: Int = 30
    ): List<Place> = nearby(
        NearbySearchRequest(
            category = NearbyCategory.PARKING,
            scope = NearbyScope.NEAR_DESTINATION,
            radiusMeters = radiusMeters,
            limit = limit
        ),
        PlaceSearchContext(
            destination = destination,
            onlineAvailable = onlineAvailable,
            preferOffline = preferOffline
        )
    )
}

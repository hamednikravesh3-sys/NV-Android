package ir.nv.navigation.places

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedPlaceRepositoryTest {
    @Test
    fun nearbyExpandsOnlyThroughConfiguredRadiusStepsAndStopsOnResults() = runBlocking {
        val attempts = mutableListOf<Int>()
        val repository = UnifiedPlaceRepository(
            textSearch = { _, _, _, _ -> emptyList() },
            onlineNearby = NearbyPlaceProvider { center, _, radius, _ ->
                attempts += radius
                if (radius == 25_000) {
                    listOf(Place(1, "داروخانه", Coordinate(center.latitude + 0.03, center.longitude), "amenity:pharmacy", source = "test", confidence = .9))
                } else emptyList()
            }
        )
        val result = repository.nearby(
            NearbySearchRequest(NearbyCategory.PHARMACY, NearbyScope.AROUND_ME, 100_000),
            PlaceSearchContext(currentLocation = Coordinate(35.0, 51.0), onlineAvailable = true)
        )

        assertEquals(listOf(5_000, 10_000, 25_000), attempts)
        assertEquals(1, result.size)
    }

    @Test
    fun repositoryNormalizesDuplicatesAndPrefersHigherConfidence() = runBlocking {
        val repository = UnifiedPlaceRepository(
            textSearch = { _, _, _, _ ->
                listOf(
                    Place(1, "بیمارستان امید", Coordinate(35.0, 51.0), "amenity:hospital", confidence = .4),
                    Place(2, "بیمارستان امید", Coordinate(35.00001, 51.00001), "amenity:hospital", confidence = .95),
                    Place(3, "", Coordinate(35.1, 51.1), "amenity:hospital")
                )
            },
            onlineNearby = NearbyPlaceProvider { _, _, _, _ -> emptyList() }
        )

        val values = repository.search("امید", onlineAvailable = true, preferOffline = false)
        assertEquals(1, values.size)
        assertTrue(values.first().confidence >= .9)
    }
}

package ir.nv.navigation.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NvCloudSyncClientTest {
    private val client = NvCloudSyncClient()

    @Test
    fun parsesAndDeduplicatesCloudPlaces() {
        val snapshot = client.parseSnapshot(
            """{"revision":7,"places":[{"code":"1845623","name":"خانه","latitude":35.7,"longitude":51.4,"updatedAt":100},{"code":"1845623","name":"خانه تکراری","latitude":35.7,"longitude":51.4,"updatedAt":101}]}"""
        )
        assertEquals(7L, snapshot.revision)
        assertEquals(1, snapshot.places.size)
        assertEquals("1845623", snapshot.places.single().code)
    }

    @Test
    fun rejectsInvalidCoordinate() {
        assertThrows(IllegalArgumentException::class.java) {
            client.parseSnapshot(
                """{"revision":1,"places":[{"code":"12","name":"x","latitude":120.0,"longitude":51.4,"updatedAt":1}]}"""
            )
        }
    }

    @Test
    fun rejectsNonNumericNvCode() {
        assertThrows(IllegalArgumentException::class.java) {
            client.parseSnapshot(
                """{"revision":1,"places":[{"code":"NV-12","name":"x","latitude":35.7,"longitude":51.4,"updatedAt":1}]}"""
            )
        }
    }
}

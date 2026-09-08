package ir.nv.navigation.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvincePackAvailabilityServiceTest {
    private val service = ProvincePackAvailabilityService()

    @Test
    fun catalogContainsAll31IranProvincesWithStableUniqueIds() {
        assertEquals(31, OfflinePackCatalog.provinces.size)
        assertEquals(31, OfflinePackCatalog.provinces.map { it.id }.toSet().size)
        assertTrue(OfflinePackCatalog.provinces.all { it.title.isNotBlank() })
        assertTrue(OfflinePackCatalog.provinceById("tehran")?.title == "تهران")
    }

    @Test
    fun releaseParserOnlyAcceptsPublishedKnownProvincePacks() {
        val availability = service.parseRelease(
            """{
              "tag_name":"map-v1",
              "assets":[
                {"name":"iran.nvpack"},
                {"name":"province-tehran.nvpack"},
                {"name":"province-fars.nvpack"},
                {"name":"province-unknown.nvpack"},
                {"name":"province-gilan.zip"}
              ]
            }"""
        )
        assertEquals("map-v1", availability.releaseTag)
        assertEquals(setOf("tehran", "fars"), availability.publishedPackIds)
        assertTrue(availability.isPublished(requireNotNull(OfflinePackCatalog.provinceById("tehran"))))
        assertFalse(availability.isPublished(requireNotNull(OfflinePackCatalog.provinceById("gilan"))))
    }
}

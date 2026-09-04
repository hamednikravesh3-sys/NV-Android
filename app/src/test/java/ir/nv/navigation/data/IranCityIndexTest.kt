package ir.nv.navigation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IranCityIndexTest {
    @Test
    fun findsPersianCityImmediatelyWithoutNetwork() {
        val result = IranCityIndex.search("تهر")
        assertTrue(result.isNotEmpty())
        assertEquals("تهران", result.first().name)
    }

    @Test
    fun supportsArabicSpellingAndLatinAliases() {
        assertEquals("تهران", IranCityIndex.search("طهران").first().name)
        assertEquals("مشهد", IranCityIndex.search("mashhad").first().name)
    }
}

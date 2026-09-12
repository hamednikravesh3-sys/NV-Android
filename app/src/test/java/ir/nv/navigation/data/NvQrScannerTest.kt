package ir.nv.navigation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NvQrScannerTest {
    @Test
    fun parsesFullNvLocationPayload() {
        val result = NvQrScanner.parse("nv://place/1845623?lat=35.6892&lon=51.3890&name=%D8%AA%D9%87%D8%B1%D8%A7%D9%86")
        assertEquals("1845623", result.code)
        assertEquals(35.6892, result.coordinate!!.latitude, 0.000001)
        assertEquals(51.3890, result.coordinate!!.longitude, 0.000001)
        assertEquals("تهران", result.name)
    }

    @Test
    fun parsesCompactNvCodePayload() {
        val result = NvQrScanner.parse("NV:1845623")
        assertEquals("1845623", result.code)
        assertNull(result.coordinate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsForeignQrPayload() {
        NvQrScanner.parse("https://example.com")
    }
}

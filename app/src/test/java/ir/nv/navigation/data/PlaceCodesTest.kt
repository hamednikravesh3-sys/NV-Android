package ir.nv.navigation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceCodesTest {
    @Test
    fun readsLatinPersianAndPrefixedPublicCodes() {
        assertEquals(1845623L, PlaceCodes.publicCode("1845623"))
        assertEquals(1845623L, PlaceCodes.publicCode("۱۸۴۵۶۲۳"))
        assertEquals(1845623L, PlaceCodes.publicCode("NV: 1845623"))
    }

    @Test
    fun rejectsNamesAndNonPositiveShareCodes() {
        assertNull(PlaceCodes.publicCode("تهران"))
        assertNull(PlaceCodes.shareCode(-1))
        assertEquals("NV:42", PlaceCodes.shareCode(42))
    }

    @Test
    fun onlineCodesPreserveOsmIdentity() {
        val code = requireNotNull(PlaceCodes.onlineCode("way", 123456789L))
        assertEquals(PlaceCodes.OnlineIdentity("way", 123456789L), PlaceCodes.onlineIdentity(code))
        assertEquals(code, PlaceCodes.publicCode("NV:$code"))
    }
}

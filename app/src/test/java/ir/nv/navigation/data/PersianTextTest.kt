package ir.nv.navigation.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianTextTest {
    @Test
    fun normalizesArabicCharactersAndWhitespace() {
        assertEquals("تهران بزرگ", PersianText.normalize("  تهران   بزرگ  "))
        assertEquals("یکی", PersianText.normalize("يكي"))
    }
}

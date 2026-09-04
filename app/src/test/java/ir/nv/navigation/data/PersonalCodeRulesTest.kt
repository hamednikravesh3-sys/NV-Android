package ir.nv.navigation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalCodeRulesTest {
    @Test fun `accepts Persian and Arabic digits`() {
        assertEquals("123", PersonalCodeRules.normalize("۱۲۳"))
        assertEquals("456", PersonalCodeRules.normalize("٤٥٦"))
    }

    @Test fun `only accepts a positive numeric code up to nine digits`() {
        assertNull(PersonalCodeRules.normalize("HOME"))
        assertNull(PersonalCodeRules.normalize("0"))
        assertNull(PersonalCodeRules.normalize("1234567890"))
    }
}

package ir.nv.navigation.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartJourneyIntentParserTest {
    @Test fun parsesFastPersianDestination() {
        val intent = SmartJourneyIntentParser.parse("میخوام برم میدان آزادی سریع‌ترین مسیر")
        assertEquals("میدان آزادی", intent.destinationQuery)
        assertEquals(SmartJourneyPriority.FASTEST, intent.priority)
    }
    @Test fun parsesTaxiRequestWithoutPollutingDestination() {
        val intent = SmartJourneyIntentParser.parse("منو با اسنپ ببر فرودگاه مهرآباد")
        assertEquals("فرودگاه مهرآباد", intent.destinationQuery)
        assertEquals(SmartJourneyMode.TAXI, intent.mode)
    }
    @Test fun parsesTransitFromCurrentLocation() {
        val intent = SmartJourneyIntentParser.parse("از موقعیت من با مترو برو تجریش")
        assertEquals("تجریش", intent.destinationQuery)
        assertEquals(SmartJourneyMode.TRANSIT, intent.mode)
    }
    @Test fun parsesWalkingRequest() {
        val intent = SmartJourneyIntentParser.parse("پیاده منو ببر پارک ملت")
        assertEquals("پارک ملت", intent.destinationQuery)
        assertEquals(SmartJourneyMode.WALKING, intent.mode)
    }
    @Test fun detectsHurryAsFastest() {
        val intent = SmartJourneyIntentParser.parse("عجله دارم ببر منو ترمینال جنوب")
        assertEquals("ترمینال جنوب", intent.destinationQuery)
        assertTrue(intent.hurry)
        assertEquals(SmartJourneyPriority.FASTEST, intent.priority)
    }
    @Test fun resolvesNaturalMultiStopPhraseIntoGeocodableFinalDestination() {
        val queries = SmartJourneyIntentParser.destinationQueries("میخوام برم خیابانی بهشتی تهران بعد میرداماد")
        assertEquals("میرداماد تهران", queries.first())
        assertTrue(queries.contains("بلوار میرداماد تهران"))
        assertTrue(queries.any { it.contains("بهشتی") && it.contains("تهران") })
    }
    @Test fun fixesCommonStreetWordTypo() {
        val queries = SmartJourneyIntentParser.destinationQueries("خیابانی بهشتی تهران")
        assertTrue(queries.first().startsWith("خیابان بهشتی"))
    }
}

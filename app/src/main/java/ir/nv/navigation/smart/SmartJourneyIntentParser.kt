package ir.nv.navigation.smart

enum class SmartJourneyMode {
    AUTO,
    WALKING,
    BICYCLE,
    MOTORCYCLE,
    DRIVING,
    TAXI,
    TRANSIT,
    MIXED
}

enum class SmartJourneyPriority {
    BALANCED,
    FASTEST,
    CHEAPEST,
    LOW_WALKING
}

data class SmartJourneyIntent(
    val destinationQuery: String,
    val mode: SmartJourneyMode,
    val priority: SmartJourneyPriority,
    val hurry: Boolean,
    val rawQuery: String
)

object SmartJourneyIntentParser {
    fun parse(raw: String): SmartJourneyIntent {
        val normalized = normalize(raw)
        val taxi = containsAny(normalized, TAXI_WORDS)
        val transit = containsAny(normalized, TRANSIT_WORDS)
        val walk = containsAny(normalized, WALK_WORDS)
        val bicycle = containsAny(normalized, BICYCLE_WORDS)
        val motorcycle = containsAny(normalized, MOTORCYCLE_WORDS)
        val driving = containsAny(normalized, DRIVING_WORDS)
        val explicitMixed = containsAny(normalized, MIXED_WORDS) || (taxi && transit)

        val mode = when {
            explicitMixed -> SmartJourneyMode.MIXED
            taxi -> SmartJourneyMode.TAXI
            transit -> SmartJourneyMode.TRANSIT
            walk -> SmartJourneyMode.WALKING
            bicycle -> SmartJourneyMode.BICYCLE
            motorcycle -> SmartJourneyMode.MOTORCYCLE
            driving -> SmartJourneyMode.DRIVING
            else -> SmartJourneyMode.AUTO
        }

        val hurry = containsAny(normalized, HURRY_WORDS)
        val priority = when {
            hurry || containsAny(normalized, FAST_WORDS) -> SmartJourneyPriority.FASTEST
            containsAny(normalized, CHEAP_WORDS) -> SmartJourneyPriority.CHEAPEST
            containsAny(normalized, LOW_WALK_WORDS) -> SmartJourneyPriority.LOW_WALKING
            else -> SmartJourneyPriority.BALANCED
        }

        return SmartJourneyIntent(
            destinationQuery = extractDestination(normalized).ifBlank { normalized },
            mode = mode,
            priority = priority,
            hurry = hurry,
            rawQuery = raw.trim()
        )
    }

    private fun extractDestination(normalized: String): String {
        var value = " $normalized "
        REMOVABLE_PHRASES
            .map(::normalize)
            .distinct()
            .sortedByDescending(String::length)
            .forEach { phrase ->
                value = value.replace(Regex("(?<!\\S)${Regex.escape(phrase)}(?!\\S)"), " ")
            }
        value = value
            .replace(Regex("[؟?!،,;؛:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("^(به|تا|سمت|طرف)\\s+"), "")
            .trim()
        return value
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace('ۀ', 'ه')
        .replace('ة', 'ه')
        .replace('\u200c', ' ')
        .replace(Regex("\\s+"), " ")

    private fun containsAny(text: String, words: List<String>): Boolean =
        words.any { text.contains(normalize(it)) }

    private val TAXI_WORDS = listOf("تاکسی", "اسنپ", "تپسی", "rideshare")
    private val TRANSIT_WORDS = listOf("مترو", "اتوبوس", "بی آر تی", "brt", "حمل و نقل عمومی", "حمل ونقل عمومی")
    private val WALK_WORDS = listOf("پیاده", "پیاده روی", "راه رفتن", "walking")
    private val BICYCLE_WORDS = listOf("دوچرخه", "بایسیکل", "bike")
    private val MOTORCYCLE_WORDS = listOf("موتور", "موتورسیکلت")
    private val DRIVING_WORDS = listOf("ماشین", "خودرو", "رانندگی")
    private val MIXED_WORDS = listOf("ترکیبی", "چند حالته", "چندحالته", "ترکیب مترو", "مترو و تاکسی", "مترو و اسنپ")
    private val HURRY_WORDS = listOf("عجله", "زود برسم", "دیرم شده", "فوری", "وقت ندارم")
    private val FAST_WORDS = listOf("سریع ترین", "سریعترین", "کمترین زمان", "زودترین", "fastest")
    private val CHEAP_WORDS = listOf("ارزان ترین", "ارزانترین", "کم هزینه", "کمترین هزینه", "اقتصادی")
    private val LOW_WALK_WORDS = listOf("کمترین پیاده روی", "کم پیاده", "پیاده روی کم")

    private val REMOVABLE_PHRASES = listOf(
        "لطفا", "لطفاً", "خواهشا", "خواهشاً",
        "میخوام برم", "می خوام برم", "می‌خوام برم", "میخواهم بروم", "می خواهم بروم", "می‌خواهم بروم",
        "میخوام", "می خوام", "می‌خوام", "میخواهم", "می خواهم", "می‌خواهم",
        "منو ببر", "مرا ببر", "ببر منو", "من را ببر", "منو", "مرا", "من را",
        "راهنمایی کن", "راهنماییم کن", "مسیر بده", "مسیریابی کن", "راه رو نشون بده", "راه را نشان بده",
        "چطور برم", "چجوری برم", "چگونه بروم", "چطور", "چجوری", "چگونه",
        "از اینجا", "از موقعیت من", "از مکان من", "از لوکیشن من", "موقعیت من",
        "با اسنپ", "اسنپ", "با تپسی", "تپسی", "با تاکسی", "تاکسی",
        "با مترو", "مترو", "با اتوبوس", "اتوبوس", "با بی آر تی", "بی آر تی", "brt",
        "حمل و نقل عمومی", "حمل ونقل عمومی",
        "پیاده روی", "پیاده", "راه رفتن", "walking",
        "با دوچرخه", "دوچرخه", "با موتور", "موتورسیکلت", "موتور",
        "با ماشین", "ماشین", "با خودرو", "خودرو", "رانندگی",
        "سریع ترین مسیر", "سریعترین مسیر", "سریع ترین", "سریعترین", "کمترین زمان", "زودترین",
        "ارزان ترین مسیر", "ارزانترین مسیر", "ارزان ترین", "ارزانترین", "کمترین هزینه", "کم هزینه", "اقتصادی",
        "کمترین پیاده روی", "پیاده روی کم", "کم پیاده",
        "عجله دارم", "عجله", "زود برسم", "دیرم شده", "فوری", "وقت ندارم",
        "ترکیبی", "چند حالته", "چندحالته", "بهترین مسیر", "بهترین راه",
        "برم", "بروم", "بریم", "برو", "برویم", "ببر", "حرکت کنم", "حرکت کنیم", "مسیر"
    )
}

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
        val queries = destinationQueries(raw)
        return SmartJourneyIntent(
            destinationQuery = queries.firstOrNull() ?: extractDestination(normalized).ifBlank { normalized },
            mode = mode,
            priority = priority,
            hurry = hurry,
            rawQuery = raw.trim()
        )
    }

    /**
     * Produces ordered geocoding candidates from normal Persian speech. For a phrase
     * such as «خیابانی بهشتی تهران بعد میرداماد», the final requested stop is tried
     * first and the city context is inherited: «میرداماد تهران». Street/boulevard
     * aliases are then attempted instead of sending the whole sentence to Photon.
     */
    fun destinationQueries(raw: String): List<String> {
        val normalized = canonicalize(normalize(raw))
        val extracted = canonicalize(extractDestination(normalized))
        if (extracted.isBlank()) return emptyList()
        val city = CITY_HINTS.firstOrNull { extracted.contains(it) || normalized.contains(it) }
        val segments = extracted.split(SEQUENCE_SEPARATOR).map(String::trim).filter(String::isNotBlank)
        val target = segments.lastOrNull().orEmpty()
        val variants = linkedSetOf<String>()
        addLocationVariants(variants, target, city)
        if (segments.size == 1) addLocationVariants(variants, extracted, city)
        // If the sentence contained more than one requested stop, retain earlier stops
        // as fallback search candidates but never pretend we routed through them.
        segments.dropLast(1).asReversed().forEach { addLocationVariants(variants, it, city) }
        return variants.filter { it.length >= 2 }.take(10)
    }

    private fun addLocationVariants(out: MutableSet<String>, raw: String, city: String?) {
        var clean = canonicalize(raw)
            .replace(Regex("^(به|تا|سمت|طرف)\\s+"), "")
            .trim()
        if (clean.isBlank()) return
        val hasCity = city != null && clean.contains(city)
        val citySuffix = if (city != null && !hasCity) " $city" else ""
        val primary = "$clean$citySuffix".replace(Regex("\\s+"), " ").trim()
        out += primary

        val cityless = if (city != null) clean.replace(city, " ").replace(Regex("\\s+"), " ").trim() else clean
        val hasRoadPrefix = ROAD_PREFIXES.any { cityless.startsWith("$it ") || cityless == it }
        if (!hasRoadPrefix && cityless.isNotBlank()) {
            val suffix = city?.let { " $it" }.orEmpty()
            out += "خیابان $cityless$suffix".replace(Regex("\\s+"), " ").trim()
            out += "بلوار $cityless$suffix".replace(Regex("\\s+"), " ").trim()
        }
        if (cityless.contains("بهشتی") && !cityless.contains("شهید بهشتی")) {
            val suffix = city?.let { " $it" }.orEmpty()
            out += "خیابان شهید بهشتی$suffix".replace(Regex("\\s+"), " ").trim()
        }
        val withoutPrefix = ROAD_PREFIXES.fold(cityless) { acc, prefix ->
            acc.replace(Regex("^${Regex.escape(prefix)}\\s+"), "")
        }.trim()
        if (withoutPrefix.isNotBlank() && withoutPrefix != cityless) {
            val suffix = city?.let { " $it" }.orEmpty()
            out += "$withoutPrefix$suffix".replace(Regex("\\s+"), " ").trim()
        }
    }

    private fun extractDestination(normalized: String): String {
        var value = " ${canonicalize(normalized)} "
        REMOVABLE_PHRASES.map(::normalize).map(::canonicalize).distinct().sortedByDescending(String::length).forEach { phrase ->
            value = value.replace(Regex("(?<!\\S)${Regex.escape(phrase)}(?!\\S)"), " ")
        }
        return value
            .replace(Regex("[؟?!،,;؛:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("^(به|تا|سمت|طرف)\\s+"), "")
            .trim()
    }

    private fun canonicalize(value: String): String = value
        .replace("خیابانی", "خیابان")
        .replace("ميرداماد", "میرداماد")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalize(value: String): String = value
        .trim().lowercase()
        .replace('ي', 'ی').replace('ك', 'ک').replace('ۀ', 'ه').replace('ة', 'ه')
        .replace('\u200c', ' ')
        .replace(Regex("\\s+"), " ")

    private fun containsAny(text: String, words: List<String>): Boolean = words.any { text.contains(normalize(it)) }

    private val SEQUENCE_SEPARATOR = Regex("\\s+(?:بعدش|بعد|سپس|بعد از آن|بعد از اون)\\s+")
    private val ROAD_PREFIXES = listOf("خیابان", "بلوار", "میدان", "بزرگراه", "اتوبان", "کوچه", "چهارراه")
    private val CITY_HINTS = listOf("تهران", "کرج", "مشهد", "اصفهان", "شیراز", "تبریز", "قم", "اهواز", "رشت", "ارومیه", "کرمان", "یزد", "قزوین", "همدان", "ساری", "گرگان", "بندرعباس")
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
        "حمل و نقل عمومی", "حمل ونقل عمومی", "پیاده روی", "پیاده", "راه رفتن", "walking",
        "با دوچرخه", "دوچرخه", "با موتور", "موتورسیکلت", "موتور", "با ماشین", "ماشین", "با خودرو", "خودرو", "رانندگی",
        "سریع ترین مسیر", "سریعترین مسیر", "سریع ترین", "سریعترین", "کمترین زمان", "زودترین",
        "ارزان ترین مسیر", "ارزانترین مسیر", "ارزان ترین", "ارزانترین", "کمترین هزینه", "کم هزینه", "اقتصادی",
        "کمترین پیاده روی", "پیاده روی کم", "کم پیاده", "عجله دارم", "عجله", "زود برسم", "دیرم شده", "فوری", "وقت ندارم",
        "ترکیبی", "چند حالته", "چندحالته", "بهترین مسیر", "بهترین راه",
        "برم", "بروم", "بریم", "برو", "برویم", "ببر", "حرکت کنم", "حرکت کنیم", "مسیر"
    )
}

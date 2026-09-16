from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


# 1) Restore the professional Smart Mobility Hub as the primary sparkle action.
v17_path = "app/src/main/java/ir/nv/navigation/ui/NvReferenceV17.kt"
v17 = read(v17_path)
v17 = v17.replace(
    "Smart navigation opens a direct,\n * functional destination assistant rather than the disconnected feature showcase.",
    "Smart navigation opens the functional Smart Mobility Hub; each card either uses\n * a real app capability or clearly reports when an external live provider is unavailable."
)
v17 = replace_once(v17, 'contentDescription = "دستیار هوشمند مسیر"', 'contentDescription = "مرکز هوشمند راهنما"', "smart button description")
v17 = replace_once(v17, "RahnamaSmartRouteAssistant(\n            state = state,\n            viewModel = viewModel,\n            onDismiss = { smartOpen = false }\n        )", "RahnamaSmartMobilityHub(\n            state = state,\n            viewModel = viewModel,\n            onDismiss = { smartOpen = false }\n        )", "open smart hub")
write(v17_path, v17)


# 2) Make the hub menu complete and make the chat input high contrast.
hub_path = "app/src/main/java/ir/nv/navigation/ui/RahnamaSmartMobilityHub.kt"
hub = read(hub_path)
if "import androidx.compose.material3.OutlinedTextFieldDefaults" not in hub:
    hub = replace_once(hub, "import androidx.compose.material3.OutlinedTextField\n", "import androidx.compose.material3.OutlinedTextField\nimport androidx.compose.material3.OutlinedTextFieldDefaults\n", "hub field colors import")
old_filter = '''    val operationalFeatures = SmartFeatureScreen.entries.filter { feature ->
        when (feature) {
            SmartFeatureScreen.STATION_TRANSFER, SmartFeatureScreen.LIVE_METRO -> transitAvailable
            SmartFeatureScreen.TAXI -> taxiAvailable
            else -> true
        }
    }
    operationalFeatures.chunked(2).forEach { rowItems ->'''
new_filter = '''    // Keep the complete professional menu visible. Cards backed by an external
    // provider remain accessible and their detail screen states availability honestly.
    // This avoids silently hiding Metro/Taxi capabilities from the product shell.
    SmartFeatureScreen.entries.chunked(2).forEach { rowItems ->'''
hub = replace_once(hub, old_filter, new_filter, "show complete smart menu")
chat_old = '''        placeholder = { Text("مثلاً: عجله دارم، از اینجا سریع‌ترین راه تا میدان آزادی را پیدا کن") },
        minLines = 2
    )'''
chat_new = '''        placeholder = { Text("مثلاً: عجله دارم، از اینجا سریع‌ترین راه تا میدان آزادی را پیدا کن") },
        minLines = 2,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = NvColors.TextPrimaryDark,
            unfocusedTextColor = NvColors.TextPrimaryDark,
            cursorColor = NvColors.RouteBlue,
            focusedBorderColor = NvColors.RouteBlue,
            unfocusedBorderColor = NvColors.DividerDark,
            focusedLabelColor = NvColors.RouteBlue,
            unfocusedLabelColor = NvColors.TextSecondaryDark,
            focusedPlaceholderColor = NvColors.TextSecondaryDark,
            unfocusedPlaceholderColor = NvColors.TextSecondaryDark
        )
    )'''
hub = replace_once(hub, chat_old, chat_new, "smart chat contrast")
write(hub_path, hub)


# 3) Also fix contrast in the legacy direct assistant so no route-entry surface is unreadable.
assistant_path = "app/src/main/java/ir/nv/navigation/ui/RahnamaSmartRouteAssistant.kt"
assistant = read(assistant_path)
if "import androidx.compose.material3.OutlinedTextFieldDefaults" not in assistant:
    assistant = replace_once(assistant, "import androidx.compose.material3.OutlinedTextField\n", "import androidx.compose.material3.OutlinedTextField\nimport androidx.compose.material3.OutlinedTextFieldDefaults\n", "assistant colors import")
assistant = replace_once(
    assistant,
    '''                minLines = 2,
                maxLines = 4
            )''',
    '''                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NvColors.TextPrimaryDark,
                    unfocusedTextColor = NvColors.TextPrimaryDark,
                    cursorColor = NvColors.RouteBlue,
                    focusedBorderColor = NvColors.RouteBlue,
                    unfocusedBorderColor = NvColors.DividerDark,
                    focusedLabelColor = NvColors.RouteBlue,
                    unfocusedLabelColor = NvColors.TextSecondaryDark,
                    focusedPlaceholderColor = NvColors.TextSecondaryDark,
                    unfocusedPlaceholderColor = NvColors.TextSecondaryDark
                )
            )''',
    "legacy assistant contrast"
)
write(assistant_path, assistant)


# 4) Persian natural-language destination parsing with resilient geocoding variants.
parser_path = "app/src/main/java/ir/nv/navigation/smart/SmartJourneyIntentParser.kt"
parser = r'''package ir.nv.navigation.smart

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
'''
write(parser_path, parser)

parser_test_path = "app/src/test/java/ir/nv/navigation/smart/SmartJourneyIntentParserTest.kt"
parser_test = r'''package ir.nv.navigation.smart

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
'''
write(parser_test_path, parser_test)


# 5) Smart chat now tries ordered geocoding fallbacks instead of one brittle sentence.
vm_path = "app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt"
vm = read(vm_path)
old_search = '''            val intent = SmartJourneyIntentParser.parse(query)
            val destinationText = intent.destinationQuery
            val snapshot = mutableState.value
            val candidates = withContext(Dispatchers.IO) {
                hybridSearchEngine.search(
                    query = destinationText,
                    onlineAvailable = snapshot.onlineAvailable,
                    preferOffline = snapshot.preferOffline,
                    limit = 8
                )
            }
            val destination = candidates.firstOrNull()'''
new_search = '''            val intent = SmartJourneyIntentParser.parse(query)
            val destinationQueries = SmartJourneyIntentParser.destinationQueries(query)
            val destinationText = destinationQueries.firstOrNull() ?: intent.destinationQuery
            val snapshot = mutableState.value
            val candidates = withContext(Dispatchers.IO) {
                val collected = mutableListOf<Place>()
                for (candidateQuery in destinationQueries.ifEmpty { listOf(destinationText) }) {
                    val matches = hybridSearchEngine.search(
                        query = candidateQuery,
                        onlineAvailable = snapshot.onlineAvailable,
                        preferOffline = snapshot.preferOffline,
                        limit = 8
                    )
                    collected += matches
                    if (matches.isNotEmpty()) break
                }
                combineSearchResults(collected)
            }
            val destination = candidates.firstOrNull()'''
vm = replace_once(vm, old_search, new_search, "smart chat geocoding fallbacks")
# Never fall back to a weak >10 m idle fix when a precise current fix timed out.
vm = vm.replace(
    "?: mutableState.value.currentLocation",
    "?: mutableState.value.takeIf { (it.locationAccuracyMeters ?: Float.POSITIVE_INFINITY) <= 10f }?.currentLocation"
)
write(vm_path, vm)


# 6) Do not let 11-20 m navigation fixes overwrite a better home marker.
location_path = "core/location/src/main/java/ir/nv/navigation/location/DeviceLocationProvider.kt"
location = read(location_path)
location = replace_once(location, "const val MAX_NAVIGATION_ACCURACY_METERS = 20f", "const val MAX_NAVIGATION_ACCURACY_METERS = 10f", "navigation GPS accuracy gate")
write(location_path, location)


# 7) Bump product version and synchronize CI metadata.
build_path = "app/build.gradle.kts"
build = read(build_path)
build = replace_once(build, "versionCode = 22", "versionCode = 23", "version code")
build = replace_once(build, 'versionName = "0.18.5"', 'versionName = "0.18.6"', "version name")
write(build_path, build)

for workflow_path in [
    ".github/workflows/android.yml",
    ".github/workflows/android16-final-gate.yml",
    ".github/workflows/release.yml",
]:
    text = read(workflow_path)
    text = text.replace("0.18.5", "0.18.6")
    text = text.replace("versionCode='22'", "versionCode='23'")
    text = text.replace("version_code=22", "version_code=23")
    text = text.replace('"version_code": 22', '"version_code": 23')
    text = text.replace('m["version_code"] == 22', 'm["version_code"] == 23')
    write(workflow_path, text)

completion_path = "tools/test_android16_completion.py"
completion = read(completion_path)
completion = completion.replace(r"versionCode\s*=\s*22", r"versionCode\s*=\s*23")
completion = completion.replace('versionName = "0.18.5"', 'versionName = "0.18.6"')
completion = completion.replace("versionCode='22'", "versionCode='23'")
completion = completion.replace("versionName='0.18.5'", "versionName='0.18.6'")
completion = completion.replace("MAX_NAVIGATION_ACCURACY_METERS = 20f", "MAX_NAVIGATION_ACCURACY_METERS = 10f")
write(completion_path, completion)

print("v0.18.6 acceptance fixes applied")

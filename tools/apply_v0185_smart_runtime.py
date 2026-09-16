from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# 1) Smart journey intent parser: Persian natural-language input -> actionable
#    destination/mode/priority. This is deterministic and testable; it does not
#    pretend to be a cloud LLM when no AI backend is configured.
# -----------------------------------------------------------------------------
parser_path = "app/src/main/java/ir/nv/navigation/smart/SmartJourneyIntentParser.kt"
parser_source = r'''package ir.nv.navigation.smart

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
'''
write(parser_path, parser_source)

parser_test_path = "app/src/test/java/ir/nv/navigation/smart/SmartJourneyIntentParserTest.kt"
parser_test_source = r'''package ir.nv.navigation.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartJourneyIntentParserTest {
    @Test
    fun parsesFastPersianDestination() {
        val intent = SmartJourneyIntentParser.parse("میخوام برم میدان آزادی سریع‌ترین مسیر")
        assertEquals("میدان آزادی", intent.destinationQuery)
        assertEquals(SmartJourneyPriority.FASTEST, intent.priority)
    }

    @Test
    fun parsesTaxiRequestWithoutPollutingDestination() {
        val intent = SmartJourneyIntentParser.parse("منو با اسنپ ببر فرودگاه مهرآباد")
        assertEquals("فرودگاه مهرآباد", intent.destinationQuery)
        assertEquals(SmartJourneyMode.TAXI, intent.mode)
    }

    @Test
    fun parsesTransitFromCurrentLocation() {
        val intent = SmartJourneyIntentParser.parse("از موقعیت من با مترو برو تجریش")
        assertEquals("تجریش", intent.destinationQuery)
        assertEquals(SmartJourneyMode.TRANSIT, intent.mode)
    }

    @Test
    fun parsesWalkingRequest() {
        val intent = SmartJourneyIntentParser.parse("پیاده منو ببر پارک ملت")
        assertEquals("پارک ملت", intent.destinationQuery)
        assertEquals(SmartJourneyMode.WALKING, intent.mode)
    }

    @Test
    fun detectsHurryAsFastest() {
        val intent = SmartJourneyIntentParser.parse("عجله دارم ببر منو ترمینال جنوب")
        assertEquals("ترمینال جنوب", intent.destinationQuery)
        assertTrue(intent.hurry)
        assertEquals(SmartJourneyPriority.FASTEST, intent.priority)
    }
}
'''
write(parser_test_path, parser_test_source)


# -----------------------------------------------------------------------------
# 2) Smart hub: no developer jargon in user UI and no dead live-service cards.
# -----------------------------------------------------------------------------
hub_path = "app/src/main/java/ir/nv/navigation/ui/RahnamaSmartMobilityHub.kt"
hub = read(hub_path)
hub = replace_once(
    hub,
    'if (screen == null) "ابزارهای هوشمند سفر، با fallback صریح برای سرویس‌های متصل‌نشده" else "داده زنده فقط وقتی منبع واقعی در دسترس باشد نمایش داده می‌شود"',
    'if (screen == null) "مقصد را طبیعی بنویسید؛ راهنما موقعیت، مسیرها، زمان و گزینه‌های واقعی سفر را بررسی می‌کند" else "فقط داده واقعی نمایش داده می‌شود؛ داده ساختگی یا سرویس نمایشی نشان داده نمی‌شود"',
    "hub subtitle"
)
hub = replace_once(
    hub,
    'null -> SmartHubMenu(onOpen = { screen = it })',
    'null -> SmartHubMenu(\n                    transitAvailable = engine.transitAvailability().available,\n                    taxiAvailable = engine.taxiAvailability().available,\n                    onOpen = { screen = it }\n                )',
    "hub menu invocation"
)
hub = replace_once(
    hub,
    '''private fun SmartHubMenu(onOpen: (SmartFeatureScreen) -> Unit) {
    SmartFeatureScreen.entries.chunked(2).forEach { rowItems ->''',
    '''private fun SmartHubMenu(
    transitAvailable: Boolean,
    taxiAvailable: Boolean,
    onOpen: (SmartFeatureScreen) -> Unit
) {
    val operationalFeatures = SmartFeatureScreen.entries.filter { feature ->
        when (feature) {
            SmartFeatureScreen.STATION_TRANSFER, SmartFeatureScreen.LIVE_METRO -> transitAvailable
            SmartFeatureScreen.TAXI -> taxiAvailable
            else -> true
        }
    }
    operationalFeatures.chunked(2).forEach { rowItems ->''',
    "hub operational filter"
)
hub = replace_once(
    hub,
    'placeholder = { Text("مثلاً سریع‌ترین مسیر چیست؟") }',
    'placeholder = { Text("مثلاً: عجله دارم، از اینجا سریع‌ترین راه تا میدان آزادی را پیدا کن") }',
    "chat placeholder"
)
hub = replace_once(
    hub,
    'Text(if (state.smartJourneyPlanning) "در حال ساخت مسیر…" else "تشخیص مقصد و ساخت مسیر از موقعیت من")',
    'Text(if (state.smartJourneyPlanning) "در حال تحلیل درخواست و ساخت مسیر…" else "تحلیل درخواست و ساخت بهترین مسیر")',
    "chat action label"
)
old_subtitles = '''private fun featureSubtitle(feature: SmartFeatureScreen): String = when (feature) {
    SmartFeatureScreen.CHAT -> "درک محلی درخواست و هدایت به ابزار مناسب"
    SmartFeatureScreen.RUSH -> "انتخاب سریع‌ترین alternative واقعی"
    SmartFeatureScreen.MULTIMODAL -> "پیاده، حمل‌ونقل عمومی و تاکسی با fallback"
    SmartFeatureScreen.STATION_TRANSFER -> "آماده اتصال GTFS/GTFS-RT"
    SmartFeatureScreen.LIVE_METRO -> "نمایش live فقط از feed واقعی"
    SmartFeatureScreen.TAXI -> "Adapter قیمت/رزرو با حالت unavailable"
    SmartFeatureScreen.ETA_CONFIDENCE -> "ETA به‌همراه confidence و بازه خطا"
    SmartFeatureScreen.TIME_COST -> "زمان، فاصله، مصرف و هزینه تنظیم‌پذیر"
    SmartFeatureScreen.WALKING -> "برآورد و مسیر واقعی با پروفایل Walking"
    SmartFeatureScreen.PARKING -> "پارک نزدیک مقصد و ادامه مسیر پیاده"
    SmartFeatureScreen.PREFERENCES -> "وسیله، پروفایل مسیر، آفلاین و حریم خصوصی"
}'''
new_subtitles = '''private fun featureSubtitle(feature: SmartFeatureScreen): String = when (feature) {
    SmartFeatureScreen.CHAT -> "مقصد و نوع سفر را از متن فارسی تشخیص می‌دهد"
    SmartFeatureScreen.RUSH -> "سریع‌ترین مسیر محاسبه‌شده را واقعاً انتخاب می‌کند"
    SmartFeatureScreen.MULTIMODAL -> "فقط گزینه‌های واقعاً قابل استفاده را مقایسه می‌کند"
    SmartFeatureScreen.STATION_TRANSFER -> "تعویض ایستگاه با داده زنده متصل"
    SmartFeatureScreen.LIVE_METRO -> "زمان حرکت و وضعیت ایستگاه از منبع زنده"
    SmartFeatureScreen.TAXI -> "قیمت و رزرو از ارائه‌دهنده متصل"
    SmartFeatureScreen.ETA_CONFIDENCE -> "زمان رسیدن همراه با درصد اطمینان و بازه خطا"
    SmartFeatureScreen.TIME_COST -> "زمان، فاصله، مصرف سوخت و هزینه سفر"
    SmartFeatureScreen.WALKING -> "مسیر پیاده با پروفایل واقعی مسیریابی"
    SmartFeatureScreen.PARKING -> "پارک نزدیک مقصد و ادامه مسیر تا مقصد"
    SmartFeatureScreen.PREFERENCES -> "وسیله، نوع مسیر، آفلاین و حریم خصوصی"
}'''
hub = replace_once(hub, old_subtitles, new_subtitles, "feature subtitles")
write(hub_path, hub)


# -----------------------------------------------------------------------------
# 3) Smart mobility assistant: never route the user into an unavailable live
#    provider. It still assists with the destination and real route.
# -----------------------------------------------------------------------------
smart_path = "app/src/main/java/ir/nv/navigation/smart/SmartMobility.kt"
smart = read(smart_path)
old_transit = '''            listOf("مترو", "اتوبوس", "brt", "حمل", "چندحالته").any(clean::contains) -> SmartAssistantReply(
                "سفر چندحالته",
                transitProvider.availability.messageFa,
                SmartFeatureScreen.MULTIMODAL
            )'''
new_transit = '''            listOf("مترو", "اتوبوس", "brt", "حمل", "چندحالته").any(clean::contains) ->
                if (transitProvider.availability.available) {
                    SmartAssistantReply(
                        "سفر چندحالته",
                        "داده حمل‌ونقل عمومی متصل است؛ گزینه‌های واقعی سفر را مقایسه می‌کنم",
                        SmartFeatureScreen.MULTIMODAL
                    )
                } else {
                    SmartAssistantReply(
                        "حمل‌ونقل عمومی",
                        "مقصد را از متن تشخیص می‌دهم و مسیرهای قابل اتکا را می‌سازم؛ زمان زنده مترو فقط پس از اتصال منبع رسمی نمایش داده می‌شود"
                    )
                }'''
smart = replace_once(smart, old_transit, new_transit, "transit assistant")
old_taxi = '''            listOf("تاکسی", "اسنپ", "تپسی").any(clean::contains) -> SmartAssistantReply(
                "هماهنگی تاکسی",
                taxiProvider.availability.messageFa,
                SmartFeatureScreen.TAXI
            )'''
new_taxi = '''            listOf("تاکسی", "اسنپ", "تپسی").any(clean::contains) ->
                if (taxiProvider.availability.available) {
                    SmartAssistantReply(
                        "هماهنگی تاکسی",
                        "ارائه‌دهنده تاکسی متصل است؛ برآورد و رزرو واقعی را بررسی می‌کنم",
                        SmartFeatureScreen.TAXI
                    )
                } else {
                    SmartAssistantReply(
                        "مسیر تاکسی",
                        "مسیر جاده‌ای واقعی را محاسبه می‌کنم؛ قیمت یا رزرو زنده تا اتصال ارائه‌دهنده نمایش داده نمی‌شود"
                    )
                }'''
smart = replace_once(smart, old_taxi, new_taxi, "taxi assistant")
write(smart_path, smart)


# -----------------------------------------------------------------------------
# 4) ViewModel: use the parser, honor fastest intent, and keep current location
#    as implicit origin. Faster intent selects the actual fastest candidate.
# -----------------------------------------------------------------------------
vm_path = "app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt"
vm = read(vm_path)
vm = replace_once(
    vm,
    'import ir.nv.navigation.search.PlaceSearchProvider\n',
    'import ir.nv.navigation.search.PlaceSearchProvider\nimport ir.nv.navigation.smart.SmartJourneyIntentParser\nimport ir.nv.navigation.smart.SmartJourneyMode\nimport ir.nv.navigation.smart.SmartJourneyPriority\n',
    "smart parser imports"
)

start = vm.index("    fun planJourneyFromChat(rawQuery: String)")
end = vm.index("    fun routeFromCurrentLocationTo", start)
chat = vm[start:end]
chat = replace_once(
    chat,
    "            val destinationText = extractChatDestination(query)",
    "            val intent = SmartJourneyIntentParser.parse(query)\n            val destinationText = intent.destinationQuery",
    "chat destination parser"
)
old_vehicle = '''            val requestedVehicle = when {
                query.contains("پیاده") -> VehicleProfile.WALKING
                query.contains("دوچرخه") -> VehicleProfile.BICYCLE
                query.contains("موتور") -> VehicleProfile.MOTORCYCLE
                else -> VehicleProfile.CAR
            }'''
new_vehicle = '''            val requestedVehicle = when (intent.mode) {
                SmartJourneyMode.WALKING -> VehicleProfile.WALKING
                SmartJourneyMode.BICYCLE -> VehicleProfile.BICYCLE
                SmartJourneyMode.MOTORCYCLE -> VehicleProfile.MOTORCYCLE
                SmartJourneyMode.DRIVING,
                SmartJourneyMode.TAXI,
                SmartJourneyMode.TRANSIT,
                SmartJourneyMode.MIXED,
                SmartJourneyMode.AUTO -> VehicleProfile.CAR
            }
            val fastestRequested = intent.hurry || intent.priority == SmartJourneyPriority.FASTEST
            val modeNotice = when (intent.mode) {
                SmartJourneyMode.TAXI -> "درخواست تاکسی تشخیص داده شد؛ مسیر جاده‌ای واقعی محاسبه می‌شود و قیمت/رزرو زنده فقط با ارائه‌دهنده متصل نمایش داده خواهد شد"
                SmartJourneyMode.TRANSIT, SmartJourneyMode.MIXED -> "درخواست حمل‌ونقل عمومی تشخیص داده شد؛ داده زمان‌بندی زنده فقط در صورت اتصال منبع واقعی نمایش داده می‌شود"
                else -> null
            }'''
chat = replace_once(chat, old_vehicle, new_vehicle, "chat vehicle intent")
chat = replace_once(
    chat,
    'smartJourneyStatus = "مقصد ${destination.name} تشخیص داده شد؛ برنامه سفر از موقعیت فعلی در حال ساخته‌شدن است",',
    'smartJourneyStatus = buildString {\n                        append("مقصد ${destination.name} تشخیص داده شد؛ ")\n                        append(if (fastestRequested) "سریع‌ترین مسیر واقعی در حال محاسبه است" else "مسیر از موقعیت فعلی در حال محاسبه است")\n                        modeNotice?.let { append(" • "); append(it) }\n                    },',
    "chat status"
)
chat = replace_once(
    chat,
    "            calculateRoute()\n        }\n    }\n",
    "            calculateRouteInternal(autoSelectFastest = fastestRequested)\n        }\n    }\n",
    "chat fastest route call"
)
vm = vm[:start] + chat + vm[end:]

vm = replace_once(
    vm,
    "    fun calculateRoute() {\n",
    "    fun calculateRoute() = calculateRouteInternal(autoSelectFastest = false)\n\n    private fun calculateRouteInternal(autoSelectFastest: Boolean) {\n",
    "route wrapper"
)
vm = replace_once(
    vm,
    '''            val candidates = plan.candidates
            val results = candidates.map { RouteOriginConnector.attach(origin.coordinate, it.route) }
            val result = results.firstOrNull()
            val source = candidates.firstOrNull()?.source ?: RouteSource.NONE''',
    '''            val candidates = plan.candidates
            val results = candidates.map { RouteOriginConnector.attach(origin.coordinate, it.route) }
            val selectedIndex = if (autoSelectFastest && results.isNotEmpty()) {
                results.indices.minByOrNull { index -> results[index].travelSeconds } ?: 0
            } else {
                0
            }
            val result = results.getOrNull(selectedIndex)
            val source = candidates.getOrNull(selectedIndex)?.source ?: RouteSource.NONE''',
    "route fastest candidate"
)
vm = replace_once(vm, "                    selectedRouteIndex = 0,", "                    selectedRouteIndex = selectedIndex,", "selected route index")
vm = replace_once(vm, "                    traffic = candidates.firstOrNull()?.traffic,", "                    traffic = candidates.getOrNull(selectedIndex)?.traffic,", "selected traffic")
write(vm_path, vm)


# -----------------------------------------------------------------------------
# 5) Precision-first location gates. We never display fake 0 m accuracy; instead
#    weak fixes are rejected until Android provides a materially better fix.
# -----------------------------------------------------------------------------
location_path = "core/location/src/main/java/ir/nv/navigation/location/DeviceLocationProvider.kt"
location = read(location_path)
replacements = {
    "const val LOCATION_COLLECTION_WINDOW_MS = 12_000L": "const val LOCATION_COLLECTION_WINDOW_MS = 15_000L",
    "const val TARGET_ACCURACY_METERS = 8f": "const val TARGET_ACCURACY_METERS = 5f",
    "const val EXCELLENT_ACCURACY_METERS = 5f": "const val EXCELLENT_ACCURACY_METERS = 3f",
    "const val ACCEPTABLE_LAST_KNOWN_ACCURACY_METERS = 12f": "const val ACCEPTABLE_LAST_KNOWN_ACCURACY_METERS = 8f",
    "const val MAX_CURRENT_LOCATION_ACCURACY_METERS = 18f": "const val MAX_CURRENT_LOCATION_ACCURACY_METERS = 10f",
    "const val GOOD_NAVIGATION_ACCURACY_METERS = 15f": "const val GOOD_NAVIGATION_ACCURACY_METERS = 8f",
    "const val MAX_NAVIGATION_ACCURACY_METERS = 35f": "const val MAX_NAVIGATION_ACCURACY_METERS = 20f",
    "const val ABSOLUTE_MAX_ACCURACY_METERS = 60f": "const val ABSOLUTE_MAX_ACCURACY_METERS = 35f",
}
for old, new in replacements.items():
    location = replace_once(location, old, new, f"location constant {old}")
write(location_path, location)


# -----------------------------------------------------------------------------
# 6) Version bump for a clearly identifiable APK.
# -----------------------------------------------------------------------------
gradle_path = "app/build.gradle.kts"
gradle = read(gradle_path)
gradle = replace_once(gradle, "versionCode = 21", "versionCode = 22", "versionCode")
gradle = replace_once(gradle, 'versionName = "0.18.2"', 'versionName = "0.18.5"', "versionName")
write(gradle_path, gradle)

print("Applied v0.18.5 smart-runtime and precision-location changes")

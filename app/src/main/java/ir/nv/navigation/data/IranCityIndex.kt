package ir.nv.navigation.data

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place

/** A bootstrap index that works before the optional Iran data pack is installed. */
object IranCityIndex {
    private data class City(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val aliases: List<String> = emptyList()
    )

    private val cities = listOf(
        City("تهران", 35.6892, 51.3890, listOf("طهران", "tehran")),
        City("کرج", 35.8400, 50.9391, listOf("karaj")),
        City("مشهد", 36.2605, 59.6168, listOf("mashhad")),
        City("اصفهان", 32.6546, 51.6680, listOf("اصفان", "isfahan", "esfahan")),
        City("شیراز", 29.5918, 52.5837, listOf("shiraz")),
        City("تبریز", 38.0800, 46.2919, listOf("tabriz")),
        City("قم", 34.6416, 50.8746, listOf("qom")),
        City("اهواز", 31.3183, 48.6706, listOf("ahvaz")),
        City("کرمانشاه", 34.3142, 47.0650, listOf("kermanshah")),
        City("ارومیه", 37.5527, 45.0761, listOf("اورمیه", "urmia", "orumiyeh")),
        City("رشت", 37.2808, 49.5832, listOf("rasht")),
        City("زاهدان", 29.4963, 60.8629, listOf("zahedan")),
        City("همدان", 34.7989, 48.5150, listOf("hamedan", "hamadan")),
        City("کرمان", 30.2839, 57.0834, listOf("kerman")),
        City("یزد", 31.8974, 54.3569, listOf("yazd")),
        City("اردبیل", 38.2498, 48.2933, listOf("ardabil")),
        City("بندرعباس", 27.1832, 56.2666, listOf("بندر عباس", "bandar abbas")),
        City("اراک", 34.0954, 49.7013, listOf("arak")),
        City("زنجان", 36.6736, 48.4787, listOf("zanjan")),
        City("سنندج", 35.3219, 46.9862, listOf("sanandaj")),
        City("قزوین", 36.2688, 50.0041, listOf("qazvin")),
        City("خرم‌آباد", 33.4878, 48.3558, listOf("خرم آباد", "khorramabad")),
        City("گرگان", 36.8456, 54.4393, listOf("gorgan")),
        City("ساری", 36.5659, 53.0586, listOf("sari")),
        City("بجنورد", 37.4747, 57.3290, listOf("bojnord")),
        City("بیرجند", 32.8649, 59.2262, listOf("birjand")),
        City("ایلام", 33.6374, 46.4227, listOf("ilam")),
        City("شهرکرد", 32.3256, 50.8644, listOf("شهر کرد", "shahrekord")),
        City("یاسوج", 30.6682, 51.5880, listOf("yasuj")),
        City("بوشهر", 28.9234, 50.8203, listOf("bushehr")),
        City("سمنان", 35.5769, 53.3921, listOf("semnan")),
        City("کیش", 26.5325, 53.9821, listOf("kish")),
        City("قشم", 26.9581, 56.2719, listOf("qeshm")),
        City("آبادان", 30.3473, 48.2934, listOf("abadan")),
        City("خرمشهر", 30.4391, 48.1664, listOf("khorramshahr")),
        City("دزفول", 32.3831, 48.4236, listOf("dezful")),
        City("کاشان", 33.9850, 51.4100, listOf("kashan")),
        City("نیشابور", 36.2133, 58.7958, listOf("neyshabur")),
        City("سبزوار", 36.2152, 57.6678, listOf("sabzevar")),
        City("مراغه", 37.3892, 46.2371, listOf("maragheh")),
        City("بابل", 36.5513, 52.6789, listOf("babol")),
        City("آمل", 36.4696, 52.3507, listOf("amol")),
        City("چالوس", 36.6540, 51.4204, listOf("chalus")),
        City("انزلی", 37.4727, 49.4622, listOf("بندر انزلی", "anzali")),
        City("نجف‌آباد", 32.6344, 51.3668, listOf("نجف آباد", "najafabad")),
        City("خمینی‌شهر", 32.7004, 51.5211, listOf("خمینی شهر", "khomeinishahr")),
        City("پاکدشت", 35.4669, 51.6861, listOf("pakdasht")),
        City("شهریار", 35.6596, 51.0597, listOf("shahriar")),
        City("ورامین", 35.3242, 51.6457, listOf("varamin")),
        City("پردیس", 35.7423, 52.0645, listOf("pardis"))
    )

    fun search(rawQuery: String, limit: Int = 8): List<Place> {
        val query = PersianText.normalize(PlaceCodes.normalizeDigits(rawQuery))
        if (query.isBlank()) return emptyList()
        return cities.mapIndexedNotNull { index, city ->
            val score = (listOf(city.name) + city.aliases).minOfOrNull { candidate ->
                val normalized = PersianText.normalize(candidate)
                when {
                    normalized == query -> 0
                    normalized.startsWith(query) -> 1
                    normalized.contains(query) -> 2
                    else -> 99
                }
            } ?: 99
            if (score == 99) null else Triple(score, index, city)
        }.sortedWith(compareBy<Triple<Int, Int, City>> { it.first }.thenBy { it.second })
            .take(limit)
            .map { (_, index, city) ->
                Place(
                    code = BUILTIN_CODE_BASE - index,
                    name = city.name,
                    coordinate = Coordinate(city.latitude, city.longitude),
                    category = "builtin:city"
                )
            }
    }

    private const val BUILTIN_CODE_BASE = -2_100_000_000L
}

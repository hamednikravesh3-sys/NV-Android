package ir.nv.navigation.offline

data class OfflineRegionPack(
    val id: String,
    val title: String,
    val estimatedSizeMb: Int,
    val mapAsset: String,
    val routingAsset: String,
    val searchAsset: String,
    val elevationAsset: String? = null,
    val voiceAsset: String? = "fa-IR"
)

object OfflinePackCatalog {
    val iran = OfflineRegionPack(
        id = "iran",
        title = "ایران",
        estimatedSizeMb = 8_200,
        mapAsset = "maps/iran.pmtiles",
        routingAsset = "routing/iran-routing",
        searchAsset = "search/iran-poi.db",
        elevationAsset = "elevation/iran-dem"
    )

    /** All 31 provinces. Package ids are stable server/file identifiers. */
    val provinces: List<OfflineRegionPack> = listOf(
        province("alborz", "البرز", 310),
        province("ardabil", "اردبیل", 360),
        province("bushehr", "بوشهر", 410),
        province("chaharmahal-bakhtiari", "چهارمحال و بختیاری", 320),
        province("east-azerbaijan", "آذربایجان شرقی", 520),
        province("fars", "فارس", 710),
        province("gilan", "گیلان", 450),
        province("golestan", "گلستان", 390),
        province("hamadan", "همدان", 380),
        province("hormozgan", "هرمزگان", 560),
        province("ilam", "ایلام", 300),
        province("isfahan", "اصفهان", 640),
        province("kerman", "کرمان", 740),
        province("kermanshah", "کرمانشاه", 410),
        province("khuzestan", "خوزستان", 690),
        province("kohgiluyeh-boyer-ahmad", "کهگیلویه و بویراحمد", 310),
        province("kordestan", "کردستان", 420),
        province("lorestan", "لرستان", 390),
        province("markazi", "مرکزی", 380),
        province("mazandaran", "مازندران", 480),
        province("north-khorasan", "خراسان شمالی", 380),
        province("qazvin", "قزوین", 340),
        province("qom", "قم", 250),
        province("razavi-khorasan", "خراسان رضوی", 720),
        province("semnan", "سمنان", 510),
        province("sistan-baluchestan", "سیستان و بلوچستان", 780),
        province("south-khorasan", "خراسان جنوبی", 520),
        province("tehran", "تهران", 620),
        province("west-azerbaijan", "آذربایجان غربی", 510),
        province("yazd", "یزد", 490),
        province("zanjan", "زنجان", 360)
    )

    fun all(): List<OfflineRegionPack> = listOf(iran) + provinces

    fun provinceById(id: String): OfflineRegionPack? = provinces.firstOrNull { it.id == id }

    private fun province(id: String, title: String, sizeMb: Int) = OfflineRegionPack(
        id = id,
        title = title,
        estimatedSizeMb = sizeMb,
        mapAsset = "maps/$id.pmtiles",
        routingAsset = "routing/$id-routing",
        searchAsset = "search/$id-poi.db",
        elevationAsset = "elevation/$id-dem"
    )
}

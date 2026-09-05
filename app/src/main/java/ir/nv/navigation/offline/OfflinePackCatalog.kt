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
        title = "Iran",
        estimatedSizeMb = 8_200,
        mapAsset = "maps/iran.pmtiles",
        routingAsset = "routing/iran-routing",
        searchAsset = "search/iran-poi.db",
        elevationAsset = "elevation/iran-dem"
    )

    val provinces: List<OfflineRegionPack> = listOf(
        province("tehran", "Tehran", 620),
        province("fars", "Fars", 710),
        province("khuzestan", "Khuzestan", 690),
        province("isfahan", "Isfahan", 640),
        province("khorasan-razavi", "Khorasan Razavi", 720),
        province("mazandaran", "Mazandaran", 480),
        province("gilan", "Gilan", 450),
        province("east-azerbaijan", "East Azerbaijan", 520)
    )

    fun all(): List<OfflineRegionPack> = listOf(iran) + provinces

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

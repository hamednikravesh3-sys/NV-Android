package ir.nv.navigation.map

/** Production terrain is optional only when no DEM provider is configured. */
data class TerrainSourceConfig(
    val rasterDemTiles: List<String>,
    val tileSize: Int = 256,
    val maxZoom: Int = 14
) {
    val configured: Boolean get() = rasterDemTiles.any { it.startsWith("https://") }
    init {
        require(tileSize in setOf(256, 512))
        require(maxZoom in 0..22)
    }
}

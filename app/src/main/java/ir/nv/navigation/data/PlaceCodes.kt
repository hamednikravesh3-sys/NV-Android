package ir.nv.navigation.data

object PlaceCodes {
    data class OnlineIdentity(val osmType: String, val osmId: Long)

    fun normalizeDigits(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(
                when (character) {
                    in '۰'..'۹' -> '0' + (character - '۰')
                    in '٠'..'٩' -> '0' + (character - '٠')
                    else -> character
                }
            )
        }
    }

    fun isExplicitNvCode(value: String): Boolean =
        Regex("^\\s*NV\\s*[:#-]?\\s*[۰-۹٠-٩0-9]+\\s*$", RegexOption.IGNORE_CASE)
            .matches(value)

    fun publicCode(value: String): Long? {
        val normalized = normalizeDigits(value).trim()
            .replace(Regex("^NV\\s*[:#-]?\\s*", RegexOption.IGNORE_CASE), "")
        return normalized.takeIf { it.all(Char::isDigit) }?.toLongOrNull()
    }

    fun shareCode(placeCode: Long): String? = placeCode.takeIf { it > 0 }?.let { "NV:$it" }

    fun isRegistryCode(code: Long): Boolean = code in REGISTRY_BASE until REGISTRY_LIMIT

    /**
     * Online codes use reserved ranges so the exact remote object can be found again by code.
     * 5T..6T is reserved for authoritative NV registry allocations.
     * 7T..10T is reserved for exact OSM identities.
     * Sequential 1..N codes remain reserved for the downloadable Iran place database.
     */
    fun onlineCode(osmType: String, osmId: Long): Long? {
        val base = when (osmType.lowercase()) {
            "node", "n" -> ONLINE_NODE_BASE
            "way", "w" -> ONLINE_WAY_BASE
            "relation", "r" -> ONLINE_RELATION_BASE
            else -> return null
        }
        val cleanId = osmId.takeIf { it in 1 until ONLINE_RANGE_SIZE } ?: return null
        return base + cleanId
    }

    fun onlineIdentity(code: Long): OnlineIdentity? = when (code) {
        in REGISTRY_BASE until REGISTRY_LIMIT -> OnlineIdentity("nv", code)
        in ONLINE_NODE_BASE until ONLINE_WAY_BASE -> OnlineIdentity("node", code - ONLINE_NODE_BASE)
        in ONLINE_WAY_BASE until ONLINE_RELATION_BASE -> OnlineIdentity("way", code - ONLINE_WAY_BASE)
        in ONLINE_RELATION_BASE until ONLINE_LIMIT -> OnlineIdentity("relation", code - ONLINE_RELATION_BASE)
        else -> null
    }

    private const val REGISTRY_BASE = 5_000_000_000_000L
    private const val REGISTRY_LIMIT = 6_000_000_000_000L
    private const val ONLINE_NODE_BASE = 7_000_000_000_000L
    private const val ONLINE_WAY_BASE = 8_000_000_000_000L
    private const val ONLINE_RELATION_BASE = 9_000_000_000_000L
    private const val ONLINE_RANGE_SIZE = 1_000_000_000_000L
    private const val ONLINE_LIMIT = ONLINE_RELATION_BASE + ONLINE_RANGE_SIZE
}

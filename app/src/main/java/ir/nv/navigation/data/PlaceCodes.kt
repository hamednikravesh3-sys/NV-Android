package ir.nv.navigation.data

object PlaceCodes {
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

    fun publicCode(value: String): Long? {
        val normalized = normalizeDigits(value).trim()
            .replace(Regex("^NV\\s*[:#-]?\\s*", RegexOption.IGNORE_CASE), "")
        return normalized.takeIf { it.all(Char::isDigit) }?.toLongOrNull()
    }

    fun shareCode(placeCode: Long): String? = placeCode.takeIf { it > 0 }?.let { "NV:$it" }
}

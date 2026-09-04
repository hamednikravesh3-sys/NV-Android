package ir.nv.navigation.data

object PersonalCodeRules {
    fun normalize(value: String): String? {
        val digits = PlaceCodes.normalizeDigits(value).trim()
        if (!digits.matches(Regex("[0-9]{1,9}"))) return null
        val number = digits.toLongOrNull() ?: return null
        if (number !in 1..999_999_999) return null
        return number.toString()
    }
}

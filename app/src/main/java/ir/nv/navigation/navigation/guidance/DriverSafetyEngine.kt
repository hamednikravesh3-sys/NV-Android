package ir.nv.navigation.navigation.guidance

/** Safety/guidance state that is independent from the rendering layer. */
data class DriverRoadContext(
    val speedLimitKmh: Int? = null,
    val exitNumber: String? = null,
    val junctionName: String? = null,
    val laneCount: Int? = null
)

data class DriverSafetySnapshot(
    val currentSpeedKmh: Int,
    val speedLimitKmh: Int?,
    val overspeed: Boolean,
    val overspeedByKmh: Int,
    val exitNumber: String?,
    val junctionName: String?,
    val warningFa: String?
)

class DriverSafetyEngine(
    private val warningToleranceKmh: Int = 5
) {
    init { require(warningToleranceKmh >= 0) }

    fun evaluate(currentSpeedKmh: Int, context: DriverRoadContext): DriverSafetySnapshot {
        val speed = currentSpeedKmh.coerceAtLeast(0)
        val limit = context.speedLimitKmh?.takeIf { it > 0 }
        val delta = if (limit == null) 0 else (speed - limit).coerceAtLeast(0)
        val overspeed = limit != null && delta > warningToleranceKmh
        val warning = when {
            overspeed -> "سرعت مجاز $limit کیلومتر بر ساعت است"
            !context.exitNumber.isNullOrBlank() -> "خروجی ${context.exitNumber} را دنبال کنید"
            else -> null
        }
        return DriverSafetySnapshot(
            currentSpeedKmh = speed,
            speedLimitKmh = limit,
            overspeed = overspeed,
            overspeedByKmh = delta,
            exitNumber = context.exitNumber?.trim()?.takeIf(String::isNotEmpty),
            junctionName = context.junctionName?.trim()?.takeIf(String::isNotEmpty),
            warningFa = warning
        )
    }
}

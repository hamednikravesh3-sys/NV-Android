package ir.nv.navigation.navigation

class ContinuousReroutePolicy(
    private val minimumTimeSavingSeconds: Double = 180.0,
    private val trafficIncreaseThresholdSeconds: Double = 120.0,
    private val cooldownMillis: Long = 90_000L,
    private val minimumTrafficSavingSeconds: Double = 60.0
) {
    data class Decision(
        val shouldReroute: Boolean,
        val reason: Reason? = null,
        val estimatedSavingSeconds: Double = 0.0
    )

    enum class Reason {
        OFF_ROUTE,
        BLOCKED,
        TRAFFIC_INCREASE,
        BETTER_ROUTE
    }

    fun evaluate(
        nowMillis: Long,
        lastRerouteMillis: Long,
        offRoute: Boolean,
        currentRouteBlocked: Boolean,
        previousTrafficDelaySeconds: Double,
        currentTrafficDelaySeconds: Double,
        currentRemainingSeconds: Double,
        bestAlternativeSeconds: Double?
    ): Decision {
        if (offRoute) return Decision(true, Reason.OFF_ROUTE)
        if (currentRouteBlocked) return Decision(true, Reason.BLOCKED)
        if (nowMillis - lastRerouteMillis < cooldownMillis) return Decision(false)

        val alternative = bestAlternativeSeconds?.takeIf { it.isFinite() && it > 0.0 }
        val saving = alternative?.let { (currentRemainingSeconds - it).coerceAtLeast(0.0) } ?: 0.0
        val trafficIncrease = (currentTrafficDelaySeconds - previousTrafficDelaySeconds).coerceAtLeast(0.0)

        if (
            trafficIncrease >= trafficIncreaseThresholdSeconds &&
            alternative != null &&
            saving >= minimumTrafficSavingSeconds
        ) {
            return Decision(true, Reason.TRAFFIC_INCREASE, saving)
        }

        if (alternative != null && saving >= minimumTimeSavingSeconds) {
            return Decision(true, Reason.BETTER_ROUTE, saving)
        }
        return Decision(false, estimatedSavingSeconds = saving)
    }

    companion object {
        fun reasonFa(reason: Reason?): String? = when (reason) {
            Reason.OFF_ROUTE -> "خروج از مسیر"
            Reason.BLOCKED -> "مسیر بسته است"
            Reason.TRAFFIC_INCREASE -> "افزایش ترافیک"
            Reason.BETTER_ROUTE -> "مسیر سریع‌تر پیدا شد"
            null -> null
        }
    }
}

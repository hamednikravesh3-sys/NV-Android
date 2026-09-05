package ir.nv.navigation.navigation

class ContinuousReroutePolicy(
    private val minimumTimeSavingSeconds: Double = 180.0,
    private val trafficIncreaseThresholdSeconds: Double = 120.0,
    private val cooldownMillis: Long = 90_000L
) {
    data class Decision(
        val shouldReroute: Boolean,
        val reason: Reason? = null
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

        val trafficIncrease = currentTrafficDelaySeconds - previousTrafficDelaySeconds
        if (trafficIncrease >= trafficIncreaseThresholdSeconds) {
            return Decision(true, Reason.TRAFFIC_INCREASE)
        }

        val alternative = bestAlternativeSeconds ?: return Decision(false)
        val saving = currentRemainingSeconds - alternative
        return if (saving >= minimumTimeSavingSeconds) {
            Decision(true, Reason.BETTER_ROUTE)
        } else Decision(false)
    }
}

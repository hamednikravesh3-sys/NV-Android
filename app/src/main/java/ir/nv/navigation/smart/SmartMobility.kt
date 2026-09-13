package ir.nv.navigation.smart

import ir.nv.navigation.core.Route
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class SmartFeatureScreen(val titleFa: String) {
    CHAT("دستیار سفر هوشمند"),
    RUSH("حالت عجله"),
    MULTIMODAL("مسیر چندحالته"),
    STATION_TRANSFER("تعویض هوشمند ایستگاه"),
    LIVE_METRO("متروی زنده"),
    TAXI("هماهنگی تاکسی"),
    ETA_CONFIDENCE("اطمینان زمان رسیدن"),
    TIME_COST("زمان و هزینه"),
    WALKING("مسیریابی پیاده"),
    PREFERENCES("ترجیحات هوشمند")
}

enum class MobilityMode(val titleFa: String) {
    WALK("پیاده"),
    METRO("مترو"),
    BUS("اتوبوس"),
    BRT("BRT"),
    TAXI("تاکسی"),
    BIKE("دوچرخه"),
    SCOOTER("اسکوتر")
}

data class ProviderAvailability(
    val available: Boolean,
    val source: String,
    val messageFa: String
)

data class StationStatus(
    val stationName: String,
    val lineName: String,
    val nextArrivalMinutes: Int?,
    val crowdingPercent: Int?,
    val live: Boolean,
    val source: String
)

data class TaxiEstimate(
    val etaMinutes: Int,
    val fareMin: Long?,
    val fareMax: Long?,
    val currency: String?,
    val source: String,
    val bookable: Boolean
)

interface TransitRealtimeProvider {
    val availability: ProviderAvailability
    fun nearbyStationStatus(): List<StationStatus>
}

interface TaxiProvider {
    val availability: ProviderAvailability
    fun estimate(distanceMeters: Double): TaxiEstimate?
}

object UnavailableTransitRealtimeProvider : TransitRealtimeProvider {
    override val availability = ProviderAvailability(
        available = false,
        source = "unconfigured",
        messageFa = "فید GTFS/GTFS-RT مترو و حمل‌ونقل عمومی هنوز متصل نشده است"
    )

    override fun nearbyStationStatus(): List<StationStatus> = emptyList()
}

object UnavailableTaxiProvider : TaxiProvider {
    override val availability = ProviderAvailability(
        available = false,
        source = "unconfigured",
        messageFa = "ارائه‌دهنده تاکسی برای رزرو و قیمت زنده پیکربندی نشده است"
    )

    override fun estimate(distanceMeters: Double): TaxiEstimate? = null
}

data class SmartAssistantReply(
    val titleFa: String,
    val messageFa: String,
    val suggestedScreen: SmartFeatureScreen? = null
)

data class RushDecision(
    val recommendedIndex: Int,
    val selectedIndex: Int,
    val savingSeconds: Double,
    val reasonFa: String
)

data class EtaConfidence(
    val etaSeconds: Double,
    val confidence: Double,
    val uncertaintySeconds: Double,
    val labelFa: String
)

data class TimeCostEstimate(
    val travelSeconds: Double,
    val distanceMeters: Double,
    val fuelLiters: Double,
    val monetaryCost: Long?,
    val currency: String?,
    val source: String,
    val warningFa: String?
)

data class WalkingEstimate(
    val distanceMeters: Double,
    val travelSeconds: Double,
    val confidence: Double,
    val exactRoute: Boolean,
    val source: String
)

data class MultimodalLeg(
    val mode: MobilityMode,
    val titleFa: String,
    val distanceMeters: Double,
    val travelSeconds: Double,
    val live: Boolean,
    val source: String
)

data class MultimodalPlan(
    val available: Boolean,
    val legs: List<MultimodalLeg>,
    val totalSeconds: Double,
    val warningFa: String?
)

class SmartMobilityEngine(
    private val transitProvider: TransitRealtimeProvider = UnavailableTransitRealtimeProvider,
    private val taxiProvider: TaxiProvider = UnavailableTaxiProvider
) {
    fun assistant(query: String, hasRoute: Boolean, hasDestination: Boolean): SmartAssistantReply {
        val clean = query.trim().lowercase()
        if (clean.isBlank()) {
            return SmartAssistantReply(
                titleFa = "چه کمکی لازم دارید؟",
                messageFa = "می‌توانید درباره سریع‌ترین مسیر، حمل‌ونقل عمومی، تاکسی، زمان رسیدن یا هزینه سفر بپرسید"
            )
        }
        return when {
            listOf("عجله", "سریع", "زود", "rush").any(clean::contains) -> SmartAssistantReply(
                "حالت عجله",
                if (hasRoute) "مسیرهای فعلی را برای کمترین زمان مقایسه می‌کنم" else "ابتدا مقصد را انتخاب کنید تا سریع‌ترین گزینه قابل محاسبه باشد",
                SmartFeatureScreen.RUSH
            )
            listOf("مترو", "اتوبوس", "brt", "حمل", "چندحالته").any(clean::contains) -> SmartAssistantReply(
                "سفر چندحالته",
                transitProvider.availability.messageFa,
                SmartFeatureScreen.MULTIMODAL
            )
            listOf("تاکسی", "اسنپ", "تپسی").any(clean::contains) -> SmartAssistantReply(
                "هماهنگی تاکسی",
                taxiProvider.availability.messageFa,
                SmartFeatureScreen.TAXI
            )
            listOf("زمان", "رسیدن", "eta", "کی می‌رسم").any(clean::contains) -> SmartAssistantReply(
                "برآورد زمان رسیدن",
                if (hasRoute) "اطمینان ETA را از طول مسیر، ترافیک و وضعیت موقعیت محاسبه می‌کنم" else "برای ETA ابتدا یک مسیر بسازید",
                SmartFeatureScreen.ETA_CONFIDENCE
            )
            listOf("هزینه", "قیمت", "مصرف", "سوخت").any(clean::contains) -> SmartAssistantReply(
                "زمان و هزینه",
                if (hasRoute) "زمان، فاصله و مصرف تقریبی قابل محاسبه است؛ قیمت پولی فقط با تعرفه پیکربندی‌شده نمایش داده می‌شود" else "برای برآورد هزینه ابتدا مسیر بسازید",
                SmartFeatureScreen.TIME_COST
            )
            listOf("پیاده", "راه رفتن", "walking").any(clean::contains) -> SmartAssistantReply(
                "مسیریابی پیاده",
                if (hasDestination) "برآورد محلی پیاده‌روی قابل نمایش است؛ مسیر دقیق عابر به provider اختصاصی نیاز دارد" else "ابتدا مقصد را مشخص کنید",
                SmartFeatureScreen.WALKING
            )
            else -> SmartAssistantReply(
                "پیشنهاد راهنما",
                if (hasRoute) "می‌توانید حالت عجله، ETA، هزینه یا گزینه‌های چندحالته را باز کنید" else "مقصد را جستجو کنید یا از اطراف من یک مکان انتخاب کنید"
            )
        }
    }

    fun rush(routes: List<Route>, selectedIndex: Int): RushDecision? {
        if (routes.isEmpty()) return null
        val safeSelected = selectedIndex.coerceIn(0, routes.lastIndex)
        val bestIndex = routes.indices.minByOrNull { routes[it].travelSeconds } ?: return null
        val selectedSeconds = routes[safeSelected].travelSeconds.coerceAtLeast(0.0)
        val bestSeconds = routes[bestIndex].travelSeconds.coerceAtLeast(0.0)
        val saving = (selectedSeconds - bestSeconds).coerceAtLeast(0.0)
        val reason = if (bestIndex == safeSelected) {
            "مسیر انتخاب‌شده در حال حاضر سریع‌ترین گزینه موجود است"
        } else {
            "این مسیر حدود ${ceil(saving / 60.0).toInt()} دقیقه سریع‌تر از مسیر انتخاب‌شده است"
        }
        return RushDecision(bestIndex, safeSelected, saving, reason)
    }

    fun etaConfidence(
        route: Route,
        liveTrafficDelaySeconds: Double?,
        locationActive: Boolean,
        onlineAvailable: Boolean
    ): EtaConfidence {
        val base = route.travelSeconds.coerceAtLeast(0.0)
        val delay = liveTrafficDelaySeconds?.coerceAtLeast(0.0) ?: 0.0
        val eta = base + delay
        var confidence = 0.52
        if (locationActive) confidence += 0.18
        if (liveTrafficDelaySeconds != null) confidence += 0.18
        if (onlineAvailable) confidence += 0.07
        if (route.points.size >= 3) confidence += 0.05
        confidence = confidence.coerceIn(0.35, 0.95)
        val uncertainty = (eta * (1.0 - confidence) * 0.65).coerceAtLeast(60.0)
        val label = when {
            confidence >= 0.82 -> "اطمینان بالا"
            confidence >= 0.65 -> "اطمینان متوسط"
            else -> "اطمینان پایین"
        }
        return EtaConfidence(eta, confidence, uncertainty, label)
    }

    fun timeAndCost(
        route: Route,
        fuelConsumptionLitersPer100Km: Double = 8.0,
        fuelPricePerLiter: Long? = null,
        currency: String? = null
    ): TimeCostEstimate {
        val distanceKm = route.distanceMeters.coerceAtLeast(0.0) / 1000.0
        val consumption = fuelConsumptionLitersPer100Km.coerceIn(0.0, 50.0)
        val liters = distanceKm * consumption / 100.0
        val cost = fuelPricePerLiter?.takeIf { it >= 0L }?.let { price -> (liters * price).roundToInt().toLong() }
        return TimeCostEstimate(
            travelSeconds = route.travelSeconds.coerceAtLeast(0.0),
            distanceMeters = route.distanceMeters.coerceAtLeast(0.0),
            fuelLiters = liters,
            monetaryCost = cost,
            currency = currency,
            source = "local-estimate",
            warningFa = if (cost == null) "قیمت سوخت در تنظیمات وارد نشده؛ هزینه پولی نمایش داده نمی‌شود" else null
        )
    }

    fun walking(distanceMeters: Double): WalkingEstimate {
        val safeDistance = distanceMeters.coerceAtLeast(0.0)
        val seconds = safeDistance / WALKING_SPEED_MPS
        return WalkingEstimate(
            distanceMeters = safeDistance,
            travelSeconds = seconds,
            confidence = 0.55,
            exactRoute = false,
            source = "local-distance-estimate"
        )
    }

    fun fallbackMultimodal(route: Route?): MultimodalPlan {
        if (route == null) {
            return MultimodalPlan(false, emptyList(), 0.0, "ابتدا مقصد و مسیر را مشخص کنید")
        }
        val walk = walking(route.distanceMeters)
        val walkingLeg = MultimodalLeg(
            mode = MobilityMode.WALK,
            titleFa = "پیاده‌روی مستقیم (برآورد محلی)",
            distanceMeters = walk.distanceMeters,
            travelSeconds = walk.travelSeconds,
            live = false,
            source = walk.source
        )
        val warning = buildString {
            append(transitProvider.availability.messageFa)
            append("؛ ")
            append(taxiProvider.availability.messageFa)
        }
        return MultimodalPlan(
            available = true,
            legs = listOf(walkingLeg),
            totalSeconds = walkingLeg.travelSeconds,
            warningFa = warning
        )
    }

    fun transitAvailability(): ProviderAvailability = transitProvider.availability

    fun metroStatus(): List<StationStatus> = if (transitProvider.availability.available) {
        transitProvider.nearbyStationStatus()
    } else {
        emptyList()
    }

    fun taxiAvailability(): ProviderAvailability = taxiProvider.availability

    fun taxiEstimate(distanceMeters: Double): TaxiEstimate? = if (taxiProvider.availability.available) {
        taxiProvider.estimate(distanceMeters.coerceAtLeast(0.0))
    } else {
        null
    }

    private companion object {
        const val WALKING_SPEED_MPS = 1.35
    }
}

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
    PARKING("پارکینگ مقصد"),
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

enum class Urgency { RELAXED, NORMAL, HURRY }

data class TravelPreferences(
    val urgency: Urgency = Urgency.NORMAL,
    val maxWalkingMeters: Int = 1_500,
    val enabledModes: Set<MobilityMode> = MobilityMode.entries.toSet(),
    val maxCost: Long? = null,
    val maxTransfers: Int = 2,
    val avoidCrowding: Boolean = false,
    val accessibilityRequired: Boolean = false,
    val weatherSensitive: Boolean = false
) {
    fun normalized(): TravelPreferences = copy(
        maxWalkingMeters = maxWalkingMeters.coerceIn(0, 20_000),
        maxCost = maxCost?.coerceAtLeast(0L),
        maxTransfers = maxTransfers.coerceIn(0, 5),
        enabledModes = enabledModes.ifEmpty { setOf(MobilityMode.WALK) }
    )
}

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

data class StationTransferOption(
    val stationName: String,
    val walkMeters: Double,
    val waitMinutes: Int?,
    val transfers: Int,
    val crowdingPercent: Int?,
    val accessible: Boolean?,
    val source: String
)

enum class EtaRiskLevel(val titleFa: String) { LOW("کم"), MEDIUM("متوسط"), HIGH("زیاد") }

data class EtaRisk(
    val level: EtaRiskLevel,
    val riskScore: Double,
    val reasonFa: String
)

data class MultimodalRerouteDecision(
    val shouldReroute: Boolean,
    val reasonFa: String,
    val triggerScore: Double
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

/** Explicit deterministic mock for tests/demos only. Never wired by default in production. */
class DeterministicMockTransitRealtimeProvider : TransitRealtimeProvider {
    override val availability = ProviderAvailability(
        available = true,
        source = "mock-non-live",
        messageFa = "داده آزمایشی mock است و زنده نیست"
    )

    override fun nearbyStationStatus(): List<StationStatus> = listOf(
        StationStatus("ایستگاه آزمایشی", "خط آزمایشی", 7, 45, live = false, source = "mock-non-live")
    )
}

/** Explicit deterministic mock for tests/demos only; never bookable. */
class DeterministicMockTaxiProvider : TaxiProvider {
    override val availability = ProviderAvailability(
        available = true,
        source = "mock-non-bookable",
        messageFa = "برآورد آزمایشی mock است و امکان رزرو ندارد"
    )

    override fun estimate(distanceMeters: Double): TaxiEstimate {
        val km = distanceMeters.coerceAtLeast(0.0) / 1_000.0
        val eta = (4 + km * 1.2).roundToInt().coerceAtLeast(4)
        val base = (50_000 + km * 12_000).roundToInt().toLong()
        return TaxiEstimate(eta, base, base + 30_000, "mock-unit", "mock-non-bookable", bookable = false)
    }
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

    fun rankStationTransfers(
        options: List<StationTransferOption>,
        preferences: TravelPreferences = TravelPreferences()
    ): List<StationTransferOption> {
        val prefs = preferences.normalized()
        return options.filter { option ->
            option.walkMeters <= prefs.maxWalkingMeters &&
                option.transfers <= prefs.maxTransfers &&
                (!prefs.accessibilityRequired || option.accessible != false)
        }.sortedBy { option ->
            val walkMinutes = option.walkMeters / WALKING_SPEED_MPS / 60.0
            val wait = option.waitMinutes?.coerceAtLeast(0)?.toDouble() ?: 12.0
            val transferPenalty = option.transfers * when (prefs.urgency) {
                Urgency.HURRY -> 12.0
                Urgency.NORMAL -> 8.0
                Urgency.RELAXED -> 5.0
            }
            val crowdPenalty = if (prefs.avoidCrowding) (option.crowdingPercent ?: 50) / 8.0 else 0.0
            walkMinutes + wait + transferPenalty + crowdPenalty
        }
    }

    fun etaRisk(confidence: EtaConfidence, trafficDelaySeconds: Double? = null): EtaRisk {
        val delayRatio = if (confidence.etaSeconds <= 0.0) 0.0 else
            (trafficDelaySeconds?.coerceAtLeast(0.0) ?: 0.0) / confidence.etaSeconds
        val uncertaintyRatio = if (confidence.etaSeconds <= 0.0) 1.0 else
            confidence.uncertaintySeconds / confidence.etaSeconds
        val score = ((1.0 - confidence.confidence) * 0.65 +
            uncertaintyRatio.coerceIn(0.0, 1.0) * 0.20 +
            delayRatio.coerceIn(0.0, 1.0) * 0.15).coerceIn(0.0, 1.0)
        val level = when {
            score >= .45 -> EtaRiskLevel.HIGH
            score >= .25 -> EtaRiskLevel.MEDIUM
            else -> EtaRiskLevel.LOW
        }
        val reason = when (level) {
            EtaRiskLevel.LOW -> "داده‌های ETA پایدار هستند"
            EtaRiskLevel.MEDIUM -> "عدم‌قطعیت یا تأخیر مسیر قابل توجه است"
            EtaRiskLevel.HIGH -> "ETA ناپایدار است؛ زمان بیشتری برای سفر در نظر بگیرید"
        }
        return EtaRisk(level, score, reason)
    }

    fun multimodalCandidates(
        route: Route?,
        preferences: TravelPreferences = TravelPreferences()
    ): List<MultimodalPlan> {
        if (route == null) return emptyList()
        val prefs = preferences.normalized()
        val plans = mutableListOf<MultimodalPlan>()
        if (MobilityMode.WALK in prefs.enabledModes && route.distanceMeters <= prefs.maxWalkingMeters) {
            val walk = walking(route.distanceMeters)
            plans += MultimodalPlan(
                available = true,
                legs = listOf(MultimodalLeg(MobilityMode.WALK, "پیاده‌روی", walk.distanceMeters, walk.travelSeconds, false, walk.source)),
                totalSeconds = walk.travelSeconds,
                warningFa = if (walk.exactRoute) null else "فاصله پیاده برآورد محلی است"
            )
        }
        if (MobilityMode.TAXI in prefs.enabledModes && taxiProvider.availability.available) {
            val estimate = taxiProvider.estimate(route.distanceMeters)
            if (estimate != null && (prefs.maxCost == null || estimate.fareMin == null || estimate.fareMin <= prefs.maxCost)) {
                plans += MultimodalPlan(
                    available = true,
                    legs = listOf(MultimodalLeg(MobilityMode.TAXI, "تاکسی", route.distanceMeters, route.travelSeconds, false, estimate.source)),
                    totalSeconds = estimate.etaMinutes * 60.0 + route.travelSeconds,
                    warningFa = if (estimate.bookable) null else "این provider امکان رزرو مستقیم ندارد"
                )
            }
        }
        if (setOf(MobilityMode.METRO, MobilityMode.BUS, MobilityMode.BRT).any(prefs.enabledModes::contains) && transitProvider.availability.available) {
            val status = transitProvider.nearbyStationStatus().firstOrNull()
            if (status != null) {
                val waitSeconds = (status.nextArrivalMinutes ?: 12).coerceAtLeast(0) * 60.0
                plans += MultimodalPlan(
                    available = true,
                    legs = listOf(MultimodalLeg(MobilityMode.METRO, status.lineName, route.distanceMeters, route.travelSeconds + waitSeconds, status.live, status.source)),
                    totalSeconds = route.travelSeconds + waitSeconds,
                    warningFa = if (status.live) null else "زمان حمل‌ونقل عمومی زنده نیست"
                )
            }
        }
        return plans.sortedBy { plan ->
            val urgencyFactor = when (prefs.urgency) { Urgency.HURRY -> 1.0; Urgency.NORMAL -> .8; Urgency.RELAXED -> .6 }
            plan.totalSeconds * urgencyFactor
        }
    }

    fun dynamicRerouteDecision(
        delayIncreaseSeconds: Double,
        crowdingPercent: Int?,
        walkingMeters: Double,
        preferences: TravelPreferences = TravelPreferences()
    ): MultimodalRerouteDecision {
        val prefs = preferences.normalized()
        val delayScore = (delayIncreaseSeconds.coerceAtLeast(0.0) / 900.0).coerceIn(0.0, 1.0)
        val crowdScore = if (prefs.avoidCrowding) ((crowdingPercent ?: 0) / 100.0).coerceIn(0.0, 1.0) else 0.0
        val walkScore = if (walkingMeters > prefs.maxWalkingMeters) 1.0 else 0.0
        val urgencyBoost = if (prefs.urgency == Urgency.HURRY) .15 else 0.0
        val score = (delayScore * .55 + crowdScore * .25 + walkScore * .20 + urgencyBoost).coerceIn(0.0, 1.0)
        val reroute = score >= .45
        return MultimodalRerouteDecision(
            shouldReroute = reroute,
            reasonFa = if (reroute) "شرایط سفر چندحالته تغییر معنادار کرده است؛ گزینه جایگزین بررسی شود" else "تغییر فعلی برای تعویض برنامه سفر کافی نیست",
            triggerScore = score
        )
    }

    private companion object {
        const val WALKING_SPEED_MPS = 1.35
    }
}

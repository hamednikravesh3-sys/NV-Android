package ir.nv.navigation.ui

import android.app.Application
import ir.nv.navigation.BuildConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.core.TrafficSummary
import ir.nv.navigation.core.TrafficSegment
import ir.nv.navigation.community.CommunityReport
import ir.nv.navigation.community.CommunityReportRepository
import ir.nv.navigation.community.CommunityReportType
import ir.nv.navigation.community.HttpCommunityReportRemote
import ir.nv.navigation.community.UnavailableCommunityReportRemote
import ir.nv.navigation.application.DestinationParkingUseCase
import ir.nv.navigation.application.EmergencySearchUseCase
import ir.nv.navigation.application.NearbyDiscoveryUseCase
import ir.nv.navigation.data.PersonalPlaceStore
import ir.nv.navigation.data.IranCityIndex
import ir.nv.navigation.data.PersianText
import ir.nv.navigation.data.PlaceCodes
import ir.nv.navigation.data.PlaceRepository
import ir.nv.navigation.data.RecentPlaceStore
import ir.nv.navigation.entitlement.TrialManager
import ir.nv.navigation.map.IranPackManager
import ir.nv.navigation.offline.ProvinceOfflineRuntime
import ir.nv.navigation.location.DeviceLocationProvider
import ir.nv.navigation.location.NavigationFix
import ir.nv.navigation.navigation.ContinuousRerouteEngine
import ir.nv.navigation.navigation.ContinuousReroutePolicy
import ir.nv.navigation.navigation.ArrivalConfirmationGate
import ir.nv.navigation.navigation.EvRoutePreferences
import ir.nv.navigation.navigation.NvNavigationPlatform
import ir.nv.navigation.navigation.OffRouteConfirmationGate
import ir.nv.navigation.navigation.RouteProfile
import ir.nv.navigation.navigation.RouteRequest
import ir.nv.navigation.navigation.TruckRestrictions
import ir.nv.navigation.navigation.VehicleProfile
import ir.nv.navigation.navigation.mapmatching.RawLocationSample
import ir.nv.navigation.navigation.mapmatching.RouteLocalMapMatcher
import ir.nv.navigation.network.NetworkMonitor
import ir.nv.navigation.online.OnlineNavigationService
import ir.nv.navigation.online.OnlinePlacesService
import ir.nv.navigation.places.NearbyCategory
import ir.nv.navigation.places.NearbySearchRequest
import ir.nv.navigation.places.PlaceSearchContext
import ir.nv.navigation.parking.ParkingOption
import ir.nv.navigation.parking.ParkingPlanner
import ir.nv.navigation.privacy.PrivacySettingsState
import ir.nv.navigation.privacy.PrivacySettingsStore
import ir.nv.navigation.routing.AStarRouter
import ir.nv.navigation.routing.RouteProgressEngine
import ir.nv.navigation.routing.RouteOriginConnector
import ir.nv.navigation.routing.NavigationCameraPolicy
import ir.nv.navigation.routing.RoutePointSampler
import ir.nv.navigation.routing.SqliteRoutingGraph
import ir.nv.navigation.search.HybridSearchEngine
import ir.nv.navigation.search.PlaceSearchProvider
import ir.nv.navigation.smart.SmartJourneyIntentParser
import ir.nv.navigation.smart.SmartJourneyMode
import ir.nv.navigation.smart.SmartJourneyPriority
import ir.nv.navigation.weather.WeatherAlertService
import ir.nv.navigation.traffic.LiveTrafficService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class NvUiState(
    val packStatus: IranPackManager.Status = IranPackManager.Status.NotStarted,
    val originQuery: String = "",
    val destinationQuery: String = "",
    val originSuggestions: List<Place> = emptyList(),
    val destinationSuggestions: List<Place> = emptyList(),
    val originSearching: Boolean = false,
    val destinationSearching: Boolean = false,
    val searchMessage: String? = null,
    val origin: Place? = null,
    val destination: Place? = null,
    val route: Route? = null,
    val routeAlternatives: List<Route> = emptyList(),
    val selectedRouteIndex: Int = 0,
    val routeProfile: RouteProfile = RouteProfile.SMART,
    val vehicleProfile: VehicleProfile = VehicleProfile.CAR,
    val truckRestrictions: TruckRestrictions = TruckRestrictions(),
    val evRoutePreferences: EvRoutePreferences = EvRoutePreferences(),
    val arrivalDetected: Boolean = false,
    val speedLimitKmh: Int? = null,
    val parkingOptions: List<ParkingOption> = emptyList(),
    val parkingSearchLoading: Boolean = false,
    val parkingFinalDestination: Place? = null,
    val parkingHandoffAvailable: Boolean = false,
    val privacySettings: PrivacySettingsState = PrivacySettingsState(),
    val communityReports: List<CommunityReport> = emptyList(),
    val communitySyncing: Boolean = false,
    val locationAccuracyMeters: Float? = null,
    val recentPlaces: List<Place> = emptyList(),
    val personalPlaces: List<Place> = emptyList(),
    val navigationActive: Boolean = false,
    val voiceEnabled: Boolean = true,
    val locating: Boolean = false,
    val currentLocation: Coordinate? = null,
    val smartJourneyPlanning: Boolean = false,
    val smartJourneyStatus: String? = null,
    val speedKmh: Int = 0,
    val bearingDegrees: Float = 0f,
    val navigationZoomLevel: Int = 18,
    val navigationRecenterToken: Int = 0,
    val cameraAutomatic: Boolean = true,
    val followNavigation: Boolean = true,
    val maneuverIndex: Int = 0,
    val distanceToNextManeuverMeters: Double = 0.0,
    val remainingDistanceMeters: Double = 0.0,
    val remainingSeconds: Double = 0.0,
    val offRoute: Boolean = false,
    val routeNotices: List<RouteNotice> = emptyList(),
    val routeInsightsLoading: Boolean = false,
    val traffic: TrafficSummary? = null,
    val trafficSegments: List<TrafficSegment> = emptyList(),
    val routing: Boolean = false,
    val message: String? = null,
    val onlineAvailable: Boolean = false,
    val offlineReady: Boolean = false,
    val preferOffline: Boolean = false,
    val satelliteMode: Boolean = false,
    val routeSource: RouteSource = RouteSource.NONE,
    val trialState: TrialManager.State = TrialManager.State.Trial(30)
)

class NvViewModel(application: Application) : AndroidViewModel(application) {
    private val packManager = IranPackManager(application)
    private val provinceRuntime = ProvinceOfflineRuntime(application)
    private val personalPlaces = PersonalPlaceStore(application)
    private val recentPlaces = RecentPlaceStore(application)
    private val online = OnlineNavigationService()
    private val onlinePlaces = OnlinePlacesService()
    private val networkMonitor = NetworkMonitor(application)
    private val locationProvider = DeviceLocationProvider(application)
    private val nearbyDiscoveryUseCase = NearbyDiscoveryUseCase(application)
    private val emergencySearchUseCase = EmergencySearchUseCase(nearbyDiscoveryUseCase)
    private val destinationParkingUseCase = DestinationParkingUseCase(nearbyDiscoveryUseCase)
    private val privacySettingsStore = PrivacySettingsStore(application)
    private val communityReportRepository = CommunityReportRepository(
        application,
        BuildConfig.COMMUNITY_REPORT_API_URL.takeIf { it.startsWith("https://") }
            ?.let(::HttpCommunityReportRemote)
            ?: UnavailableCommunityReportRemote
    )
    private val trialManager = TrialManager(application)
    private val weatherAlerts = WeatherAlertService()
    private val liveTraffic = LiveTrafficService()
    private val mutableState = MutableStateFlow(
        NvUiState(
            packStatus = packManager.status(),
            onlineAvailable = networkMonitor.isOnline(),
            offlineReady = packManager.isReady(),
            recentPlaces = recentPlaces.all(),
            personalPlaces = personalPlaces.all(),
            privacySettings = privacySettingsStore.load(),
            communityReports = communityReportRepository.all(),
            trialState = runCatching { trialManager.state() }.getOrDefault(TrialManager.State.Trial(30))
        )
    )
    val state: StateFlow<NvUiState> = mutableState.asStateFlow()

    private var places: PlaceRepository? = null
    private var graph: SqliteRoutingGraph? = null
    private var router: AStarRouter? = null
    private var activeOfflineMapFile: java.io.File? = null
    private val navigationPlatform by lazy {
        NvNavigationPlatform(
            onlineService = online,
            routerProvider = { router },
            liveTrafficService = liveTraffic
        )
    }
    private val hybridSearchEngine by lazy {
        HybridSearchEngine(
            offline = PlaceSearchProvider { query ->
                combineSearchResults(
                    personalPlaces.search(query) +
                        places?.search(query).orEmpty() +
                        IranCityIndex.search(query)
                )
            },
            online = PlaceSearchProvider { query -> online.search(query) }
        )
    }
    private var downloadMonitor: Job? = null
    private var searchJob: Job? = null
    private var navigationJob: Job? = null
    private var homeLocationJob: Job? = null
    private var insightsRefreshJob: Job? = null
    private val offRouteConfirmationGate = OffRouteConfirmationGate()
    private val arrivalConfirmationGate = ArrivalConfirmationGate()
    private var lastRerouteAt = 0L
    private var lastContinuousRerouteCheckAt = 0L
    private var previousTrafficDelaySeconds = 0.0
    private var lastInsightsRemainingMeters = Double.NaN

    init {
        viewModelScope.launch {
            networkMonitor.online.collect { available ->
                mutableState.update { state ->
                    state.copy(
                        onlineAvailable = available,
                        message = when {
                            !available && !state.offlineReady -> "اینترنت قطع است؛ برای استفاده آفلاین نقشه ایران را دانلود کنید"
                            !available && state.offlineReady -> "اینترنت قطع شد؛ NV به‌صورت خودکار آفلاین شد"
                            available && state.message?.startsWith("اینترنت قطع") == true -> null
                            else -> state.message
                        }
                    )
                }
                if (available) {
                    syncCommunityReports()
                } else if (!mutableState.value.offlineReady) {
                    ensureOfflineDataOpen()
                }
            }
        }
        when {
            packManager.isReady() -> viewModelScope.launch { openDataPack() }
            provinceRuntime.hasReadyPack() -> viewModelScope.launch { openProvinceDataPack() }
            packManager.status() !is IranPackManager.Status.NotStarted -> monitorDownload()
        }
    }

    fun startMapDownload() {
        runCatching {
            mutableState.update { it.copy(message = null) }
            packManager.startDownload()
            monitorDownload()
        }.onFailure { error ->
            mutableState.update {
                it.copy(packStatus = IranPackManager.Status.Failed(error.message ?: "شروع دانلود ممکن نشد"))
            }
        }
    }

    fun retryDownload() {
        mutableState.update { it.copy(message = null) }
        packManager.retry()
        monitorDownload()
    }

    fun cancelDownload() {
        downloadMonitor?.cancel()
        packManager.cancelDownload()
        mutableState.update { it.copy(packStatus = IranPackManager.Status.NotStarted) }
    }

    fun deleteOfflineMap() {
        downloadMonitor?.cancel()
        places?.close(); places = null
        graph?.close(); graph = null
        router = null
        packManager.deleteInstalledPack()
        val provinceReady = provinceRuntime.hasReadyPack()
        mutableState.update {
            it.copy(
                packStatus = IranPackManager.Status.NotStarted,
                offlineReady = provinceReady,
                preferOffline = false,
                message = if (provinceReady) "بسته کامل ایران حذف شد؛ بسته استانی همچنان آماده است" else "نقشه آفلاین حذف شد"
            )
        }
        if (provinceReady) viewModelScope.launch { openProvinceDataPack() }
    }

    fun setPreferOffline(value: Boolean) {
        if (!value) {
            mutableState.update { it.copy(preferOffline = false, message = null) }
            return
        }
        viewModelScope.launch {
            val ready = ensureOfflineDataOpen()
            if (ready) {
                mutableState.update { it.copy(preferOffline = true, offlineReady = true, message = null) }
            } else {
                mutableState.update { it.copy(preferOffline = false, offlineReady = false, message = "ابتدا از بخش «دانلود استان‌ها» یک استان را دانلود کنید") }
            }
        }
    }

    fun setRouteProfile(profile: RouteProfile) {
        mutableState.update { it.copy(routeProfile = profile, message = null) }
    }

    fun setVehicleProfile(profile: VehicleProfile) {
        mutableState.update { state ->
            state.copy(
                vehicleProfile = profile,
                message = when {
                    profile == VehicleProfile.TRANSIT && !state.onlineAvailable ->
                        "مسیر حمل‌ونقل عمومی به provider آنلاین سازگار نیاز دارد"
                    else -> null
                }
            )
        }
    }

    fun updateTruckRestrictions(value: TruckRestrictions) {
        mutableState.update { it.copy(truckRestrictions = value.normalized(), vehicleProfile = VehicleProfile.TRUCK) }
    }

    fun updateEvRoutePreferences(value: EvRoutePreferences) {
        mutableState.update { it.copy(evRoutePreferences = value.normalized(), vehicleProfile = VehicleProfile.EV) }
    }

    fun toggleSatelliteMode() {
        mutableState.update { state ->
            if (!state.onlineAvailable) {
                state.copy(
                    satelliteMode = false,
                    message = "نمای ماهواره‌ای فقط هنگام اتصال اینترنت در دسترس است"
                )
            } else {
                val enabled = !state.satelliteMode
                state.copy(
                    satelliteMode = enabled,
                    preferOffline = if (enabled) false else state.preferOffline,
                    message = if (enabled) "نمای ماهواره‌ای فعال شد؛ مسیریابی همچنان از داده معابر انجام می‌شود" else null
                )
            }
        }
    }

    private fun monitorDownload() {
        downloadMonitor?.cancel()
        downloadMonitor = viewModelScope.launch {
            while (isActive && !packManager.isReady()) {
                val status = packManager.status()
                mutableState.update { it.copy(packStatus = status) }
                if (status is IranPackManager.Status.Installing) {
                    val result = packManager.installDownloadedPack()
                    if (result.isFailure) {
                        mutableState.update {
                            it.copy(packStatus = IranPackManager.Status.Failed(result.exceptionOrNull()?.message ?: "نصب بسته ناموفق بود"))
                        }
                        return@launch
                    }
                }
                if (status is IranPackManager.Status.Failed) return@launch
                delay(1_000)
            }
            if (packManager.isReady()) openDataPack()
        }
    }

    fun refreshEntitlement(isPaid: Boolean) {
        val entitlement = runCatching { trialManager.state(isPaid) }
            .getOrDefault(TrialManager.State.Trial(30))
        mutableState.update { it.copy(trialState = entitlement) }
    }

    fun updateOriginQuery(query: String) {
        mutableState.update { it.copy(originQuery = query, origin = null) }
        search(query, true)
    }

    fun updateDestinationQuery(query: String) {
        mutableState.update { it.copy(destinationQuery = query, destination = null) }
        search(query, false)
    }

    fun selectOrigin(place: Place) {
        mutableState.update { it.copy(origin = place, originQuery = place.name, originSuggestions = emptyList()) }
    }

    fun selectDestination(place: Place) {
        recentPlaces.record(place)
        mutableState.update {
            it.copy(
                destination = place,
                destinationQuery = place.name,
                destinationSuggestions = emptyList(),
                recentPlaces = recentPlaces.all()
            )
        }
    }

    fun selectRoute(index: Int) {
        val selected = mutableState.value.routeAlternatives.getOrNull(index) ?: return
        mutableState.update {
            it.copy(
                route = selected,
                selectedRouteIndex = index,
                maneuverIndex = 0,
                distanceToNextManeuverMeters = selected.maneuvers.firstOrNull()?.distanceMeters ?: selected.distanceMeters,
                remainingDistanceMeters = selected.distanceMeters,
                remainingSeconds = selected.travelSeconds,
                routeNotices = emptyList(),
                routeInsightsLoading = true,
                traffic = null,
                trafficSegments = emptyList()
            )
        }
        lastInsightsRemainingMeters = selected.distanceMeters
        viewModelScope.launch { loadRouteNotices(selected, selected) }
    }

    fun swapEndpoints() {
        mutableState.update { state ->
            val origin = state.origin
            val destination = state.destination
            if (origin == null || destination == null) {
                state.copy(message = "ابتدا مبدأ و مقصد را انتخاب کنید")
            } else {
                state.copy(
                    origin = destination,
                    destination = origin,
                    originQuery = destination.name,
                    destinationQuery = origin.name,
                    originSuggestions = emptyList(),
                    destinationSuggestions = emptyList(),
                    message = null
                )
            }
        }
    }

    fun clearRoute() {
        navigationJob?.cancel()
        insightsRefreshJob?.cancel()
        mutableState.update {
            it.copy(
                route = null,
                routeAlternatives = emptyList(),
                selectedRouteIndex = 0,
                navigationActive = false,
                maneuverIndex = 0,
                offRoute = false,
                arrivalDetected = false,
                speedLimitKmh = null,
                parkingOptions = emptyList(),
                parkingSearchLoading = false,
                parkingFinalDestination = null,
                parkingHandoffAvailable = false,
                routeSource = RouteSource.NONE,
                routeNotices = emptyList(),
                routeInsightsLoading = false,
                traffic = null,
                trafficSegments = emptyList()
            )
        }
    }

    fun startNavigation() {
        val route = mutableState.value.route ?: return
        if (!locationProvider.hasPermission()) {
            mutableState.update { it.copy(message = "برای راهنمای زنده، دسترسی موقعیت مکانی را فعال کنید") }
            return
        }
        navigationJob?.cancel()
        offRouteConfirmationGate.reset()
        arrivalConfirmationGate.reset()
        lastRerouteAt = 0L
        lastContinuousRerouteCheckAt = 0L
        previousTrafficDelaySeconds = mutableState.value.traffic?.delaySeconds ?: 0.0
        lastInsightsRemainingMeters = route.distanceMeters
        mutableState.update {
            it.copy(
                navigationActive = true,
                arrivalDetected = false,
                navigationZoomLevel = DEFAULT_NAVIGATION_ZOOM,
                cameraAutomatic = true,
                followNavigation = true,
                message = null,
                remainingDistanceMeters = route.distanceMeters,
                remainingSeconds = route.travelSeconds
            )
        }
        navigationJob = viewModelScope.launch {
            locationProvider.updates().collect { fix ->
                updateNavigationProgress(fix)
            }
        }
    }

    fun stopNavigation() {
        navigationJob?.cancel()
        mutableState.update { it.copy(navigationActive = false) }
    }

    fun zoomNavigationIn() {
        mutableState.update {
            it.copy(
                navigationZoomLevel = (it.navigationZoomLevel + 1).coerceAtMost(MAX_NAVIGATION_ZOOM),
                cameraAutomatic = false,
                followNavigation = true,
                navigationRecenterToken = it.navigationRecenterToken + 1
            )
        }
    }

    fun zoomNavigationOut() {
        mutableState.update {
            it.copy(
                navigationZoomLevel = (it.navigationZoomLevel - 1).coerceAtLeast(MIN_NAVIGATION_ZOOM),
                cameraAutomatic = false,
                followNavigation = true,
                navigationRecenterToken = it.navigationRecenterToken + 1
            )
        }
    }

    fun recenterNavigation() {
        mutableState.update {
            it.copy(
                navigationZoomLevel = DEFAULT_NAVIGATION_ZOOM,
                cameraAutomatic = true,
                followNavigation = true,
                navigationRecenterToken = it.navigationRecenterToken + 1
            )
        }
    }

    fun pauseNavigationFollow() {
        mutableState.update { state ->
            if (!state.navigationActive || !state.followNavigation) state
            else state.copy(followNavigation = false)
        }
    }

    fun toggleVoice() {
        mutableState.update { it.copy(voiceEnabled = !it.voiceEnabled) }
    }

    private fun locationFailureMessage(): String = when {
        !locationProvider.hasPermission() -> "دسترسی موقعیت مکانی داده نشده است"
        !locationProvider.isLocationEnabled() -> "سرویس موقعیت مکانی دستگاه خاموش است؛ Location را از تنظیمات دستگاه روشن کنید"
        !locationProvider.hasFinePermission() -> "موقعیت تقریبی فعال است؛ برای دقت بیشتر، مجوز «موقعیت دقیق» را برای راهنما فعال کنید"
        else -> "Location روشن است اما هنوز GPS به دقت مناسب نرسیده؛ چند لحظه صبر کنید یا نزدیک فضای باز بروید"
    }

    private fun ensureHomeLocationTracking() {
        if (!locationProvider.hasPermission() || homeLocationJob?.isActive == true) return
        homeLocationJob = viewModelScope.launch {
            runCatching {
                locationProvider.updates().collect { fix ->
                    mutableState.update { state ->
                        val movingOrigin = state.origin?.category == DEVICE_LOCATION_CATEGORY
                        val updatedOrigin = if (movingOrigin) {
                            state.origin?.copy(coordinate = fix.coordinate)
                        } else state.origin
                        state.copy(
                            currentLocation = fix.coordinate,
                            locationAccuracyMeters = fix.accuracyMeters.takeIf { it.isFinite() },
                            origin = updatedOrigin,
                            locating = false,
                            message = if (state.message?.startsWith("Location روشن است") == true ||
                                state.message?.contains("GPS") == true) null else state.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Chat entry point: current device position is always the implicit origin.
     * The user only describes where/how they want to travel; no origin/destination form is required.
     */
    fun planJourneyFromChat(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isBlank()) return
        if (!locationProvider.hasPermission()) {
            mutableState.update { it.copy(smartJourneyStatus = "برای شروع مسیر از موقعیت فعلی، مجوز Location را بدهید", message = locationFailureMessage()) }
            return
        }
        ensureHomeLocationTracking()
        viewModelScope.launch {
            mutableState.update { it.copy(smartJourneyPlanning = true, smartJourneyStatus = "در حال تشخیص مقصد و دریافت موقعیت فعلی…", message = null) }
            val coordinate = withTimeoutOrNull(15_000L) { locationProvider.currentLocation() }
                ?: mutableState.value.currentLocation
            if (coordinate == null) {
                val failure = locationFailureMessage()
                mutableState.update { it.copy(smartJourneyPlanning = false, smartJourneyStatus = failure, message = failure) }
                return@launch
            }

            val intent = SmartJourneyIntentParser.parse(query)
            val destinationText = intent.destinationQuery
            val snapshot = mutableState.value
            val candidates = withContext(Dispatchers.IO) {
                hybridSearchEngine.search(
                    query = destinationText,
                    onlineAvailable = snapshot.onlineAvailable,
                    preferOffline = snapshot.preferOffline,
                    limit = 8
                )
            }
            val destination = candidates.firstOrNull()
            if (destination == null) {
                mutableState.update {
                    it.copy(
                        smartJourneyPlanning = false,
                        currentLocation = coordinate,
                        smartJourneyStatus = "مقصد «$destinationText» پیدا نشد؛ نام مکان یا کد NV را دقیق‌تر بنویسید"
                    )
                }
                return@launch
            }

            val origin = Place(
                code = CURRENT_LOCATION_CODE,
                name = "موقعیت فعلی من",
                coordinate = coordinate,
                category = DEVICE_LOCATION_SNAPSHOT_CATEGORY
            )
            val requestedVehicle = when (intent.mode) {
                SmartJourneyMode.WALKING -> VehicleProfile.WALKING
                SmartJourneyMode.BICYCLE -> VehicleProfile.BICYCLE
                SmartJourneyMode.MOTORCYCLE -> VehicleProfile.MOTORCYCLE
                SmartJourneyMode.DRIVING,
                SmartJourneyMode.TAXI,
                SmartJourneyMode.TRANSIT,
                SmartJourneyMode.MIXED,
                SmartJourneyMode.AUTO -> VehicleProfile.CAR
            }
            val fastestRequested = intent.hurry || intent.priority == SmartJourneyPriority.FASTEST
            val modeNotice = when (intent.mode) {
                SmartJourneyMode.TAXI -> "درخواست تاکسی تشخیص داده شد؛ مسیر جاده‌ای واقعی محاسبه می‌شود و قیمت/رزرو زنده فقط با ارائه‌دهنده متصل نمایش داده خواهد شد"
                SmartJourneyMode.TRANSIT, SmartJourneyMode.MIXED -> "درخواست حمل‌ونقل عمومی تشخیص داده شد؛ داده زمان‌بندی زنده فقط در صورت اتصال منبع واقعی نمایش داده می‌شود"
                else -> null
            }
            recentPlaces.record(destination)
            mutableState.update {
                it.copy(
                    currentLocation = coordinate,
                    origin = origin,
                    originQuery = origin.name,
                    destination = destination,
                    destinationQuery = destination.name,
                    originSuggestions = emptyList(),
                    destinationSuggestions = emptyList(),
                    recentPlaces = recentPlaces.all(),
                    vehicleProfile = requestedVehicle,
                    smartJourneyPlanning = false,
                    smartJourneyStatus = buildString {
                        append("مقصد ${destination.name} تشخیص داده شد؛ ")
                        append(if (fastestRequested) "سریع‌ترین مسیر واقعی در حال محاسبه است" else "مسیر از موقعیت فعلی در حال محاسبه است")
                        modeNotice?.let { append(" • "); append(it) }
                    },
                    message = null
                )
            }
            calculateRouteInternal(autoSelectFastest = fastestRequested)
        }
    }

    private fun extractChatDestination(input: String): String {
        var clean = PersianText.normalize(input)
        val removable = listOf(
            "لطفا", "لطفاً", "میخوام", "می خوام", "می‌خوام", "میخواهم", "می خواهم", "می‌خواهم",
            "منو ببر", "مرا ببر", "ببر منو", "مسیر بده", "مسیریابی کن", "راه رو نشون بده", "راه را نشان بده",
            "با اسنپ", "اسنپ", "با تاکسی", "تاکسی", "با مترو", "مترو", "با اتوبوس", "اتوبوس",
            "پیاده", "سریع ترین", "سریع‌ترین", "بهترین مسیر", "از اینجا", "از موقعیت من"
        )
        removable.forEach { clean = clean.replace(it, " ", ignoreCase = true) }
        clean = clean.replace(Regex("\\s+"), " ").trim()
        clean = clean.replace(Regex("^(به|تا|سمت)\\s+"), "").trim()
        return clean.ifBlank { input.trim() }
    }

    fun routeFromCurrentLocationTo(destination: Place, vehicleProfile: VehicleProfile = mutableState.value.vehicleProfile) {
        if (!locationProvider.hasPermission()) {
            mutableState.update { it.copy(message = "برای مسیریابی از موقعیت فعلی، دسترسی موقعیت مکانی را فعال کنید") }
            return
        }
        ensureHomeLocationTracking()
        viewModelScope.launch {
            mutableState.update { it.copy(locating = true, routing = true, message = "در حال دریافت موقعیت فعلی…") }
            val coordinate = withTimeoutOrNull(12_000L) { locationProvider.currentLocation() }
            if (coordinate == null) {
                mutableState.update { it.copy(locating = false, routing = false, message = locationFailureMessage()) }
                return@launch
            }
            val origin = Place(
                code = CURRENT_LOCATION_CODE,
                name = "موقعیت فعلی من",
                coordinate = coordinate,
                category = DEVICE_LOCATION_SNAPSHOT_CATEGORY
            )
            recentPlaces.record(destination)
            mutableState.update {
                it.copy(
                    locating = false,
                    routing = false,
                    currentLocation = coordinate,
                    origin = origin,
                    originQuery = origin.name,
                    originSuggestions = emptyList(),
                    destination = destination,
                    destinationQuery = destination.name,
                    destinationSuggestions = emptyList(),
                    recentPlaces = recentPlaces.all(),
                    vehicleProfile = vehicleProfile,
                    arrivalDetected = false,
                    message = null
                )
            }
            calculateRoute()
        }
    }

    fun useCurrentLocationAsOrigin() {
        if (!locationProvider.hasPermission()) {
            mutableState.update { it.copy(message = locationFailureMessage()) }
            return
        }
        ensureHomeLocationTracking()
        viewModelScope.launch {
            mutableState.update { it.copy(locating = true, message = null) }
            val coordinate = withTimeoutOrNull(12_000L) { locationProvider.currentLocation() }
                ?: mutableState.value.currentLocation
            if (coordinate == null) {
                mutableState.update { it.copy(locating = false, message = locationFailureMessage()) }
            } else {
                val place = Place(
                    code = CURRENT_LOCATION_CODE,
                    name = "موقعیت فعلی من",
                    coordinate = coordinate,
                    category = DEVICE_LOCATION_CATEGORY
                )
                mutableState.update {
                    it.copy(
                        locating = false,
                        currentLocation = coordinate,
                        origin = place,
                        originQuery = place.name,
                        originSuggestions = emptyList(),
                        message = if (!locationProvider.hasFinePermission()) locationFailureMessage() else null
                    )
                }
            }
        }
    }

    fun savePersonalCode(place: Place, code: String) {
        val result = personalPlaces.save(code, place.name, place.coordinate)
        val cleanCode = ir.nv.navigation.data.PersonalCodeRules.normalize(code)
        mutableState.update {
            it.copy(
                personalPlaces = personalPlaces.all(),
                message = result.exceptionOrNull()?.message ?: "کد شخصی «$cleanCode» ذخیره شد"
            )
        }
    }

    fun deletePersonalCode(code: String) {
        personalPlaces.delete(code)
        mutableState.update {
            it.copy(personalPlaces = personalPlaces.all(), message = "کد شخصی حذف شد")
        }
    }

    fun calculateRoute() = calculateRouteInternal(autoSelectFastest = false)

    private fun calculateRouteInternal(autoSelectFastest: Boolean) {
        val initialState = mutableState.value
        val originSelection = initialState.origin
        val destination = initialState.destination
        if (originSelection == null) {
            mutableState.update { it.copy(message = "ابتدا مبدأ را انتخاب کنید") }
            return
        }
        if (destination == null) {
            mutableState.update { it.copy(message = "ابتدا مقصد را انتخاب کنید") }
            return
        }
        val currentLocationOrigin = originSelection.category == DEVICE_LOCATION_CATEGORY
        if (currentLocationOrigin && !locationProvider.hasPermission()) {
            mutableState.update { it.copy(message = "برای تعیین مبدأ، دسترسی موقعیت مکانی را فعال کنید") }
            return
        }
        viewModelScope.launch {
            var origin = originSelection
            if (currentLocationOrigin) {
                mutableState.update { it.copy(routing = true, locating = true, message = "در حال دریافت مبدأ از GPS…") }
                val coordinate = withTimeoutOrNull(12_000L) { locationProvider.currentLocation() }
                if (coordinate == null) {
                    mutableState.update {
                        it.copy(routing = false, locating = false, message = locationFailureMessage())
                    }
                    return@launch
                }
                origin = Place(
                    code = CURRENT_LOCATION_CODE,
                    name = "موقعیت فعلی من",
                    coordinate = coordinate,
                    category = DEVICE_LOCATION_CATEGORY
                )
                mutableState.update {
                    it.copy(
                        locating = false,
                        currentLocation = coordinate,
                        origin = origin,
                        originQuery = origin.name,
                        originSuggestions = emptyList(),
                        message = null
                    )
                }
            } else {
                mutableState.update { it.copy(routing = true, locating = false, message = null) }
            }

            val snapshot = mutableState.value
            val request = RouteRequest(
                origin = origin.coordinate,
                destination = destination.coordinate,
                profile = snapshot.routeProfile,
                vehicleProfile = snapshot.vehicleProfile,
                preferOffline = snapshot.preferOffline,
                onlineAvailable = snapshot.onlineAvailable,
                offlineAvailable = snapshot.offlineReady && router != null,
                truck = snapshot.truckRestrictions,
                ev = snapshot.evRoutePreferences
            )
            val plan = runCatching { navigationPlatform.routeCoordinator.plan(request) }
                .getOrElse { error ->
                    mutableState.update {
                        it.copy(routing = false, message = error.message ?: "محاسبه مسیر ناموفق بود")
                    }
                    return@launch
                }
            val candidates = plan.candidates
            val results = candidates.map { RouteOriginConnector.attach(origin.coordinate, it.route) }
            val selectedIndex = if (autoSelectFastest && results.isNotEmpty()) {
                results.indices.minByOrNull { index -> results[index].travelSeconds } ?: 0
            } else {
                0
            }
            val result = results.getOrNull(selectedIndex)
            val source = candidates.getOrNull(selectedIndex)?.source ?: RouteSource.NONE

            mutableState.update {
                it.copy(
                    routing = false,
                    route = result,
                    routeAlternatives = results,
                    selectedRouteIndex = selectedIndex,
                    navigationActive = false,
                    maneuverIndex = 0,
                    distanceToNextManeuverMeters = result?.maneuvers?.firstOrNull()?.distanceMeters
                        ?: result?.distanceMeters ?: 0.0,
                    remainingDistanceMeters = result?.distanceMeters ?: 0.0,
                    remainingSeconds = result?.travelSeconds ?: 0.0,
                    offRoute = false,
                    arrivalDetected = false,
                    speedLimitKmh = result?.maneuvers?.firstOrNull()?.speedLimitKmh,
                    routeSource = source,
                    routeNotices = emptyList(),
                    routeInsightsLoading = result != null,
                    traffic = candidates.getOrNull(selectedIndex)?.traffic,
                    trafficSegments = emptyList(),
                    message = when {
                        result != null && plan.fallbackUsed && source == RouteSource.OFFLINE ->
                            "سرویس آنلاین پاسخ نداد؛ مسیر با داده آفلاین محاسبه شد"
                        result != null -> plan.warning
                        !snapshot.onlineAvailable && !snapshot.offlineReady ->
                            "اینترنت در دسترس نیست و نقشه آفلاین دانلود نشده است"
                        plan.warning != null -> plan.warning
                        else -> "برای این دو نقطه مسیر پیدا نشد"
                    }
                )
            }
            result?.let {
                lastInsightsRemainingMeters = it.distanceMeters
                loadRouteNotices(it, it)
            }
        }
    }

    suspend fun discoverNearby(request: NearbySearchRequest): List<Place> {
        val snapshot = mutableState.value
        return nearbyDiscoveryUseCase(
            request,
            PlaceSearchContext(
                currentLocation = snapshot.currentLocation,
                origin = snapshot.origin?.coordinate,
                destination = snapshot.destination?.coordinate,
                route = snapshot.route,
                onlineAvailable = snapshot.onlineAvailable,
                preferOffline = snapshot.preferOffline
            )
        )
    }

    suspend fun discoverEmergency(category: NearbyCategory, radiusMeters: Int = 25_000): List<Place> {
        val snapshot = mutableState.value
        val center = snapshot.currentLocation ?: snapshot.origin?.coordinate
            ?: throw IllegalStateException("ابتدا موقعیت فعلی را دریافت کنید")
        return emergencySearchUseCase.search(
            category = category,
            center = center,
            onlineAvailable = snapshot.onlineAvailable,
            preferOffline = snapshot.preferOffline,
            radiusMeters = radiusMeters
        )
    }

    suspend fun discoverDestinationParking(radiusMeters: Int = 5_000): List<Place> {
        val snapshot = mutableState.value
        val destination = snapshot.destination?.coordinate
            ?: throw IllegalStateException("ابتدا مقصد را مشخص کنید")
        return destinationParkingUseCase.search(
            destination = destination,
            onlineAvailable = snapshot.onlineAvailable,
            preferOffline = snapshot.preferOffline,
            radiusMeters = radiusMeters
        )
    }

    fun setStrictPrivacy(enabled: Boolean) {
        val updated = privacySettingsStore.update { it.copy(strictMode = enabled) }
        mutableState.update { it.copy(privacySettings = updated) }
    }

    fun setLocationHistoryConsent(enabled: Boolean) {
        val updated = privacySettingsStore.update { it.copy(locationHistory = enabled) }
        mutableState.update { it.copy(privacySettings = updated) }
    }

    fun setAnalyticsConsent(enabled: Boolean) {
        val updated = privacySettingsStore.update { it.copy(analytics = enabled) }
        mutableState.update { it.copy(privacySettings = updated) }
    }

    fun setCommunityUploads(enabled: Boolean) {
        val updated = privacySettingsStore.update { it.copy(communityUploads = enabled) }
        mutableState.update {
            it.copy(
                privacySettings = updated,
                message = if (enabled && BuildConfig.COMMUNITY_REPORT_API_URL.isBlank()) {
                    "رضایت ارسال ثبت شد، اما endpoint گزارش‌های مشارکتی پیکربندی نشده است"
                } else null
            )
        }
        if (enabled && mutableState.value.onlineAvailable) syncCommunityReports()
    }

    fun setCloudSyncConsent(enabled: Boolean) {
        val updated = privacySettingsStore.update { it.copy(cloudSync = enabled) }
        mutableState.update { it.copy(privacySettings = updated) }
    }

    fun submitCommunityReport(type: CommunityReportType, note: String? = null) {
        val snapshot = mutableState.value
        val coordinate = snapshot.currentLocation ?: run {
            mutableState.update { it.copy(message = "برای ثبت گزارش، موقعیت فعلی لازم است") }
            return
        }
        communityReportRepository.submit(
            type = type,
            coordinate = coordinate,
            accuracyMeters = snapshot.locationAccuracyMeters,
            note = note
        )
        mutableState.update {
            it.copy(
                communityReports = communityReportRepository.all(),
                message = if (it.privacySettings.communityUploads) {
                    "گزارش ${type.titleFa} ثبت شد و برای همگام‌سازی آماده است"
                } else {
                    "گزارش ${type.titleFa} محلی ثبت شد؛ ارسال شبکه در حریم خصوصی خاموش است"
                }
            )
        }
        if (snapshot.onlineAvailable && snapshot.privacySettings.communityUploads) syncCommunityReports()
    }

    private fun syncCommunityReports() {
        val snapshot = mutableState.value
        if (!snapshot.onlineAvailable || !snapshot.privacySettings.communityUploads || snapshot.communitySyncing) return
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.update { it.copy(communitySyncing = true) }
            val reports = runCatching { communityReportRepository.syncPending(mutableState.value.privacySettings) }
                .getOrDefault(communityReportRepository.all())
            mutableState.update { it.copy(communityReports = reports, communitySyncing = false) }
        }
    }

    fun searchParkingNearDestination(radiusMeters: Int = 5_000) {
        val snapshot = mutableState.value
        val destination = snapshot.destination ?: run {
            mutableState.update { it.copy(message = "ابتدا مقصد را مشخص کنید") }
            return
        }
        if (!snapshot.onlineAvailable && !snapshot.offlineReady) {
            mutableState.update { it.copy(message = "برای جستجوی پارکینگ، اینترنت یا بسته آفلاین مکان‌ها لازم است") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(parkingSearchLoading = true, parkingOptions = emptyList(), message = null) }
            val result = runCatching {
                val places = discoverDestinationParking(radiusMeters)
                ParkingPlanner.rank(destination.coordinate, places)
            }
            result.onSuccess { options ->
                mutableState.update {
                    it.copy(
                        parkingSearchLoading = false,
                        parkingOptions = options,
                        message = if (options.isEmpty()) "پارکینگ نزدیک مقصد پیدا نشد" else null
                    )
                }
            }.onFailure { error ->
                mutableState.update { it.copy(parkingSearchLoading = false, message = error.message ?: "جستجوی پارکینگ ناموفق بود") }
            }
        }
    }

    fun routeToParking(option: ParkingOption) {
        val finalDestination = mutableState.value.destination ?: return
        recentPlaces.record(option.place)
        mutableState.update {
            it.copy(
                destination = option.place,
                destinationQuery = option.place.name,
                destinationSuggestions = emptyList(),
                recentPlaces = recentPlaces.all(),
                vehicleProfile = VehicleProfile.CAR,
                parkingFinalDestination = finalDestination,
                parkingHandoffAvailable = false,
                arrivalDetected = false,
                message = "مسیر خودرو تا پارکینگ انتخاب شد"
            )
        }
        calculateRoute()
    }

    fun continueWalkingAfterParking() {
        val snapshot = mutableState.value
        val finalDestination = snapshot.parkingFinalDestination ?: run {
            mutableState.update { it.copy(message = "مقصد نهایی برای ادامه پیاده ثبت نشده است") }
            return
        }
        val current = snapshot.currentLocation ?: run {
            mutableState.update { it.copy(message = "برای شروع مسیر پیاده، موقعیت فعلی لازم است") }
            return
        }
        val walkingOrigin = Place(
            code = CURRENT_LOCATION_CODE,
            name = "موقعیت فعلی من",
            coordinate = current,
            category = DEVICE_LOCATION_SNAPSHOT_CATEGORY
        )
        mutableState.update {
            it.copy(
                origin = walkingOrigin,
                originQuery = walkingOrigin.name,
                destination = finalDestination,
                destinationQuery = finalDestination.name,
                vehicleProfile = VehicleProfile.WALKING,
                parkingFinalDestination = null,
                parkingHandoffAvailable = false,
                parkingOptions = emptyList(),
                arrivalDetected = false,
                message = "ادامه مسیر به‌صورت پیاده"
            )
        }
        calculateRoute()
    }

    fun cancelParkingHandoff() {
        mutableState.update {
            it.copy(
                parkingFinalDestination = null,
                parkingHandoffAvailable = false,
                parkingOptions = emptyList(),
                message = null
            )
        }
    }

    private fun search(query: String, origin: Boolean) {
        searchJob?.cancel()
        if (query.trim().isEmpty()) {
            mutableState.update {
                if (origin) {
                    it.copy(originSuggestions = emptyList(), originSearching = false, searchMessage = null)
                } else {
                    it.copy(destinationSuggestions = emptyList(), destinationSearching = false, searchMessage = null)
                }
            }
            return
        }
        searchJob = viewModelScope.launch {
            val immediate = withContext(Dispatchers.IO) {
                hybridSearchEngine.searchDetailed(
                    query = query,
                    onlineAvailable = false,
                    preferOffline = true
                ).items
            }
            mutableState.update {
                if (origin) {
                    it.copy(originSuggestions = immediate, originSearching = false, searchMessage = null)
                } else {
                    it.copy(destinationSuggestions = immediate, destinationSearching = false, searchMessage = null)
                }
            }

            val publicCode = PlaceCodes.publicCode(query)
            val snapshot = mutableState.value
            val needsOnline = query.trim().length >= 2 &&
                (publicCode == null || PlaceCodes.onlineIdentity(publicCode) != null) &&
                snapshot.onlineAvailable && !snapshot.preferOffline
            if (!needsOnline) return@launch

            delay(220)
            mutableState.update {
                if (origin) it.copy(originSearching = true) else it.copy(destinationSearching = true)
            }
            val detailed = withContext(Dispatchers.IO) {
                hybridSearchEngine.searchDetailed(
                    query = query,
                    onlineAvailable = true,
                    preferOffline = false
                )
            }
            val activeQuery = if (origin) mutableState.value.originQuery else mutableState.value.destinationQuery
            if (activeQuery != query) return@launch
            val warning = if (detailed.onlineFailed && immediate.isEmpty()) {
                "جست‌وجوی آنلاین پاسخ نداد؛ اتصال اینترنت را بررسی کنید"
            } else null
            mutableState.update {
                if (origin) {
                    it.copy(originSuggestions = detailed.items, originSearching = false, searchMessage = warning)
                } else {
                    it.copy(destinationSuggestions = detailed.items, destinationSearching = false, searchMessage = warning)
                }
            }
        }
    }

    private fun combineSearchResults(values: List<Place>): List<Place> = values
        .distinctBy {
            Triple(
                PersianText.normalize(it.name),
                (it.coordinate.latitude * 1_000).toInt(),
                (it.coordinate.longitude * 1_000).toInt()
            )
        }
        .take(30)

    private suspend fun openDataPack() = withContext(Dispatchers.IO) {
        runCatching {
            places?.close(); graph?.close()
            places = PlaceRepository(packManager.placesFile)
            graph = SqliteRoutingGraph(packManager.routingFile)
            router = AStarRouter(requireNotNull(graph))
            activeOfflineMapFile = packManager.mapFile
        }.onSuccess {
            mutableState.update { it.copy(packStatus = IranPackManager.Status.Ready, offlineReady = true) }
        }.onFailure { error ->
            activeOfflineMapFile = null
            mutableState.update { it.copy(packStatus = IranPackManager.Status.Failed(error.message ?: "داده نامعتبر"), offlineReady = false) }
        }
    }

    private suspend fun openProvinceDataPack(): Boolean = withContext(Dispatchers.IO) {
        val active = provinceRuntime.active() ?: return@withContext false
        runCatching {
            places?.close(); graph?.close()
            places = PlaceRepository(active.files.placesFile)
            graph = SqliteRoutingGraph(active.files.routingFile)
            router = AStarRouter(requireNotNull(graph))
            activeOfflineMapFile = active.files.mapFile
        }.onSuccess {
            mutableState.update { state ->
                state.copy(
                    offlineReady = true,
                    message = if (!state.onlineAvailable) "اینترنت قطع است؛ بسته آفلاین ${active.pack.title} فعال شد" else state.message
                )
            }
        }.onFailure { error ->
            activeOfflineMapFile = null
            router = null
            mutableState.update { it.copy(offlineReady = false, message = error.message ?: "بسته استانی قابل استفاده نیست") }
        }.isSuccess
    }

    private suspend fun ensureOfflineDataOpen(): Boolean = when {
        packManager.isReady() -> {
            openDataPack()
            router != null && activeOfflineMapFile?.isFile == true
        }
        provinceRuntime.hasReadyPack() -> openProvinceDataPack()
        else -> false
    }

    fun mapFile() = activeOfflineMapFile ?: packManager.mapFile

    private suspend fun loadRouteNotices(route: Route, ownerRoute: Route) {
        val onlineNow = networkMonitor.isOnline()
        val (notices, trafficReport) = withContext(Dispatchers.IO) {
            coroutineScope {
                val offlinePlaces = async { runCatching { places?.noticesAlong(route, 12).orEmpty() }.getOrDefault(emptyList()) }
                val remotePlaces = async {
                    if (onlineNow) runCatching { onlinePlaces.noticesAhead(route) }.getOrDefault(emptyList())
                    else emptyList()
                }
                val weather = async {
                    if (onlineNow) runCatching { weatherAlerts.alertsAhead(route) }.getOrDefault(emptyList())
                    else emptyList()
                }
                val currentTraffic = async {
                    if (onlineNow) runCatching { liveTraffic.report(route) }.getOrNull() else null
                }
                val merged = (weather.await() + offlinePlaces.await() + remotePlaces.await())
                    .distinctBy { Triple(it.kind, it.title, it.distanceAheadMeters.toInt() / 250) }
                    .sortedBy { it.distanceAheadMeters }
                    .take(16)
                merged to currentTraffic.await()
            }
        }
        mutableState.update { state ->
            if (state.route === ownerRoute) {
                state.copy(
                    routeNotices = notices,
                    routeInsightsLoading = false,
                    traffic = trafficReport?.summary,
                    trafficSegments = trafficReport?.segments.orEmpty()
                )
            } else state
        }
    }

    private suspend fun updateNavigationProgress(fix: NavigationFix) {
        val snapshot = mutableState.value
        val route = snapshot.route ?: return
        val sample = RawLocationSample(
            coordinate = fix.coordinate,
            speedKmh = fix.speedKmh.toDouble(),
            bearingDegrees = fix.bearingDegrees,
            accuracyMeters = fix.accuracyMeters,
            timestampMillis = fix.timestampMillis
        )
        val remoteMatched = runCatching { navigationPlatform.mapMatchingEngine.match(sample) }.getOrNull()
        val localMatched = RouteLocalMapMatcher.match(route, sample)
        val matched = listOfNotNull(remoteMatched, localMatched)
            .filter { it.confidence >= MIN_MAP_MATCH_CONFIDENCE }
            .maxByOrNull { it.confidence }
        val coordinate = matched?.coordinate ?: fix.coordinate
        val progress = RouteProgressEngine.calculate(route, coordinate) ?: return
        val destination = snapshot.destination
        if (destination != null && arrivalConfirmationGate.observe(
                location = coordinate,
                destination = destination.coordinate,
                gpsAccuracyMeters = fix.accuracyMeters,
                speedKmh = fix.speedKmh
            )
        ) {
            offRouteConfirmationGate.reset()
            navigationJob?.cancel()
            mutableState.update {
                it.copy(
                    currentLocation = coordinate,
                    speedKmh = fix.speedKmh.toInt().coerceIn(0, 240),
                    bearingDegrees = fix.bearingDegrees,
                    navigationActive = false,
                    arrivalDetected = true,
                    parkingHandoffAvailable = it.parkingFinalDestination != null,
                    offRoute = false,
                    remainingDistanceMeters = 0.0,
                    remainingSeconds = 0.0,
                    message = if (it.parkingFinalDestination != null) {
                        "به پارکینگ رسیدید؛ برای ادامه تا مقصد، مسیر پیاده را شروع کنید"
                    } else {
                        "به مقصد رسیدید"
                    }
                )
            }
            return
        }
        val speedLimit = route.maneuvers.getOrNull(progress.maneuverIndex)?.speedLimitKmh
            ?: route.speedLimitsKmh.firstOrNull()
        mutableState.update {
            it.copy(
                currentLocation = coordinate,
                locationAccuracyMeters = fix.accuracyMeters,
                speedKmh = fix.speedKmh.toInt().coerceIn(0, 240),
                bearingDegrees = fix.bearingDegrees,
                navigationZoomLevel = if (it.cameraAutomatic) {
                    NavigationCameraPolicy.zoomLevel(fix.speedKmh.toInt(), progress.distanceToManeuverMeters)
                } else it.navigationZoomLevel,
                maneuverIndex = progress.maneuverIndex,
                distanceToNextManeuverMeters = progress.distanceToManeuverMeters,
                remainingDistanceMeters = progress.remainingDistanceMeters,
                remainingSeconds = progress.remainingSeconds,
                speedLimitKmh = speedLimit,
                offRoute = progress.offRoute,
                message = if (progress.offRoute) "از مسیر خارج شده‌اید؛ در حال بررسی مسیر جدید…" else null
            )
        }

        val confirmedOffRoute = offRouteConfirmationGate.observe(
            offRoute = progress.offRoute,
            gpsAccuracyMeters = fix.accuracyMeters,
            mapMatchConfidence = matched?.confidence
        )
        val now = System.currentTimeMillis()
        val needsImmediateCheck = confirmedOffRoute
        val needsPeriodicCheck = now - lastContinuousRerouteCheckAt >= CONTINUOUS_REROUTE_INTERVAL_MS
        if (needsImmediateCheck || needsPeriodicCheck) {
            lastContinuousRerouteCheckAt = now
            val rerouteState = mutableState.value
            val destination = rerouteState.destination
            if (destination != null && (rerouteState.onlineAvailable || rerouteState.offlineReady)) {
                val check = runCatching {
                    navigationPlatform.continuousRerouteEngine.check(
                        ContinuousRerouteEngine.CheckRequest(
                            currentPosition = coordinate,
                            destination = destination.coordinate,
                            currentRoute = route,
                            currentRemainingSeconds = progress.remainingSeconds,
                            previousTrafficDelaySeconds = previousTrafficDelaySeconds,
                            onlineAvailable = rerouteState.onlineAvailable,
                            offlineAvailable = rerouteState.offlineReady && router != null,
                            preferOffline = rerouteState.preferOffline,
                            lastRerouteMillis = lastRerouteAt,
                            offRoute = needsImmediateCheck,
                            currentRouteBlocked = false,
                            profile = rerouteState.routeProfile,
                            vehicleProfile = rerouteState.vehicleProfile,
                            truck = rerouteState.truckRestrictions,
                            ev = rerouteState.evRoutePreferences
                        )
                    )
                }.getOrNull()
                if (check != null) {
                    previousTrafficDelaySeconds = check.currentTrafficDelaySeconds
                    if (check.decision.shouldReroute && check.replacement != null) {
                        applyContinuousReroute(coordinate, check)
                        lastRerouteAt = now
                        offRouteConfirmationGate.reset()
                        return
                    }
                }
            }
        }

        val shouldRefreshInsights = lastInsightsRemainingMeters.isNaN() ||
            lastInsightsRemainingMeters - progress.remainingDistanceMeters >= INSIGHTS_REFRESH_DISTANCE_METERS
        if (shouldRefreshInsights) {
            lastInsightsRemainingMeters = progress.remainingDistanceMeters
            RoutePointSampler.remainingRoute(route, coordinate)?.let { remainingRoute ->
                insightsRefreshJob?.cancel()
                insightsRefreshJob = viewModelScope.launch {
                    loadRouteNotices(remainingRoute, route)
                }
            }
        }
    }

    private suspend fun applyContinuousReroute(
        coordinate: Coordinate,
        check: ContinuousRerouteEngine.Result
    ) {
        val candidate = check.replacement ?: return
        val replacement = RouteOriginConnector.attach(coordinate, candidate.route)
        val reason = when (check.decision.reason) {
            ContinuousReroutePolicy.Reason.OFF_ROUTE -> "خروج از مسیر"
            ContinuousReroutePolicy.Reason.BLOCKED -> "مسدودی مسیر"
            ContinuousReroutePolicy.Reason.TRAFFIC_INCREASE -> "افزایش ترافیک"
            ContinuousReroutePolicy.Reason.BETTER_ROUTE -> "مسیر سریع‌تر"
            null -> "شرایط مسیر"
        }
        mutableState.update {
            it.copy(
                route = replacement,
                routeAlternatives = listOf(replacement),
                selectedRouteIndex = 0,
                routeSource = candidate.source,
                maneuverIndex = 0,
                distanceToNextManeuverMeters = replacement.maneuvers.firstOrNull()?.distanceMeters
                    ?: replacement.distanceMeters,
                remainingDistanceMeters = replacement.distanceMeters,
                remainingSeconds = replacement.travelSeconds,
                speedLimitKmh = replacement.maneuvers.firstOrNull()?.speedLimitKmh,
                offRoute = false,
                arrivalDetected = false,
                cameraAutomatic = true,
                followNavigation = true,
                traffic = candidate.traffic,
                trafficSegments = emptyList(),
                message = "مسیر به‌دلیل $reason بهینه شد"
            )
        }
        lastInsightsRemainingMeters = replacement.distanceMeters
        loadRouteNotices(replacement, replacement)
    }

    private suspend fun rerouteFrom(coordinate: Coordinate) {
        val snapshot = mutableState.value
        val destination = snapshot.destination ?: return
        val request = RouteRequest(
            origin = coordinate,
            destination = destination.coordinate,
            profile = snapshot.routeProfile,
            vehicleProfile = snapshot.vehicleProfile,
            preferOffline = snapshot.preferOffline,
            onlineAvailable = snapshot.onlineAvailable,
            offlineAvailable = snapshot.offlineReady && router != null,
            truck = snapshot.truckRestrictions,
            ev = snapshot.evRoutePreferences
        )
        val plan = runCatching { navigationPlatform.routeCoordinator.plan(request) }.getOrNull() ?: return
        val candidate = plan.selected ?: return
        val replacement = RouteOriginConnector.attach(coordinate, candidate.route)
        val alternatives = plan.candidates.map { RouteOriginConnector.attach(coordinate, it.route) }
        mutableState.update {
            it.copy(
                route = replacement,
                routeAlternatives = alternatives,
                selectedRouteIndex = 0,
                routeSource = candidate.source,
                maneuverIndex = 0,
                distanceToNextManeuverMeters = replacement.maneuvers.firstOrNull()?.distanceMeters
                    ?: replacement.distanceMeters,
                remainingDistanceMeters = replacement.distanceMeters,
                remainingSeconds = replacement.travelSeconds,
                speedLimitKmh = replacement.maneuvers.firstOrNull()?.speedLimitKmh,
                offRoute = false,
                arrivalDetected = false,
                cameraAutomatic = true,
                followNavigation = true,
                traffic = candidate.traffic,
                trafficSegments = emptyList(),
                message = if (plan.fallbackUsed) {
                    "مسیر با موقعیت جدید و منبع جایگزین اصلاح شد"
                } else {
                    "مسیر با موقعیت جدید اصلاح شد"
                }
            )
        }
        lastInsightsRemainingMeters = replacement.distanceMeters
        loadRouteNotices(replacement, replacement)
    }

    override fun onCleared() {
        downloadMonitor?.cancel()
        searchJob?.cancel()
        navigationJob?.cancel()
        insightsRefreshJob?.cancel()
        places?.close()
        graph?.close()
        nearbyDiscoveryUseCase.close()
        networkMonitor.close()
        super.onCleared()
    }

    private companion object {
        const val CURRENT_LOCATION_CODE = -9_000_000_001L
        const val DEVICE_LOCATION_CATEGORY = "device:location"
        const val DEVICE_LOCATION_SNAPSHOT_CATEGORY = "device:location:snapshot"
        const val MIN_NAVIGATION_ZOOM = 15
        const val DEFAULT_NAVIGATION_ZOOM = 18
        const val MAX_NAVIGATION_ZOOM = 19
        const val INSIGHTS_REFRESH_DISTANCE_METERS = 2_500.0
        const val CONTINUOUS_REROUTE_INTERVAL_MS = 30_000L
        const val MIN_MAP_MATCH_CONFIDENCE = 0.35
    }
}

package ir.nv.navigation.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.RouteSource
import ir.nv.navigation.core.TrafficSummary
import ir.nv.navigation.data.PersonalPlaceStore
import ir.nv.navigation.data.IranCityIndex
import ir.nv.navigation.data.PersianText
import ir.nv.navigation.data.PlaceCodes
import ir.nv.navigation.data.PlaceRepository
import ir.nv.navigation.data.RecentPlaceStore
import ir.nv.navigation.entitlement.TrialManager
import ir.nv.navigation.map.IranPackManager
import ir.nv.navigation.location.DeviceLocationProvider
import ir.nv.navigation.location.NavigationFix
import ir.nv.navigation.network.NetworkMonitor
import ir.nv.navigation.online.OnlineNavigationService
import ir.nv.navigation.routing.AStarRouter
import ir.nv.navigation.routing.NavigationModeResolver
import ir.nv.navigation.routing.RouteProgressEngine
import ir.nv.navigation.routing.SqliteRoutingGraph
import ir.nv.navigation.weather.WeatherAlertService
import ir.nv.navigation.traffic.LiveTrafficService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val recentPlaces: List<Place> = emptyList(),
    val navigationActive: Boolean = false,
    val voiceEnabled: Boolean = true,
    val locating: Boolean = false,
    val currentLocation: Coordinate? = null,
    val speedKmh: Int = 0,
    val bearingDegrees: Float = 0f,
    val maneuverIndex: Int = 0,
    val distanceToNextManeuverMeters: Double = 0.0,
    val remainingDistanceMeters: Double = 0.0,
    val remainingSeconds: Double = 0.0,
    val offRoute: Boolean = false,
    val routeNotices: List<RouteNotice> = emptyList(),
    val traffic: TrafficSummary? = null,
    val routing: Boolean = false,
    val message: String? = null,
    val onlineAvailable: Boolean = false,
    val offlineReady: Boolean = false,
    val preferOffline: Boolean = false,
    val routeSource: RouteSource = RouteSource.NONE,
    val trialState: TrialManager.State = TrialManager.State.Trial(30)
)

class NvViewModel(application: Application) : AndroidViewModel(application) {
    private val packManager = IranPackManager(application)
    private val personalPlaces = PersonalPlaceStore(application)
    private val recentPlaces = RecentPlaceStore(application)
    private val online = OnlineNavigationService()
    private val networkMonitor = NetworkMonitor(application)
    private val locationProvider = DeviceLocationProvider(application)
    private val trialManager = TrialManager(application)
    private val weatherAlerts = WeatherAlertService()
    private val liveTraffic = LiveTrafficService()
    private val mutableState = MutableStateFlow(
        NvUiState(
            packStatus = packManager.status(),
            onlineAvailable = networkMonitor.isOnline(),
            offlineReady = packManager.isReady(),
            recentPlaces = recentPlaces.all(),
            trialState = runCatching { trialManager.state() }.getOrDefault(TrialManager.State.Trial(30))
        )
    )
    val state: StateFlow<NvUiState> = mutableState.asStateFlow()

    private var places: PlaceRepository? = null
    private var graph: SqliteRoutingGraph? = null
    private var router: AStarRouter? = null
    private var downloadMonitor: Job? = null
    private var searchJob: Job? = null
    private var navigationJob: Job? = null
    private var offRouteSamples = 0
    private var lastRerouteAt = 0L

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
            }
        }
        if (packManager.isReady()) viewModelScope.launch { openDataPack() }
        else if (packManager.status() !is IranPackManager.Status.NotStarted) monitorDownload()
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
        mutableState.update {
            it.copy(
                packStatus = IranPackManager.Status.NotStarted,
                offlineReady = false,
                preferOffline = false,
                message = "نقشه آفلاین حذف شد"
            )
        }
    }

    fun setPreferOffline(value: Boolean) {
        if (value && !packManager.isReady()) {
            mutableState.update { it.copy(message = "ابتدا نقشه آفلاین را دانلود کنید") }
        } else {
            mutableState.update { it.copy(preferOffline = value, message = null) }
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
                routeNotices = emptyList()
            )
        }
        viewModelScope.launch { loadRouteNotices(selected) }
    }

    fun swapEndpoints() {
        navigationJob?.cancel()
        mutableState.update {
            it.copy(
                origin = it.destination,
                destination = it.origin,
                originQuery = it.destination?.name.orEmpty(),
                destinationQuery = it.origin?.name.orEmpty(),
                originSuggestions = emptyList(),
                destinationSuggestions = emptyList(),
                route = null,
                routeAlternatives = emptyList(),
                selectedRouteIndex = 0,
                navigationActive = false,
                maneuverIndex = 0,
                offRoute = false,
                routeSource = RouteSource.NONE,
                routeNotices = emptyList()
            )
        }
    }

    fun clearRoute() {
        navigationJob?.cancel()
        mutableState.update {
            it.copy(
                route = null,
                routeAlternatives = emptyList(),
                selectedRouteIndex = 0,
                navigationActive = false,
                maneuverIndex = 0,
                offRoute = false,
                routeSource = RouteSource.NONE,
                routeNotices = emptyList(),
                traffic = null
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
        offRouteSamples = 0
        mutableState.update {
            it.copy(
                navigationActive = true,
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

    fun toggleVoice() {
        mutableState.update { it.copy(voiceEnabled = !it.voiceEnabled) }
    }

    fun useCurrentLocationAsOrigin() {
        if (!locationProvider.hasPermission()) {
            mutableState.update { it.copy(message = "دسترسی موقعیت مکانی داده نشده است") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(locating = true, message = null) }
            val coordinate = withTimeoutOrNull(12_000L) { locationProvider.currentLocation() }
            if (coordinate == null) {
                mutableState.update { it.copy(locating = false, message = "موقعیت فعلی پیدا نشد؛ GPS را روشن کنید") }
            } else {
                val place = Place(
                    code = CURRENT_LOCATION_CODE,
                    name = "موقعیت فعلی من",
                    coordinate = coordinate,
                    category = "device:location"
                )
                mutableState.update {
                    it.copy(
                        locating = false,
                        currentLocation = coordinate,
                        origin = place,
                        originQuery = place.name,
                        originSuggestions = emptyList(),
                        message = null
                    )
                }
            }
        }
    }

    fun savePersonalCode(place: Place, code: String) {
        val result = personalPlaces.save(code, place.name, place.coordinate)
        mutableState.update { it.copy(message = result.exceptionOrNull()?.message ?: "کد شخصی «${code.trim()}» ذخیره شد") }
    }

    fun calculateRoute() {
        val snapshot = mutableState.value
        val origin = snapshot.origin
        val destination = snapshot.destination
        if (origin == null || destination == null) {
            mutableState.update { it.copy(message = "ابتدا مبدأ و مقصد را انتخاب کنید") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(routing = true, message = null) }
            val preferredSource = NavigationModeResolver.preferredSource(
                onlineAvailable = snapshot.onlineAvailable,
                offlineReady = snapshot.offlineReady,
                preferOffline = snapshot.preferOffline
            )
            var source = RouteSource.NONE
            var onlineError: String? = null
            val results: List<Route> = if (preferredSource == RouteSource.OFFLINE) {
                val activeRouter = router
                if (activeRouter == null) emptyList() else listOfNotNull(withContext(Dispatchers.Default) {
                    activeRouter.route(origin.coordinate, destination.coordinate)
                }).also { if (it.isNotEmpty()) source = RouteSource.OFFLINE }
            } else if (preferredSource == RouteSource.ONLINE) {
                runCatching { online.routes(origin.coordinate, destination.coordinate) }
                    .onFailure { onlineError = it.message }
                    .getOrNull()?.takeIf { it.isNotEmpty() }
                    ?.also { source = RouteSource.ONLINE }
                    ?: router?.let { r ->
                        listOfNotNull(withContext(Dispatchers.Default) { r.route(origin.coordinate, destination.coordinate) })
                            .also { if (it.isNotEmpty()) source = RouteSource.OFFLINE }
                    }.orEmpty()
            } else emptyList()
            val result = results.firstOrNull()

            mutableState.update {
                it.copy(
                    routing = false,
                    route = result,
                    routeAlternatives = results,
                    selectedRouteIndex = 0,
                    navigationActive = false,
                    maneuverIndex = 0,
                    distanceToNextManeuverMeters = result?.maneuvers?.firstOrNull()?.distanceMeters
                        ?: result?.distanceMeters ?: 0.0,
                    remainingDistanceMeters = result?.distanceMeters ?: 0.0,
                    remainingSeconds = result?.travelSeconds ?: 0.0,
                    offRoute = false,
                    routeSource = source,
                    routeNotices = emptyList(),
                    traffic = null,
                    message = when {
                        result != null -> null
                        source == RouteSource.OFFLINE && snapshot.onlineAvailable -> "سرویس آنلاین پاسخ نداد؛ مسیر با داده آفلاین محاسبه شد"
                        !snapshot.onlineAvailable && !packManager.isReady() -> "اینترنت در دسترس نیست و نقشه آفلاین دانلود نشده است"
                        onlineError != null -> onlineError
                        else -> "برای این دو نقطه مسیر پیدا نشد"
                    }
                )
            }
            result?.let { loadRouteNotices(it) }
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
                val personal = personalPlaces.search(query)
                val local = places?.search(query).orEmpty()
                val cities = IranCityIndex.search(query)
                combineSearchResults(personal + local + cities)
            }
            mutableState.update {
                if (origin) {
                    it.copy(originSuggestions = immediate, originSearching = false, searchMessage = null)
                } else {
                    it.copy(destinationSuggestions = immediate, destinationSearching = false, searchMessage = null)
                }
            }

            val needsOnline = query.trim().length >= 2 && PlaceCodes.publicCode(query) == null &&
                networkMonitor.isOnline() && !mutableState.value.preferOffline
            if (!needsOnline) return@launch
            delay(220)
            mutableState.update {
                if (origin) it.copy(originSearching = true) else it.copy(destinationSearching = true)
            }
            val remoteResult = withContext(Dispatchers.IO) { runCatching { online.search(query) } }
            val activeQuery = if (origin) mutableState.value.originQuery else mutableState.value.destinationQuery
            if (activeQuery != query) return@launch
            val combined = combineSearchResults(immediate + remoteResult.getOrDefault(emptyList()))
            mutableState.update {
                val warning = remoteResult.exceptionOrNull()?.let {
                    if (immediate.isEmpty()) "جست‌وجوی آنلاین پاسخ نداد؛ اتصال اینترنت را بررسی کنید" else null
                }
                if (origin) {
                    it.copy(originSuggestions = combined, originSearching = false, searchMessage = warning)
                } else {
                    it.copy(destinationSuggestions = combined, destinationSearching = false, searchMessage = warning)
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
        }.onSuccess {
            mutableState.update { it.copy(packStatus = IranPackManager.Status.Ready, offlineReady = true) }
        }.onFailure { error ->
            mutableState.update { it.copy(packStatus = IranPackManager.Status.Failed(error.message ?: "داده نامعتبر"), offlineReady = false) }
        }
    }

    fun mapFile() = packManager.mapFile

    private suspend fun loadRouteNotices(route: Route) {
        val notices = withContext(Dispatchers.IO) {
            val attractions = places?.noticesAlong(route).orEmpty()
            val weather = if (networkMonitor.isOnline()) {
                runCatching { weatherAlerts.alertsAhead(route) }.getOrDefault(emptyList())
            } else emptyList()
            (weather + attractions).sortedBy { it.distanceAheadMeters }.take(8)
        }
        val traffic = if (networkMonitor.isOnline()) {
            withContext(Dispatchers.IO) { runCatching { liveTraffic.summary(route) }.getOrNull() }
        } else null
        mutableState.update { state ->
            if (state.route === route) state.copy(routeNotices = notices, traffic = traffic) else state
        }
    }

    private suspend fun updateNavigationProgress(fix: NavigationFix) {
        val coordinate = fix.coordinate
        val snapshot = mutableState.value
        val route = snapshot.route ?: return
        val progress = RouteProgressEngine.calculate(route, coordinate) ?: return
        mutableState.update {
            it.copy(
                currentLocation = coordinate,
                speedKmh = fix.speedKmh.toInt().coerceIn(0, 240),
                bearingDegrees = fix.bearingDegrees,
                maneuverIndex = progress.maneuverIndex,
                distanceToNextManeuverMeters = progress.distanceToManeuverMeters,
                remainingDistanceMeters = progress.remainingDistanceMeters,
                remainingSeconds = progress.remainingSeconds,
                offRoute = progress.offRoute,
                message = if (progress.offRoute) "از مسیر خارج شده‌اید؛ در حال بررسی مسیر جدید…" else null
            )
        }

        offRouteSamples = if (progress.offRoute) offRouteSamples + 1 else 0
        val now = System.currentTimeMillis()
        if (offRouteSamples >= 3 && now - lastRerouteAt >= 30_000L) {
            lastRerouteAt = now
            rerouteFrom(coordinate)
            offRouteSamples = 0
        }
    }

    private suspend fun rerouteFrom(coordinate: Coordinate) {
        val snapshot = mutableState.value
        val destination = snapshot.destination ?: return
        val replacement = if (snapshot.onlineAvailable) {
            runCatching { online.route(coordinate, destination.coordinate) }.getOrNull()
        } else {
            router?.let { active ->
                withContext(Dispatchers.Default) { active.route(coordinate, destination.coordinate) }
            }
        } ?: return
        mutableState.update {
            it.copy(
                route = replacement,
                routeAlternatives = listOf(replacement),
                selectedRouteIndex = 0,
                routeSource = if (snapshot.onlineAvailable) RouteSource.ONLINE else RouteSource.OFFLINE,
                maneuverIndex = 0,
                distanceToNextManeuverMeters = replacement.maneuvers.firstOrNull()?.distanceMeters
                    ?: replacement.distanceMeters,
                remainingDistanceMeters = replacement.distanceMeters,
                remainingSeconds = replacement.travelSeconds,
                offRoute = false,
                message = "مسیر با موقعیت جدید اصلاح شد"
            )
        }
        loadRouteNotices(replacement)
    }

    override fun onCleared() {
        downloadMonitor?.cancel()
        searchJob?.cancel()
        navigationJob?.cancel()
        places?.close()
        graph?.close()
        networkMonitor.close()
        super.onCleared()
    }

    private companion object {
        const val CURRENT_LOCATION_CODE = -9_000_000_001L
    }
}

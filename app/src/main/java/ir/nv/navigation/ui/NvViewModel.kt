package ir.nv.navigation.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.TrafficSummary
import ir.nv.navigation.data.PersonalPlaceStore
import ir.nv.navigation.data.PlaceRepository
import ir.nv.navigation.entitlement.TrialManager
import ir.nv.navigation.map.IranPackManager
import ir.nv.navigation.online.OnlineNavigationService
import ir.nv.navigation.routing.AStarRouter
import ir.nv.navigation.routing.SqliteRoutingGraph
import ir.nv.navigation.weather.WeatherAlertService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NvUiState(
    val packStatus: IranPackManager.Status = IranPackManager.Status.NotStarted,
    val originQuery: String = "",
    val destinationQuery: String = "",
    val originSuggestions: List<Place> = emptyList(),
    val destinationSuggestions: List<Place> = emptyList(),
    val origin: Place? = null,
    val destination: Place? = null,
    val route: Route? = null,
    val routeNotices: List<RouteNotice> = emptyList(),
    val traffic: TrafficSummary? = null,
    val routing: Boolean = false,
    val message: String? = null,
    val offlineReady: Boolean = false,
    val preferOffline: Boolean = false,
    val trialState: TrialManager.State = TrialManager.State.Trial(30)
)

class NvViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val packManager = IranPackManager(application)
    private val personalPlaces = PersonalPlaceStore(application)
    private val online = OnlineNavigationService()
    private val trialManager = TrialManager(application)
    private val weatherAlerts = WeatherAlertService()
    private val mutableState = MutableStateFlow(
        NvUiState(
            packStatus = packManager.status(),
            offlineReady = packManager.isReady(),
            trialState = runCatching { trialManager.state() }.getOrDefault(TrialManager.State.Trial(30))
        )
    )
    val state: StateFlow<NvUiState> = mutableState.asStateFlow()

    private var places: PlaceRepository? = null
    private var graph: SqliteRoutingGraph? = null
    private var router: AStarRouter? = null
    private var downloadMonitor: Job? = null
    private var searchJob: Job? = null

    init {
        if (packManager.isReady()) viewModelScope.launch { openDataPack() }
        else if (packManager.status() !is IranPackManager.Status.NotStarted) monitorDownload()
    }

    fun startMapDownload() {
        mutableState.update { it.copy(message = null) }
        packManager.startDownload()
        monitorDownload()
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
        mutableState.update { it.copy(destination = place, destinationQuery = place.name, destinationSuggestions = emptyList()) }
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
            val useOffline = snapshot.preferOffline || !isOnline()
            val result: Route? = if (useOffline) {
                val activeRouter = router
                if (activeRouter == null) null else withContext(Dispatchers.Default) {
                    activeRouter.route(origin.coordinate, destination.coordinate)
                }
            } else {
                runCatching { online.route(origin.coordinate, destination.coordinate) }.getOrNull()
                    ?: router?.let { r -> withContext(Dispatchers.Default) { r.route(origin.coordinate, destination.coordinate) } }
            }

            val notices = if (result == null) emptyList() else withContext(Dispatchers.IO) {
                val attractions = places?.attractionsAlong(result).orEmpty()
                val weather = if (isOnline()) runCatching { weatherAlerts.alertsAhead(result) }.getOrDefault(emptyList()) else emptyList()
                (weather + attractions).sortedBy { it.distanceAheadMeters }.take(8)
            }
            mutableState.update {
                it.copy(
                    routing = false,
                    route = result,
                    routeNotices = notices,
                    traffic = null,
                    message = when {
                        result != null -> null
                        !isOnline() && !packManager.isReady() -> "اینترنت در دسترس نیست و نقشه آفلاین دانلود نشده است"
                        else -> "برای این دو نقطه مسیر پیدا نشد"
                    }
                )
            }
        }
    }

    private fun search(query: String, origin: Boolean) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            mutableState.update { if (origin) it.copy(originSuggestions = emptyList()) else it.copy(destinationSuggestions = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val results = withContext(Dispatchers.IO) {
                val personal = personalPlaces.search(query)
                val local = places?.search(query).orEmpty()
                val remote = if (isOnline() && !mutableState.value.preferOffline) {
                    runCatching { online.search(query) }.getOrDefault(emptyList())
                } else emptyList()
                (personal + remote + local).distinctBy { Triple(it.name, it.coordinate.latitude, it.coordinate.longitude) }.take(30)
            }
            mutableState.update { if (origin) it.copy(originSuggestions = results) else it.copy(destinationSuggestions = results) }
        }
    }

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

    fun isOnline(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun onCleared() {
        downloadMonitor?.cancel(); searchJob?.cancel(); places?.close(); graph?.close(); super.onCleared()
    }
}

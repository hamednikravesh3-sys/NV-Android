package ir.nv.navigation.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.nv.navigation.core.Place
import ir.nv.navigation.core.Route
import ir.nv.navigation.core.RouteNotice
import ir.nv.navigation.core.TrafficSummary
import ir.nv.navigation.data.PlaceRepository
import ir.nv.navigation.entitlement.TrialManager
import ir.nv.navigation.map.IranPackManager
import ir.nv.navigation.routing.AStarRouter
import ir.nv.navigation.routing.SqliteRoutingGraph
import ir.nv.navigation.weather.WeatherAlertService
import kotlinx.coroutines.Dispatchers
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
    val trialState: TrialManager.State = TrialManager.State.Trial(30)
)

class NvViewModel(application: Application) : AndroidViewModel(application) {
    private val packManager = IranPackManager(application)
    private val trialManager = TrialManager(application)
    private val weatherAlerts = WeatherAlertService()
    private val mutableState = MutableStateFlow(
        NvUiState(trialState = runCatching { trialManager.state() }
            .getOrDefault(TrialManager.State.Tampered))
    )
    val state: StateFlow<NvUiState> = mutableState.asStateFlow()

    private var places: PlaceRepository? = null
    private var graph: SqliteRoutingGraph? = null
    private var router: AStarRouter? = null

    init {
        viewModelScope.launch {
            if (!packManager.isReady()) packManager.ensureDownloadStarted()
            while (isActive && !packManager.isReady()) {
                val status = packManager.status()
                mutableState.update { it.copy(packStatus = status) }
                if (status is IranPackManager.Status.Installing) {
                    val result = packManager.installDownloadedPack()
                    if (result.isFailure) {
                        mutableState.update {
                            it.copy(
                                packStatus = IranPackManager.Status.Failed(
                                    result.exceptionOrNull()?.message ?: "نصب بسته ناموفق بود"
                                )
                            )
                        }
                        break
                    }
                }
                if (status is IranPackManager.Status.Failed) break
                delay(1_000)
            }
            if (packManager.isReady()) openDataPack()
        }
    }

    fun retryDownload() {
        mutableState.update { it.copy(message = null) }
        packManager.retry()
        viewModelScope.launch {
            while (isActive && !packManager.isReady()) {
                val status = packManager.status()
                mutableState.update { it.copy(packStatus = status) }
                if (status is IranPackManager.Status.Installing) {
                    val result = packManager.installDownloadedPack()
                    if (result.isFailure) {
                        mutableState.update {
                            it.copy(
                                packStatus = IranPackManager.Status.Failed(
                                    result.exceptionOrNull()?.message ?: "نصب بسته ناموفق بود"
                                )
                            )
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
            .getOrDefault(TrialManager.State.Tampered)
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
        mutableState.update {
            it.copy(origin = place, originQuery = place.displayName, originSuggestions = emptyList())
        }
    }

    fun selectDestination(place: Place) {
        mutableState.update {
            it.copy(
                destination = place,
                destinationQuery = place.displayName,
                destinationSuggestions = emptyList()
            )
        }
    }

    fun calculateRoute() {
        val snapshot = mutableState.value
        val origin = snapshot.origin
        val destination = snapshot.destination
        if (origin == null || destination == null) {
            mutableState.update { it.copy(message = "ابتدا مبدأ و مقصد را انتخاب کنید") }
            return
        }
        val activeRouter = router ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(routing = true, message = null) }
            val result = withContext(Dispatchers.Default) {
                activeRouter.route(origin.coordinate, destination.coordinate)
            }
            val notices = if (result == null) emptyList() else withContext(Dispatchers.IO) {
                val attractions = places?.attractionsAlong(result).orEmpty()
                val weather = runCatching { weatherAlerts.alertsAhead(result) }.getOrDefault(emptyList())
                (weather + attractions).sortedBy { notice -> notice.distanceAheadMeters }.take(8)
            }
            mutableState.update {
                it.copy(
                    routing = false,
                    route = result,
                    routeNotices = notices,
                    traffic = null,
                    message = if (result == null) "برای این دو نقطه مسیر پیدا نشد" else null
                )
            }
        }
    }

    private fun search(query: String, origin: Boolean) {
        if (query.length < 2 && query.toLongOrNull() == null) {
            mutableState.update {
                if (origin) it.copy(originSuggestions = emptyList())
                else it.copy(destinationSuggestions = emptyList())
            }
            return
        }
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                places?.search(query).orEmpty()
            }
            mutableState.update {
                if (origin) it.copy(originSuggestions = results)
                else it.copy(destinationSuggestions = results)
            }
        }
    }

    private suspend fun openDataPack() = withContext(Dispatchers.IO) {
        runCatching {
            places?.close()
            graph?.close()
            places = PlaceRepository(packManager.placesFile)
            graph = SqliteRoutingGraph(packManager.routingFile)
            router = AStarRouter(requireNotNull(graph))
        }.onSuccess {
            mutableState.update { it.copy(packStatus = IranPackManager.Status.Ready) }
        }.onFailure { error ->
            mutableState.update {
                it.copy(packStatus = IranPackManager.Status.Failed(error.message ?: "داده نامعتبر"))
            }
        }
    }

    fun mapFile() = packManager.mapFile

    override fun onCleared() {
        places?.close()
        graph?.close()
        super.onCleared()
    }
}

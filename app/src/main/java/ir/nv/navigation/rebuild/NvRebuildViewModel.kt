package ir.nv.navigation.rebuild

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.nv.navigation.data.PlaceRepository
import ir.nv.navigation.map.IranPackManager
import ir.nv.navigation.network.NetworkMonitor
import ir.nv.navigation.online.OnlineNavigationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NvRebuildViewModel(app: Application) : AndroidViewModel(app) {
    private val network = NetworkMonitor(app)
    private val pack = IranPackManager(app)
    private val onlineSearch = NvHybridSearchEngine()
    private val router = OnlineNavigationService()
    private var localPlaces: PlaceRepository? = null
    private var searchJob: Job? = null
    private val mutable = MutableStateFlow(NvRebuildState(online = network.isOnline(), offlineReady = pack.isReady()))
    val state: StateFlow<NvRebuildState> = mutable.asStateFlow()

    init {
        if (pack.isReady()) runCatching { localPlaces = PlaceRepository(pack.placesFile) }
        viewModelScope.launch {
            network.online.collect { value -> mutable.update { it.copy(online = value) } }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        mutable.update { it.copy(searchQuery = query, searching = query.trim().length >= 2) }
        if (query.trim().length < 2) {
            mutable.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(160)
            val snapshot = mutable.value
            val results = withContext(Dispatchers.IO) {
                val offline = localPlaces?.search(query, 20).orEmpty().map {
                    NvSearchResult("offline:${it.code}", it.name, "کد NV ${it.code}", it.coordinate, it.category, SearchSource.OFFLINE, it.code.toString())
                }
                val online = if (snapshot.online) onlineSearch.searchOnline(query, 24) else emptyList()
                (online + offline).distinctBy { "${it.title}|${it.coordinate.latitude}|${it.coordinate.longitude}" }.take(30)
            }
            mutable.update { it.copy(searchResults = results, searching = false, message = if (results.isEmpty()) "نتیجه‌ای پیدا نشد" else null) }
        }
    }

    fun chooseDestination(result: NvSearchResult) {
        mutable.update { it.copy(destination = result, searchQuery = result.title, searchResults = emptyList(), screen = NvScreen.ROUTE_SELECTION, message = null) }
    }

    fun useCurrentLocationAsOrigin() {
        // Location wiring is added in the next rebuild layer; keeping screen state clean here.
        mutable.update { it.copy(message = "موقعیت فعلی از GPS در مرحله بعدی متصل می‌شود") }
    }

    fun calculateRoutes() {
        val s = mutable.value
        val origin = s.origin
        val destination = s.destination
        if (origin == null || destination == null) {
            mutable.update { it.copy(message = "مبدأ و مقصد را انتخاب کنید") }
            return
        }
        viewModelScope.launch {
            val routes = runCatching { router.routes(origin.coordinate, destination.coordinate) }.getOrDefault(emptyList())
            val options = routes.mapIndexed { index, route ->
                NvRouteOption("route-$index", route.distanceMeters, route.travelSeconds, label = if (index == 0) "پیشنهادی" else "مسیر ${index + 1}", recommended = index == 0)
            }
            mutable.update { it.copy(routeOptions = options, selectedRouteId = options.firstOrNull()?.id, screen = NvScreen.ROUTE_SELECTION, message = if (options.isEmpty()) "مسیر پیدا نشد" else null) }
        }
    }

    fun selectRoute(id: String) = mutable.update { it.copy(selectedRouteId = id) }
    fun startDriving() = mutable.update { it.copy(screen = NvScreen.DRIVING, navigationActive = true) }
    fun stopDriving() = mutable.update { it.copy(screen = NvScreen.ROUTE_SELECTION, navigationActive = false) }
    fun openHome() = mutable.update { it.copy(screen = NvScreen.HOME) }
    fun openExplore() = mutable.update { it.copy(screen = NvScreen.EXPLORE) }

    override fun onCleared() {
        localPlaces?.close()
        network.close()
        super.onCleared()
    }
}

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
import ir.nv.navigation.core.TrafficSegment
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
import ir.nv.navigation.navigation.ContinuousRerouteEngine
import ir.nv.navigation.navigation.ContinuousReroutePolicy
import ir.nv.navigation.navigation.NvNavigationPlatform
import ir.nv.navigation.navigation.OffRouteConfirmationGate
import ir.nv.navigation.navigation.ArrivalConfirmationGate
import ir.nv.navigation.navigation.RouteProfile
import ir.nv.navigation.navigation.RouteRequest
import ir.nv.navigation.navigation.VehicleProfile
import ir.nv.navigation.navigation.TruckRestrictions
import ir.nv.navigation.navigation.EvRoutePreferences
import ir.nv.navigation.navigation.mapmatching.RawLocationSample
import ir.nv.navigation.network.NetworkMonitor
import ir.nv.navigation.online.OnlineNavigationService
import ir.nv.navigation.online.OnlinePlacesService
import ir.nv.navigation.routing.AStarRouter
import ir.nv.navigation.routing.RouteProgressEngine
import ir.nv.navigation.routing.RouteOriginConnector
import ir.nv.navigation.routing.NavigationCameraPolicy
import ir.nv.navigation.routing.RoutePointSampler
import ir.nv.navigation.routing.SqliteRoutingGraph
import ir.nv.navigation.search.HybridSearchEngine
import ir.nv.navigation.search.PlaceSearchProvider
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
    val recentPlaces: List<Place> = emptyList(),
    val personalPlaces: List<Place> = emptyList(),
    val navigationActive: Boolean = false,
    val voiceEnabled: Boolean = true,
    val locating: Boolean = false,
    val currentLocation: Coordinate? = null,
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
    val routeProfile: RouteProfile = RouteProfile.SMART,
    val vehicleProfile: VehicleProfile = VehicleProfile.CAR,
    val truckRestrictions: TruckRestrictions = TruckRestrictions(),
    val evPreferences: EvRoutePreferences = EvRoutePreferences(),
    val trialState: TrialManager.State = TrialManager.State.Trial(30)
)

class NvViewModel(application: Application) : AndroidViewModel(application) {
    private val packManager = IranPackManager(application)
    private val personalPlaces = PersonalPlaceStore(application)
    private val recentPlaces = RecentPlaceStore(application)
    private val online = OnlineNavigationService()
    private val onlinePlaces = OnlinePlacesService()
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
            personalPlaces = personalPlaces.all(),
            trialState = runCatching { trialManager.state() }.getOrDefault(TrialManager.State.Trial(30))
        )
    )
    val state: StateFlow<NvUiState> = mutableState.asStateFlow()

    private var places: PlaceRepository? = null
    private var graph: SqliteRoutingGraph? = null
    private var router: AStarRouter? = null
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
    private var insightsRefreshJob: Job? = null
    private val offRouteGate = OffRouteConfirmationGate()
    private val arrivalGate = ArrivalConfirmationGate()
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
                 ²È="25•ÑÕÉ¸(€€€€€€€ô((€€€€€€€Ù…°¹½Ü€ôMåÍÑ•´¹ÕÉÉ•¹ÑQ¥µ•5¥±±¥Ì ¤(€€€€€€€Ù…°¹••‘Í%µµ•‘¥…Ñ•¡•¬€ô½¹™¥Éµ•‘=™™I½ÕÑ”(€€€€€€€Ù…°¹••‘ÍA•É¥½‘¥¡•¬€ô¹½Ü€´±…ÍÑ½¹Ñ¥¹Õ½ÕÍI•É½ÕÑ•¡•­Ğ€øô=9Q%9U=UM}II=UQ}%9QIY1}5L(€€€€€€€¥˜€¡¹••‘Í%µµ•‘¥…Ñ•¡•¬ñğ¹••‘ÍA•É¥½‘¥¡•¬¤ì(€€€€€€€€€€€±…ÍÑ½¹Ñ¥¹Õ½ÕÍI•É½ÕÑ•¡•­Ğ€ô¹½Ü(€€€€€€€€€€€Ù…°É•É½ÕÑ•MÑ…Ñ”€ôµÕÑ…‰±•MÑ…Ñ”¹Ù…±Õ”(€€€€€€€€€€€Ù…°‘•ÍÑ¥¹…Ñ¥½¸€ôÉ•É½ÕÑ•MÑ…Ñ”¹‘•ÍÑ¥¹…Ñ¥½¸(€€€€€€€€€€€¥˜€¡‘•ÍÑ¥¹…Ñ¥½¸€„ô¹Õ±°€˜˜€¡É•É½ÕÑ•MÑ…Ñ”¹½¹±¥¹•Ù…¥±…‰±”ñğÉ•É½ÕÑ•MÑ…Ñ”¹½™™±¥¹•I•…‘ä¤¤ì(€€€€€€€€€€€€€€€Ù…°¡•¬€ôÉÕ¹…Ñ¡¥¹œì(€€€€€€€€€€€€€€€€€€€¹…Ù¥…Ñ¥½¹A±…Ñ™½É´¹½¹Ñ¥¹Õ½ÕÍI•É½ÕÑ•¹¥¹”¹¡•¬ (€€€€€€€€€€€€€€€€€€€€€€€½¹Ñ¥¹Õ½ÕÍI•É½ÕÑ•¹¥¹”¹¡•­I•ÅÕ•ÍĞ (€€€€€€€€€€€€€€€€€€€€€€€€€€€ÕÉÉ•¹ÑA½Í¥Ñ¥½¸€ô½½É‘¥¹…Ñ”°(€€€€€€€€€€€€€€€€€€€€€€€€€€€‘•ÍÑ¥¹…Ñ¥½¸€ô‘•ÍÑ¥¹…Ñ¥½¸¹½½É‘¥¹…Ñ”°(€€€€€€€€€€€€€€€€€€€€€€€€€€€ÕÉÉ•¹ÑI½ÕÑ”€ôÉ½ÕÑ”°(€€€€€€€€€€€€€€€€€€€€€€€€€€€ÕÉÉ•¹ÑI•µ…¥¹¥¹M•½¹‘Ì€ôÁÉ½É•ÍÌ¹É•µ…¥¹¥¹M•½¹‘Ì°(€€€€€€€€€€€€€€€€€€€€€€€€€€€ÁÉ•Ù¥½ÕÍQÉ…™™¥•±…åM•½¹‘Ì€ôÁÉ•Ù¥½ÕÍQÉ…™™¥•±…åM•½¹‘Ì°(€€€€€€€€€€€€€€€€€€€€€€€€€€€½¹±¥¹•Ù…¥±…‰±”€ôÉ•É½ÕÑ•MÑ…Ñ”¹½¹±¥¹•Ù…¥±…‰±”°(€€€€€€€€€€€€€€€€€€€€€€€€€€€½™™±¥¹•Ù…¥±…‰±”€ôÉ•É½ÕÑ•MÑ…Ñ”¹½™™±¥¹•I•…‘ä€˜˜É½ÕÑ•È€„ô¹Õ±°°(€€€€€€€€€€€€€€€€€€€€€€€€€€€ÁÉ•™•É=™™±¥¹”€ôÉ•É½ÕÑ•MÑ…Ñ”¹ÁÉ•™•É=™™±¥¹”°(€€€€€€€€€€€€€€€€€€€€€€€€€€€±…ÍÑI•É½ÕÑ•5¥±±¥Ì€ô±…ÍÑI•É½ÕÑ•Ğ°(€€€€€€€€€€€€€€€€€€€€€€€€€€€½™™I½ÕÑ”€ô¹••‘Í%µµ•‘¥…Ñ•¡•¬°(€€€€€€€€€€€€€€€€€€€€€€€€€€€ÕÉÉ•¹ÑI½ÕÑ•	±½­•€ô™…±Í”°(€€€€€€€€€€€€€€€€€€€€€€€€€€€ÁÉ½™¥±”€ôÉ•É½ÕÑ•MÑ…Ñ”¹É½ÕÑ•AÉ½™¥±”°(€€€€€€€€€€€€€€€€€€€€€€€€€€€Ù•¡¥±•AÉ½™¥±”€ôÉ•É½ÕÑ•MÑ…Ñ”¹Ù•¡¥±•AÉ½™¥±”°(€€€€€€€€€€€€€€€€€€€€€€€€€€€ÑÉÕ¬€ôÉ•É½ÕÑ•MÑ…Ñ”¹ÑÉÕ­I•ÍÑÉ¥Ñ¥½¹Ì°(€€€€€€€€€€€€€€€€€€€€€€€€€€€•Ø€ôÉ•É½ÕÑ•MÑ…Ñ”¹•ÙAÉ•™•É•¹•Ì(€€€€€€€€€€€€€€€€€€€€€€€€¤(€€€€€€€€€€€€€€€€€€€€¤(€€€€€€€€€€€€€€€ô¹•Ñ=É9Õ±° ¤(€€€€€€€€€€€€€€€¥˜€¡¡•¬€„ô¹Õ±°¤ì(€€€€€€€€€€€€€€€€€€€ÁÉ•Ù¥½ÕÍQÉ…™™¥•±…åM•½¹‘Ì€ô¡•¬¹ÕÉÉ•¹ÑQÉ…™™¥•±…åM•½¹‘Ì(€€€€€€€€€€€€€€€€€€€¥˜€¡¡•¬¹‘•¥Í¥½¸¹Í¡½Õ±‘I•É½ÕÑ”€˜˜¡•¬¹É•Á±…•µ•¹Ğ€„ô¹Õ±°¤ì(€€€€€€€€€€€€€€€€€€€€€€€…ÁÁ±å½¹Ñ¥¹Õ½ÕÍI•É½ÕÑ”¡½½É‘¥¹…Ñ”°¡•¬¤(€€€€€€€€€€€€€€€€€€€€€€€±…ÍÑI•É½ÕÑ•Ğ€ô¹½Ü(€€€€€€€€€€€€€€€€€€€€€€€½™™I½ÕÑ•…Ñ”¹É•Í•Ğ ¤(€€€€€€€€€€€€€€€€€€€€€€€…ÉÉ¥Ù…±…Ñ”¹É•Í•Ğ ¤(€€€€€€€€€€€€€€€€€€€€€€€É•ÑÕÉ¸(€€€€€€€€€€€€€€€€€€€ô(€€€€€€€€€€€€€€€ô(€€€€€€€€€€€ô(€€€€€€€ô((€€€€€€€Ù…°Í¡½Õ±‘I•™É•Í¡%¹Í¥¡ÑÌ€ô±…ÍÑ%¹Í¥¡ÑÍI•µ…¥¹¥¹5•Ñ•ÉÌ¹¥Í9…8 ¤ñğ(€€€€€€€€€€€±…ÍÑ%¹Í¥¡ÑÍI•µ…¥¹¥¹5•Ñ•ÉÌ€´ÁÉ½É•ÍÌ¹É•µ…¥¹¥¹¥ÍÑ…¹•5•Ñ•ÉÌ€øô%9M%!QM}IIM!}%MQ9}5QIL(€€€€€€€¥˜€¡Í¡½Õ±‘I•™É•Í¡%¹Í¥¡ÑÌ¤ì(€€€€€€€€€€€±…ÍÑ%¹Í¥¡ÑÍI•µ…¥¹¥¹5•Ñ•ÉÌ€ôÁÉ½É•ÍÌ¹É•µ…¥¹¥¹¥ÍÑ…¹•5•Ñ•ÉÌ(€€€€€€€€€€€I½ÕÑ•A½¥¹ÑM…µÁ±•È¹É•µ…¥¹¥¹I½ÕÑ”¡É½ÕÑ”°½½É‘¥¹…Ñ”¤ü¹±•ĞìÉ•µ…¥¹¥¹I½ÕÑ”€´ø(€€€€€€€€€€€€€€€¥¹Í¥¡ÑÍI•™É•Í¡)½ˆü¹…¹•° ¤(€€€€€€€€€€€€€€€¥¹Í¥¡ÑÍI•™É•Í¡)½ˆ€ôÙ¥•İ5½‘•±M½Á”¹±…Õ¹ ì(€€€€€€€€€€€€€€€€€€€±½…‘I½ÕÑ•9½Ñ¥•Ì¡É•µ…¥¹¥¹I½ÕÑ”°É½ÕÑ”¤(€€€€€€€€€€€€€€€ô(€€€€€€€€€€€ô(€€€€€€€ô(€€€ô((€€€ÁÉ¥Ù…Ñ”ÍÕÍÁ•¹™Õ¸…ÁÁ±å½¹Ñ¥¹Õ½ÕÍI•É½ÕÑ” (€€€€€€€½½É‘¥¹…Ñ”è½½É‘¥¹…Ñ”°(€€€€€€€¡•¬è½¹Ñ¥¹Õ½ÕÍI•É½ÕÑ•¹¥¹”¹I•ÍÕ±Ğ(€€€€¤ì(€€€€€€€Ù…°…¹‘¥‘…Ñ”€ô¡•¬¹É•Á±…•µ•¹Ğ€üèÉ•ÑÕÉ¸(€€€€€€€Ù…°É•Á±…•µ•¹Ğ€ôI½ÕÑ•=É¥¥¹½¹¹•Ñ½È¹…ÑÑ… ¡½½É‘¥¹…Ñ”°…¹‘¥‘…Ñ”¹É½ÕÑ”¤(€€€€€€€Ù…°É•…Í½¸€ôİ¡•¸€¡¡•¬¹‘•¥Í¥½¸¹É•…Í½¸¤ì(€€€€€€€€€€€½¹Ñ¥¹Õ½ÕÍI•É½ÕÑ•A½±¥ä¹I•…Í½¸¹=}I=UQ€´ø€‹b»bÇf#b°ƒbŸbÈƒfbÏn3bÄˆ(€€€€€€€€€€€½¹Ñ¥¹Õ½ÕÍI•É½ÕÑ•A½±¥ä¹I•…Í½¸¹	1=-€´ø€‹fbÏb¿f#b¿n0ƒfbÏn3bÄˆ(€€€€€€€€€€€½¹Ñ¥¹Õ½ÕÍI•É½ÕÑ•A½±¥ä¹I•…Í½¸¹QI%}%9IM€´ø€‹bŸfbËbŸn3bĞƒb«bÇbŸfn3j¤ˆ(€€€€€€€€€€€½¹Ñ¥¹Õ½ÕÍI•É½ÕÑ•A½±¥ä¹I•…Í½¸¹	QQI}I=UQ€´ø€‹fbÏn3bÄƒbÏbÇn3bçŠ3b«bÄˆ(€€€€€€€€€€€¹Õ±°€´ø€‹bÓbÇbŸn3bÜƒfbÏn3bÄˆ(€€€€€€€ô(€€€€€€€µÕÑ…‰±•MÑ…Ñ”¹ÕÁ‘…Ñ”ì(€€€€€€€€€€€¥Ğ¹½Áä (€€€€€€€€€€€€€€€É½ÕÑ”€ôÉ•Á±…•µ•¹Ğ°(€€€€€€€€€€€€€€€É½ÕÑ•±Ñ•É¹…Ñ¥Ù•Ì€ô±¥ÍÑ=˜¡É•Á±…•µ•¹Ğ¤°(€€€€€€€€€€€€€€€Í•±•Ñ•‘I½ÕÑ•%¹‘•à€ô€À°(€€€€€€€€€€€€€€€É½ÕÑ•M½ÕÉ”€ô…¹‘¥‘…Ñ”¹Í½ÕÉ”°(€€€€€€€€€€€€€€€µ…¹•ÕÙ•É%¹‘•à€ô€À°(€€€€€€€€€€€€€€€‘¥ÍÑ…¹•Q½9•áÑ5…¹•ÕÙ•É5•Ñ•ÉÌ€ôÉ•Á±…•µ•¹Ğ¹µ…¹•ÕÙ•ÉÌ¹™¥ÉÍÑ=É9Õ±° ¤ü¹‘¥ÍÑ…¹•5•Ñ•ÉÌ(€€€€€€€€€€€€€€€€€€€€üèÉ•Á±…•µ•¹Ğ¹‘¥ÍÑ…¹•5•Ñ•ÉÌ°(€€€€€€€€€€€€€€€É•µ…¥¹¥¹¥ÍÑ…¹•5•Ñ•ÉÌ€ôÉ•Á±…•µ•¹Ğ¹‘¥ÍÑ…¹•5•Ñ•ÉÌ°(€€€€€€€€€€€€€€€É•µ…¥¹¥¹M•½¹‘Ì€ôÉ•Á±…•µ•¹Ğ¹ÑÉ…Ù•±M•½¹‘Ì°(€€€€€€€€€€€€€€€½™™I½ÕÑ”€ô™…±Í”°(€€€€€€€€€€€€€€€…µ•É…ÕÑ½µ…Ñ¥Œ€ôÑÉÕ”°(€€€€€€€€€€€€€€€™½±±½İ9…Ù¥…Ñ¥½¸€ôÑÉÕ”°(€€€€€€€€€€€€€€€ÑÉ…™™¥Œ€ô…¹‘¥‘…Ñ”¹ÑÉ…™™¥Œ°(€€€€€€€€€€€€€€€ÑÉ…™™¥M•µ•¹ÑÌ€ô•µÁÑå1¥ÍĞ ¤°(€€€€€€€€€€€€€€€µ•ÍÍ…”€ô€‹fbÏn3bÄƒb£fŠ3b¿fn3f€‘É•…Í½¸ƒb£fn3ffƒbÓb¼ˆ(€€€€€€€€€€€€¤(€€€€€€€ô(€€€€€€€±…ÍÑ%¹Í¥¡ÑÍI•µ…¥¹¥¹5•Ñ•ÉÌ€ôÉ•Á±…•µ•¹Ğ¹‘¥ÍÑ…¹•5•Ñ•ÉÌ(€€€€€€€±½…‘I½ÕÑ•9½Ñ¥•Ì¡É•Á±…•µ•¹Ğ°É•Á±…•µ•¹Ğ¤(€€€ô((€€€ÁÉ¥Ù…Ñ”ÍÕÍÁ•¹™Õ¸É•É½ÕÑ•É½´¡½½É‘¥¹…Ñ”è½½É‘¥¹…Ñ”¤ì(€€€€€€€Ù…°Í¹…ÁÍ¡½Ğ€ôµÕÑ…‰±•MÑ…Ñ”¹Ù…±Õ”(€€€€€€€Ù…°‘•ÍÑ¥¹…Ñ¥½¸€ôÍ¹…ÁÍ¡½Ğ¹‘•ÍÑ¥¹…Ñ¥½¸€üèÉ•ÑÕÉ¸(€€€€€€€Ù…°É•ÅÕ•ÍĞ€ôI½ÕÑ•I•ÅÕ•ÍĞ (€€€€€€€€€€€½É¥¥¸€ô½½É‘¥¹…Ñ”°(€€€€€€€€€€€‘•ÍÑ¥¹…Ñ¥½¸€ô‘•ÍÑ¥¹…Ñ¥½¸¹½½É‘¥¹…Ñ”°(€€€€€€€€€€€ÁÉ½™¥±”€ôÍ¹…ÁÍ¡½Ğ¹É½ÕÑ•AÉ½™¥±”°(€€€€€€€€€€€Ù•¡¥±•AÉ½™¥±”€ôÍ¹…ÁÍ¡½Ğ¹Ù•¡¥±•AÉ½™¥±”°(€€€€€€€€€€€ÁÉ•™•É=™™±¥¹”€ôÍ¹…ÁÍ¡½Ğ¹ÁÉ•™•É=™™±¥¹”°(€€€€€€€€€€€½¹±¥¹•Ù…¥±…‰±”€ôÍ¹…ÁÍ¡½Ğ¹½¹±¥¹•Ù…¥±…‰±”°(€€€€€€€€€€€½™™±¥¹•Ù…¥±…‰±”€ôÍ¹…ÁÍ¡½Ğ¹½™™±¥¹•I•…‘ä€˜˜É½ÕÑ•È€„ô¹Õ±°°(€€€€€€€€€€€ÑÉÕ¬€ôÍ¹…ÁÍ¡½Ğ¹ÑÉÕ­I•ÍÑÉ¥Ñ¥½¹Ì°(€€€€€€€€€€€•Ø€ôÍ¹…ÁÍ¡½Ğ¹•ÙAÉ•™•É•¹•Ì(€€€€€€€€¤(€€€€€€€Ù…°Á±…¸€ôÉÕ¹…Ñ¡¥¹œì¹…Ù¥…Ñ¥½¹A±…Ñ™½É´¹É½ÕÑ•½½É‘¥¹…Ñ½È¹Á±…¸¡É•ÅÕ•ÍĞ¤ô¹•Ñ=É9Õ±° ¤€üèÉ•ÑÕÉ¸(€€€€€€€Ù…°…¹‘¥‘…Ñ”€ôÁ±…¸¹Í•±•Ñ•€üèÉ•ÑÕÉ¸(€€€€€€€Ù…°É•Á±…•µ•¹Ğ€ôI½ÕÑ•=É¥¥¹½¹¹•Ñ½È¹…ÑÑ… ¡½½É‘¥¹…Ñ”°…¹‘¥‘…Ñ”¹É½ÕÑ”¤(€€€€€€€Ù…°…±Ñ•É¹…Ñ¥Ù•Ì€ôÁ±…¸¹…¹‘¥‘…Ñ•Ì¹µ…ÀìI½ÕÑ•=É¥¥¹½¹¹•Ñ½È¹…ÑÑ… ¡½½É‘¥¹…Ñ”°¥Ğ¹É½ÕÑ”¤ô(€€€€€€€µÕÑ…‰±•MÑ…Ñ”¹ÕÁ‘…Ñ”ì(€€€€€€€€€€€¥Ğ¹½Áä (€€€€€€€€€€€€€€€É½ÕÑ”€ôÉ•Á±…•µ•¹Ğ°(€€€€€€€€€€€€€€€É½ÕÑ•±Ñ•É¹…Ñ¥Ù•Ì€ô…±Ñ•É¹…Ñ¥Ù•Ì°(€€€€€€€€€€€€€€€Í•±•Ñ•‘I½ÕÑ•%¹‘•à€ô€À°(€€€€€€€€€€€€€€€É½ÕÑ•M½ÕÉ”€ô…¹‘¥‘…Ñ”¹Í½ÕÉ”°(€€€€€€€€€€€€€€€µ…¹•ÕÙ•É%¹‘•à€ô€À°(€€€€€€€€€€€€€€€‘¥ÍÑ…¹•Q½9•áÑ5…¹•ÕÙ•É5•Ñ•ÉÌ€ôÉ•Á±…•µ•¹Ğ¹µ…¹•ÕÙ•ÉÌ¹™¥ÉÍÑ=É9Õ±° ¤ü¹‘¥ÍÑ…¹•5•Ñ•ÉÌ(€€€€€€€€€€€€€€€€€€€€üèÉ•Á±…•µ•¹Ğ¹‘¥ÍÑ…¹•5•Ñ•ÉÌ°(€€€€€€€€€€€€€€€É•µ…¥¹¥¹¥ÍÑ…¹•5•Ñ•ÉÌ€ôÉ•Á±…•µ•¹Ğ¹‘¥ÍÑ…¹•5•Ñ•ÉÌ°(€€€€€€€€€€€€€€€É•µ…¥¹¥¹M•½¹‘Ì€ôÉ•Á±…•µ•¹Ğ¹ÑÉ…Ù•±M•½¹‘Ì°(€€€€€€€€€€€€€€€½™™I½ÕÑ”€ô™…±Í”°(€€€€€€€€€€€€€€€…µ•É…ÕÑ½µ…Ñ¥Œ€ôÑÉÕ”°(€€€€€€€€€€€€€€€™½±±½İ9…Ù¥…Ñ¥½¸€ôÑÉÕ”°(€€€€€€€€€€€€€€€ÑÉ…™™¥Œ€ô…¹‘¥‘…Ñ”¹ÑÉ…™™¥Œ°(€€€€€€€€€€€€€€€ÑÉ…™™¥M•µ•¹ÑÌ€ô•µÁÑå1¥ÍĞ ¤°(€€€€€€€€€€€€€€€µ•ÍÍ…”€ô¥˜€¡Á±…¸¹™…±±‰…­UÍ•¤ì(€€€€€€€€€€€€€€€€€€€€‹fbÏn3bÄƒb£bœƒff#fbçn3b¨ƒb³b¿n3b¼ƒf ƒffb£bäƒb³bŸn3j¿bËn3fƒbŸb×fbŸb´ƒbÓb¼ˆ(€€€€€€€€€€€€€€€ô•±Í”ì(€€€€€€€€€€€€€€€€€€€€‹fbÏn3bÄƒb£bœƒff#fbçn3b¨ƒb³b¿n3b¼ƒbŸb×fbŸb´ƒbÓb¼ˆ(€€€€€€€€€€€€€€€ô(€€€€€€€€€€€€¤(€€€€€€€ô(€€€€€€€±…ÍÑ%¹Í¥¡ÑÍI•µ…¥¹¥¹5•Ñ•ÉÌ€ôÉ•Á±…•µ•¹Ğ¹‘¥ÍÑ…¹•5•Ñ•ÉÌ(€€€€€€€±½…‘I½ÕÑ•9½Ñ¥•Ì¡É•Á±…•µ•¹Ğ°É•Á±…•µ•¹Ğ¤(€€€ô((€€€½Ù•ÉÉ¥‘”™Õ¸½¹±•…É• ¤ì(€€€€€€€‘½İ¹±½…‘5½¹¥Ñ½Èü¹…¹•° ¤(€€€€€€€Í•…É¡)½ˆü¹…¹•° ¤(€€€€€€€¹…Ù¥…Ñ¥½¹)½ˆü¹…¹•° ¤(€€€€€€€¥¹Í¥¡ÑÍI•™É•Í¡)½ˆü¹…¹•° ¤(€€€€€€€Á±…•Ìü¹±½Í” ¤(€€€€€€€É…Á ü¹±½Í” ¤(€€€€€€€¹•Ñİ½É­5½¹¥Ñ½È¹±½Í” ¤(€€€€€€€ÍÕÁ•È¹½¹±•…É• ¤(€€€ô((€€€ÁÉ¥Ù…Ñ”½µÁ…¹¥½¸½‰©•Ğì(€€€€€€€½¹ÍĞÙ…°UII9Q}1=Q%=9}=€ô€´å|ÀÀÁ|ÀÀÁ|ÀÀÅ0(€€€€€€€½¹ÍĞÙ…°Y%}1=Q%=9}Q=Id€ô€‰‘•Ù¥”é±½…Ñ¥½¸ˆ(€€€€€€€½¹ÍĞÙ…°5%9}9Y%Q%=9}i==4€ô€ÄÔ(€€€€€€€½¹ÍĞÙ…°U1Q}9Y%Q%=9}i==4€ô€Äà(€€€€€€€½¹ÍĞÙ…°5a}9Y%Q%=9}i==4€ô€Ää(€€€€€€€½¹ÍĞÙ…°%9M%!QM}IIM!}%MQ9}5QIL€ô€É|ÔÀÀ¸À(€€€€€€€½¹ÍĞÙ…°=9Q%9U=UM}II=UQ}%9QIY1}5L€ô€ÌÁ|ÀÀÁ0(€€€€€€€½¹ÍĞÙ…°5%9}5A}5Q!}=9%9€ô€À¸ÌÔ(€€€ô)ô(
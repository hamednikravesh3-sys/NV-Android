package ir.nv.navigation.rebuild

import ir.nv.navigation.core.Coordinate

enum class NvScreen { HOME, ROUTE_SELECTION, DRIVING, EXPLORE }

data class NvSearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val coordinate: Coordinate,
    val category: String,
    val source: SearchSource,
    val code: String? = null
)

enum class SearchSource { ONLINE, OFFLINE, RECENT }

data class NvRouteOption(
    val id: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val delaySeconds: Double = 0.0,
    val label: String,
    val recommended: Boolean = false
)

data class NvRebuildState(
    val screen: NvScreen = NvScreen.HOME,
    val online: Boolean = false,
    val offlineReady: Boolean = false,
    val searchQuery: String = "",
    val searching: Boolean = false,
    val searchResults: List<NvSearchResult> = emptyList(),
    val origin: NvSearchResult? = null,
    val destination: NvSearchResult? = null,
    val routeOptions: List<NvRouteOption> = emptyList(),
    val selectedRouteId: String? = null,
    val navigationActive: Boolean = false,
    val message: String? = null
)

package ir.nv.navigation.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(context: Context) : AutoCloseable {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val mutableOnline = MutableStateFlow(currentStatus())
    val online: StateFlow<Boolean> = mutableOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
    }

    init {
        connectivity.registerDefaultNetworkCallback(callback)
    }

    fun isOnline(): Boolean = currentStatus().also { mutableOnline.value = it }

    private fun refresh() {
        mutableOnline.value = currentStatus()
    }

    private fun currentStatus(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        // NET_CAPABILITY_VALIDATED is intentionally not required. Some Android devices,
        // VPNs and emulators can reach HTTPS services while Android's captive-portal
        // validation is delayed or unavailable. Requiring VALIDATED made NV falsely
        // disable online search and satellite imagery even with working internet.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun close() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }
}

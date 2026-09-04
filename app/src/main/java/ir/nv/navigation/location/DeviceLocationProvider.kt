package ir.nv.navigation.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import ir.nv.navigation.core.Coordinate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class DeviceLocationProvider(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Coordinate? {
        if (!hasPermission()) return null
        bestLastKnown()
            ?.takeIf { System.currentTimeMillis() - it.time <= FRESH_LOCATION_MS }
            ?.let { return it.toCoordinate() }
        return suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location.toCoordinate())
                }
            }
            val providers = activeProviders()
            if (providers.isEmpty()) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            providers.forEach {
                manager.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper())
            }
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
        }
    }

    @SuppressLint("MissingPermission")
    fun updates(): Flow<Coordinate> = callbackFlow {
        if (!hasPermission()) {
            close(SecurityException("مجوز موقعیت مکانی داده نشده است"))
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toCoordinate())
            }
        }
        bestLastKnown()
            ?.takeIf { System.currentTimeMillis() - it.time <= RECENT_LOCATION_MS }
            ?.let { trySend(it.toCoordinate()) }
        activeProviders().forEach {
            manager.requestLocationUpdates(it, 2_000L, 3f, listener, Looper.getMainLooper())
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(): Location? = activeProviders()
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }

    private fun activeProviders(): List<String> = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    ).filter { provider ->
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
    }

    private fun Location.toCoordinate() = Coordinate(latitude, longitude)

    private companion object {
        const val FRESH_LOCATION_MS = 2 * 60 * 1_000L
        const val RECENT_LOCATION_MS = 10 * 60 * 1_000L
    }
}

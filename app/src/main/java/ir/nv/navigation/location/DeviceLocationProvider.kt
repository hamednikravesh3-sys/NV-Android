package ir.nv.navigation.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
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

    fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Coordinate? {
        if (!hasPermission()) return null

        val recent = bestLastKnown()
            ?.takeIf { System.currentTimeMillis() - it.time <= MAX_LAST_KNOWN_AGE_MS }
            ?.takeIf { !it.hasAccuracy() || it.accuracy <= ACCEPTABLE_LAST_KNOWN_ACCURACY_METERS }
        if (recent != null && recent.hasAccuracy() && recent.accuracy <= EXCELLENT_ACCURACY_METERS) {
            return recent.toCoordinate()
        }

        return suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var best: Location? = recent
            var completed = false
            lateinit var listener: LocationListener

            fun finish(location: Location?) {
                if (completed) return
                completed = true
                manager.removeUpdates(listener)
                handler.removeCallbacksAndMessages(null)
                if (continuation.isActive) continuation.resume(location?.toCoordinate())
            }

            listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!isUsable(location)) return
                    val current = best
                    if (current == null || locationScore(location) < locationScore(current)) best = location
                    if (location.hasAccuracy() && location.accuracy <= TARGET_ACCURACY_METERS &&
                        System.currentTimeMillis() - location.time <= FRESH_SAMPLE_AGE_MS
                    ) {
                        finish(location)
                    }
                }
            }

            val providers = activeProviders()
            if (providers.isEmpty()) {
                continuation.resume(recent?.toCoordinate())
                return@suspendCancellableCoroutine
            }
            providers.forEach { provider ->
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }
            handler.postDelayed({ finish(best) }, LOCATION_COLLECTION_WINDOW_MS)
            continuation.invokeOnCancellation {
                manager.removeUpdates(listener)
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun updates(): Flow<NavigationFix> = callbackFlow {
        if (!hasPermission()) {
            close(SecurityException("مجوز موقعیت مکانی داده نشده است"))
            return@callbackFlow
        }
        var bestRecentAccuracy = Float.POSITIVE_INFINITY
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!isUsable(location)) return
                val accuracy = if (location.hasAccuracy()) location.accuracy else Float.POSITIVE_INFINITY
                if (accuracy > MAX_NAVIGATION_ACCURACY_METERS && bestRecentAccuracy <= GOOD_NAVIGATION_ACCURACY_METERS) return
                bestRecentAccuracy = minOf(bestRecentAccuracy * 1.08f, accuracy)
                trySend(location.toNavigationFix())
            }
        }
        bestLastKnown()
            ?.takeIf { System.currentTimeMillis() - it.time <= RECENT_LOCATION_MS }
            ?.takeIf(::isUsable)
            ?.let {
                bestRecentAccuracy = if (it.hasAccuracy()) it.accuracy else Float.POSITIVE_INFINITY
                trySend(it.toNavigationFix())
            }
        activeProviders().forEach {
            manager.requestLocationUpdates(it, 1_000L, 1f, listener, Looper.getMainLooper())
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(): Location? = activeProviders()
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .filter(::isUsable)
        .minByOrNull(::locationScore)

    private fun locationScore(location: Location): Double {
        val accuracyPenalty = if (location.hasAccuracy()) location.accuracy.toDouble() else 500.0
        val ageSeconds = ((System.currentTimeMillis() - location.time).coerceAtLeast(0L) / 1000.0)
        val gpsBonus = if (location.provider == LocationManager.GPS_PROVIDER) -12.0 else 0.0
        return accuracyPenalty + ageSeconds * 0.35 + gpsBonus
    }

    private fun isUsable(location: Location): Boolean {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return false
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return false
        val age = System.currentTimeMillis() - location.time
        if (age > MAX_SAMPLE_AGE_MS) return false
        return !location.hasAccuracy() || location.accuracy <= ABSOLUTE_MAX_ACCURACY_METERS
    }

    private fun activeProviders(): List<String> = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    ).filter { provider ->
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
    }

    private fun Location.toCoordinate() = Coordinate(latitude, longitude)

    private fun Location.toNavigationFix() = NavigationFix(
        coordinate = toCoordinate(),
        speedKmh = if (hasSpeed()) (speed * 3.6f).coerceAtLeast(0f) else 0f,
        bearingDegrees = if (hasBearing()) bearing else 0f,
        accuracyMeters = if (hasAccuracy()) accuracy else Float.POSITIVE_INFINITY,
        timestampMillis = time.takeIf { it > 0L } ?: System.currentTimeMillis()
    )

    private companion object {
        const val LOCATION_COLLECTION_WINDOW_MS = 6_000L
        const val TARGET_ACCURACY_METERS = 20f
        const val EXCELLENT_ACCURACY_METERS = 12f
        const val ACCEPTABLE_LAST_KNOWN_ACCURACY_METERS = 35f
        const val GOOD_NAVIGATION_ACCURACY_METERS = 35f
        const val MAX_NAVIGATION_ACCURACY_METERS = 90f
        const val ABSOLUTE_MAX_ACCURACY_METERS = 250f
        const val FRESH_SAMPLE_AGE_MS = 10_000L
        const val MAX_LAST_KNOWN_AGE_MS = 30_000L
        const val MAX_SAMPLE_AGE_MS = 2 * 60 * 1_000L
        const val RECENT_LOCATION_MS = 30_000L
    }
}

data class NavigationFix(
    val coordinate: Coordinate,
    val speedKmh: Float,
    val bearingDegrees: Float,
    val accuracyMeters: Float,
    val timestampMillis: Long = System.currentTimeMillis()
)

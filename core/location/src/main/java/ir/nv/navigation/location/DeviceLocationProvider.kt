package ir.nv.navigation.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

        // Never present an unknown-accuracy or stale cached coordinate as the user's exact position.
        val recent = bestLastKnown()
            ?.takeIf { it.hasAccuracy() }
            ?.takeIf { System.currentTimeMillis() - it.time <= MAX_LAST_KNOWN_AGE_MS }
            ?.takeIf { it.accuracy <= ACCEPTABLE_LAST_KNOWN_ACCURACY_METERS }
        if (recent != null &&
            recent.accuracy <= EXCELLENT_ACCURACY_METERS &&
            System.currentTimeMillis() - recent.time <= FRESH_SAMPLE_AGE_MS
        ) {
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
                val accepted = location
                    ?.takeIf { it.hasAccuracy() }
                    ?.takeIf { System.currentTimeMillis() - it.time <= MAX_CURRENT_FIX_AGE_MS }
                    ?.takeIf { it.accuracy <= MAX_CURRENT_LOCATION_ACCURACY_METERS }
                if (continuation.isActive) continuation.resume(accepted?.toCoordinate())
            }

            listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!isUsable(location) || !location.hasAccuracy()) return
                    val age = System.currentTimeMillis() - location.time
                    if (age > MAX_CURRENT_FIX_AGE_MS) return
                    val current = best
                    if (current == null || locationScore(location) < locationScore(current)) best = location
                    if (location.accuracy <= TARGET_ACCURACY_METERS && age <= FRESH_SAMPLE_AGE_MS) {
                        finish(location)
                    }
                }
            }

            val providers = activeProviders()
            if (providers.isEmpty()) {
                continuation.resume(recent?.takeIf { it.accuracy <= MAX_CURRENT_LOCATION_ACCURACY_METERS }?.toCoordinate())
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
        val sensorFusion = NavigationSensorFusion(context.applicationContext).also { it.start() }
        var bestRecentAccuracy = Float.POSITIVE_INFINITY
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!isUsable(location) || !location.hasAccuracy()) return
                val accuracy = location.accuracy
                if (accuracy > MAX_NAVIGATION_ACCURACY_METERS && bestRecentAccuracy <= GOOD_NAVIGATION_ACCURACY_METERS) return
                bestRecentAccuracy = minOf(bestRecentAccuracy * 1.08f, accuracy)
                trySend(location.toNavigationFix(sensorFusion.snapshot()))
            }
        }
        bestLastKnown()
            ?.takeIf { System.currentTimeMillis() - it.time <= RECENT_LOCATION_MS }
            ?.takeIf { it.hasAccuracy() && it.accuracy <= MAX_NAVIGATION_ACCURACY_METERS }
            ?.takeIf(::isUsable)
            ?.let {
                bestRecentAccuracy = it.accuracy
                trySend(it.toNavigationFix(sensorFusion.snapshot()))
            }
        activeProviders().forEach {
            manager.requestLocationUpdates(it, 1_000L, 1f, listener, Looper.getMainLooper())
        }
        awaitClose {
            manager.removeUpdates(listener)
            sensorFusion.close()
        }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(): Location? = activeProviders()
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .filter(::isUsable)
        .minByOrNull(::locationScore)

    private fun locationScore(location: Location): Double {
        val accuracyPenalty = if (location.hasAccuracy()) location.accuracy.toDouble() else 1_000.0
        val ageSeconds = ((System.currentTimeMillis() - location.time).coerceAtLeast(0L) / 1000.0)
        val gpsBonus = if (location.provider == LocationManager.GPS_PROVIDER && hasFinePermission()) -25.0 else 0.0
        return accuracyPenalty + ageSeconds * 0.5 + gpsBonus
    }

    private fun isUsable(location: Location): Boolean {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return false
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return false
        val age = System.currentTimeMillis() - location.time
        if (age < -FUTURE_TIMESTAMP_TOLERANCE_MS || age > MAX_SAMPLE_AGE_MS) return false
        return !location.hasAccuracy() || location.accuracy <= ABSOLUTE_MAX_ACCURACY_METERS
    }

    private fun activeProviders(): List<String> = buildList {
        if (hasFinePermission() && runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
            add(LocationManager.GPS_PROVIDER)
        }
        if (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
            add(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun Location.toCoordinate() = Coordinate(latitude, longitude)

    private fun Location.toNavigationFix(sensor: SensorFusionSnapshot) = NavigationFix(
        coordinate = toCoordinate(),
        altitudeMeters = if (hasAltitude()) altitude else null,
        speedKmh = if (hasSpeed()) (speed * 3.6f).coerceAtLeast(0f) else sensor.estimatedSpeedKmh,
        bearingDegrees = fusedBearing(this, sensor),
        accuracyMeters = if (hasAccuracy()) accuracy else Float.POSITIVE_INFINITY,
        timestampMillis = time.takeIf { it > 0L } ?: System.currentTimeMillis(),
        sensorFusionActive = sensor.active,
        linearAccelerationMps2 = sensor.linearAccelerationMps2,
        yawRateDegreesPerSecond = sensor.yawRateDegreesPerSecond
    )

    private fun fusedBearing(location: Location, sensor: SensorFusionSnapshot): Float {
        val gpsBearing = location.bearing.takeIf { location.hasBearing() && it.isFinite() }
        val sensorBearing = sensor.bearingDegrees.takeIf { it.isFinite() }
        if (gpsBearing == null) return sensorBearing ?: 0f
        if (sensorBearing == null || !sensor.active) return normalizeBearing(gpsBearing)

        val speedMps = if (location.hasSpeed()) location.speed.coerceAtLeast(0f) else 0f
        val gpsWeight = when {
            speedMps >= 8f -> 0.88f
            speedMps >= 3f -> 0.72f
            speedMps >= 1f -> 0.52f
            else -> 0.20f
        }
        return circularBlend(gpsBearing, sensorBearing, gpsWeight)
    }

    private fun circularBlend(a: Float, b: Float, aWeight: Float): Float {
        val weight = aWeight.coerceIn(0f, 1f).toDouble()
        val ar = Math.toRadians(a.toDouble())
        val br = Math.toRadians(b.toDouble())
        val x = cos(ar) * weight + cos(br) * (1.0 - weight)
        val y = sin(ar) * weight + sin(br) * (1.0 - weight)
        return normalizeBearing(Math.toDegrees(atan2(y, x)).toFloat())
    }

    private fun normalizeBearing(value: Float): Float = ((value % 360f) + 360f) % 360f

    private companion object {
        const val LOCATION_COLLECTION_WINDOW_MS = 8_000L
        const val TARGET_ACCURACY_METERS = 18f
        const val EXCELLENT_ACCURACY_METERS = 10f
        const val ACCEPTABLE_LAST_KNOWN_ACCURACY_METERS = 35f
        const val MAX_CURRENT_LOCATION_ACCURACY_METERS = 55f
        const val GOOD_NAVIGATION_ACCURACY_METERS = 35f
        const val MAX_NAVIGATION_ACCURACY_METERS = 90f
        const val ABSOLUTE_MAX_ACCURACY_METERS = 250f
        const val FRESH_SAMPLE_AGE_MS = 8_000L
        const val MAX_CURRENT_FIX_AGE_MS = 15_000L
        const val MAX_LAST_KNOWN_AGE_MS = 20_000L
        const val MAX_SAMPLE_AGE_MS = 2 * 60 * 1_000L
        const val RECENT_LOCATION_MS = 20_000L
        const val FUTURE_TIMESTAMP_TOLERANCE_MS = 2_000L
    }
}

data class NavigationFix(
    val coordinate: Coordinate,
    val altitudeMeters: Double? = null,
    val speedKmh: Float,
    val bearingDegrees: Float,
    val accuracyMeters: Float,
    val timestampMillis: Long = System.currentTimeMillis(),
    val sensorFusionActive: Boolean = false,
    val linearAccelerationMps2: Float = 0f,
    val yawRateDegreesPerSecond: Float = 0f
)

private data class SensorFusionSnapshot(
    val active: Boolean = false,
    val bearingDegrees: Float = Float.NaN,
    val estimatedSpeedKmh: Float = 0f,
    val linearAccelerationMps2: Float = 0f,
    val yawRateDegreesPerSecond: Float = 0f
)

/**
 * Lightweight on-device sensor fusion used to stabilize heading at low vehicle speeds.
 * Rotation-vector is preferred because Android already fuses accelerometer, gyroscope and
 * magnetometer data. Raw accelerometer/gyroscope/magnetic sensors are also registered so the
 * navigation engine keeps useful motion signals on devices without a rotation-vector sensor.
 */
private class NavigationSensorFusion(context: Context) : SensorEventListener, AutoCloseable {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)
    private val gravity = FloatArray(3)
    private var haveGravity = false
    private var magnetic = FloatArray(3)
    private var haveMagnetic = false
    private var bearing = Float.NaN
    private var linearAcceleration = 0f
    private var yawRate = 0f
    private var estimatedSpeedMps = 0f
    private var lastAccelerationTimestampNs = 0L
    private var lastSensorAtMs = 0L
    private var started = false

    fun start() {
        if (started) return
        started = true
        listOf(
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD
        ).mapNotNull(manager::getDefaultSensor)
            .distinctBy { it.type }
            .forEach { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun snapshot(): SensorFusionSnapshot = SensorFusionSnapshot(
        active = started && System.currentTimeMillis() - lastSensorAtMs <= SENSOR_STALE_MS,
        bearingDegrees = bearing,
        estimatedSpeedKmh = (estimatedSpeedMps * 3.6f).coerceIn(0f, 220f),
        linearAccelerationMps2 = linearAcceleration,
        yawRateDegreesPerSecond = yawRate
    )

    override fun onSensorChanged(event: SensorEvent) {
        lastSensorAtMs = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                bearing = normalize(Math.toDegrees(orientation[0].toDouble()).toFloat())
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (!haveGravity) {
                    event.values.copyInto(gravity, endIndex = minOf(3, event.values.size))
                    haveGravity = true
                } else {
                    for (i in 0..2) gravity[i] = LOW_PASS_ALPHA * gravity[i] + (1f - LOW_PASS_ALPHA) * event.values[i]
                }
                val lx = event.values[0] - gravity[0]
                val ly = event.values[1] - gravity[1]
                val lz = event.values[2] - gravity[2]
                linearAcceleration = sqrt(lx * lx + ly * ly + lz * lz)
                if (lastAccelerationTimestampNs > 0L) {
                    val dt = ((event.timestamp - lastAccelerationTimestampNs) / 1_000_000_000.0).coerceIn(0.0, 0.25)
                    val longitudinalEstimate = (linearAcceleration - MOTION_NOISE_FLOOR).coerceAtLeast(0f)
                    estimatedSpeedMps = (estimatedSpeedMps + longitudinalEstimate * dt.toFloat()) * SPEED_DECAY
                }
                lastAccelerationTimestampNs = event.timestamp
                updateFallbackOrientation()
            }
            Sensor.TYPE_GYROSCOPE -> {
                yawRate = Math.toDegrees(event.values[2].toDouble()).toFloat()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                event.values.copyInto(magnetic, endIndex = minOf(3, event.values.size))
                haveMagnetic = true
                updateFallbackOrientation()
            }
        }
    }

    private fun updateFallbackOrientation() {
        if (!bearing.isNaN() || !haveGravity || !haveMagnetic) return
        if (SensorManager.getRotationMatrix(rotation, null, gravity, magnetic)) {
            SensorManager.getOrientation(rotation, orientation)
            bearing = normalize(Math.toDegrees(orientation[0].toDouble()).toFloat())
        }
    }

    private fun normalize(value: Float): Float = ((value % 360f) + 360f) % 360f

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun close() {
        manager.unregisterListener(this)
        started = false
    }

    private companion object {
        const val LOW_PASS_ALPHA = 0.82f
        const val MOTION_NOISE_FLOOR = 0.18f
        const val SPEED_DECAY = 0.985f
        const val SENSOR_STALE_MS = 2_500L
    }
}

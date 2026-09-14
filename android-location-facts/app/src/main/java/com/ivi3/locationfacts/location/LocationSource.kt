package com.ivi3.locationfacts.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * A single location fix, via the platform [LocationManager] only — no Play Services, so the
 * app builds and runs on devices without Google services.
 *
 * Strategy: take a recent cached fix if one exists (instant), otherwise ask the best
 * available provider for one fix and give up after [FIX_TIMEOUT_MS].
 */
class LocationSource(private val context: Context) {

    class Unavailable(message: String) : Exception(message)

    fun hasPermission(): Boolean = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission") // guarded by hasPermission() below
    suspend fun currentLocation(): Location {
        if (!hasPermission()) throw Unavailable("Location permission has not been granted.")

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: throw Unavailable("This device has no location service.")

        val providers = candidateProviders(manager)
        if (providers.isEmpty()) {
            throw Unavailable("Location is turned off. Switch it on and try again.")
        }

        cachedFix(manager, providers)?.let { return it }

        for (provider in providers) {
            val fix = try {
                withTimeout(FIX_TIMEOUT_MS) { singleFix(manager, provider) }
            } catch (_: TimeoutCancellationException) {
                null
            }
            if (fix != null) return fix
        }

        // Nothing fresh arrived; a stale cached fix still beats no facts at all.
        lastKnown(manager, providers)?.let { return it }
        throw Unavailable("Could not get a location fix. Try again outdoors or near a window.")
    }

    private fun candidateProviders(manager: LocationManager): List<String> {
        val preferred = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        val enabled = preferred.filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
        return enabled.ifEmpty {
            // PASSIVE never powers up hardware, but it can hand back a fix another app got.
            listOf(LocationManager.PASSIVE_PROVIDER)
                .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun cachedFix(manager: LocationManager, providers: List<String>): Location? =
        lastKnown(manager, providers)?.takeIf {
            System.currentTimeMillis() - it.time < MAX_CACHED_AGE_MS
        }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }

    @SuppressLint("MissingPermission")
    private suspend fun singleFix(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = android.os.CancellationSignal()
                manager.getCurrentLocation(
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context),
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
                continuation.invokeOnCancellation { signal.cancel() }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    @Deprecated("Required by the pre-API-29 LocationListener interface")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

                    override fun onProviderDisabled(provider: String) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            }
        }

    private companion object {
        const val MAX_CACHED_AGE_MS = 2 * 60 * 1000L
        const val FIX_TIMEOUT_MS = 12_000L
    }
}

/** The runtime permissions the app asks for, in the order the system dialog should offer them. */
val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

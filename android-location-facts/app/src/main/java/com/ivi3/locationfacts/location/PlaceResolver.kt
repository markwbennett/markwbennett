package com.ivi3.locationfacts.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import com.ivi3.locationfacts.ai.PlaceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume

/**
 * Turns a raw fix into the [PlaceContext] the model is given.
 *
 * Reverse geocoding is best-effort: on a device with no geocoder backend, or with no
 * network, the coordinates alone still make a usable prompt.
 */
class PlaceResolver(private val context: Context) {

    suspend fun resolve(location: Location): PlaceContext {
        val address = reverseGeocode(location)
        return PlaceContext(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
            addressLine = address?.getAddressLine(0),
            city = address?.locality ?: address?.subAdminArea,
            region = address?.adminArea,
            countryCode = address?.countryCode?.uppercase(Locale.US),
            timezone = TimeZone.getDefault().id,
        )
    }

    private suspend fun reverseGeocode(location: Location): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            withTimeout(GEOCODE_TIMEOUT_MS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocodeAsync(geocoder, location)
                } else {
                    geocodeBlocking(geocoder, location)
                }
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (_: Exception) {
            // Geocoder throws IOException when its backend is unreachable, and
            // IllegalArgumentException on odd coordinates. Neither is fatal here.
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun geocodeAsync(geocoder: Geocoder, location: Location): Address? =
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }

    @Suppress("DEPRECATION") // the listener overload only exists from API 33
    private suspend fun geocodeBlocking(geocoder: Geocoder, location: Location): Address? =
        withContext(Dispatchers.IO) {
            geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
        }

    private companion object {
        const val GEOCODE_TIMEOUT_MS = 8_000L
    }
}

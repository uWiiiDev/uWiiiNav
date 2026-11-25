package dev.uwiii.nav.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class NavLocation(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float, // Geschwindigkeit in km/h
    val bearing: Float,  // Fahrtrichtung in Grad (0-360)
    val altitude: Double,
    val accuracy: Float
)

class LocationClient(private val context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMs: Long = 1000L): Flow<NavLocation> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(1.0f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val speedKmh = if (location.hasSpeed()) (location.speed * 3.6f) else 0.0f
                    val navLocation = NavLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        speedKmh = speedKmh,
                        bearing = if (location.hasBearing()) location.bearing else 0.0f,
                        altitude = location.altitude,
                        accuracy = location.accuracy
                    )
                    trySend(navLocation)
                }
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose {
            client.removeLocationUpdates(callback)
        }
    }
}

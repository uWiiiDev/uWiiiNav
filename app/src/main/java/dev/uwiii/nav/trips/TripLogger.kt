package dev.uwiii.nav.trips

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.*

class TripLogger(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("nav_trip_logger", Context.MODE_PRIVATE)

    var tripHistory by mutableStateOf<List<TripRecord>>(loadHistory())
        private set

    // Aktiver Fahrt-Tracker State
    var isTracking by mutableStateOf(false)
        private set
    private var currentDestination = ""
    private var tripStartTime = 0L
    private var lastRecordedLat = 0.0
    private var lastRecordedLon = 0.0
    private var accumulatedDistanceMeters = 0.0
    private var maxRecordedSpeed = 0f

    fun startTrip(destination: String, startLat: Double, startLon: Double) {
        currentDestination = destination
        tripStartTime = System.currentTimeMillis()
        lastRecordedLat = startLat
        lastRecordedLon = startLon
        accumulatedDistanceMeters = 0.0
        maxRecordedSpeed = 0f
        isTracking = true
    }

    fun recordLocation(speedKmh: Float, lat: Double, lon: Double) {
        if (!isTracking) return
        if (speedKmh > maxRecordedSpeed) {
            maxRecordedSpeed = speedKmh
        }

        if (lastRecordedLat != 0.0 && lastRecordedLon != 0.0) {
            val dist = calculateDistanceMeters(lastRecordedLat, lastRecordedLon, lat, lon)
            // Nur sinnvolle GPS-Sprünge werten
            if (dist in 1.0..400.0) {
                accumulatedDistanceMeters += dist
            }
        }
        lastRecordedLat = lat
        lastRecordedLon = lon
    }

    fun finishTrip(): TripRecord? {
        if (!isTracking || tripStartTime == 0L) return null
        val endTime = System.currentTimeMillis()
        val durationMillis = max(endTime - tripStartTime, 1000L)
        val durationMinutes = ((durationMillis / 1000.0) / 60.0).roundToInt().coerceAtLeast(1)
        val distanceKm = (accumulatedDistanceMeters / 100.0).roundToInt() / 10.0

        // Durchschnittsgeschwindigkeit
        val durationHours = durationMillis / (1000.0 * 3600.0)
        val avgSpeed = if (durationHours > 0) (distanceKm / durationHours).toFloat() else 0f

        val record = TripRecord(
            id = UUID.randomUUID().toString(),
            destinationName = currentDestination.ifBlank { "Navigationsfahrt" },
            startTime = tripStartTime,
            endTime = endTime,
            distanceKm = distanceKm,
            durationMinutes = durationMinutes,
            averageSpeedKmh = avgSpeed,
            maxSpeedKmh = maxRecordedSpeed
        )

        // Reset
        isTracking = false
        tripStartTime = 0L

        // Speichern
        val updated = mutableListOf(record).apply { addAll(tripHistory) }
        tripHistory = updated
        saveHistory(updated)

        return record
    }

    fun cancelTrip() {
        isTracking = false
        tripStartTime = 0L
    }

    fun deleteTrip(id: String) {
        val updated = tripHistory.filter { it.id != id }
        tripHistory = updated
        saveHistory(updated)
    }

    fun clearAllTrips() {
        tripHistory = emptyList()
        prefs.edit().remove("history").apply()
    }

    private fun saveHistory(list: List<TripRecord>) {
        val arr = JSONArray()
        for (r in list.take(50)) {
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("destination", r.destinationName)
                put("start", r.startTime)
                put("end", r.endTime)
                put("dist", r.distanceKm)
                put("dur", r.durationMinutes)
                put("avg", r.averageSpeedKmh.toDouble())
                put("max", r.maxSpeedKmh.toDouble())
            })
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory(): List<TripRecord> {
        val str = prefs.getString("history", null) ?: return emptyList()
        return try {
            val arr = JSONArray(str)
            val list = mutableListOf<TripRecord>()
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                list.add(
                    TripRecord(
                        id = j.getString("id"),
                        destinationName = j.getString("destination"),
                        startTime = j.getLong("start"),
                        endTime = j.getLong("end"),
                        distanceKm = j.getDouble("dist"),
                        durationMinutes = j.getInt("dur"),
                        averageSpeedKmh = j.getDouble("avg").toFloat(),
                        maxSpeedKmh = j.getDouble("max").toFloat()
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}

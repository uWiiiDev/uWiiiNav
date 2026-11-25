package dev.uwiii.nav.trips

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TripRecord(
    val id: String,
    val destinationName: String,
    val startTime: Long,
    val endTime: Long,
    val distanceKm: Double,
    val durationMinutes: Int,
    val averageSpeedKmh: Float,
    val maxSpeedKmh: Float
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd.MM.yyyy • HH:mm", Locale.GERMAN).format(Date(startTime))
}

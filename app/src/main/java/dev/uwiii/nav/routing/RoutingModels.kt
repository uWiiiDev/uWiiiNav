package dev.uwiii.nav.routing

import org.maplibre.android.geometry.LatLng

data class RouteStep(
    val instruction: String,
    val streetName: String,
    val maneuverType: String,      // z. B. "turn", "new name", "depart", "arrive", "roundabout"
    val maneuverModifier: String,  // z. B. "right", "left", "slight right", "straight"
    val location: LatLng,
    val distanceMeters: Double,
    val durationSeconds: Double
)

data class RouteData(
    val points: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val steps: List<RouteStep>
) {
    val distanceKm: String
        get() = String.format(java.util.Locale.GERMAN, "%.1f km", distanceMeters / 1000.0)

    val durationMin: String
        get() {
            val mins = (durationSeconds / 60.0).toInt()
            return if (mins >= 60) {
                val hours = mins / 60
                val remainingMins = mins % 60
                "${hours} Std. ${remainingMins} Min."
            } else {
                "$mins Min."
            }
        }

    val etaFormatted: String
        get() {
            val now = java.util.Calendar.getInstance()
            now.add(java.util.Calendar.SECOND, durationSeconds.toInt())
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMAN)
            return sdf.format(now.time)
        }
}

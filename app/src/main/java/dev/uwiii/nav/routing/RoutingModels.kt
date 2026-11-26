package dev.uwiii.nav.routing

import org.maplibre.android.geometry.LatLng

data class LaneInfo(
    val isValid: Boolean,
    val indications: List<String> // z. B. ["left"], ["straight", "right"]
)

data class RouteStep(
    val instruction: String,
    val streetName: String,
    val maneuverType: String,      // z. B. "turn", "new name", "depart", "arrive", "roundabout"
    val maneuverModifier: String,  // z. B. "right", "left", "slight right", "straight"
    val location: LatLng,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val lanes: List<LaneInfo> = emptyList()
) {
    /**
     * Erzeugt eine prägnante deutsche Fahrspur-Sprachansage, falls Spuren vorhanden sind
     * und eine Auswahl erforderlich ist (also nicht alle Spuren gültig sind).
     */
    fun getLaneVoiceHint(): String? {
        if (lanes.isEmpty()) return null
        val total = lanes.size
        val validIndices = lanes.mapIndexedNotNull { index, lane -> if (lane.isValid) index else null }
        if (validIndices.isEmpty() || validIndices.size == total) return null // Keine Einschränkung

        val validCount = validIndices.size

        // Prüfen, ob alle gültigen Spuren ganz rechts liegen
        val isAllRight = validIndices.first() == total - validCount
        if (isAllRight) {
            return when (validCount) {
                1 -> "Bitte die rechte Spur nutzen"
                2 -> "Bitte eine der beiden rechten Spuren nutzen"
                else -> "Bitte auf einer der rechten Spuren einordnen"
            }
        }

        // Prüfen, ob alle gültigen Spuren ganz links liegen
        val isAllLeft = validIndices.last() == validCount - 1
        if (isAllLeft) {
            return when (validCount) {
                1 -> "Bitte die linke Spur nutzen"
                2 -> "Bitte eine der beiden linken Spuren nutzen"
                else -> "Bitte auf einer der linken Spuren einordnen"
            }
        }

        // Mittlere Spur(en)
        if (total >= 3 && validCount == 1 && validIndices[0] == total / 2) {
            return "Bitte die mittlere Spur nutzen"
        }

        return "Bitte auf die passende Spur einordnen"
    }
}

data class RouteData(
    val points: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val steps: List<RouteStep>,
    val routeIndex: Int = 0,
    val summary: String = ""
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

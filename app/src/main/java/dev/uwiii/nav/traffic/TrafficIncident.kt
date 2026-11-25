package dev.uwiii.nav.traffic

import org.maplibre.android.geometry.LatLng

enum class IncidentType {
    TRAFFIC_JAM,
    ACCIDENT,
    CLOSURE,
    ROADWORKS,
    HAZARD
}

data class TrafficIncident(
    val id: String,
    val road: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val coordinate: LatLng,
    val isBlocked: Boolean,
    val geometry: List<LatLng>,
    val type: IncidentType
)

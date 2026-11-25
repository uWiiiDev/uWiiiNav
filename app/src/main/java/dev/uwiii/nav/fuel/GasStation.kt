package dev.uwiii.nav.fuel

import org.maplibre.android.geometry.LatLng

data class GasStation(
    val id: String,
    val name: String,
    val brand: String,
    val street: String,
    val place: String,
    val location: LatLng,
    val distanceKm: Double,
    val diesel: Double?,
    val e5: Double?,
    val e10: Double?,
    val isOpen: Boolean
)

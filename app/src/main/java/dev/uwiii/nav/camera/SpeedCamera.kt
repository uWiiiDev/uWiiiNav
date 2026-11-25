package dev.uwiii.nav.camera

data class SpeedCamera(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val maxSpeed: Int?,
    val direction: Double?,
    val name: String
)

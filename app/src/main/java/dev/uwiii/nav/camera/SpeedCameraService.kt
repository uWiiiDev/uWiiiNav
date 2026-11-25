package dev.uwiii.nav.camera

import dev.uwiii.nav.location.NavLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.*

class SpeedCameraService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Lokaler Cache der erfassten Blitzer
    private val cameraCache = ConcurrentHashMap<Long, SpeedCamera>()

    // Bereits gewarnte Blitzer (um Dauer-Warnungen zu verhindern)
    private val warnedCameras = ConcurrentHashMap<Long, Long>()

    suspend fun loadCamerasForRoute(routePoints: List<LatLng>) = withContext(Dispatchers.IO) {
        if (routePoints.isEmpty()) return@withContext

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        for (pt in routePoints) {
            minLat = minOf(minLat, pt.latitude)
            maxLat = maxOf(maxLat, pt.latitude)
            minLon = minOf(minLon, pt.longitude)
            maxLon = maxOf(maxLon, pt.longitude)
        }

        // Puffer hinzufügen (ca. 1 km um die Route)
        val pad = 0.015
        fetchOverpassCameras(minLat - pad, minLon - pad, maxLat + pad, maxLon + pad)
    }

    suspend fun loadCamerasAround(lat: Double, lon: Double, radiusKm: Double = 8.0) = withContext(Dispatchers.IO) {
        val deltaLat = radiusKm / 111.0
        val deltaLon = radiusKm / (111.0 * cos(Math.toRadians(lat)))
        fetchOverpassCameras(lat - deltaLat, lon - deltaLon, lat + deltaLat, lon + deltaLon)
    }

    private fun fetchOverpassCameras(south: Double, west: Double, north: Double, east: Double) {
        try {
            val query = "[out:json][timeout:10];node[\"highway\"=\"speed_camera\"]($south,$west,$north,$east);out body;"
            val requestBody = FormBody.Builder()
                .add("data", query)
                .build()

            val request = Request.Builder()
                .url("https://overpass-api.de/api/interpreter")
                .header("User-Agent", "uWiiiNav-Android-App/1.0 (uWiiiDev)")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body?.string() ?: return
                val json = JSONObject(body)
                val elements = json.optJSONArray("elements") ?: return

                for (i in 0 until elements.length()) {
                    val elem = elements.getJSONObject(i)
                    val id = elem.getLong("id")
                    val lat = elem.getDouble("lat")
                    val lon = elem.getDouble("lon")
                    val tags = elem.optJSONObject("tags")

                    val maxSpeed = tags?.optString("maxspeed")?.toIntOrNull()
                    val direction = tags?.optString("direction")?.toDoubleOrNull()
                    val name = tags?.optString("name", "Geschwindigkeitsüberwachung") ?: "Geschwindigkeitsüberwachung"

                    cameraCache[id] = SpeedCamera(
                        id = id,
                        latitude = lat,
                        longitude = lon,
                        maxSpeed = maxSpeed,
                        direction = direction,
                        name = name
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Prüft, ob ein Blitzer vor dem Fahrzeug liegt.
     * Berücksichtigt Entfernung und Fahrtrichtung (Heading).
     */
    fun findCameraAhead(userLoc: NavLocation, maxDistanceMeters: Double = 650.0): Pair<SpeedCamera, Double>? {
        var closestCamera: SpeedCamera? = null
        var minDistance = Double.MAX_VALUE

        for (cam in cameraCache.values) {
            val dist = calculateDistance(userLoc.latitude, userLoc.longitude, cam.latitude, cam.longitude)
            if (dist <= maxDistanceMeters) {
                // Peilung vom Auto zum Blitzer berechnen
                val bearingToCam = calculateBearing(userLoc.latitude, userLoc.longitude, cam.latitude, cam.longitude)
                val angleDiff = abs(normalizeAngle(userLoc.bearing.toDouble() - bearingToCam))

                // Liegt der Blitzer vor uns (innerhalb eines 45°-Sichtwinkels)?
                if (angleDiff <= 45.0) {
                    if (dist < minDistance) {
                        minDistance = dist
                        closestCamera = cam
                    }
                }
            }
        }

        return if (closestCamera != null) Pair(closestCamera, minDistance) else null
    }

    fun shouldTriggerVoiceAlert(cameraId: Long): Boolean {
        val lastWarned = warnedCameras[cameraId] ?: 0L
        val now = System.currentTimeMillis()
        if (now - lastWarned > 60_000L) { // Nicht öfter als einmal pro Minute für denselben Blitzer
            warnedCameras[cameraId] = now
            return true
        }
        return false
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)
        return (Math.toDegrees(theta) + 360.0) % 360.0
    }

    private fun normalizeAngle(angle: Double): Double {
        var a = angle % 360.0
        if (a > 180.0) a -= 360.0
        if (a < -180.0) a += 360.0
        return a
    }
}

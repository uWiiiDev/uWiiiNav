package dev.uwiii.nav.traffic

import dev.uwiii.nav.location.NavLocation
import dev.uwiii.nav.routing.RouteData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.*

class TrafficService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Cache aller geladenen Störungen nach Autobahn
    private val incidentCache = ConcurrentHashMap<String, List<TrafficIncident>>()

    // Verhindert doppelte Sprachansagen für dieselbe Störung innerhalb von 10 Minuten
    private val warnedIncidentIds = ConcurrentHashMap<String, Long>()

    private var lastLoadedRouteTime = 0L

    /**
     * Sucht Autobahnen auf der Route und lädt für diese Stau- und Sperrmeldungen
     */
    suspend fun loadTrafficForRoute(route: RouteData) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastLoadedRouteTime < 60_000L && incidentCache.isNotEmpty()) {
            return@withContext // Max. alle 60 Sekunden neu abfragen
        }
        lastLoadedRouteTime = now

        val autobahnNumbers = mutableSetOf<String>()
        val regex = Regex("""\bA\s?(\d+)\b""", RegexOption.IGNORE_CASE)

        for (step in route.steps) {
            val matches = regex.findAll(step.streetName)
            for (m in matches) {
                val num = m.groupValues[1]
                autobahnNumbers.add("A$num")
            }
        }

        for (road in autobahnNumbers) {
            loadIncidentsForRoad(road)
        }
    }

    /**
     * Lädt Störungen für eine bestimmte Autobahn (z. B. "A8")
     */
    suspend fun loadIncidentsForRoad(road: String): List<TrafficIncident> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TrafficIncident>()
        list.addAll(fetchEndpoint(road, "warning"))
        list.addAll(fetchEndpoint(road, "closure"))
        incidentCache[road] = list
        list
    }

    private fun fetchEndpoint(road: String, endpoint: String): List<TrafficIncident> {
        val results = mutableListOf<TrafficIncident>()
        try {
            val url = "https://verkehr.autobahn.de/o/autobahn/$road/services/$endpoint"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "uWiiiNav-Android-App/1.0 (uWiiiDev)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val array = json.optJSONArray(endpoint) ?: return emptyList()

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val id = item.optString("identifier", "${road}_$i")
                    val title = item.optString("title", "").trim()
                    val subtitle = item.optString("subtitle", "").trim()
                    val isBlockedStr = item.optString("isBlocked", "false")
                    val isBlocked = isBlockedStr.equals("true", ignoreCase = true)

                    val descArray = item.optJSONArray("description")
                    val descLines = mutableListOf<String>()
                    if (descArray != null) {
                        for (j in 0 until descArray.length()) {
                            val line = descArray.optString(j, "").trim()
                            if (line.isNotBlank() && !line.startsWith("Ereignisnummer") && !line.startsWith("Beginn:")) {
                                descLines.add(line)
                            }
                        }
                    }
                    val fullDescription = descLines.joinToString(" • ")

                    val coordObj = item.optJSONObject("coordinate")
                    val lat = coordObj?.optDouble("lat", 0.0) ?: 0.0
                    val lon = coordObj?.optDouble("long", 0.0) ?: 0.0
                    if (lat == 0.0 || lon == 0.0) continue

                    // Geometrie (Staustrecke)
                    val geomList = mutableListOf<LatLng>()
                    val geomObj = item.optJSONObject("geometry")
                    val geomCoords = geomObj?.optJSONArray("coordinates")
                    if (geomCoords != null) {
                        for (k in 0 until geomCoords.length()) {
                            val pt = geomCoords.optJSONArray(k)
                            if (pt != null && pt.length() >= 2) {
                                geomList.add(LatLng(pt.getDouble(1), pt.getDouble(0)))
                            }
                        }
                    }

                    val type = when {
                        isBlocked || title.contains("Sperrung", ignoreCase = true) -> IncidentType.CLOSURE
                        fullDescription.contains("Unfall", ignoreCase = true) || title.contains("Unfall", ignoreCase = true) -> IncidentType.ACCIDENT
                        fullDescription.contains("Stau", ignoreCase = true) || fullDescription.contains("stockend", ignoreCase = true) || fullDescription.contains("zähfließend", ignoreCase = true) -> IncidentType.TRAFFIC_JAM
                        fullDescription.contains("Baustelle", ignoreCase = true) || fullDescription.contains("Fahrbahn", ignoreCase = true) -> IncidentType.ROADWORKS
                        else -> IncidentType.HAZARD
                    }

                    results.add(
                        TrafficIncident(
                            id = id,
                            road = road,
                            title = title.ifBlank { "$road Verkehrsmeldung" },
                            subtitle = subtitle,
                            description = fullDescription.ifBlank { "Verkehrsstörung gemeldet" },
                            coordinate = LatLng(lat, lon),
                            isBlocked = isBlocked,
                            geometry = geomList,
                            type = type
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    /**
     * Findet die nächste Störung voraus (bis zu 15 km Entfernung in Fahrtrichtung)
     */
    fun findIncidentAhead(
        userLoc: NavLocation,
        maxDistanceMeters: Double = 15000.0
    ): Pair<TrafficIncident, Double>? {
        var closestIncident: TrafficIncident? = null
        var minDistance = Double.MAX_VALUE

        for (incidents in incidentCache.values) {
            for (incident in incidents) {
                val dist = calculateDistanceMeters(
                    userLoc.latitude, userLoc.longitude,
                    incident.coordinate.latitude, incident.coordinate.longitude
                )

                if (dist <= maxDistanceMeters) {
                    // Prüfen, ob die Störung vor uns liegt (Sichtwinkel bis 60°)
                    val bearingToIncident = calculateBearing(
                        userLoc.latitude, userLoc.longitude,
                        incident.coordinate.latitude, incident.coordinate.longitude
                    )
                    val angleDiff = abs(normalizeAngle(userLoc.bearing.toDouble() - bearingToIncident))

                    if (angleDiff <= 65.0) {
                        if (dist < minDistance) {
                            minDistance = dist
                            closestIncident = incident
                        }
                    }
                }
            }
        }

        return if (closestIncident != null) Pair(closestIncident, minDistance) else null
    }

    fun shouldTriggerVoiceAlert(incidentId: String): Boolean {
        val now = System.currentTimeMillis()
        val lastWarned = warnedIncidentIds[incidentId]
        if (lastWarned == null || now - lastWarned > 600_000L) { // 10 Minuten Cooldown
            warnedIncidentIds[incidentId] = now
            return true
        }
        return false
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
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

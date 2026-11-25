package dev.uwiii.nav.geocoding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.maplibre.android.geometry.LatLng
import java.net.URLEncoder

data class SearchResult(
    val title: String,
    val subtitle: String,
    val location: LatLng,
    val distanceKm: Double? = null
)

class SearchService {
    private val client = OkHttpClient()

    suspend fun searchPlaces(query: String, userLocation: LatLng? = null): List<SearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")

            // 1. Wenn GPS-Standort bekannt: Zuerst strikt im Nahbereich (ca. 10-15 km) mit bounded=1 suchen
            if (userLocation != null) {
                val localResults = performNominatimSearch(
                    encodedQuery = encodedQuery,
                    userLocation = userLocation,
                    bounded = true,
                    delta = 0.12
                )
                if (localResults.isNotEmpty()) {
                    return@withContext localResults
                }
            }

            // 2. Fallback (oder wenn keine lokalen Treffer / Fernziel gesucht wird): bundesweit mit Priorisierung
            performNominatimSearch(
                encodedQuery = encodedQuery,
                userLocation = userLocation,
                bounded = false,
                delta = 0.5
            )
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun performNominatimSearch(
        encodedQuery: String,
        userLocation: LatLng?,
        bounded: Boolean,
        delta: Double
    ): List<SearchResult> {
        var url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=15&addressdetails=1&countrycodes=de"

        if (userLocation != null) {
            val minLon = userLocation.longitude - delta
            val maxLon = userLocation.longitude + delta
            val minLat = userLocation.latitude - delta
            val maxLat = userLocation.latitude + delta
            url += "&viewbox=$minLon,$maxLat,$maxLon,$minLat&bounded=${if (bounded) 1 else 0}"
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "uWiiiNav-Android-App/1.0 (uWiiiDev)")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val jsonArray = JSONArray(body)
                val results = ArrayList<SearchResult>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val displayName = item.optString("display_name", "")
                    val rawName = item.optString("name", "").trim()
                    val lat = item.optDouble("lat", 0.0)
                    val lon = item.optDouble("lon", 0.0)
                    val osmType = item.optString("type", "")
                    if (lat == 0.0 || lon == 0.0) continue

                    val parts = displayName.split(",", limit = 2)
                    val firstPart = parts.getOrNull(0)?.trim() ?: displayName
                    val subtitle = parts.getOrNull(1)?.trim() ?: ""

                    val title = when {
                        rawName.isNotBlank() -> rawName
                        osmType == "charging_station" -> "Ladesäule • $firstPart"
                        osmType == "parking" -> "Parkplatz • $firstPart"
                        osmType == "restaurant" -> "Restaurant • $firstPart"
                        firstPart.isNotBlank() -> firstPart
                        else -> displayName
                    }

                    val distKm = if (userLocation != null) {
                        calculateDistanceKm(userLocation.latitude, userLocation.longitude, lat, lon)
                    } else null

                    results.add(
                        SearchResult(
                            title = title,
                            subtitle = subtitle,
                            location = LatLng(lat, lon),
                            distanceKm = distKm
                        )
                    )
                }

                // Treffer nach tatsächlicher Entfernung sortieren
                if (userLocation != null) {
                    results.sortBy { it.distanceKm ?: Double.MAX_VALUE }
                }
                results
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}

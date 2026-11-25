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
        if (query.trim().length < 2) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            var url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=10&addressdetails=1"

            // Lokale Priorisierung um aktuellen Standort bevorzugen
            if (userLocation != null) {
                val delta = 0.55
                val minLon = userLocation.longitude - delta
                val maxLon = userLocation.longitude + delta
                val minLat = userLocation.latitude - delta
                val maxLat = userLocation.latitude + delta
                url += "&viewbox=$minLon,$maxLat,$maxLon,$minLat&bounded=0"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "uWiiiNav-Android-App/1.0 (uWiiiDev)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(body)
                val results = ArrayList<SearchResult>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val displayName = item.optString("display_name", "")
                    val lat = item.optDouble("lat", 0.0)
                    val lon = item.optDouble("lon", 0.0)
                    if (lat == 0.0 || lon == 0.0) continue

                    val parts = displayName.split(",", limit = 2)
                    val title = parts.getOrNull(0)?.trim() ?: displayName
                    val subtitle = parts.getOrNull(1)?.trim() ?: ""

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

                // Treffer nach Nähe sortieren
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

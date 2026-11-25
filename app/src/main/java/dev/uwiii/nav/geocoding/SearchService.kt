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
    val location: LatLng
)

class SearchService {
    private val client = OkHttpClient()

    suspend fun searchPlaces(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5&addressdetails=1"

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

                    val parts = displayName.split(",", limit = 2)
                    val title = parts.getOrNull(0)?.trim() ?: displayName
                    val subtitle = parts.getOrNull(1)?.trim() ?: ""

                    results.add(
                        SearchResult(
                            title = title,
                            subtitle = subtitle,
                            location = LatLng(lat, lon)
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

package dev.uwiii.nav.discovery

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.*

data class DiscoveryItem(
    val id: Long,
    val title: String,
    val extract: String,
    val distanceMeters: Double,
    val lat: Double,
    val lon: Double
)

class DiscoveryService(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("discovery_settings", Context.MODE_PRIVATE)

    var isEnabled by mutableStateOf(prefs.getBoolean("is_enabled", true))
        private set

    var displayDurationSeconds by mutableStateOf(prefs.getInt("display_duration", 10))
        private set

    var isAudioEnabled by mutableStateOf(prefs.getBoolean("is_audio_enabled", false))
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val seenPageIds = mutableSetOf<Long>()
    private var lastCheckedLat = 0.0
    private var lastCheckedLon = 0.0
    private var lastAlertTime = 0L

    fun updateEnabled(enabled: Boolean) {
        isEnabled = enabled
        prefs.edit().putBoolean("is_enabled", enabled).apply()
    }

    fun updateDisplayDuration(seconds: Int) {
        displayDurationSeconds = seconds.coerceIn(5, 30)
        prefs.edit().putInt("display_duration", displayDurationSeconds).apply()
    }

    fun updateAudioEnabled(enabled: Boolean) {
        isAudioEnabled = enabled
        prefs.edit().putBoolean("is_audio_enabled", enabled).apply()
    }

    suspend fun checkNearbySights(currentLat: Double, currentLon: Double): DiscoveryItem? = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext null

        val now = System.currentTimeMillis()
        // Mindestens 75 Sekunden Pause zwischen zwei neuen Sehenswürdigkeiten
        if (now - lastAlertTime < 75_000L) return@withContext null

        // Mindestens 350 Meter bewegt seit der letzten erfolgreichen Abfrage
        if (lastCheckedLat != 0.0 && calculateDistanceMeters(currentLat, currentLon, lastCheckedLat, lastCheckedLon) < 350.0) {
            return@withContext null
        }

        lastCheckedLat = currentLat
        lastCheckedLon = currentLon

        try {
            val url = "https://de.wikipedia.org/w/api.php?action=query&generator=geosearch" +
                    "&ggscoord=$currentLat|$currentLon&ggsradius=1800&ggslimit=8" +
                    "&prop=coordinates|extracts&exintro=1&explaintext=1&exsentences=2&format=json"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "uWiiiNav-Android-App/1.0 (uWiiiDev; kontakt@uwiii.dev)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val root = JSONObject(body)
                val queryObj = root.optJSONObject("query") ?: return@withContext null
                val pagesObj = queryObj.optJSONObject("pages") ?: return@withContext null

                var bestItem: DiscoveryItem? = null
                var minDistance = Double.MAX_VALUE

                val keys = pagesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val page = pagesObj.getJSONObject(key)
                    val pageId = page.optLong("pageid", 0L)
                    if (pageId == 0L || seenPageIds.contains(pageId)) continue

                    val title = page.optString("title", "").trim()
                    var extract = page.optString("extract", "").trim()
                    if (title.isBlank() || extract.length < 25) continue

                    // Uninteressante Meta-Artikel filtern
                    if (title.startsWith("Liste ") || title.startsWith("Kategorie:") || title.contains("(Begriffsklärung)") || title.startsWith("Amt ")) {
                        continue
                    }

                    // Bereinige extract (z. B. unschöne Zeilenumbrüche entfernen)
                    extract = extract.replace(Regex("\\s+"), " ").trim()

                    val coordsArray = page.optJSONArray("coordinates")
                    val (lat, lon) = if (coordsArray != null && coordsArray.length() > 0) {
                        val c = coordsArray.getJSONObject(0)
                        Pair(c.optDouble("lat", currentLat), c.optDouble("lon", currentLon))
                    } else {
                        Pair(currentLat, currentLon)
                    }

                    val dist = calculateDistanceMeters(currentLat, currentLon, lat, lon)
                    if (dist < minDistance) {
                        minDistance = dist
                        bestItem = DiscoveryItem(
                            id = pageId,
                            title = title,
                            extract = extract,
                            distanceMeters = dist,
                            lat = lat,
                            lon = lon
                        )
                    }
                }

                if (bestItem != null) {
                    seenPageIds.add(bestItem.id)
                    lastAlertTime = now
                }
                bestItem
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}

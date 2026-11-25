package dev.uwiii.nav.speed

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.*

class SpeedLimitService(context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    var currentSpeedLimit by mutableStateOf<Int?>(null)
        private set

    private var lastCheckedLat = 0.0
    private var lastCheckedLon = 0.0
    private var lastCheckTime = 0L

    suspend fun checkSpeedLimit(lat: Double, lon: Double): Int? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Mindestens 12 Sekunden Pause oder 150m Bewegung zwischen Abfragen
        if (now - lastCheckTime < 12_000L && calculateDistanceMeters(lat, lon, lastCheckedLat, lastCheckedLon) < 150.0) {
            return@withContext currentSpeedLimit
        }

        lastCheckedLat = lat
        lastCheckedLon = lon
        lastCheckTime = now

        try {
            val query = "[out:json][timeout:4];way(around:35,$lat,$lon)[maxspeed];out tags;"
            val requestBody = FormBody.Builder()
                .add("data", query)
                .build()

            val request = Request.Builder()
                .url("https://overpass-api.de/api/interpreter")
                .header("User-Agent", "uWiiiNav-Android-App/1.0 (uWiiiDev)")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext currentSpeedLimit
                val body = response.body?.string() ?: return@withContext currentSpeedLimit
                val json = JSONObject(body)
                val elements = json.optJSONArray("elements") ?: return@withContext currentSpeedLimit

                for (i in 0 until elements.length()) {
                    val elem = elements.getJSONObject(i)
                    val tags = elem.optJSONObject("tags") ?: continue
                    val maxspeedRaw = tags.optString("maxspeed", "").trim()

                    val limit = parseMaxSpeed(maxspeedRaw)
                    if (limit != null) {
                        currentSpeedLimit = limit
                        return@withContext limit
                    }
                }
            }
        } catch (e: Exception) {
            // Ignoriere Netzwerkfehler stillschweigend
        }
        currentSpeedLimit
    }

    fun setDirectLimit(limit: Int?) {
        currentSpeedLimit = limit
    }

    private fun parseMaxSpeed(raw: String): Int? {
        val direct = raw.toIntOrNull()
        if (direct != null && direct in 10..150) return direct

        val lower = raw.lowercase()
        return when {
            lower == "de:urban" || lower == "at:urban" || lower == "ch:urban" -> 50
            lower == "de:rural" || lower == "at:rural" -> 100
            lower == "ch:rural" -> 80
            lower == "de:motorway" -> 130
            lower == "at:motorway" -> 130
            lower == "ch:motorway" -> 120
            lower == "walk" || lower == "de:living_street" -> 10
            lower.contains("none") -> null
            else -> null
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

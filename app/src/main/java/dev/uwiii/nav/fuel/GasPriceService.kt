package dev.uwiii.nav.fuel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class FuelType {
    DIESEL,
    E10,
    E5
}

class GasPriceService(context: Context) {
    companion object {
        const val DEMO_API_KEY = "00000000-0000-0000-0000-000000000002"
    }

    private val prefs = context.applicationContext.getSharedPreferences("nav_fuel_settings", Context.MODE_PRIVATE)

    var apiKey by mutableStateOf(prefs.getString("api_key", DEMO_API_KEY) ?: DEMO_API_KEY)
        private set

    val isDemoKey: Boolean
        get() = apiKey.isBlank() || apiKey == DEMO_API_KEY

    var lastErrorMessage by mutableStateOf<String?>(null)
        private set

    var preferredFuelType by mutableStateOf(
        FuelType.valueOf(prefs.getString("fuel_type", FuelType.DIESEL.name) ?: FuelType.DIESEL.name)
    )
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    fun updateApiKey(key: String) {
        val sanitized = key.trim()
        apiKey = if (sanitized.isBlank()) DEMO_API_KEY else sanitized
        prefs.edit().putString("api_key", apiKey).apply()
        lastErrorMessage = null
    }

    fun resetToDemoKey() {
        updateApiKey(DEMO_API_KEY)
    }

    fun updatePreferredFuelType(type: FuelType) {
        preferredFuelType = type
        prefs.edit().putString("fuel_type", type.name).apply()
    }

    suspend fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double = 6.0): List<GasStation> = withContext(Dispatchers.IO) {
        try {
            val url = String.format(
                Locale.US,
                "https://creativecommons.tankerkoenig.de/json/list.php?lat=%.4f&lng=%.4f&rad=%.1f&sort=dist&type=all&apikey=%s",
                lat, lon, radiusKm, apiKey
            )

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "uWiiiNav-Android-App/1.0 (uWiiiDev)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    lastErrorMessage = "Serverfehler HTTP ${response.code}"
                    return@withContext emptyList()
                }
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val ok = json.optBoolean("ok", false) || json.optString("status") == "ok"
                if (!ok) {
                    lastErrorMessage = json.optString("message", "API-Fehler von Tankerkönig")
                    return@withContext emptyList()
                }

                lastErrorMessage = null
                val stationsArr = json.optJSONArray("stations") ?: return@withContext emptyList()
                val list = ArrayList<GasStation>()

                for (i in 0 until stationsArr.length()) {
                    val s = stationsArr.getJSONObject(i)
                    val id = s.getString("id")
                    val name = s.optString("name", "Tankstelle")
                    val brand = s.optString("brand", "").ifBlank { name }
                    val street = s.optString("street", "")
                    val place = s.optString("place", "")
                    val sLat = s.optDouble("lat", 0.0)
                    val sLon = s.optDouble("lng", 0.0)
                    val dist = s.optDouble("dist", 0.0)
                    val isOpen = s.optBoolean("isOpen", true)

                    val dieselPrice = s.optDouble("diesel", 0.0).takeIf { it > 0.5 }
                    val e5Price = s.optDouble("e5", 0.0).takeIf { it > 0.5 }
                    val e10Price = s.optDouble("e10", 0.0).takeIf { it > 0.5 }

                    if (sLat != 0.0 && sLon != 0.0) {
                        list.add(
                            GasStation(
                                id = id,
                                name = name,
                                brand = brand,
                                street = street,
                                place = place,
                                location = LatLng(sLat, sLon),
                                distanceKm = dist,
                                diesel = dieselPrice,
                                e5 = e5Price,
                                e10 = e10Price,
                                isOpen = isOpen
                            )
                        )
                    }
                }
                list
            }
        } catch (e: Exception) {
            e.printStackTrace()
            lastErrorMessage = e.localizedMessage ?: "Netzwerkfehler"
            emptyList()
        }
    }
}

package dev.uwiii.nav.favorites

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng

data class SavedPlace(
    val title: String,
    val address: String,
    val location: LatLng,
    val type: PlaceType = PlaceType.RECENT
)

enum class PlaceType {
    HOME,
    WORK,
    FAVORITE,
    RECENT
}

class FavoritesManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nav_favorites", Context.MODE_PRIVATE)

    var homePlace by mutableStateOf<SavedPlace?>(loadPlace("home"))
        private set

    var workPlace by mutableStateOf<SavedPlace?>(loadPlace("work"))
        private set

    var recentPlaces by mutableStateOf<List<SavedPlace>>(loadRecents())
        private set

    fun setHome(title: String, address: String, location: LatLng) {
        val p = SavedPlace(title, address, location, PlaceType.HOME)
        homePlace = p
        savePlace("home", p)
    }

    fun clearHome() {
        homePlace = null
        savePlace("home", null)
    }

    fun setWork(title: String, address: String, location: LatLng) {
        val p = SavedPlace(title, address, location, PlaceType.WORK)
        workPlace = p
        savePlace("work", p)
    }

    fun clearWork() {
        workPlace = null
        savePlace("work", null)
    }

    fun addRecent(title: String, address: String, location: LatLng) {
        val current = recentPlaces.toMutableList()
        current.removeAll {
            calculateDistance(it.location.latitude, it.location.longitude, location.latitude, location.longitude) < 50.0
        }
        current.add(0, SavedPlace(title, address, location, PlaceType.RECENT))
        val limited = current.take(6)
        recentPlaces = limited
        saveRecents(limited)
    }

    fun removeRecent(place: SavedPlace) {
        val current = recentPlaces.toMutableList()
        current.removeAll {
            it.location.latitude == place.location.latitude && it.location.longitude == place.location.longitude
        }
        recentPlaces = current
        saveRecents(current)
    }

    private fun savePlace(key: String, place: SavedPlace?) {
        if (place == null) {
            prefs.edit().remove(key).apply()
        } else {
            val json = JSONObject().apply {
                put("title", place.title)
                put("address", place.address)
                put("lat", place.location.latitude)
                put("lon", place.location.longitude)
            }
            prefs.edit().putString(key, json.toString()).apply()
        }
    }

    private fun loadPlace(key: String): SavedPlace? {
        val str = prefs.getString(key, null) ?: return null
        return try {
            val json = JSONObject(str)
            SavedPlace(
                title = json.getString("title"),
                address = json.optString("address", ""),
                location = LatLng(json.getDouble("lat"), json.getDouble("lon")),
                type = if (key == "home") PlaceType.HOME else PlaceType.WORK
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun saveRecents(list: List<SavedPlace>) {
        val arr = JSONArray()
        for (p in list) {
            arr.put(JSONObject().apply {
                put("title", p.title)
                put("address", p.address)
                put("lat", p.location.latitude)
                put("lon", p.location.longitude)
            })
        }
        prefs.edit().putString("recents", arr.toString()).apply()
    }

    private fun loadRecents(): List<SavedPlace> {
        val str = prefs.getString("recents", null) ?: return emptyList()
        return try {
            val arr = JSONArray(str)
            val list = mutableListOf<SavedPlace>()
            for (i in 0 until arr.length()) {
                val json = arr.getJSONObject(i)
                list.add(
                    SavedPlace(
                        title = json.getString("title"),
                        address = json.optString("address", ""),
                        location = LatLng(json.getDouble("lat"), json.getDouble("lon")),
                        type = PlaceType.RECENT
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}

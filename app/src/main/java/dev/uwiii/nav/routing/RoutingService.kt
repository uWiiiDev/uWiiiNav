package dev.uwiii.nav.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.util.Locale

class RoutingService {
    private val client = OkHttpClient()

    suspend fun calculateRoute(start: LatLng, destination: LatLng): RouteData? = withContext(Dispatchers.IO) {
        try {
            // OSRM freie Routing-API für Kraftfahrzeuge (Driving)
            val url = String.format(
                Locale.US,
                "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true",
                start.longitude, start.latitude,
                destination.longitude, destination.latitude
            )

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "uWiiiNav-Android-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)

                if (json.optString("code") != "Ok") return@withContext null

                val routes = json.getJSONArray("routes")
                if (routes.length() == 0) return@withContext null

                val route = routes.getJSONObject(0)
                val distanceMeters = route.getDouble("distance")
                val durationSeconds = route.getDouble("duration")

                // Geometrie (Polylinie)
                val geometry = route.getJSONObject("geometry")
                val coords = geometry.getJSONArray("coordinates")
                val points = ArrayList<LatLng>()
                for (i in 0 until coords.length()) {
                    val coord = coords.getJSONArray(i)
                    val lon = coord.getDouble(0)
                    val lat = coord.getDouble(1)
                    points.add(LatLng(lat, lon))
                }

                // Einzelschritte (Turn-by-Turn Maneuvers)
                val stepsList = ArrayList<RouteStep>()
                val legs = route.getJSONArray("legs")
                if (legs.length() > 0) {
                    val leg = legs.getJSONObject(0)
                    val steps = leg.getJSONArray("steps")
                    for (i in 0 until steps.length()) {
                        val s = steps.getJSONObject(i)
                        val stepDistance = s.getDouble("distance")
                        val stepDuration = s.getDouble("duration")
                        val streetName = s.optString("name", "")

                        val maneuver = s.getJSONObject("maneuver")
                        val mType = maneuver.optString("type", "")
                        val mModifier = maneuver.optString("modifier", "")
                        val mLoc = maneuver.getJSONArray("location")
                        val stepLatLng = LatLng(mLoc.getDouble(1), mLoc.getDouble(0))

                        val instruction = buildGermanInstruction(mType, mModifier, streetName)

                        stepsList.add(
                            RouteStep(
                                instruction = instruction,
                                streetName = streetName,
                                maneuverType = mType,
                                maneuverModifier = mModifier,
                                location = stepLatLng,
                                distanceMeters = stepDistance,
                                durationSeconds = stepDuration
                            )
                        )
                    }
                }

                RouteData(
                    points = points,
                    distanceMeters = distanceMeters,
                    durationSeconds = durationSeconds,
                    steps = stepsList
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buildGermanInstruction(type: String, modifier: String, street: String): String {
        val target = if (street.isNotBlank()) " auf $street" else ""
        return when (type) {
            "depart" -> "Fahrt beginnen$target"
            "arrive" -> "Sie haben Ihr Ziel erreicht"
            "roundabout" -> "Im Kreisverkehr ausfahren$target"
            "turn" -> when (modifier) {
                "right" -> "Rechts abbiegen$target"
                "left" -> "Links abbiegen$target"
                "slight right" -> "Halb rechts halten$target"
                "slight left" -> "Halb links halten$target"
                "sharp right" -> "Scharf rechts abbiegen$target"
                "sharp left" -> "Scharf links abbiegen$target"
                "straight" -> "Geradeaus weiterfahren$target"
                "uturn" -> "Bitte wenden"
                else -> "Abbiegen$target"
            }
            "new name" -> "Weiterfahren$target"
            "merge" -> "Einfädeln$target"
            "ramp" -> when (modifier) {
                "right", "slight right" -> "Ausfahrt rechts nehmen$target"
                "left", "slight left" -> "Ausfahrt links nehmen$target"
                else -> "Auffahrt nehmen$target"
            }
            "fork" -> when (modifier) {
                "right" -> "Rechts halten$target"
                "left" -> "Links halten$target"
                else -> "Spur halten$target"
            }
            else -> "Weiterfahren$target"
        }
    }
}

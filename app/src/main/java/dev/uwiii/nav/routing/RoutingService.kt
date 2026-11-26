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

    suspend fun calculateRoute(start: LatLng, destination: LatLng): RouteData? {
        return calculateRoutes(start, destination).firstOrNull()
    }

    suspend fun calculateRoutes(start: LatLng, destination: LatLng): List<RouteData> = withContext(Dispatchers.IO) {
        try {
            // OSRM freie Routing-API für Kraftfahrzeuge mit Alternativrouten (alternatives=true)
            val url = String.format(
                Locale.US,
                "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true&alternatives=true",
                start.longitude, start.latitude,
                destination.longitude, destination.latitude
            )

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "uWiiiNav-Android-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)

                if (json.optString("code") != "Ok") return@withContext emptyList()

                val routes = json.getJSONArray("routes")
                if (routes.length() == 0) return@withContext emptyList()

                val resultList = ArrayList<RouteData>()
                for (rIndex in 0 until routes.length()) {
                    val routeObj = routes.getJSONObject(rIndex)
                    val parsed = parseSingleRoute(routeObj, rIndex)
                    if (parsed != null) {
                        resultList.add(parsed)
                    }
                }
                resultList
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseSingleRoute(route: JSONObject, routeIndex: Int): RouteData? {
        try {
            val distanceMeters = route.getDouble("distance")
            val durationSeconds = route.getDouble("duration")
            val summary = route.optString("weight_name", "")

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
            var routeStreetSummary = ""
            if (legs.length() > 0) {
                val leg = legs.getJSONObject(0)
                val steps = leg.getJSONArray("steps")
                for (i in 0 until steps.length()) {
                    val s = steps.getJSONObject(i)
                    val stepDistance = s.getDouble("distance")
                    val stepDuration = s.getDouble("duration")
                    val streetName = s.optString("name", "")
                    if (routeStreetSummary.isBlank() && streetName.isNotBlank() && streetName != "Fahrt beginnen") {
                        routeStreetSummary = streetName
                    }

                    val maneuver = s.getJSONObject("maneuver")
                    val mType = maneuver.optString("type", "")
                    val mModifier = maneuver.optString("modifier", "")
                    val mLoc = maneuver.getJSONArray("location")
                    val stepLatLng = LatLng(mLoc.getDouble(1), mLoc.getDouble(0))

                    val instruction = buildGermanInstruction(mType, mModifier, streetName)

                    // Fahrspuren (Lane Guidance) aus intersections extrahieren
                    val lanesList = ArrayList<LaneInfo>()
                    val intersections = s.optJSONArray("intersections")
                    if (intersections != null) {
                        for (j in 0 until intersections.length()) {
                            val inter = intersections.optJSONObject(j) ?: continue
                            val lanesArr = inter.optJSONArray("lanes")
                            if (lanesArr != null && lanesArr.length() > 0) {
                                for (k in 0 until lanesArr.length()) {
                                    val laneObj = lanesArr.optJSONObject(k) ?: continue
                                    val valid = laneObj.optBoolean("valid", false)
                                    val indicationsArr = laneObj.optJSONArray("indications")
                                    val indications = ArrayList<String>()
                                    if (indicationsArr != null) {
                                        for (m in 0 until indicationsArr.length()) {
                                            indications.add(indicationsArr.getString(m))
                                        }
                                    }
                                    lanesList.add(LaneInfo(isValid = valid, indications = indications))
                                }
                                break
                            }
                        }
                    }

                    stepsList.add(
                        RouteStep(
                            instruction = instruction,
                            streetName = streetName,
                            maneuverType = mType,
                            maneuverModifier = mModifier,
                            location = stepLatLng,
                            distanceMeters = stepDistance,
                            durationSeconds = stepDuration,
                            lanes = lanesList
                        )
                    )
                }
            }

            val routeLabel = when (routeIndex) {
                0 -> "Schnellste Route"
                1 -> if (routeStreetSummary.isNotBlank()) "Via $routeStreetSummary" else "Alternative 1"
                2 -> if (routeStreetSummary.isNotBlank()) "Via $routeStreetSummary" else "Alternative 2"
                else -> "Route ${routeIndex + 1}"
            }

            return RouteData(
                points = points,
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds,
                steps = stepsList,
                routeIndex = routeIndex,
                summary = routeLabel
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
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

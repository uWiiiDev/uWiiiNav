package dev.uwiii.nav.offline

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

data class OfflineArea(
    val id: String,
    val name: String,
    val bounds: LatLngBounds,
    val minZoom: Double = 10.0,
    val maxZoom: Double = 16.0,
    val approxMb: Int
)

sealed class AreaStatus {
    object NotDownloaded : AreaStatus()
    data class Downloading(val progressPercent: Int, val downloadedMb: Double) : AreaStatus()
    data class Downloaded(val sizeMb: Double, val regionId: Long) : AreaStatus()
}

class OfflineMapManager(private val context: Context) {
    private val offlineManager = OfflineManager.getInstance(context)
    private val styleUrl = "https://tiles.openfreemap.org/styles/liberty"
    private val pixelRatio = context.resources.displayMetrics.density

    val statusMap = mutableStateMapOf<String, AreaStatus>()

    init {
        // Größeres Limit für Offline-Karten setzen (z. B. bis zu 2 GB)
        try {
            offlineManager.setMaximumAmbientCacheSize(1024L * 1024L * 1024L * 2L, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        refreshDownloadedRegions()
    }

    fun getStandardAreas(currentLocation: LatLng?): List<OfflineArea> {
        val list = mutableListOf<OfflineArea>()

        // 1. Umkreis um aktuellen Standort (ca. 40 km Radius)
        currentLocation?.let { loc ->
            val deltaLat = 0.35
            val deltaLon = 0.50
            val bounds = LatLngBounds.Builder()
                .include(LatLng(loc.latitude - deltaLat, loc.longitude - deltaLon))
                .include(LatLng(loc.latitude + deltaLat, loc.longitude + deltaLon))
                .build()

            list.add(
                OfflineArea(
                    id = "current_radius",
                    name = "Aktueller Umkreis (ca. 40 km)",
                    bounds = bounds,
                    minZoom = 11.0,
                    maxZoom = 16.0,
                    approxMb = 80
                )
            )
        }

        // 2. Alle 16 deutschen Bundesländer (alphabetisch sortiert)
        val bundeslaender = listOf(
            OfflineArea(
                id = "bw",
                name = "Baden-Württemberg",
                bounds = LatLngBounds.from(49.79, 10.50, 47.53, 7.51),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 350
            ),
            OfflineArea(
                id = "by",
                name = "Bayern",
                bounds = LatLngBounds.from(50.57, 13.84, 47.27, 8.97),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 500
            ),
            OfflineArea(
                id = "be",
                name = "Berlin",
                bounds = LatLngBounds.from(52.68, 13.77, 52.33, 13.08),
                minZoom = 10.0,
                maxZoom = 16.0,
                approxMb = 120
            ),
            OfflineArea(
                id = "bb",
                name = "Brandenburg",
                bounds = LatLngBounds.from(53.56, 14.77, 51.35, 11.26),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 280
            ),
            OfflineArea(
                id = "hb",
                name = "Bremen",
                bounds = LatLngBounds.from(53.60, 8.95, 53.00, 8.48),
                minZoom = 10.0,
                maxZoom = 16.0,
                approxMb = 60
            ),
            OfflineArea(
                id = "hh",
                name = "Hamburg",
                bounds = LatLngBounds.from(53.75, 10.33, 53.39, 9.73),
                minZoom = 10.0,
                maxZoom = 16.0,
                approxMb = 80
            ),
            OfflineArea(
                id = "he",
                name = "Hessen",
                bounds = LatLngBounds.from(51.65, 10.25, 49.39, 7.77),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 280
            ),
            OfflineArea(
                id = "mv",
                name = "Mecklenburg-Vorpommern",
                bounds = LatLngBounds.from(54.69, 14.42, 53.07, 10.59),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 290
            ),
            OfflineArea(
                id = "ni",
                name = "Niedersachsen",
                bounds = LatLngBounds.from(53.90, 11.60, 51.29, 6.65),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 450
            ),
            OfflineArea(
                id = "nrw",
                name = "Nordrhein-Westfalen",
                bounds = LatLngBounds.from(52.54, 9.47, 50.32, 5.86),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 450
            ),
            OfflineArea(
                id = "rp",
                name = "Rheinland-Pfalz",
                bounds = LatLngBounds.from(50.95, 8.51, 48.96, 6.11),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 260
            ),
            OfflineArea(
                id = "sl",
                name = "Saarland",
                bounds = LatLngBounds.from(49.64, 7.41, 49.11, 6.35),
                minZoom = 10.0,
                maxZoom = 16.0,
                approxMb = 90
            ),
            OfflineArea(
                id = "sn",
                name = "Sachsen",
                bounds = LatLngBounds.from(51.69, 15.05, 50.17, 11.86),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 300
            ),
            OfflineArea(
                id = "st",
                name = "Sachsen-Anhalt",
                bounds = LatLngBounds.from(53.05, 13.19, 50.93, 10.55),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 260
            ),
            OfflineArea(
                id = "sh",
                name = "Schleswig-Holstein",
                bounds = LatLngBounds.from(55.06, 11.32, 53.36, 7.86),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 280
            ),
            OfflineArea(
                id = "th",
                name = "Thüringen",
                bounds = LatLngBounds.from(51.65, 12.65, 50.20, 9.87),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 250
            )
        )
        list.addAll(bundeslaender)

        // 3. Nachbarländer / Urlaubsregionen
        list.add(
            OfflineArea(
                id = "at",
                name = "Österreich",
                bounds = LatLngBounds.from(49.03, 17.17, 46.37, 9.53),
                minZoom = 9.0,
                maxZoom = 14.0,
                approxMb = 550
            )
        )
        list.add(
            OfflineArea(
                id = "ch",
                name = "Schweiz",
                bounds = LatLngBounds.from(47.81, 10.50, 45.81, 5.95),
                minZoom = 9.0,
                maxZoom = 14.0,
                approxMb = 480
            )
        )
        list.add(
            OfflineArea(
                id = "mallorca",
                name = "Mallorca (Balearen)",
                bounds = LatLngBounds.from(39.95, 3.50, 39.25, 2.30),
                minZoom = 10.0,
                maxZoom = 16.0,
                approxMb = 120
            )
        )

        return list
    }

    fun refreshDownloadedRegions() {
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                if (offlineRegions == null) return
                for (region in offlineRegions) {
                    val metadata = String(region.metadata)
                    try {
                        val json = JSONObject(metadata)
                        val areaId = json.optString("area_id")
                        if (areaId.isNotBlank()) {
                            region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                                override fun onStatus(status: OfflineRegionStatus?) {
                                    if (status != null && status.isComplete) {
                                        val mb = status.completedResourceSize / (1024.0 * 1024.0)
                                        statusMap[areaId] = AreaStatus.Downloaded(mb, region.id)
                                    }
                                }
                                override fun onError(error: String?) {}
                            })
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onError(error: String) {}
        })
    }

    fun downloadArea(area: OfflineArea) {
        statusMap[area.id] = AreaStatus.Downloading(0, 0.0)

        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            area.bounds,
            area.minZoom,
            area.maxZoom,
            pixelRatio
        )

        val metadataJson = JSONObject().apply {
            put("area_id", area.id)
            put("area_name", area.name)
        }
        val metadata = metadataJson.toString().toByteArray(Charsets.UTF_8)

        offlineManager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        val percentage = if (status.requiredResourceCount >= 0) {
                            (100.0 * status.completedResourceCount / status.requiredResourceCount).toInt()
                        } else 0
                        val mb = status.completedResourceSize / (1024.0 * 1024.0)

                        if (status.isComplete) {
                            statusMap[area.id] = AreaStatus.Downloaded(mb, offlineRegion.id)
                        } else {
                            statusMap[area.id] = AreaStatus.Downloading(percentage.coerceIn(0, 99), mb)
                        }
                    }

                    override fun onError(error: OfflineRegionError) {
                        statusMap[area.id] = AreaStatus.NotDownloaded
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        statusMap[area.id] = AreaStatus.NotDownloaded
                    }
                })

                // Download sofort starten
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }

            override fun onError(error: String) {
                statusMap[area.id] = AreaStatus.NotDownloaded
            }
        })
    }

    fun deleteArea(areaId: String, regionId: Long) {
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val region = offlineRegions?.firstOrNull { it.id == regionId }
                region?.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() {
                        statusMap[areaId] = AreaStatus.NotDownloaded
                    }
                    override fun onError(error: String) {}
                })
            }
            override fun onError(error: String) {}
        })
    }
}

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

        // 2. Wichtige Bundesländer
        list.add(
            OfflineArea(
                id = "bw",
                name = "Baden-Württemberg",
                bounds = LatLngBounds.from(49.8, 10.5, 47.5, 7.5),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 350
            )
        )
        list.add(
            OfflineArea(
                id = "by",
                name = "Bayern",
                bounds = LatLngBounds.from(50.6, 13.9, 47.2, 8.9),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 500
            )
        )
        list.add(
            OfflineArea(
                id = "he",
                name = "Hessen",
                bounds = LatLngBounds.from(51.7, 10.3, 49.4, 7.7),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 280
            )
        )
        list.add(
            OfflineArea(
                id = "nrw",
                name = "Nordrhein-Westfalen",
                bounds = LatLngBounds.from(52.6, 9.5, 50.3, 5.8),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 450
            )
        )
        list.add(
            OfflineArea(
                id = "be_bb",
                name = "Berlin & Brandenburg",
                bounds = LatLngBounds.from(53.6, 14.8, 51.3, 11.2),
                minZoom = 10.0,
                maxZoom = 15.0,
                approxMb = 320
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

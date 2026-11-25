package dev.uwiii.nav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.uwiii.nav.offline.AreaStatus
import dev.uwiii.nav.offline.OfflineArea
import dev.uwiii.nav.offline.OfflineMapManager
import dev.uwiii.nav.ui.theme.*
import org.maplibre.android.geometry.LatLng

@Composable
fun OfflineMapsDialog(
    offlineManager: OfflineMapManager,
    currentLocation: LatLng?,
    onDismiss: () -> Unit
) {
    val areas = remember(currentLocation) {
        offlineManager.getStandardAreas(currentLocation)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header mit Titel und Schließen-Kreuz
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Offline-Karten",
                            color = TextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Karten ohne Internet nutzen",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DarkSurface80)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = TextWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Liste der Regionen
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(areas) { area ->
                        val status = offlineManager.statusMap[area.id] ?: AreaStatus.NotDownloaded
                        OfflineAreaItem(
                            area = area,
                            status = status,
                            onDownload = { offlineManager.downloadArea(area) },
                            onDelete = { regionId -> offlineManager.deleteArea(area.id, regionId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineAreaItem(
    area: OfflineArea,
    status: AreaStatus,
    onDownload: () -> Unit,
    onDelete: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkBackground)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = area.name,
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    when (status) {
                        is AreaStatus.NotDownloaded -> {
                            Text(
                                text = "ca. ${area.approxMb} MB",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        is AreaStatus.Downloading -> {
                            Text(
                                text = "Lade herunter: ${status.progressPercent}% (${String.format(java.util.Locale.US, "%.1f", status.downloadedMb)} MB)",
                                color = CyanPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        is AreaStatus.Downloaded -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SpeedGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Installiert (${String.format(java.util.Locale.US, "%.1f", status.sizeMb)} MB)",
                                    color = SpeedGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Aktions-Button
                when (status) {
                    is AreaStatus.NotDownloaded -> {
                        IconButton(
                            onClick = onDownload,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Herunterladen",
                                tint = Color.Black
                            )
                        }
                    }
                    is AreaStatus.Downloading -> {
                        CircularProgressIndicator(
                            progress = { status.progressPercent / 100f },
                            modifier = Modifier.size(32.dp),
                            color = CyanPrimary,
                            strokeWidth = 3.dp
                        )
                    }
                    is AreaStatus.Downloaded -> {
                        IconButton(
                            onClick = { onDelete(status.regionId) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SpeedRed.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Löschen",
                                tint = SpeedRed
                            )
                        }
                    }
                }
            }

            // Fortschrittsbalken beim Herunterladen
            if (status is AreaStatus.Downloading) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { status.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CyanPrimary,
                    trackColor = DarkSurface80
                )
            }
        }
    }
}

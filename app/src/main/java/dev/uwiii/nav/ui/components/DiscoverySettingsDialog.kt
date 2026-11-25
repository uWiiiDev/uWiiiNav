package dev.uwiii.nav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TravelExplore
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
import dev.uwiii.nav.discovery.DiscoveryItem
import dev.uwiii.nav.discovery.DiscoveryService
import dev.uwiii.nav.ui.theme.*

@Composable
fun DiscoverySettingsDialog(
    discoveryService: DiscoveryService,
    onTestItem: (DiscoveryItem) -> Unit,
    onDismiss: () -> Unit
) {
    var isEnabled by remember { mutableStateOf(discoveryService.isEnabled) }
    var durationSeconds by remember { mutableFloatStateOf(discoveryService.displayDurationSeconds.toFloat()) }
    var isAudioEnabled by remember { mutableStateOf(discoveryService.isAudioEnabled) }

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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TravelExplore,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Entdecker-Modus",
                                color = TextWhite,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Orte & Sehenswürdigkeiten in der Nähe",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
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

                Spacer(modifier = Modifier.height(18.dp))

                // Hauptschalter: Entdecker-Modus Ein / Aus
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isEnabled) CyanPrimary.copy(alpha = 0.12f) else DarkBackground)
                        .border(
                            1.dp,
                            if (isEnabled) CyanPrimary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            val newVal = !isEnabled
                            isEnabled = newVal
                            discoveryService.updateEnabled(newVal)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEnabled) "Entdecker-Modus aktiv" else "Entdecker-Modus ausgeschaltet",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isEnabled) "Wissenswertes während der Fahrt anzeigen" else "Keine automatischen Einblendungen",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            discoveryService.updateEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = CyanPrimary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurface80
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Anzeigedauer konfigurieren
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkBackground)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Anzeigedauer der Einblendung:",
                            color = TextWhite,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${durationSeconds.toInt()} Sekunden",
                            color = CyanPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = durationSeconds,
                        onValueChange = {
                            durationSeconds = it
                            discoveryService.updateDisplayDuration(it.toInt())
                        },
                        valueRange = 5f..30f,
                        steps = 24, // Schritte in 1s-Intervallen
                        colors = SliderDefaults.colors(
                            thumbColor = CyanPrimary,
                            activeTrackColor = CyanPrimary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )

                    // Schnellauswahl-Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(5, 8, 10, 15, 20, 30).forEach { sec ->
                            val isPreset = durationSeconds.toInt() == sec
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isPreset) CyanPrimary else DarkSurface80)
                                    .clickable {
                                        durationSeconds = sec.toFloat()
                                        discoveryService.updateDisplayDuration(sec)
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${sec}s",
                                    color = if (isPreset) Color.Black else TextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = if (isPreset) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Audio-Guide Switch: Ansagen vorlesen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkBackground)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .clickable {
                            val newVal = !isAudioEnabled
                            isAudioEnabled = newVal
                            discoveryService.updateAudioEnabled(newVal)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Audio-Guide (Vorlesen)",
                            color = TextWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Kurzinfo bei Erscheinen per Stimme sprechen",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = isAudioEnabled,
                        onCheckedChange = {
                            isAudioEnabled = it
                            discoveryService.updateAudioEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = CyanPrimary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurface80
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Test-Vorschau-Button
                Button(
                    onClick = {
                        val demoItem = DiscoveryItem(
                            id = 999999L,
                            title = "Schloss Neuschwanstein",
                            extract = "Das Schloss wurde ab 1869 für den bayerischen König Ludwig II. als idealisierte mittelalterliche Ritterburg errichtet.",
                            distanceMeters = 650.0,
                            lat = 47.5576,
                            lon = 10.7498
                        )
                        onTestItem(demoItem)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Vorschau testen (${durationSeconds.toInt()}s)",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

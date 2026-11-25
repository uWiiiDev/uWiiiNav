package dev.uwiii.nav.ui.components

import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
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
import dev.uwiii.nav.audio.VoiceNavigator
import dev.uwiii.nav.ui.theme.*

@Composable
fun VoiceSettingsDialog(
    voiceNavigator: VoiceNavigator,
    onDismiss: () -> Unit
) {
    val voices = remember { voiceNavigator.getGermanVoices() }
    val isMuted = voiceNavigator.isMuted
    val currentVoiceName = voiceNavigator.selectedVoiceName

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
                            text = "Sprachausgabe & Stimmen",
                            color = TextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isMuted) "Aktuell stummgeschaltet" else "Stimme für Navigationsansagen",
                            color = if (isMuted) SpeedYellow else TextMuted,
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

                // Hauptschalter: Sprachansagen an / stumm
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isMuted) SpeedYellow.copy(alpha = 0.12f) else CyanPrimary.copy(alpha = 0.12f))
                        .border(
                            1.dp,
                            if (isMuted) SpeedYellow.copy(alpha = 0.4f) else CyanPrimary.copy(alpha = 0.4f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { voiceNavigator.toggleMute() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = if (isMuted) SpeedYellow else CyanPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isMuted) "Sprachansagen: Stumm" else "Sprachansagen: Aktiv",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (isMuted) "Tippen zum Einschalten" else "Tippen zum Stummschalten",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Switch(
                        checked = !isMuted,
                        onCheckedChange = { voiceNavigator.toggleMute() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = CyanPrimary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurface80
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Verfügbare Stimmen (${voices.size}):",
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (voices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Standard-Systemstimme ist aktiv.\n(Weitere Stimmen in den Android-Einstellungen installierbar)",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                    ) {
                        itemsIndexed(voices) { index, voice ->
                            val isSelected = voice.name == currentVoiceName || (currentVoiceName == null && index == 0)
                            val displayName = getVoiceLabel(voice, index)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) CyanPrimary.copy(alpha = 0.15f) else DarkBackground)
                                    .border(
                                        1.dp,
                                        if (isSelected) CyanPrimary else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { voiceNavigator.selectVoice(voice) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayName,
                                        color = if (isSelected) CyanPrimary else TextWhite,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (voice.isNetworkConnectionRequired) "Online-Qualität" else "Offline verfügbar",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                IconButton(
                                    onClick = { voiceNavigator.selectVoice(voice) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) CyanPrimary else DarkSurface80)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Probehören",
                                        tint = if (isSelected) Color.Black else TextWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getVoiceLabel(voice: Voice, index: Int): String {
    val nameLower = voice.name.lowercase()
    val gender = when {
        nameLower.contains("male") || nameLower.contains("-deb-") || nameLower.contains("-dem-") -> "Männlich"
        nameLower.contains("female") || nameLower.contains("-dea-") || nameLower.contains("-deg-") || nameLower.contains("-def-") -> "Weiblich"
        else -> "Stimme ${index + 1}"
    }
    return "Deutsch $gender (${index + 1})"
}

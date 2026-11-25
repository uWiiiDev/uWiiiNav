package dev.uwiii.nav.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.uwiii.nav.traffic.IncidentType
import dev.uwiii.nav.traffic.TrafficIncident
import dev.uwiii.nav.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun TrafficAlertBanner(
    incident: TrafficIncident?,
    distanceMeters: Double,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = incident != null,
        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        if (incident == null) return@AnimatedVisibility

        val (accentColor, iconVector, badgeLabel) = when (incident.type) {
            IncidentType.CLOSURE -> Triple(SpeedRed, Icons.Default.Block, "Sperrung")
            IncidentType.ACCIDENT -> Triple(SpeedRed, Icons.Default.Warning, "Unfall")
            IncidentType.TRAFFIC_JAM -> Triple(Color(0xFFFF9800), Icons.Default.Traffic, "Stau")
            IncidentType.ROADWORKS -> Triple(SpeedYellow, Icons.Default.Construction, "Baustelle")
            IncidentType.HAZARD -> Triple(SpeedYellow, Icons.Default.WarningAmber, "Gefahr")
        }

        val distText = if (distanceMeters >= 1000) {
            val km = (distanceMeters / 100.0).roundToInt() / 10.0
            "$km km"
        } else {
            "${(distanceMeters / 50.0).roundToInt() * 50} m"
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1010).copy(alpha = 0.96f))
                .border(1.5.dp, accentColor, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Autobahn Pill & Warn-Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Autobahn-Schild-Look (Blau mit weißem Rand und Text)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF003399))
                            .border(1.dp, Color.White, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = incident.road,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$badgeLabel in $distText",
                                color = accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = incident.title,
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (incident.subtitle.isNotBlank() || incident.description.isNotBlank()) {
                            Text(
                                text = incident.subtitle.ifBlank { incident.description },
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(DarkSurface80)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Schließen",
                        tint = TextWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

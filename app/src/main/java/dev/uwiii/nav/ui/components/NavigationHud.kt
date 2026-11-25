package dev.uwiii.nav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.uwiii.nav.routing.RouteData
import dev.uwiii.nav.routing.RouteStep
import dev.uwiii.nav.ui.theme.*

@Composable
fun NavigationTopBanner(
    step: RouteStep?,
    distanceToStepMeters: Double,
    modifier: Modifier = Modifier
) {
    if (step == null) return

    val icon = getManeuverIcon(step.maneuverType, step.maneuverModifier)
    val formattedDist = formatDistance(distanceToStepMeters)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkSurface80)
            .border(1.5.dp, CyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Abbiege-Symbol im Kreis
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(CyanPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formattedDist,
                    color = CyanPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = step.instruction,
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun NavigationBottomBar(
    routeData: RouteData,
    onCancelRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface80)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ankunftszeit & Fahrzeit
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = routeData.etaFormatted,
                        color = SpeedGreen,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ankunft",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = routeData.durationMin,
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "•",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                    Text(
                        text = routeData.distanceKm,
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Route beenden Button
            IconButton(
                onClick = onCancelRoute,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SpeedRed.copy(alpha = 0.2f))
                    .border(1.dp, SpeedRed, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Navigation beenden",
                    tint = SpeedRed
                )
            }
        }
    }
}

private fun getManeuverIcon(type: String, modifier: String): ImageVector {
    return when {
        type == "arrive" -> Icons.Default.Flag
        type == "roundabout" -> Icons.Default.Refresh
        modifier.contains("right") -> Icons.Default.TurnRight
        modifier.contains("left") -> Icons.Default.TurnLeft
        modifier.contains("uturn") -> Icons.Default.Undo
        else -> Icons.Default.ArrowUpward
    }
}

private fun formatDistance(meters: Double): String {
    return when {
        meters >= 1000 -> String.format(java.util.Locale.GERMAN, "%.1f km", meters / 1000.0)
        else -> "${meters.toInt().coerceAtLeast(0)} m"
    }
}

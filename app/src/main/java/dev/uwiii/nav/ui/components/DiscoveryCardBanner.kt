package dev.uwiii.nav.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.uwiii.nav.discovery.DiscoveryItem
import dev.uwiii.nav.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun DiscoveryCardBanner(
    item: DiscoveryItem?,
    displayDurationSeconds: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPaused by remember { mutableStateOf(false) }
    var remainingMillis by remember(item) { mutableIntStateOf(displayDurationSeconds * 1000) }
    val totalMillis = displayDurationSeconds * 1000

    LaunchedEffect(item, isPaused) {
        if (item == null) return@LaunchedEffect
        remainingMillis = displayDurationSeconds * 1000
        val interval = 100L
        while (remainingMillis > 0) {
            delay(interval)
            if (!isPaused) {
                remainingMillis -= interval.toInt()
            }
        }
        if (remainingMillis <= 0 && !isPaused) {
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = item != null,
        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        if (item == null) return@AnimatedVisibility

        val distText = if (item.distanceMeters >= 1000) {
            val km = (item.distanceMeters / 100.0).roundToInt() / 10.0
            "$km km"
        } else {
            "${(item.distanceMeters / 50.0).roundToInt() * 50} m"
        }

        val progress = (remainingMillis.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(durationMillis = 100, easing = LinearEasing),
            label = "discovery_progress"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkSurface.copy(alpha = 0.96f),
                            Color(0xFF142436).copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        colors = listOf(
                            CyanPrimary.copy(alpha = 0.8f),
                            Color(0xFFFFB300).copy(alpha = 0.5f)
                        )
                    ),
                    RoundedCornerShape(20.dp)
                )
                .clickable { isPaused = !isPaused }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(CyanPrimary.copy(alpha = 0.18f))
                                .border(1.dp, CyanPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TravelExplore,
                                contentDescription = "Entdecker-Modus",
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.title,
                                    color = TextWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• $distText",
                                    color = Color(0xFFFFB300),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = if (isPaused) "Angehalten • Tippen zum Fortsetzen" else "Wissenswertes in deiner Nähe",
                                color = if (isPaused) Color(0xFFFFB300) else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(DarkSurface80)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = item.extract,
                    color = TextWhite.copy(alpha = 0.9f),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Countdown-Fortschrittsbalken am Kartenboden
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (isPaused) Color(0xFFFFB300) else CyanPrimary,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
            }
        }
    }
}

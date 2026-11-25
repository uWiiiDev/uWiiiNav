package dev.uwiii.nav.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.uwiii.nav.camera.SpeedCamera
import dev.uwiii.nav.ui.theme.DarkSurface80
import dev.uwiii.nav.ui.theme.SpeedRed
import dev.uwiii.nav.ui.theme.TextMuted
import dev.uwiii.nav.ui.theme.TextWhite
import kotlin.math.roundToInt

@Composable
fun SpeedCameraAlertBanner(
    camera: SpeedCamera?,
    distanceMeters: Double,
    currentSpeedKmh: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = camera != null,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        if (camera == null) return@AnimatedVisibility

        val isSpeeding = camera.maxSpeed != null && currentSpeedKmh > camera.maxSpeed
        val borderColor = if (isSpeeding) SpeedRed else Color(0xFFFFB300)

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(DarkSurface80)
                .border(2.dp, borderColor, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Deutsches Verkehrsschild: Tempolimit (Runder roter Kreis mit weißer Fläche)
                if (camera.maxSpeed != null) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(4.dp, SpeedRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${camera.maxSpeed}",
                            color = Color.Black,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(SpeedRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Text & Countdown
                Column {
                    Text(
                        text = if (isSpeeding) "Geschwindigkeit anpassen!" else "Gefahrenstelle",
                        color = if (isSpeeding) SpeedRed else TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "in ${(distanceMeters.roundToInt() / 10) * 10} m",
                            color = borderColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "feste Überwachung",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

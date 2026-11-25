package dev.uwiii.nav.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.uwiii.nav.ui.theme.SpeedRed

@Composable
fun SpeedLimitSign(
    speedLimit: Int?,
    currentSpeedKmh: Float,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    if (speedLimit == null) return

    val isOverSpeed = currentSpeedKmh > (speedLimit + 5)

    // Pulsierender roter Rand bei Tempoüberschreitung
    val infiniteTransition = rememberInfiniteTransition(label = "overspeed_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .border(
                width = if (isOverSpeed) 4.5.dp else 3.5.dp,
                color = if (isOverSpeed) SpeedRed.copy(alpha = pulseAlpha) else SpeedRed,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        val fontSize = if (speedLimit >= 100) (size.value * 0.38).sp else (size.value * 0.44).sp
        Text(
            text = "$speedLimit",
            color = Color.Black,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif
        )
    }
}

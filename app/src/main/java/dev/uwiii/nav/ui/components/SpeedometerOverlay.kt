package dev.uwiii.nav.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.uwiii.nav.ui.theme.DarkSurface80
import dev.uwiii.nav.ui.theme.SpeedGreen
import dev.uwiii.nav.ui.theme.SpeedRed
import dev.uwiii.nav.ui.theme.SpeedYellow
import dev.uwiii.nav.ui.theme.TextMuted
import dev.uwiii.nav.ui.theme.TextWhite
import kotlin.math.roundToInt

@Composable
fun SpeedometerOverlay(
    speedKmh: Float,
    modifier: Modifier = Modifier
) {
    val roundedSpeed = speedKmh.roundToInt().coerceAtLeast(0)
    val accentColor = when {
        roundedSpeed > 130 -> SpeedRed
        roundedSpeed > 100 -> SpeedYellow
        else -> SpeedGreen
    }

    Box(
        modifier = modifier
            .width(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface80)
            .border(2.dp, accentColor.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$roundedSpeed",
                color = TextWhite,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = "km/h",
                color = TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

package dev.uwiii.nav.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.uwiii.nav.routing.LaneInfo
import dev.uwiii.nav.ui.theme.DarkBackground
import dev.uwiii.nav.ui.theme.SpeedGreen
import dev.uwiii.nav.ui.theme.TextMuted

@Composable
fun LaneGuidanceBar(
    lanes: List<LaneInfo>,
    modifier: Modifier = Modifier
) {
    if (lanes.isEmpty()) return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DarkBackground.copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        lanes.forEach { lane ->
            LaneBox(lane = lane)
        }
    }
}

@Composable
fun LaneBox(
    lane: LaneInfo,
    modifier: Modifier = Modifier
) {
    val isValid = lane.isValid
    val arrowColor = if (isValid) SpeedGreen else TextMuted.copy(alpha = 0.45f)
    val bgColor = if (isValid) SpeedGreen.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f)
    val borderColor = if (isValid) SpeedGreen else Color.White.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .size(width = 32.dp, height = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(if (isValid) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(24.dp, 28.dp)
        ) {
            val strokeWidth = 2.4.dp.toPx()
            val w = size.width
            val h = size.height
            val midX = w / 2f
            val baseY = h - 3f

            val indications = if (lane.indications.isEmpty()) listOf("straight") else lane.indications

            indications.forEach { ind ->
                drawLaneArrow(
                    indication = ind,
                    color = arrowColor,
                    strokeWidth = strokeWidth,
                    w = w,
                    h = h,
                    midX = midX,
                    baseY = baseY
                )
            }
        }
    }
}

private fun DrawScope.drawLaneArrow(
    indication: String,
    color: Color,
    strokeWidth: Float,
    w: Float,
    h: Float,
    midX: Float,
    baseY: Float
) {
    val strokeStyle = Stroke(
        width = strokeWidth,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )
    val headSize = 5.5.dp.toPx()

    when (indication) {
        "straight", "none" -> {
            val topY = 4f
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(midX, baseY),
                end = androidx.compose.ui.geometry.Offset(midX, topY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            val headPath = Path().apply {
                moveTo(midX - headSize, topY + headSize)
                lineTo(midX, topY)
                lineTo(midX + headSize, topY + headSize)
            }
            drawPath(headPath, color, style = strokeStyle)
        }
        "left" -> {
            val endX = 3f
            val endY = 8f
            val path = Path().apply {
                moveTo(midX, baseY)
                cubicTo(midX, baseY - 6f, endX + 4f, baseY - 6f, endX, endY)
            }
            drawPath(path, color, style = strokeStyle)
            val headPath = Path().apply {
                moveTo(endX + headSize, endY - headSize * 0.8f)
                lineTo(endX, endY)
                lineTo(endX + headSize, endY + headSize * 0.8f)
            }
            drawPath(headPath, color, style = strokeStyle)
        }
        "right" -> {
            val endX = w - 3f
            val endY = 8f
            val path = Path().apply {
                moveTo(midX, baseY)
                cubicTo(midX, baseY - 6f, endX - 4f, baseY - 6f, endX, endY)
            }
            drawPath(path, color, style = strokeStyle)
            val headPath = Path().apply {
                moveTo(endX - headSize, endY - headSize * 0.8f)
                lineTo(endX, endY)
                lineTo(endX - headSize, endY + headSize * 0.8f)
            }
            drawPath(headPath, color, style = strokeStyle)
        }
        "slight left" -> {
            val endX = 4f
            val endY = 5f
            val path = Path().apply {
                moveTo(midX, baseY)
                quadraticTo(midX - 2f, h * 0.5f, endX, endY)
            }
            drawPath(path, color, style = strokeStyle)
            val headPath = Path().apply {
                moveTo(endX + headSize * 1.1f, endY)
                lineTo(endX, endY)
                lineTo(endX, endY + headSize * 1.1f)
            }
            drawPath(headPath, color, style = strokeStyle)
        }
        "slight right" -> {
            val endX = w - 4f
            val endY = 5f
            val path = Path().apply {
                moveTo(midX, baseY)
                quadraticTo(midX + 2f, h * 0.5f, endX, endY)
            }
            drawPath(path, color, style = strokeStyle)
            val headPath = Path().apply {
                moveTo(endX - headSize * 1.1f, endY)
                lineTo(endX, endY)
                lineTo(endX, endY + headSize * 1.1f)
            }
            drawPath(headPath, color, style = strokeStyle)
        }
        "uturn" -> {
            val loopTop = 5f
            val leftX = 4f
            val rightX = midX + 3f
            val path = Path().apply {
                moveTo(rightX, baseY)
                lineTo(rightX, loopTop + 6f)
                cubicTo(rightX, loopTop, leftX, loopTop, leftX, loopTop + 6f)
                lineTo(leftX, baseY - 4f)
            }
            drawPath(path, color, style = strokeStyle)
            val headPath = Path().apply {
                moveTo(leftX - headSize * 0.7f, baseY - 4f - headSize * 0.7f)
                lineTo(leftX, baseY - 4f)
                lineTo(leftX + headSize * 0.7f, baseY - 4f - headSize * 0.7f)
            }
            drawPath(headPath, color, style = strokeStyle)
        }
        else -> {
            val topY = 4f
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(midX, baseY),
                end = androidx.compose.ui.geometry.Offset(midX, topY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            val headPath = Path().apply {
                moveTo(midX - headSize, topY + headSize)
                lineTo(midX, topY)
                lineTo(midX + headSize, topY + headSize)
            }
            drawPath(headPath, color, style = strokeStyle)
        }
    }
}

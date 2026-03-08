package com.vesc0.heartratemonitor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun HeartTimerView(
    heartScale: Float,
    secondsLeft: Int,
    totalSeconds: Int,
    heartSize: Dp = 96.dp,
    color: Color = Color.Red
) {
    val progress by animateFloatAsState(
        targetValue = if (totalSeconds > 0) secondsLeft.toFloat() / totalSeconds else 0f,
        animationSpec = tween(250),
        label = "progress"
    )

    val ringInset = 72.dp
    val ringLineWidth = 14.dp
    val totalSize = heartSize + ringInset

    val animatedScale by animateFloatAsState(
        targetValue = heartScale,
        animationSpec = tween(120),
        label = "scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(totalSize)
    ) {
        // Background ring
        Canvas(modifier = Modifier.size(totalSize)) {
            drawCircle(
                color = color.copy(alpha = 0.15f),
                style = Stroke(width = ringLineWidth.toPx())
            )
        }

        // Foreground countdown ring
        Canvas(modifier = Modifier.size(totalSize)) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = ringLineWidth.toPx(), cap = StrokeCap.Round)
            )
        }

        // Heart icon
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Heart",
            tint = Color.Red,
            modifier = Modifier.size(heartSize * animatedScale)
        )
    }
}

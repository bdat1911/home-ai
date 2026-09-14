package com.example.revroom.features.design_studio.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.revroom.core.ui.StudioPink
import com.example.revroom.core.ui.StudioPurple

/**
 * Premium Design Studio loader with a soft glow and layered rotating arcs.
 */
@Composable
fun FanSpinnerAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "design_loader")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loader_rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loader_pulse"
    )

    Canvas(modifier = modifier.size(72.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val diameter = size.minDimension
        val ringRadius = diameter * 0.38f
        val ringStroke = diameter * 0.075f
        val ringTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
        val ringSize = Size(ringRadius * 2f, ringRadius * 2f)
        val ringStyle = Stroke(width = ringStroke, cap = StrokeCap.Round)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    StudioPink.copy(alpha = 0.22f),
                    StudioPurple.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = center,
                radius = diameter * 0.44f
            ),
            radius = diameter * 0.44f * pulse,
            center = center
        )

        drawCircle(
            color = StudioPurple.copy(alpha = 0.12f),
            radius = ringRadius,
            center = center,
            style = ringStyle
        )

        rotate(degrees = rotation, pivot = center) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        StudioPurple.copy(alpha = 0.15f),
                        StudioPurple,
                        StudioPink,
                        StudioPurple.copy(alpha = 0.15f)
                    ),
                    center = center
                ),
                startAngle = -90f,
                sweepAngle = 285f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = ringStyle
            )
        }

        rotate(degrees = -rotation * 0.72f, pivot = center) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        StudioPink.copy(alpha = 0.0f),
                        StudioPink.copy(alpha = 0.55f),
                        StudioPurple.copy(alpha = 0.0f)
                    ),
                    center = center
                ),
                startAngle = 80f,
                sweepAngle = 115f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringStroke * 0.58f, cap = StrokeCap.Round)
            )
        }

        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(StudioPurple, StudioPink),
                start = Offset(center.x - diameter * 0.14f, center.y - diameter * 0.14f),
                end = Offset(center.x + diameter * 0.14f, center.y + diameter * 0.14f)
            ),
            radius = diameter * 0.105f * pulse,
            center = center
        )

        drawCircle(
            color = Color.White.copy(alpha = 0.86f),
            radius = diameter * 0.042f * pulse,
            center = center
        )
    }
}

package com.example.revroom.features.design_studio.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay

@Composable
fun AutoBeforeAfterPreview(
    beforeImageRes: Int?,
    afterImageRes: Int?,
    fallbackColors: List<Color>,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        while (true) {
            delay(HOLD_DELAY_MILLIS)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = SLIDE_DURATION_MILLIS,
                    easing = FastOutSlowInEasing
                )
            )
            delay(HOLD_DELAY_MILLIS)
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SLIDE_DURATION_MILLIS,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }
    val hasImages = beforeImageRes != null && afterImageRes != null
    val colors = fallbackColors.ifEmpty {
        listOf(Color(0xFFE5E7EB), Color(0xFF6B7280), Color(0xFF111827))
    }
    val clippedProgress = progress.value.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        if (hasImages) {
            Image(
                painter = painterResource(id = afterImageRes!!),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = colors.reversed()
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = size.width * clippedProgress) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            if (hasImages) {
                Image(
                    painter = painterResource(id = beforeImageRes!!),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = colors
                            )
                        )
                )
            }
        }
    }
}

private const val HOLD_DELAY_MILLIS = 1100L
private const val SLIDE_DURATION_MILLIS = 550

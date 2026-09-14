package com.example.revroom.features.design_studio.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlin.math.roundToInt

/**
 * Before/After image comparison slider.
 * Layer 1: After image (full)
 * Layer 2: Before image clipped to slider position
 * Layer 3: White divider line
 * Layer 4: Draggable circular handle
 *
 * Shows error placeholder with icon when image fails to load.
 */
@Composable
fun BeforeAfterSlider(
    beforeImageUrl: String,
    afterImageUrl: String,
    modifier: Modifier = Modifier
) {
    var fraction by remember { mutableFloatStateOf(0.5f) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var imageAspectRatio by remember(beforeImageUrl, afterImageUrl) { mutableStateOf<Float?>(null) }
    val comparisonAspectRatio = (imageAspectRatio ?: 1f).coerceIn(0.75f, 1.8f)

    fun updateAspectRatio(size: Size) {
        size.toImageAspectRatio()?.let { aspectRatio ->
            imageAspectRatio = aspectRatio
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .heightIn(max = 520.dp)
            .aspectRatio(comparisonAspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE5E7EB))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    containerWidthPx = size.width.toFloat()
                }
                .pointerInput(containerWidthPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (containerWidthPx > 0f) {
                            fraction = (fraction + dragAmount.x / containerWidthPx).coerceIn(0f, 1f)
                        }
                    }
                }
        ) {
            // Layer 1: After image (full size, underneath)
            SubcomposeAsyncImage(
                model = afterImageUrl,
                contentDescription = "Ảnh thiết kế",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                success = { state ->
                    LaunchedEffect(state.painter.intrinsicSize) {
                        updateAspectRatio(state.painter.intrinsicSize)
                    }
                    SubcomposeAsyncImageContent()
                },
                error = { ImageErrorPlaceholder("Lỗi tải ảnh thiết kế") }
            )

            // Layer 2: Before image clipped to slider position
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(right = size.width * fraction.coerceIn(0f, 1f)) {
                            this@drawWithContent.drawContent()
                        }
                    }
            ) {
                SubcomposeAsyncImage(
                    model = beforeImageUrl,
                    contentDescription = "Ảnh gốc",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    success = { state ->
                        LaunchedEffect(state.painter.intrinsicSize) {
                            if (imageAspectRatio == null) {
                                updateAspectRatio(state.painter.intrinsicSize)
                            }
                        }
                        SubcomposeAsyncImageContent()
                    },
                    error = { ImageErrorPlaceholder("Lỗi tải ảnh gốc") }
                )
            }

            // Layer 3: White divider line
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(x = (containerWidthPx * fraction).roundToInt() - 1, y = 0)
                    }
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.White)
            )

            // Layer 4: Draggable circular handle
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (containerWidthPx * fraction).roundToInt() - 22.dp.roundToPx(),
                            y = 0
                        )
                    }
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
                    contentDescription = "Kéo để so sánh",
                    tint = Color(0xFF111827)
                )
            }
        }
    }
}

private fun Size.toImageAspectRatio(): Float? {
    return if (width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
        width / height
    } else {
        null
    }
}

@Composable
private fun ImageErrorPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE5E7EB)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = null,
                tint = Color(0xFF6B7280),
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280)
            )
        }
    }
}

package com.example.revroom.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class StudioTab {
    Interior,
    Exterior,
    Chat,
    Gallery
}

val StudioPurple = Color(0xFF934BFF)
val StudioPink = Color(0xFFFF3DA2)
val StudioBackground = Color(0xFFF6F7F8)
val StudioMuted = Color(0xFF9CA3AF)
val StudioText = Color(0xFF111827)
val StudioGradient = Brush.horizontalGradient(listOf(StudioPurple, StudioPink))

@Composable
fun StudioScaffold(
    selectedTab: StudioTab,
    modifier: Modifier = Modifier,
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onChat: () -> Unit,
    onGallery: () -> Unit,
    horizontalPadding: Dp = 18.dp,
    showBottomBar: Boolean = true,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .background(StudioBackground)
            .padding(horizontal = horizontalPadding)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            content()
        }

        if (showBottomBar) {
            StudioBottomBar(
                selectedTab = selectedTab,
                onInterior = onInterior,
                onExterior = onExterior,
                onChat = onChat,
                onGallery = onGallery
            )
        }
    }
}

@Composable
fun StudioBottomBar(
    selectedTab: StudioTab,
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onChat: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = Color.White,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BottomBarItem("Interior", Icons.Outlined.Home, selectedTab == StudioTab.Interior, onInterior, Modifier.weight(1f))
            BottomBarItem("Exterior", Icons.Outlined.Landscape, selectedTab == StudioTab.Exterior, onExterior, Modifier.weight(1f))
            BottomBarItem("Chat", Icons.Outlined.ChatBubbleOutline, selectedTab == StudioTab.Chat, onChat, Modifier.weight(1f))
            BottomBarItem("Gallery", Icons.Outlined.Image, selectedTab == StudioTab.Gallery, onGallery, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val color = if (selected) Color.White else StudioMuted
    val selectedBackground = if (selected) {
        Modifier
            .clip(shape)
            .background(StudioGradient)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .then(selectedBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
            Text(
                text = label,
                color = color,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TopTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            leading?.invoke()
        }

        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 56.dp),
            color = StudioText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(48.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            trailing?.invoke()
        }
    }
}

@Composable
fun StepProgress(
    currentStep: Int,
    caption: String,
    modifier: Modifier = Modifier,
    totalSteps: Int = 4
) {
    val stepCount = totalSteps.coerceAtLeast(1)
    val activeStep = currentStep.coerceIn(1, stepCount)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.78f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (step in 1..stepCount) {
                StepDot(step = step, currentStep = activeStep)
                if (step != stepCount) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (step < activeStep) StudioPink else Color(0xFFE1E4E8))
                    )
                }
            }
        }
        Text(
            text = caption,
            modifier = Modifier.padding(top = 6.dp),
            color = StudioMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StepDot(step: Int, currentStep: Int) {
    val isCurrent = step == currentStep
    val isDone = step < currentStep
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (isCurrent || isDone) StudioGradient else Brush.horizontalGradient(
                    listOf(
                        Color(0xFFE1E4E8),
                        Color(0xFFE1E4E8)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isDone) {
            Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        } else {
            Text(
                text = step.toString(),
                color = if (isCurrent) Color.White else StudioMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .alpha(if (enabled) 1f else 0.45f)
            .background(StudioGradient)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text = text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun BackOutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(2.dp, StudioPurple, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = StudioPurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

package com.example.revroom.features.design_studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.revroom.core.ui.StudioScaffold
import com.example.revroom.core.ui.StudioTab
import com.example.revroom.core.ui.StudioText
import com.example.revroom.features.design_studio.components.AutoBeforeAfterPreview
import com.example.revroom.features.design_studio.viewmodel.DesignViewModel
import com.example.revroom.core.theme.RevroomTheme

@Composable
fun DesignHomeScreen(
    title: String,
    chips: List<String>,
    features: List<DesignViewModel.DesignFeatureItem>,
    selectedTab: StudioTab,
    onFeatureClick: (String) -> Unit,
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onChat: () -> Unit,
    onGallery: () -> Unit,
    showBottomBar: Boolean = true
) {
    StudioScaffold(
        selectedTab = selectedTab,
        horizontalPadding = 8.dp,
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        onInterior = onInterior,
        onExterior = onExterior,
        onChat = onChat,
        onGallery = onGallery,
        showBottomBar = showBottomBar
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val featureCardHeight = calculateFeatureCardHeight(
                availableHeight = maxHeight,
                featureCount = features.size
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 12.dp)
            ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    color = StudioText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    chips.forEach { chip ->
                        CategoryChip(
                            text = chip,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    features.forEach { feature ->
                        FeatureCard(
                            feature = feature,
                            height = featureCardHeight,
                            onClick = { onFeatureClick(feature.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFFF0F2F5))
            .border(1.dp, Color(0xFFE0E4EA), RoundedCornerShape(15.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF4B5563),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeatureCard(
    feature: DesignViewModel.DesignFeatureItem,
    height: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        AutoBeforeAfterPreview(
            beforeImageRes = feature.beforeImageRes,
            afterImageRes = feature.afterImageRes,
            fallbackColors = feature.colors,
            modifier = Modifier.matchParentSize()
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x66000000), Color(0xCC000000))
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, end = 72.dp, bottom = 17.dp)
        ) {
            Text(
                text = feature.title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = feature.subtitle,
                color = Color(0xFFE5E7EB),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x55FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun calculateFeatureCardHeight(
    availableHeight: Dp,
    featureCount: Int
): Dp {
    if (featureCount <= 0) {
        return 156.dp
    }

    val fixedContentHeight = 16.dp + 30.dp + 12.dp + 56.dp + 14.dp + 12.dp
    val interCardSpacing = 12.dp * (featureCount - 1)
    val availableForCards = availableHeight - fixedContentHeight - interCardSpacing
    return (availableForCards / featureCount).coerceIn(156.dp, 192.dp)
}

@Preview(showBackground = true)
@Composable
fun DesignHomeScreenPreview() {
    RevroomTheme {
        DesignHomeScreen(
            title = "Design Studio",
            chips = listOf("Living Room", "Bedroom", "Kitchen", "Office"),
            features = listOf(
                DesignViewModel.DesignFeatureItem(
                    id = "interior_design",
                    title = "Interior Design",
                    subtitle = "Redesign your interior space",
                    colors = listOf(Color(0xFFE8E2D8), Color(0xFF75675B), Color(0xFF181818))
                ),
                DesignViewModel.DesignFeatureItem(
                    id = "exterior_design",
                    title = "Exterior Design",
                    subtitle = "Redesign your exterior space",
                    colors = listOf(Color(0xFFC6D8A8), Color(0xFF4D744B), Color(0xFF182512))
                )
            ),
            selectedTab = StudioTab.Interior,
            onFeatureClick = {},
            onInterior = {},
            onExterior = {},
            onChat = {},
            onGallery = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FeatureCardPreview() {
    RevroomTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            FeatureCard(
                feature = DesignViewModel.DesignFeatureItem(
                    id = "interior_design",
                    title = "Interior Design",
                    subtitle = "Redesign your interior space",
                    colors = listOf(Color(0xFFE8E2D8), Color(0xFF75675B), Color(0xFF181818))
                ),
                height = 176.dp,
                onClick = {}
            )
        }
    }
}

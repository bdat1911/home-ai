package com.example.revroom.features.design_studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.revroom.core.ui.BackOutlineButton
import com.example.revroom.core.ui.GradientButton
import com.example.revroom.core.ui.StepProgress
import com.example.revroom.core.ui.StudioGradient
import com.example.revroom.core.ui.StudioMuted
import com.example.revroom.core.ui.StudioScaffold
import com.example.revroom.core.ui.StudioTab
import com.example.revroom.core.ui.StudioText
import com.example.revroom.core.ui.TopTitleBar
import com.example.revroom.features.design_studio.viewmodel.DesignViewModel

@Composable
fun StyleScreen(
    title: String,
    stepCaption: String = "Choose your style",
    selectedTab: StudioTab,
    selectedStyle: String?,
    styles: List<DesignViewModel.SelectionItem>,
    onStyleSelected: (String) -> Unit,
    onCreateDesign: () -> Unit,
    onBack: () -> Unit,
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onChat: () -> Unit,
    onGallery: () -> Unit,
    totalSteps: Int = 4,
    showBottomBar: Boolean = true
) {
    StudioScaffold(
        selectedTab = selectedTab,
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        onInterior = onInterior,
        onExterior = onExterior,
        onChat = onChat,
        onGallery = onGallery,
        showBottomBar = showBottomBar
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopTitleBar(
                title = title,
                modifier = Modifier.padding(top = 16.dp),
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = StudioText
                        )
                    }
                }
            )

            StepProgress(
                currentStep = 3,
                caption = stepCaption,
                modifier = Modifier.padding(top = 6.dp),
                totalSteps = totalSteps
            )

            Text(
                text = stepCaption,
                modifier = Modifier.padding(top = 24.dp),
                color = StudioGradientColor,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (selectedTab == StudioTab.Exterior) {
                    "Select your preferred outdoor aesthetic"
                } else {
                    "Select your preferred interior look"
                },
                modifier = Modifier.padding(top = 4.dp),
                color = StudioMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(styles, key = { it.id }) { style ->
                    StyleSelectionItem(
                        item = style,
                        selected = selectedStyle == style.id,
                        onClick = { onStyleSelected(style.id) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BackOutlineButton(
                    text = "Back",
                    modifier = Modifier.weight(1f),
                    onClick = onBack
                )
                GradientButton(
                    text = "Generate design",
                    icon = Icons.Outlined.AutoAwesome,
                    enabled = selectedStyle != null,
                    modifier = Modifier.weight(2f),
                    onClick = onCreateDesign
                )
            }
        }
    }
}

@Composable
private fun StyleSelectionItem(
    item: DesignViewModel.SelectionItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(if (item.id == "custom") StudioGradient else Brush.linearGradient(item.colors))
                .border(if (selected) 3.dp else 0.dp, StudioGradient, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (item.id == "custom") {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x33000000))))
                )
            }
        }
        Text(
            text = item.label,
            modifier = Modifier.padding(top = 9.dp),
            color = if (selected) StudioGradientColor else Color(0xFF242A32),
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

private val StudioGradientColor = Color(0xFFC744D9)

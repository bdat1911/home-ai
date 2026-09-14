package com.example.revroom.features.design_studio.ui

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.revroom.core.theme.RevroomTheme
import com.example.revroom.core.ui.BackOutlineButton
import com.example.revroom.core.ui.GradientButton
import com.example.revroom.core.ui.StepProgress
import com.example.revroom.core.ui.StudioGradient
import com.example.revroom.core.ui.StudioScaffold
import com.example.revroom.core.ui.StudioTab
import com.example.revroom.core.ui.StudioText
import com.example.revroom.core.ui.TopTitleBar
import com.example.revroom.features.design_studio.viewmodel.DesignViewModel

@Composable
fun RoomTypeScreen(
    title: String = "Interior Design",
    stepCaption: String = "What room is this?",
    selectedRoomType: String?,
    roomTypes: List<DesignViewModel.SelectionItem>,
    onRoomTypeSelected: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onChat: () -> Unit,
    onGallery: () -> Unit,
    totalSteps: Int = 4,
    showBottomBar: Boolean = true
) {
    StudioScaffold(
        selectedTab = StudioTab.Interior,
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
                currentStep = 2,
                caption = stepCaption,
                modifier = Modifier.padding(top = 6.dp),
                totalSteps = totalSteps
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                userScrollEnabled = false
            ) {
                items(roomTypes, key = { it.id }) { room ->
                    RoundSelectionItem(
                        item = room,
                        selected = selectedRoomType == room.id,
                        onClick = { onRoomTypeSelected(room.id) }
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
                    text = "Next",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    enabled = selectedRoomType != null,
                    modifier = Modifier.weight(2f),
                    onClick = onNext
                )
            }
        }
    }
}

@Composable
fun RoundSelectionItem(
    item: DesignViewModel.SelectionItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(if (selected) 3.dp else 0.dp, StudioGradient, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (item.imageRes != null) {
                Image(
                    painter = painterResource(id = item.imageRes),
                    contentDescription = item.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(item.colors))
                )
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
            color = if (selected) StudioText else Color(0xFF242A32),
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RoomTypeScreenPreview() {
    val sampleRoomTypes = listOf(
        DesignViewModel.SelectionItem("living_room", "Living Room", listOf(Color(0xFFD8C3A5), Color(0xFF735F4D))),
        DesignViewModel.SelectionItem("bedroom", "Bedroom", listOf(Color(0xFFE6D8CC), Color(0xFF78909C))),
        DesignViewModel.SelectionItem("kitchen", "Kitchen", listOf(Color(0xFFDCE8E4), Color(0xFF7E8D85))),
        DesignViewModel.SelectionItem("bathroom", "Bathroom", listOf(Color(0xFFE7E1D4), Color(0xFFA79F93))),
        DesignViewModel.SelectionItem("dining_room", "Dining Room", listOf(Color(0xFFECE4D7), Color(0xFFB48B58))),
        DesignViewModel.SelectionItem("hallway", "Hallway", listOf(Color(0xFFE8E2DB), Color(0xFFB9A18D))),
    )
    RevroomTheme {
        RoomTypeScreen(
            selectedRoomType = "living_room",
            roomTypes = sampleRoomTypes,
            onRoomTypeSelected = {},
            onNext = {},
            onBack = {},
            onInterior = {},
            onExterior = {},
            onChat = {},
            onGallery = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RoundSelectionItemPreview() {
    RevroomTheme {
        RoundSelectionItem(
            item = DesignViewModel.SelectionItem(
                "living_room",
                "Living Room",
                listOf(Color(0xFFD8C3A5), Color(0xFF735F4D))
            ),
            selected = true,
            onClick = {}
        )
    }
}

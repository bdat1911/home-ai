package com.example.revroom.features.design_studio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.revroom.features.design_studio.components.BeforeAfterSlider
import com.example.revroom.features.design_studio.model.DesignUiState
import com.example.revroom.features.design_studio.model.selectedFeatureResultTitle
import com.example.revroom.core.theme.RevroomTheme

@Composable
fun ResultScreen(
    uiState: DesignUiState,
    onCreateAnother: () -> Unit
) {
    val originalImageUrl = uiState.originalImageUrl
    val designedImageUrl = uiState.designedImageUrl

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(text = uiState.selectedFeatureResultTitle, style = MaterialTheme.typography.headlineMedium)

            if (originalImageUrl != null && designedImageUrl != null) {
                BeforeAfterSlider(
                    beforeImageUrl = originalImageUrl,
                    afterImageUrl = designedImageUrl,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(text = "Chưa có ảnh kết quả.", style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = onCreateAnother,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Icon(imageVector = Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                Text(text = "Tạo ảnh khác", modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ResultScreenPreview() {
    RevroomTheme {
        ResultScreen(
            uiState = DesignUiState(
                originalImageUrl = "https://example.com/before.jpg",
                designedImageUrl = "https://example.com/after.jpg"
            ),
            onCreateAnother = {}
        )
    }
}

package com.example.revroom.features.chat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.revroom.core.ui.StudioScaffold
import com.example.revroom.core.ui.StudioTab
import com.example.revroom.data.remote.ChatCitationResponse
import com.example.revroom.data.remote.ChatMessageResponse
import com.example.revroom.features.chat.viewmodel.AdviceChatUiState
import com.example.revroom.features.chat.viewmodel.AdviceChatViewModel
import com.example.revroom.features.chat.viewmodel.DesignJobUi
import com.example.revroom.features.chat.viewmodel.REMOVE_FURNITURE
import com.example.revroom.features.design_studio.model.DesignJobStatus

private val RoomOptions = listOf(
    "living_room" to "Phòng khách",
    "master_bedroom" to "Phòng ngủ",
    "kitchen" to "Phòng bếp",
    "bathroom" to "Phòng tắm",
    "study_room" to "Phòng làm việc"
)

private val FeatureOptions = listOf(
    "interior_design" to "Thiết kế lại phòng",
    "furnish_empty_room" to "Bày trí phòng trống",
    REMOVE_FURNITURE to "Dọn sạch nội thất"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdviceChatScreen(
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onChat: () -> Unit,
    onGallery: () -> Unit,
    showBottomBar: Boolean,
    viewModel: AdviceChatViewModel = viewModel(factory = AdviceChatViewModel.Factory(LocalContext.current))
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    var showConversations by remember { mutableStateOf(false) }
    var showBriefSheet by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectImage(uri)
            showBriefSheet = true
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    StudioScaffold(
        selectedTab = StudioTab.Chat,
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        onInterior = onInterior,
        onExterior = onExterior,
        onChat = onChat,
        onGallery = onGallery,
        showBottomBar = showBottomBar
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = 104.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tư vấn nội thất", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = viewModel::newChat, enabled = !state.isBusy && !state.isConfirming) {
                    Icon(Icons.Default.Add, contentDescription = "Cuộc trò chuyện mới")
                }
            }

            Box {
                TextButton(onClick = { showConversations = true }, enabled = !state.isBusy && !state.isConfirming) {
                    val title = state.conversations.firstOrNull { it.id == state.conversationId }?.title
                        ?: "Cuộc trò chuyện mới"
                    Text("Lịch sử: ${title.take(32)} ▾")
                }
                DropdownMenu(expanded = showConversations, onDismissRequest = { showConversations = false }) {
                    state.conversations.forEach { conversation ->
                        DropdownMenuItem(
                            text = { Text(conversation.title.take(45)) },
                            onClick = {
                                showConversations = false
                                viewModel.selectConversation(conversation.id)
                            }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Text(
                            "Kể mình nghe màu sắc và cảm giác bạn thích. Mình sẽ gợi ý phong cách, rồi bạn có thể gửi ảnh phòng để tạo thiết kế.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    val designId = message.designId
                    if (designId != null) {
                        DesignJobCard(
                            message = message,
                            job = state.designJobs[designId] ?: DesignJobUi(),
                            onCheck = { viewModel.checkDesignStatus(designId) },
                            onGallery = onGallery
                        )
                    } else {
                        MessageBubble(message)
                    }
                }
            }

            if (state.error != null) {
                Text(
                    state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                TextButton(onClick = viewModel::refresh, enabled = !state.isBusy) { Text("Tải lại") }
            }

            if (state.hasBriefContent) {
                BriefSummary(state, onOpen = { showBriefSheet = true })
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        imagePicker.launch("image/*")
                    },
                    enabled = !state.isConfirming,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = "Thêm ảnh phòng gốc",
                        tint = MaterialTheme.colorScheme.primary)
                }
                TextField(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChanged,
                    placeholder = { Text("Bạn thích phòng và phong cách nào?") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    maxLines = 4
                )
                Button(onClick = viewModel::send, enabled = !state.isBusy && state.draft.isNotBlank()) {
                    if (state.isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi")
                }
            }
        }
    }

    if (showBriefSheet) {
        ModalBottomSheet(onDismissRequest = { showBriefSheet = false }) {
            BriefSheet(
                state = state,
                viewModel = viewModel,
                onPickImage = { imagePicker.launch("image/*") },
                onConfirmed = { showBriefSheet = false }
            )
        }
    }

    // The sheet stays open on failure so the reason is visible; it closes once the job message arrives.
    LaunchedEffect(state.designJobs.size) {
        if (state.designJobs.isNotEmpty() && !state.isConfirming && state.designError == null) {
            showBriefSheet = false
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessageResponse) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isUser) Color(0xFFE9D5FF) else Color(0xFFF3F4F6),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(message.content, modifier = Modifier.padding(12.dp))
            }
            message.citations?.takeIf { it.isNotEmpty() }?.let { Citations(it) }
        }
    }
}

@Composable
private fun Citations(citations: List<ChatCitationResponse>) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text("Nguồn tham khảo (${citations.size}) ${if (expanded) "▴" else "▾"}",
                style = MaterialTheme.typography.labelMedium)
        }
        if (expanded) {
            citations.distinctBy { it.documentId }.forEach { citation ->
                val url = citation.sourceUrl
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = url != null) { url?.let(uriHandler::openUri) }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(citation.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(citation.sourceLabel, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun DesignJobCard(
    message: ChatMessageResponse,
    job: DesignJobUi,
    onCheck: () -> Unit,
    onGallery: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF3F4F6),
        modifier = Modifier.fillMaxWidth(0.9f)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message.content, style = MaterialTheme.typography.bodyMedium)
            val statusText = when {
                job.status == DesignJobStatus.Completed -> "Thiết kế đã xong."
                job.status == DesignJobStatus.Failed -> "Chưa tạo được thiết kế từ ảnh này. Hãy thử ảnh khác hoặc xem Gallery."
                job.needsManualCheck -> "Chưa kiểm tra được tiến độ. Bạn có thể thử lại hoặc xem Gallery."
                else -> "Đang tạo thiết kế, bạn có thể quay lại sau."
            }
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            job.originalImageUrl?.let { url ->
                Text("Ảnh phòng gốc", style = MaterialTheme.typography.labelMedium)
                AsyncImage(
                    model = url,
                    contentDescription = "Ảnh phòng gốc đã gửi",
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentScale = ContentScale.Crop
                )
            }
            job.designedImageUrl?.let { url ->
                Text("Phương án thiết kế", style = MaterialTheme.typography.labelMedium)
                AsyncImage(
                    model = url,
                    contentDescription = "Thiết kế nội thất từ ảnh phòng",
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (job.needsManualCheck && !job.isChecking) {
                    TextButton(onClick = onCheck) { Text("Kiểm tra lại") }
                }
                TextButton(onClick = onGallery) { Text("Xem Gallery") }
            }
        }
    }
}

@Composable
private fun BriefSummary(state: AdviceChatUiState, onOpen: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF3F4F6),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.selectedImageUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = "Ảnh phòng đã chọn",
                    modifier = Modifier.size(44.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Yêu cầu thiết kế", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val room = RoomOptions.firstOrNull { it.first == state.brief?.roomType }?.second
                Text(
                    listOfNotNull(room, state.brief?.styleName).joinToString(" · ")
                        .ifEmpty { "Chọn phòng và phong cách" },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("Xem lại", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun BriefSheet(
    state: AdviceChatUiState,
    viewModel: AdviceChatViewModel,
    onPickImage: () -> Unit,
    onConfirmed: () -> Unit
) {
    val brief = state.brief
    var roomMenu by remember { mutableStateOf(false) }
    var styleMenu by remember { mutableStateOf(false) }
    var featureMenu by remember { mutableStateOf(false) }
    var color by remember(brief?.version) { mutableStateOf(brief?.colorPreference.orEmpty()) }
    var keep by remember(brief?.version) { mutableStateOf(brief?.mustKeep.orEmpty()) }
    var avoid by remember(brief?.version) { mutableStateOf(brief?.mustAvoid.orEmpty()) }
    val needsStyle = brief?.featureId != REMOVE_FURNITURE

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Yêu cầu thiết kế", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Xem lại rồi xác nhận. Ảnh chỉ được tạo sau khi bạn bấm nút bên dưới.",
            style = MaterialTheme.typography.bodyMedium)

        Box {
            OutlinedButton(onClick = { featureMenu = true }, modifier = Modifier.fillMaxWidth(),
                enabled = !state.isConfirming) {
                Text(FeatureOptions.firstOrNull { it.first == brief?.featureId }?.second ?: FeatureOptions[0].second)
            }
            DropdownMenu(expanded = featureMenu, onDismissRequest = { featureMenu = false }) {
                FeatureOptions.forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        viewModel.selectFeature(code)
                        featureMenu = false
                    })
                }
            }
        }

        Box {
            OutlinedButton(onClick = { roomMenu = true }, modifier = Modifier.fillMaxWidth(),
                enabled = !state.isConfirming) {
                Text(RoomOptions.firstOrNull { it.first == brief?.roomType }?.second ?: "Chọn loại phòng")
            }
            DropdownMenu(expanded = roomMenu, onDismissRequest = { roomMenu = false }) {
                RoomOptions.forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        viewModel.selectRoom(code)
                        roomMenu = false
                    })
                }
            }
        }

        if (needsStyle) {
            Box {
                OutlinedButton(onClick = { styleMenu = true }, modifier = Modifier.fillMaxWidth(),
                    enabled = state.designStyles.isNotEmpty() && !state.isConfirming) {
                    Text(brief?.styleName ?: "Chọn phong cách")
                }
                DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                    state.designStyles.forEach { style ->
                        DropdownMenuItem(text = { Text(style.styleName) }, onClick = {
                            viewModel.selectStyle(style.styleId)
                            styleMenu = false
                        })
                    }
                }
            }
            if (state.designStyles.isEmpty()) {
                TextButton(onClick = viewModel::loadDesignStyles) { Text("Tải lại phong cách") }
            }
            OutlinedTextField(
                value = color, onValueChange = { color = it.take(300) },
                label = { Text("Màu mong muốn (không bắt buộc)") },
                enabled = !state.isConfirming, modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = keep, onValueChange = { keep = it.take(300) },
            label = { Text("Cần giữ nguyên (không bắt buộc)") },
            enabled = !state.isConfirming, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = avoid, onValueChange = { avoid = it.take(300) },
            label = { Text("Cần tránh (không bắt buộc)") },
            enabled = !state.isConfirming, modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickImage, modifier = Modifier.weight(1f), enabled = !state.isConfirming) {
                Text(if (state.selectedImageUri == null) "Chọn ảnh phòng gốc" else "Đổi ảnh phòng")
            }
            if (state.selectedImageUri != null) {
                IconButton(onClick = { viewModel.selectImage(null) }, enabled = !state.isConfirming) {
                    Icon(Icons.Default.Close, contentDescription = "Bỏ ảnh đã chọn")
                }
            }
        }
        state.selectedImageUri?.let { uri ->
            AsyncImage(model = uri, contentDescription = "Ảnh phòng gốc đã chọn",
                modifier = Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Crop)
        }
        state.designError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { viewModel.confirmDesign(color.trim(), keep.trim(), avoid.trim()) },
            enabled = state.canConfirm,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isConfirming) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Xác nhận và tạo thiết kế")
        }
        TextButton(onClick = onConfirmed, enabled = !state.isConfirming) { Text("Để sau") }
    }
}

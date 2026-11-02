package com.example.revroom.features.history.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.revroom.core.ui.*
import com.example.revroom.features.history.model.ProjectModel
import com.example.revroom.features.history.viewmodel.HistoryViewModel
import com.example.revroom.features.history.viewmodel.HistoryUiState
import java.util.Locale

@Composable
fun HistoryScreen(
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onChat: () -> Unit,
    onGallery: () -> Unit,
    showBottomBar: Boolean = true,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(LocalContext.current))
) {
    val context = LocalContext.current
    
    val detailLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val deletedId = result.data?.getStringExtra(ProjectDetailActivity.EXTRA_DELETED_ID)
            if (deletedId != null) {
                viewModel.removeProjectFromList(deletedId)
            }
        }
    }

    val uiState by viewModel.uiState.observeAsState(initial = HistoryUiState.Loading)
    val isLoading by viewModel.isLoading.observeAsState(initial = false)
    val isDeleting by viewModel.isDeleting.observeAsState(initial = false)
    val deleteError by viewModel.deleteError.observeAsState(initial = null)
    
    var projectToDelete by remember { mutableStateOf<ProjectModel?>(null) }

    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project") },
            text = { Text("Are you sure you want to delete this project history?") },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        projectToDelete?.designId?.let { viewModel.deleteProject(it) }
                        projectToDelete = null
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearDeleteError,
            title = { Text("Couldn't delete project") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearDeleteError) { Text("OK") }
            }
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.refreshProjects()
    }

    StudioScaffold(
        selectedTab = StudioTab.Gallery,
        horizontalPadding = 8.dp,
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        onInterior = onInterior,
        onExterior = onExterior,
        onChat = onChat,
        onGallery = onGallery,
        showBottomBar = showBottomBar
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 14.dp)
        ) {
            TopTitleBar(
                title = "My projects",
                trailing = {
                    IconButton(onClick = viewModel::refreshProjects, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh projects", tint = StudioText)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val state = uiState) {
                    HistoryUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = StudioPurple
                        )
                    }
                    HistoryUiState.Empty -> EmptyStateView()
                    is HistoryUiState.Error -> GalleryErrorView(state.message, viewModel::retryProjects)
                    is HistoryUiState.Content -> Column(modifier = Modifier.fillMaxSize()) {
                        if (state.isRefreshing || isDeleting) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = StudioPurple)
                            Text(
                                text = if (isDeleting) "Deleting project..." else "Refreshing projects...",
                                modifier = Modifier.padding(8.dp),
                                color = StudioMuted,
                                fontSize = 12.sp
                            )
                        }
                        state.error?.let { message ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(message, modifier = Modifier.weight(1f), color = StudioText, fontSize = 12.sp)
                                TextButton(onClick = viewModel::retryProjects, enabled = !isLoading) {
                                    Text("Retry")
                                }
                            }
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(1),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(top = 2.dp, bottom = 100.dp)
                        ) {
                            itemsIndexed(
                                items = state.projects,
                                key = { index, project ->
                                    project.designId?.takeIf { it.isNotBlank() }?.let { "project:$it" }
                                        ?: "missing:$index"
                                }
                            ) { _, project ->
                                ProjectItem(
                                    project = project,
                                    deleteEnabled = !isDeleting && !project.designId.isNullOrBlank(),
                                    onDelete = { projectToDelete = project },
                                    onClick = {
                                        val intent = Intent(context, ProjectDetailActivity::class.java).apply {
                                            putExtra(ProjectDetailActivity.EXTRA_DESIGN_ID, project.designId)
                                            putExtra(ProjectDetailActivity.EXTRA_ORIGINAL_IMAGE, project.originalImageUrl)
                                            putExtra(ProjectDetailActivity.EXTRA_DESIGNED_IMAGE, project.designedImageUrl)
                                            putExtra(ProjectDetailActivity.EXTRA_STATUS, project.status)
                                            putExtra(ProjectDetailActivity.EXTRA_CREATED_AT, project.createdAt)
                                        }
                                        detailLauncher.launch(intent)
                                    }
                                )
                            }
                            if (state.hasMore || state.isLoadingMore) {
                                item(key = "load-more") {
                                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                        if (state.isLoadingMore) {
                                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = StudioPurple)
                                        } else {
                                            TextButton(onClick = viewModel::loadMoreProjects, enabled = !isLoading) {
                                                Text("Load more projects")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Collections, null, tint = StudioMuted, modifier = Modifier.size(48.dp))
        Text("No projects yet", Modifier.padding(top = 14.dp), color = StudioText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Your redesign projects will appear here", Modifier.padding(top = 7.dp), color = StudioMuted, fontSize = 12.sp)
    }
}

@Composable
private fun GalleryErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = StudioMuted, modifier = Modifier.size(48.dp))
        Text("Couldn't load projects", Modifier.padding(top = 14.dp), color = StudioText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(message, Modifier.padding(top = 7.dp), color = StudioMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun ProjectItem(
    project: ProjectModel,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    deleteEnabled: Boolean = true
) {
    val status = project.status?.trim()?.lowercase(Locale.ROOT)
    val (statusLabel, statusColor) = when (status) {
        "completed" -> "Completed" to Color(0xFF2E7D32)
        "failed" -> "Failed" to Color(0xFFC62828)
        "pending", "queued", null, "" -> "Pending" to Color(0xFF9A6700)
        "processing", "in_progress" -> "Processing" to Color(0xFF1565C0)
        else -> "Unknown" to Color(0xFF616161)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Original Image
                AsyncImage(
                    model = project.originalImageUrl,
                    contentDescription = "Original",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentScale = ContentScale.Crop
                )
                
                // Divider
                Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color.White))
                
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFF1F3F5)),
                    contentAlignment = Alignment.Center
                ) {
                    if (project.designedImageUrl.isNullOrBlank()) {
                        Text(
                            text = when (status) {
                                "failed" -> "Generation failed"
                                "completed" -> "Preview unavailable"
                                "pending", "queued", null, "" -> "Waiting to start"
                                "processing", "in_progress" -> "Creating your design"
                                else -> "Preview unavailable"
                            },
                            modifier = Modifier.padding(16.dp),
                            color = StudioMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        AsyncImage(
                            model = project.designedImageUrl,
                            contentDescription = "Generated design",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            // Delete Button
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(enabled = deleteEnabled) { onDelete() }
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White.copy(alpha = if (deleteEnabled) 1f else 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Status Badge
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .align(Alignment.TopEnd)
            ) {
                Text(
                    statusLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        
        // Date/Info
        Text(
            text = "Project: ${project.designId?.take(8)?.uppercase() ?: "N/A"}",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.DarkGray
        )
    }
}

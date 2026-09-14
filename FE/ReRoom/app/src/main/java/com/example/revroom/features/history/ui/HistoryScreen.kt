package com.example.revroom.features.history.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.revroom.core.ui.*
import com.example.revroom.features.history.model.ProjectModel
import com.example.revroom.features.history.viewmodel.HistoryViewModel

@Composable
fun HistoryScreen(
    onInterior: () -> Unit,
    onExterior: () -> Unit,
    onChat: () -> Unit,
    onGallery: () -> Unit,
    onProfileClick: () -> Unit,
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

    // Observe ViewModel states
    val projects by viewModel.projects.observeAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.observeAsState(initial = false)
    val error by viewModel.error.observeAsState(initial = null)
    
    var projectToDelete by remember { mutableStateOf<ProjectModel?>(null) }

    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project") },
            text = { Text("Are you sure you want to delete this project history?") },
            confirmButton = {
                TextButton(
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

    LaunchedEffect(Unit) {
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(StudioGradient)
                            .clickable { onProfileClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Person, "Profile", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (projects.isEmpty() && !isLoading) {
                    EmptyStateView(error)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 2.dp, bottom = 100.dp)
                    ) {
                        items(items = projects) { project ->
                            ProjectItem(
                                project = project,
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
                    }
                }

                if (isLoading && projects.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFC62828))
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(error: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Collections, null, tint = StudioMuted, modifier = Modifier.size(48.dp))
        Text("No projects yet", Modifier.padding(top = 14.dp), color = StudioText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(error ?: "Your redesign projects will appear here", Modifier.padding(top = 7.dp), color = StudioMuted, fontSize = 12.sp)
    }
}

@Composable
fun ProjectItem(
    project: ProjectModel,
    onDelete: () -> Unit,
    onClick: () -> Unit // Add onClick parameter
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { onClick() } // Call onClick when the item is clicked
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
                
                // Generated Image
                AsyncImage(
                    model = project.designedImageUrl,
                    contentDescription = "Generated",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentScale = ContentScale.Crop,
                    error = null // Or a placeholder
                )
            }

            // Delete Button
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onDelete() }
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Status Badge
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (project.status?.equals("completed", ignoreCase = true) == true) Color(0xFF2E7D32) else Color(0xFFC62828))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .align(Alignment.TopEnd)
            ) {
                Text(
                    project.status ?: "Processing",
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

package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraPink
import com.example.ui.theme.AuraPurple
import com.example.utils.MediaFileInfo
import com.example.utils.getMediaFileInfo
import kotlinx.coroutines.delay

@Composable
fun CreateScreen(
    onCreatePost: (String, String, String, Boolean) -> Unit,
    onCreateStory: (String, String, Boolean) -> Unit,
    onCreateReel: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Post, 1: Story, 2: Reel
    val tabs = listOf("Post", "Story", "Reel")

    var caption by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var commentsDisabled by remember { mutableStateOf(false) }
    var isCloseFriendsOnly by remember { mutableStateOf(false) }

    // Media file states
    val selectedMediaList = remember { mutableStateListOf<MediaFileInfo>() }
    var primaryMediaUrl by remember { mutableStateOf("https://images.unsplash.com/photo-1518770660439-4636190af475?w=800") }
    var videoUrl by remember { mutableStateOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4") }

    // Upload progress animation simulation
    var uploadProgress by remember { mutableFloatStateOf(1f) }
    var isUploading by remember { mutableStateOf(false) }
    var mediaErrorMessage by remember { mutableStateOf<String?>(null) }

    // Single / Multiple Image Launcher
    val multipleImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            mediaErrorMessage = null
            selectedMediaList.clear()
            val fileInfos = uris.map { getMediaFileInfo(context, it) }
            selectedMediaList.addAll(fileInfos)
            primaryMediaUrl = fileInfos.joinToString(",") { it.persistentPath }

            // Simulate progress
            isUploading = true
            uploadProgress = 0.2f
        }
    }

    // Video Launcher for Reels / Videos
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            mediaErrorMessage = null
            val fileInfo = getMediaFileInfo(context, uri)
            selectedMediaList.clear()
            selectedMediaList.add(fileInfo)
            primaryMediaUrl = fileInfo.persistentPath
            videoUrl = fileInfo.persistentPath

            isUploading = true
            uploadProgress = 0.2f
        }
    }

    // Single Image Picker
    val singleImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            mediaErrorMessage = null
            val fileInfo = getMediaFileInfo(context, uri)
            selectedMediaList.clear()
            selectedMediaList.add(fileInfo)
            primaryMediaUrl = fileInfo.persistentPath

            isUploading = true
            uploadProgress = 0.2f
        }
    }

    LaunchedEffect(isUploading) {
        if (isUploading) {
            uploadProgress = 0.4f
            delay(150)
            uploadProgress = 0.8f
            delay(150)
            uploadProgress = 1.0f
            isUploading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("create_screen")
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        selectedMediaList.clear()
                    },
                    text = { Text(title, fontWeight = FontWeight.Bold) }
                )
            }
        }

        // Media Preview Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (selectedTab == 0) 1.2f else 0.8f)
                .clip(RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val previewPath = primaryMediaUrl.split(",").firstOrNull() ?: primaryMediaUrl
                val previewModel = remember(previewPath) {
                    when {
                        previewPath.startsWith("content://") -> Uri.parse(previewPath)
                        previewPath.startsWith("/") -> java.io.File(previewPath)
                        else -> previewPath
                    }
                }
                AsyncImage(
                    model = previewModel,
                    contentDescription = "Selected media preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (selectedMediaList.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            selectedMediaList.clear()
                            primaryMediaUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800"
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove Media",
                            tint = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(8.dp)
                ) {
                    if (selectedMediaList.isNotEmpty()) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📁 ${selectedMediaList.first().name}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = selectedMediaList.first().formattedSize,
                                    color = AuraOrange,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (isUploading) {
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { uploadProgress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = AuraPink,
                                    trackColor = Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "No device media selected yet. Choose from Gallery below.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // GALLERY UPLOAD BUTTONS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    when (selectedTab) {
                        0 -> multipleImagePicker.launch("image/*")
                        1 -> singleImagePicker.launch("image/*")
                        2 -> videoPicker.launch("video/*")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("choose_from_gallery_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AuraPurple)
            ) {
                Icon(
                    imageVector = if (selectedTab == 2) Icons.Default.VideoLibrary else Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = if (selectedTab == 2) "Gallery Video" else "Choose from Gallery",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            if (selectedTab == 0) {
                OutlinedButton(
                    onClick = { videoPicker.launch("video/*") },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("choose_video_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Video", fontSize = 13.sp)
                }
            }
        }

        // Multi-image list preview
        if (selectedMediaList.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Selected Images (${selectedMediaList.size}):",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectedMediaList) { media ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (media.uri.toString() == primaryMediaUrl) 2.dp else 0.dp,
                                color = AuraPink,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { primaryMediaUrl = media.uri.toString() }
                    ) {
                        AsyncImage(
                            model = media.uri,
                            contentDescription = media.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Caption & Hashtags
        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            label = { Text(if (selectedTab == 2) "Write video caption & hashtags..." else "Write a caption or hashtags...") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_caption_input"),
            minLines = 3,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) { // Post Options
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Add Location (e.g. Tokyo, Japan)") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_location_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Turn off commenting", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = commentsDisabled,
                    onCheckedChange = { commentsDisabled = it },
                    modifier = Modifier.testTag("comments_disabled_switch")
                )
            }
        } else if (selectedTab == 1) { // Story Options
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Share with Close Friends only ⭐️", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isCloseFriendsOnly,
                    onCheckedChange = { isCloseFriendsOnly = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                when (selectedTab) {
                    0 -> onCreatePost(caption, location, primaryMediaUrl, commentsDisabled)
                    1 -> onCreateStory(caption, primaryMediaUrl, isCloseFriendsOnly)
                    2 -> onCreateReel(caption, videoUrl, primaryMediaUrl)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("publish_button"),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AuraPink)
        ) {
            Text(
                text = "Share ${tabs[selectedTab]}",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

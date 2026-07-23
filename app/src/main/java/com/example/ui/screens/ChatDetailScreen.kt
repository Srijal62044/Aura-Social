package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.local.MessageEntity
import com.example.ui.theme.AuraPink
import com.example.ui.theme.AuraPurple
import kotlinx.coroutines.delay

@Composable
fun ChatDetailScreen(
    conversationId: String,
    messages: List<MessageEntity>,
    onBackClick: () -> Unit,
    onStartCall: (Boolean) -> Unit, // true: video call, false: voice call
    onSendMessage: (String, String, String) -> Unit,
    onDeleteMessage: (Long) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var isRecordingVoiceNote by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }
    var selectedFullImage by remember { mutableStateOf<String?>(null) }

    // Launcher for picking photos from gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            onSendMessage("Photo 📸", it.toString(), "image")
        }
    }

    // Voice recording timer
    LaunchedEffect(isRecordingVoiceNote) {
        if (isRecordingVoiceNote) {
            recordingSeconds = 0
            while (isRecordingVoiceNote) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .testTag("chat_detail_screen")
    ) {
        // Chat Header with Call Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                AsyncImage(
                    model = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500",
                    contentDescription = conversationId,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = conversationId,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Active now",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }

            Row {
                IconButton(
                    onClick = { onStartCall(false) },
                    modifier = Modifier.testTag("voice_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Voice Call",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = { onStartCall(true) },
                    modifier = Modifier.testTag("video_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Video Call",
                        tint = AuraPink
                    )
                }
            }
        }

        // Messages History
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMine = msg.isMine
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (isMine) 18.dp else 4.dp,
                                    bottomEnd = if (isMine) 4.dp else 18.dp
                                )
                            )
                            .background(if (isMine) AuraPink else MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Column {
                            // Check message type (Photo, Voice Note, or Text)
                            if (msg.mediaUrl.isNotBlank() || msg.type == "image") {
                                val imgUrl = msg.mediaUrl.ifBlank { "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800" }
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = "Message Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 220.dp, height = 180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedFullImage = imgUrl }
                                )
                                if (msg.text.isNotBlank() && msg.text != "Photo 📸" && msg.text != "Shared photo 📸") {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            } else if (msg.type == "voice") {
                                VoiceNoteBubble(
                                    durationText = msg.text.ifBlank { "Voice Note (0:15)" },
                                    isMine = isMine
                                )
                            } else {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg.timestamp,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    color = if (isMine) Color.White.copy(alpha = 0.7f) else Color.Gray
                                ),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }

        // Voice Note Recording Overlay
        AnimatedVisibility(visible = isRecordingVoiceNote) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AuraPurple.copy(alpha = 0.2f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recording Voice Note: 0:${if (recordingSeconds < 10) "0$recordingSeconds" else recordingSeconds}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = AuraPink)
                    )
                }

                Row {
                    IconButton(onClick = { isRecordingVoiceNote = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Recording", tint = Color.Red)
                    }
                    IconButton(onClick = {
                        val durationStr = "0:${if (recordingSeconds < 10) "0$recordingSeconds" else recordingSeconds}"
                        onSendMessage("🎤 Voice Note ($durationStr)", "", "voice")
                        isRecordingVoiceNote = false
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Send Recording", tint = AuraPink)
                    }
                }
            }
        }

        // Bottom Input Bar
        if (!isRecordingVoiceNote) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showPhotoOptionsDialog = true }) {
                    Icon(Icons.Default.Image, contentDescription = "Send Photo", tint = AuraPink)
                }

                IconButton(onClick = { isRecordingVoiceNote = true }) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice Note", tint = MaterialTheme.colorScheme.onSurface)
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Message...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input"),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    onSendMessage(messageText, "", "text")
                                    messageText = ""
                                }
                            },
                            enabled = messageText.isNotBlank(),
                            modifier = Modifier.testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (messageText.isNotBlank()) AuraPink else Color.Gray
                            )
                        }
                    }
                )
            }
        }
    }

    // Photo Selection Options Dialog
    if (showPhotoOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoOptionsDialog = false },
            title = { Text("Send Photo", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📷 Choose from Phone Gallery")
                    }
                    TextButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            onSendMessage("Beautiful Sunset 🌅", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800", "image")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🌅 Send Sunset Photo")
                    }
                    TextButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            onSendMessage("Cozy Coffee ☕", "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=800", "image")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("☕ Send Coffee Photo")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Fullscreen Image Preview Modal
    selectedFullImage?.let { img ->
        AlertDialog(
            onDismissRequest = { selectedFullImage = null },
            confirmButton = {
                TextButton(onClick = { selectedFullImage = null }) {
                    Text("Close")
                }
            },
            text = {
                AsyncImage(
                    model = img,
                    contentDescription = "Full image view",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        )
    }
}

@Composable
fun VoiceNoteBubble(
    durationText: String,
    isMine: Boolean
) {
    var isPlaying by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveAnim"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(180.dp)
    ) {
        IconButton(
            onClick = { isPlaying = !isPlaying },
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isMine) Color.White.copy(alpha = 0.2f) else AuraPink.copy(alpha = 0.2f))
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play Voice Note",
                tint = if (isMine) Color.White else AuraPink
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(12) { i ->
                    val heightFactor = if (isPlaying) ((i % 4 + 1) * 0.25f * waveAnim) else ((i % 3 + 1) * 0.25f)
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height((16 * heightFactor).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isMine) Color.White else AuraPink)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = durationText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = if (isMine) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

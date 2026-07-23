package com.example.ui.components

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.ui.CallState
import com.example.ui.theme.AuraPink
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.DarkBackground
import kotlinx.coroutines.delay

@Composable
fun CallOverlayModal(
    callState: CallState,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit
) {
    if (!callState.isActive) return

    var callDurationSeconds by remember { mutableIntStateOf(0) }
    var isConnected by remember { mutableStateOf(false) }
    var callStatusText by remember { mutableStateOf("Calling...") }

    // Ringing -> Auto answer sequence
    LaunchedEffect(callState.isActive) {
        if (callState.isActive) {
            isConnected = false
            callDurationSeconds = 0
            callStatusText = "Calling @${callState.peerUsername}..."
            delay(1500)
            if (!isConnected) {
                callStatusText = "Ringing... 🔔"
            }
            delay(3000)
            if (!isConnected) {
                isConnected = true
                callStatusText = "Connected"
            }
        }
    }

    // Call duration timer once connected
    LaunchedEffect(isConnected) {
        if (isConnected) {
            while (callState.isActive && isConnected) {
                delay(1000)
                callDurationSeconds++
            }
        }
    }

    val minutes = callDurationSeconds / 60
    val seconds = callDurationSeconds % 60
    val formattedDuration = String.format("%02d:%02d", minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Dialog(
        onDismissRequest = onEndCall,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkBackground
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("call_overlay_dialog")
            ) {
                if (callState.isVideo && callState.isCameraOn && isConnected) {
                    // Peer video stream backdrop
                    AsyncImage(
                        model = callState.peerAvatar,
                        contentDescription = "Peer video stream",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                    )

                    // Self PIP camera view box in top corner with live CameraX preview
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 50.dp, end = 20.dp)
                            .size(width = 110.dp, height = 150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, AuraPink, RoundedCornerShape(16.dp))
                            .background(Color.DarkGray)
                    ) {
                        LiveCameraPreview(
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("You (Live)", style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 9.sp))
                        }
                    }
                }

                // Call Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 70.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (!isConnected) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(AuraPink.copy(alpha = 0.25f))
                            )
                        }
                        AsyncImage(
                            model = callState.peerAvatar,
                            contentDescription = callState.peerUsername,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .border(3.dp, AuraPink, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "@${callState.peerUsername}",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (!isConnected) callStatusText else if (callState.isVideo) "Aura Video Call • $formattedDuration" else "Aura Voice Call • $formattedDuration",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = if (isConnected) Color(0xFF4ADE80) else AuraPink,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    // Quick option to answer immediately if ringing
                    if (!isConnected) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF22C55E))
                                .clickable {
                                    isConnected = true
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Simulate Answer by @${callState.peerUsername}",
                                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    // Audio spectrum wave bars for voice calls when connected
                    if (!callState.isVideo && isConnected) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(16) { i ->
                                val heightFactor = (i % 5 + 1) * 0.2f * pulseScale
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height((32 * heightFactor).dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (i % 2 == 0) AuraPink else AuraPurple)
                                )
                            }
                        }
                    }
                }

                // Control Bar
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 60.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute Button
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(if (callState.isMuted) AuraPink else Color.White.copy(alpha = 0.2f))
                            .testTag("call_mute_button")
                    ) {
                        Icon(
                            imageVector = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = Color.White
                        )
                    }

                    // End Call Button
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                            .testTag("call_end_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Camera Toggle Button for Video Call
                    if (callState.isVideo) {
                        IconButton(
                            onClick = onToggleCamera,
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(if (!callState.isCameraOn) AuraPink else Color.White.copy(alpha = 0.2f))
                                .testTag("call_camera_button")
                        ) {
                            Icon(
                                imageVector = if (callState.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                contentDescription = "Camera",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveCameraPreview(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isCameraBound by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                    isCameraBound = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )

    if (!isCameraBound) {
        Box(
            modifier = modifier.background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500",
                contentDescription = "Fallback Camera",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

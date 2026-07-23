package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.PostEntity
import com.example.ui.theme.AuraPink
import com.example.ui.theme.VerifiedBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Surface

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    post: PostEntity,
    currentUsername: String,
    onUserClick: (String) -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onSaveClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onReportClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showHeartAnimation by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val mediaList = remember(post.mediaUrlsJson) {
        val list = post.mediaUrlsJson.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (list.isEmpty()) listOf("https://picsum.photos/id/1015/800/800")
        else list
    }

    val pagerState = rememberPagerState(pageCount = { mediaList.size })

    val heartScale by animateFloatAsState(
        targetValue = if (showHeartAnimation) 1.2f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "heartScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("post_card_${post.id}"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
        // Post Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .combinedClickable(
                        onClick = { onUserClick(post.username) }
                    )
            ) {
                AsyncImage(
                    model = post.userAvatar,
                    contentDescription = "${post.username}'s avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.username,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                        if (post.isVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = VerifiedBlue,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    if (post.location.isNotBlank()) {
                        Text(
                            text = post.location,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Post Options"
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (post.isSaved) "Unsave Post" else "Save Post") },
                        onClick = {
                            showMenu = false
                            onSaveClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) }
                    )
                    if (post.username == currentUsername) {
                        DropdownMenuItem(
                            text = { Text("Archive Post") },
                            onClick = {
                                showMenu = false
                                onArchiveClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Post", color = AuraPink) },
                            onClick = {
                                showMenu = false
                                onDeleteClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AuraPink) }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Report Post") },
                            onClick = {
                                showMenu = false
                                onReportClick()
                            },
                            leadingIcon = { Icon(Icons.Default.Report, contentDescription = null) }
                        )
                    }
                }
            }
        }

        // Post Media with Double Tap Heart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (!post.isLiked) {
                                onLikeClick()
                            }
                            coroutineScope.launch {
                                showHeartAnimation = true
                                delay(800)
                                showHeartAnimation = false
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val mediaPath = mediaList[page]
                val mediaModel = remember(mediaPath) {
                    when {
                        mediaPath.startsWith("content://") -> android.net.Uri.parse(mediaPath)
                        mediaPath.startsWith("/") -> java.io.File(mediaPath)
                        else -> mediaPath
                    }
                }
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(mediaModel)
                        .crossfade(true)
                        .error(android.R.drawable.ic_menu_gallery)
                        .build(),
                    contentDescription = "Post image $page",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bursting Heart Overlay on Double Tap
            if (showHeartAnimation) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Liked",
                    tint = AuraPink,
                    modifier = Modifier
                        .size(100.dp)
                        .scale(heartScale)
                )
            }

            // Carousel Page Indicator Pill
            if (mediaList.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${mediaList.size}",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White)
                    )
                }
            }
        }

        // Interactive Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier.testTag("like_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (post.isLiked) AuraPink else MaterialTheme.colorScheme.onBackground
                    )
                }

                if (!post.commentsDisabled) {
                    IconButton(
                        onClick = onCommentClick,
                        modifier = Modifier.testTag("comment_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Comment"
                        )
                    }
                }

                IconButton(onClick = { /* Share sheet */ }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Share"
                    )
                }
            }

            IconButton(
                onClick = onSaveClick,
                modifier = Modifier.testTag("save_button_${post.id}")
            ) {
                Icon(
                    imageVector = if (post.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (post.isSaved) AuraPink else MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Likes Count
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "${post.likeCount} likes",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            )

            // Caption & Hashtags
            if (post.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = "${post.username} ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                    Text(
                        text = post.caption,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                    )
                }
            }

            if (post.hashtags.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = post.hashtags,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            // Comments Count Trigger
            if (!post.commentsDisabled && post.commentCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "View all ${post.commentCount} comments",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .clickable { onCommentClick() }
                        .testTag("view_comments_${post.id}")
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = post.timestamp,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            )
        }
    }
}
}

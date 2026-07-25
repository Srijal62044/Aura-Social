package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import com.example.data.local.PostEntity
import com.example.data.local.ReelEntity
import com.example.data.local.UserEntity
import com.example.ui.theme.AuraPink
import com.example.ui.theme.AuraPurple
import com.example.ui.theme.VerifiedBlue

@Composable
fun ProfileScreen(
    user: UserEntity?,
    isSelf: Boolean,
    posts: List<PostEntity>,
    reels: List<ReelEntity>,
    savedPosts: List<PostEntity>,
    isFollowLoading: Boolean = false,
    followersList: List<UserEntity>? = null,
    followingList: List<UserEntity>? = null,
    isLoadingFollowList: Boolean = false,
    activeListType: String? = null,
    onEditProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFollowClick: () -> Unit,
    onFollowersClick: () -> Unit = {},
    onFollowingClick: () -> Unit = {},
    onCloseFollowList: () -> Unit = {},
    onUserClick: (UserEntity) -> Unit = {},
    onMessageClick: () -> Unit,
    onBlockClick: () -> Unit,
    onRestrictClick: () -> Unit,
    onReportClick: () -> Unit,
    onPostClick: (PostEntity) -> Unit
) {
    if (user == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Loading profile...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
        return
    }

    var selectedTabIndex by remember { mutableStateOf(0) } // 0: Posts, 1: Reels, 2: Tagged, 3: Saved
    var showOptionsMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("profile_screen")
    ) {
        // Top Profile Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (user.isPrivate) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Private Account",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = user.username,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
                if (user.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = VerifiedBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row {
                if (isSelf) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                } else {
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (user.isBlocked) "Unblock User" else "Block User") },
                                onClick = {
                                    showOptionsMenu = false
                                    onBlockClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (user.isRestricted) "Unrestrict User" else "Restrict User") },
                                onClick = {
                                    showOptionsMenu = false
                                    onRestrictClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Report Profile", color = AuraPink) },
                                onClick = {
                                    showOptionsMenu = false
                                    onReportClick()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Profile Header Info: Avatar & Stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AsyncImage(
                model = user.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500" },
                contentDescription = "${user.username}'s avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(2.dp, AuraPink, CircleShape)
            )

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(count = "${user.postCount}", label = "Posts")
                StatColumn(count = "${user.followerCount}", label = "Followers", onClick = onFollowersClick)
                StatColumn(count = "${user.followingCount}", label = "Following", onClick = onFollowingClick)
            }
        }

        // Bio & Website
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = user.fullName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
            )
            if (user.bio.isNotBlank()) {
                Text(
                    text = user.bio,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                )
            }
            if (user.website.isNotBlank()) {
                Text(
                    text = user.website,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSelf) {
                OutlinedButton(
                    onClick = onEditProfileClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("edit_profile_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Edit Profile", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onFollowClick,
                    enabled = !isFollowLoading,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("follow_user_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (user.followStatus) {
                            "following" -> MaterialTheme.colorScheme.surfaceVariant
                            "requested" -> MaterialTheme.colorScheme.surfaceVariant
                            else -> AuraPink
                        },
                        contentColor = when (user.followStatus) {
                            "following" -> MaterialTheme.colorScheme.onSurface
                            "requested" -> MaterialTheme.colorScheme.onSurface
                            else -> Color.White
                        }
                    )
                ) {
                    if (isFollowLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = if (user.followStatus == "following") MaterialTheme.colorScheme.onSurface else Color.White
                        )
                    } else {
                        Text(
                            text = when (user.followStatus) {
                                "following" -> "Following"
                                "requested" -> "Requested"
                                else -> "Follow"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onMessageClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("message_user_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Message", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Story Highlights Bar
        val highlights = listOf("Highlights", "Travel", "Work", "Vibes")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            items(highlights) { title ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title.take(1),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Profile Tabs
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                icon = { Icon(Icons.Default.GridOn, contentDescription = "Posts") }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                icon = { Icon(Icons.Default.Movie, contentDescription = "Reels") }
            )
            Tab(
                selected = selectedTabIndex == 2,
                onClick = { selectedTabIndex = 2 },
                icon = { Icon(Icons.Default.PersonPin, contentDescription = "Tagged") }
            )
            if (isSelf) {
                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = "Saved") }
                )
            }
        }

        // Content Grids
        val currentGridPosts = when (selectedTabIndex) {
            0 -> posts
            3 -> savedPosts
            else -> posts
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(currentGridPosts, key = { "${it.id}_${it.timestamp}_${it.caption.take(8)}" }) { post ->
                val mediaUrl = post.mediaUrlsJson.split(",").firstOrNull() ?: ""
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onPostClick(post) }
                ) {
                    AsyncImage(
                        model = mediaUrl,
                        contentDescription = "Post ${post.id}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (activeListType != null) {
        val title = if (activeListType == "followers") "Followers" else "Following"
        val usersList = if (activeListType == "followers") followersList else followingList

        AlertDialog(
            onDismissRequest = onCloseFollowList,
            confirmButton = {
                TextButton(onClick = onCloseFollowList) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingFollowList) {
                        CircularProgressIndicator(color = AuraPink)
                    } else if (usersList.isNullOrEmpty()) {
                        Text(
                            text = if (activeListType == "followers") "No followers yet." else "Not following anyone yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(usersList) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onCloseFollowList()
                                            onUserClick(item)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = item.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500" },
                                        contentDescription = item.username,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, AuraPink, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.username,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        )
                                        if (item.fullName.isNotBlank()) {
                                            Text(
                                                text = item.fullName,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun StatColumn(
    count: String,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        )
    }
}

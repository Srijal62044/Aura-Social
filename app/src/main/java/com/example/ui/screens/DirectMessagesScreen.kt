package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.UserEntity
import com.example.ui.theme.AuraPink

@Composable
fun DirectMessagesScreen(
    users: List<UserEntity>,
    unreadCounts: Map<String, Int> = emptyMap(),
    onBackClick: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Primary, 1: General, 2: Requests
    var searchQuery by remember { mutableStateOf("") }
    val totalUnread = unreadCounts.values.sum()
    val tabs = listOf("Primary${if (totalUnread > 0) " ($totalUnread)" else ""}", "General", "Requests")

    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) users
        else users.filter {
            it.username.contains(searchQuery, ignoreCase = true) ||
            it.fullName.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("direct_messages_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Messages",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                IconButton(onClick = { /* New Group */ }) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "New Group Chat")
                }
            }

            // Search Bar for followed users
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("message_search_input"),
                placeholder = { Text("Search followed users...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuraPink,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            // Conversation List or Empty State
            if (users.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "You are not following anyone yet.",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Follow users from Explore to start chatting with them directly.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredUsers, key = { it.username }) { user ->
                        val unread = unreadCounts[user.username] ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenChat(user.username) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("chat_item_${user.username}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                AsyncImage(
                                    model = user.avatarUrl,
                                    contentDescription = user.username,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                )
                                if (unread > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(AuraPink)
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.fullName.ifBlank { user.username },
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = if (unread > 0) FontWeight.ExtraBold else FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    )
                                    if (unread > 0) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(AuraPink)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "$unread NEW",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp
                                                )
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (unread > 0) "• $unread unread message${if (unread > 1) "s" else ""}" else "Active now • Tap to chat",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                                        color = if (unread > 0) AuraPink else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(onClick = { onOpenChat(user.username) }) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Quick Photo Message",
                                    tint = if (unread > 0) AuraPink else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

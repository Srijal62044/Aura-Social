package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.PostEntity
import com.example.data.local.StoryEntity
import com.example.data.local.UserEntity
import com.example.ui.components.PostCard
import com.example.ui.components.StoriesTray

@Composable
fun HomeScreen(
    currentUser: UserEntity?,
    stories: List<StoryEntity>,
    posts: List<PostEntity>,
    onAddStoryClick: () -> Unit,
    onStoryClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onLikeClick: (PostEntity) -> Unit,
    onCommentClick: (PostEntity) -> Unit,
    onSaveClick: (PostEntity) -> Unit,
    onArchiveClick: (PostEntity) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onReportClick: (PostEntity) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home_screen")
    ) {
        if (posts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No posts in your feed yet. Follow creators in Explore!",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    StoriesTray(
                        currentUser = currentUser,
                        stories = stories,
                        onAddStoryClick = onAddStoryClick,
                        onStoryClick = onStoryClick
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                items(posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        currentUsername = currentUser?.username ?: "",
                        onUserClick = onUserClick,
                        onLikeClick = { onLikeClick(post) },
                        onCommentClick = { onCommentClick(post) },
                        onSaveClick = { onSaveClick(post) },
                        onArchiveClick = { onArchiveClick(post) },
                        onDeleteClick = { onDeleteClick(post.id) },
                        onReportClick = { onReportClick(post) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        thickness = 1.dp
                    )
                }
            }
        }
    }
}

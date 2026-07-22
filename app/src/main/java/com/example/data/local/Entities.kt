package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val username: String,
    val fullName: String,
    val email: String,
    val bio: String = "",
    val website: String = "",
    val avatarUrl: String = "",
    val isPrivate: Boolean = false,
    val isVerified: Boolean = false,
    val isAdmin: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val postCount: Int = 0,
    val followStatus: String = "none", // "none", "following", "requested"
    val isBlocked: Boolean = false,
    val isRestricted: Boolean = false
)

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val username: String,
    val userAvatar: String,
    val isVerified: Boolean = false,
    val location: String = "",
    val timestamp: String = "Just now",
    val caption: String = "",
    val hashtags: String = "",
    val mediaUrlsJson: String = "", // Comma-separated or single image URL
    val isVideo: Boolean = false,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val commentsDisabled: Boolean = false,
    val isArchived: Boolean = false,
    val isReported: Boolean = false
)

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postId: Long,
    val username: String,
    val userAvatar: String,
    val text: String,
    val timestamp: String = "Just now",
    val likeCount: Int = 0,
    val isLiked: Boolean = false,
    val parentCommentId: Long = 0
)

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val userAvatar: String,
    val isVerified: Boolean = false,
    val mediaUrl: String,
    val caption: String = "",
    val timestamp: String = "1h ago",
    val isCloseFriends: Boolean = false,
    val isViewed: Boolean = false,
    val viewerCount: Int = 12
)

@Entity(tableName = "story_highlights")
data class StoryHighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val title: String,
    val coverUrl: String
)

@Entity(tableName = "reels")
data class ReelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val userAvatar: String,
    val isVerified: Boolean = false,
    val videoUrl: String,
    val thumbnailUrl: String,
    val caption: String,
    val hashtags: String = "",
    val audioTitle: String = "Original Audio",
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val shareCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isFollowing: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String, // username or groupId
    val senderUsername: String,
    val senderAvatar: String,
    val text: String = "",
    val mediaUrl: String = "",
    val type: String = "text", // "text", "image", "voice", "post_share"
    val timestamp: String = "Just now",
    val isRead: Boolean = true,
    val isMine: Boolean = true,
    val reaction: String = ""
)

@Entity(tableName = "group_chats")
data class GroupChatEntity(
    @PrimaryKey val groupId: String,
    val groupName: String,
    val groupAvatar: String = "",
    val adminUsername: String,
    val memberUsernamesJson: String, // Comma separated usernames
    val lastMessage: String = "",
    val lastMessageTime: String = "Just now"
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actorUsername: String,
    val actorAvatar: String,
    val type: String, // "follow", "follow_request", "like", "comment", "mention", "story_reaction"
    val targetId: Long = 0,
    val text: String,
    val timestamp: String = "10m ago",
    val isRead: Boolean = false
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverUrl: String = ""
)

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reporterUsername: String,
    val contentType: String, // "post", "reel", "story", "comment", "user"
    val contentId: String,
    val reason: String,
    val timestamp: String = "Just now",
    val status: String = "Pending" // "Pending", "Dismissed", "Action Taken"
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

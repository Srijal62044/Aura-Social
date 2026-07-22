package com.example.data.repository

import com.example.data.local.AuraDao
import com.example.data.local.CollectionEntity
import com.example.data.local.CommentEntity
import com.example.data.local.GroupChatEntity
import com.example.data.local.MessageEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PostEntity
import com.example.data.local.ReelEntity
import com.example.data.local.ReportEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.SeedData
import com.example.data.local.StoryEntity
import com.example.data.local.StoryHighlightEntity
import com.example.data.local.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class AuraRepository(private val dao: AuraDao) {

    suspend fun checkAndSeedInitialData() {
        val existingUsers = dao.getAllUsers().firstOrNull()
        if (existingUsers.isNullOrEmpty()) {
            dao.insertUsers(SeedData.initialUsers)
            dao.insertPosts(SeedData.initialPosts)
            dao.insertStories(SeedData.initialStories)
            dao.insertReels(SeedData.initialReels)
            dao.insertMessages(SeedData.initialMessages)
            dao.insertNotifications(SeedData.initialNotifications)
            for (col in SeedData.initialCollections) {
                dao.insertCollection(col)
            }
        }
    }

    // USERS
    fun getAllUsers(): Flow<List<UserEntity>> = dao.getAllUsers()

    fun getUserByUsername(username: String): Flow<UserEntity?> = dao.getUserByUsername(username)

    suspend fun getUserDirect(username: String): UserEntity? = dao.getUserDirect(username)

    suspend fun updateUser(user: UserEntity) = dao.updateUser(user)

    suspend fun insertUser(user: UserEntity) = dao.insertUser(user)

    fun searchUsers(query: String): Flow<List<UserEntity>> = dao.searchUsers(query)

    suspend fun toggleFollowUser(targetUsername: String, currentUsername: String) {
        val user = dao.getUserDirect(targetUsername) ?: return
        val newStatus = when (user.followStatus) {
            "following" -> "none"
            "requested" -> "none"
            else -> if (user.isPrivate) "requested" else "following"
        }
        val delta = if (newStatus == "following") 1 else if (user.followStatus == "following") -1 else 0
        dao.updateUser(user.copy(followStatus = newStatus, followerCount = (user.followerCount + delta).coerceAtLeast(0)))
    }

    suspend fun acceptFollowRequest(actorUsername: String) {
        val user = dao.getUserDirect(actorUsername) ?: return
        dao.updateUser(user.copy(followStatus = "following", followerCount = user.followerCount + 1))
    }

    suspend fun toggleBlockUser(username: String) {
        val user = dao.getUserDirect(username) ?: return
        dao.updateUser(user.copy(isBlocked = !user.isBlocked))
    }

    suspend fun toggleRestrictUser(username: String) {
        val user = dao.getUserDirect(username) ?: return
        dao.updateUser(user.copy(isRestricted = !user.isRestricted))
    }

    suspend fun toggleVerifyUser(username: String) {
        val user = dao.getUserDirect(username) ?: return
        dao.updateUser(user.copy(isVerified = !user.isVerified))
    }

    // POSTS
    fun getAllPosts(): Flow<List<PostEntity>> = dao.getAllPosts()

    fun getPostsByUsername(username: String): Flow<List<PostEntity>> = dao.getPostsByUsername(username)

    fun getSavedPosts(): Flow<List<PostEntity>> = dao.getSavedPosts()

    fun getArchivedPosts(): Flow<List<PostEntity>> = dao.getArchivedPosts()

    suspend fun createPost(post: PostEntity): Long = dao.insertPost(post)

    suspend fun updatePost(post: PostEntity) = dao.updatePost(post)

    suspend fun toggleLikePost(post: PostEntity) {
        val newLiked = !post.isLiked
        val newCount = if (newLiked) post.likeCount + 1 else (post.likeCount - 1).coerceAtLeast(0)
        dao.updatePost(post.copy(isLiked = newLiked, likeCount = newCount))
    }

    suspend fun toggleSavePost(post: PostEntity) {
        dao.updatePost(post.copy(isSaved = !post.isSaved))
    }

    suspend fun toggleArchivePost(post: PostEntity) {
        dao.updatePost(post.copy(isArchived = !post.isArchived))
    }

    suspend fun deletePost(postId: Long) = dao.deletePost(postId)

    // COMMENTS
    fun getCommentsForPost(postId: Long): Flow<List<CommentEntity>> = dao.getCommentsForPost(postId)

    suspend fun addComment(comment: CommentEntity) {
        dao.insertComment(comment)
    }

    suspend fun deleteComment(commentId: Long) = dao.deleteComment(commentId)

    // STORIES
    fun getAllStories(): Flow<List<StoryEntity>> = dao.getAllStories()

    fun getStoriesByUsername(username: String): Flow<List<StoryEntity>> = dao.getStoriesByUsername(username)

    suspend fun createStory(story: StoryEntity) = dao.insertStory(story)

    suspend fun deleteStory(storyId: Long) = dao.deleteStory(storyId)

    // HIGHLIGHTS
    fun getHighlightsByUsername(username: String): Flow<List<StoryHighlightEntity>> = dao.getHighlightsByUsername(username)

    suspend fun addHighlight(highlight: StoryHighlightEntity) {
        dao.insertHighlights(listOf(highlight))
    }

    // REELS
    fun getAllReels(): Flow<List<ReelEntity>> = dao.getAllReels()

    fun getReelsByUsername(username: String): Flow<List<ReelEntity>> = dao.getReelsByUsername(username)

    suspend fun createReel(reel: ReelEntity) = dao.insertReel(reel)

    suspend fun toggleLikeReel(reel: ReelEntity) {
        val newLiked = !reel.isLiked
        val newCount = if (newLiked) reel.likeCount + 1 else (reel.likeCount - 1).coerceAtLeast(0)
        dao.updateReel(reel.copy(isLiked = newLiked, likeCount = newCount))
    }

    // MESSAGES
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> =
        dao.getMessagesForConversation(conversationId)

    suspend fun sendMessage(message: MessageEntity) = dao.insertMessage(message)

    suspend fun deleteMessage(messageId: Long) = dao.deleteMessage(messageId)

    // GROUPS
    fun getAllGroupChats(): Flow<List<GroupChatEntity>> = dao.getAllGroupChats()

    suspend fun createGroupChat(group: GroupChatEntity) = dao.insertGroupChat(group)

    // NOTIFICATIONS
    fun getAllNotifications(): Flow<List<NotificationEntity>> = dao.getAllNotifications()

    suspend fun markNotificationRead(id: Long) = dao.markNotificationRead(id)

    suspend fun addNotification(notification: NotificationEntity) = dao.insertNotification(notification)

    // COLLECTIONS
    fun getAllCollections(): Flow<List<CollectionEntity>> = dao.getAllCollections()

    suspend fun addCollection(collection: CollectionEntity) = dao.insertCollection(collection)

    // REPORTS
    fun getAllReports(): Flow<List<ReportEntity>> = dao.getAllReports()

    suspend fun submitReport(report: ReportEntity) = dao.insertReport(report)

    suspend fun updateReport(report: ReportEntity) = dao.updateReport(report)

    // SEARCH HISTORY
    fun getSearchHistory(): Flow<List<SearchHistoryEntity>> = dao.getSearchHistory()

    suspend fun addSearchQuery(query: String) {
        if (query.isNotBlank()) {
            dao.insertSearchQuery(SearchHistoryEntity(query = query.trim()))
        }
    }

    suspend fun deleteSearchQuery(query: String) = dao.deleteSearchQuery(query)

    suspend fun clearSearchHistory() = dao.clearSearchHistory()
}

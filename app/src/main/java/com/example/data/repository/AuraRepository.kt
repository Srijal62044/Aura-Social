package com.example.data.repository

import android.content.Context
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
import com.example.data.local.StoryEntity
import com.example.data.local.StoryHighlightEntity
import com.example.data.local.UserEntity
import com.example.data.remote.SupabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AuraRepository(
    private val dao: AuraDao? = null,
    private val context: Context? = null
) {
    private val supabase = SupabaseService()
    private val scope = CoroutineScope(Dispatchers.IO)

    // StateFlow caches for real-time app state
    private val _users = MutableStateFlow<List<UserEntity>>(emptyList())
    private val _posts = MutableStateFlow<List<PostEntity>>(emptyList())
    private val _stories = MutableStateFlow<List<StoryEntity>>(emptyList())
    private val _reels = MutableStateFlow<List<ReelEntity>>(emptyList())
    private val _notifications = MutableStateFlow<List<NotificationEntity>>(emptyList())
    private val _reports = MutableStateFlow<List<ReportEntity>>(emptyList())
    private val _searchHistory = MutableStateFlow<List<SearchHistoryEntity>>(emptyList())
    private val _commentsMap = MutableStateFlow<Map<Long, List<CommentEntity>>>(emptyMap())
    private val _messagesMap = MutableStateFlow<Map<String, List<MessageEntity>>>(emptyMap())

    init {
        refreshAllData()
    }

    fun refreshAllData() {
        scope.launch {
            fetchUsers()
            fetchPosts()
            fetchStories()
            fetchReels()
            fetchReports()
        }
    }

    // --- AUTHENTICATION ---

    suspend fun getUserByUsernameOrEmail(identifier: String): UserEntity? {
        val clean = identifier.trim().lowercase()
        return supabase.getProfileByUsername(clean) ?: supabase.getProfileByEmail(clean)
    }

    suspend fun registerUser(user: UserEntity): Pair<Boolean, String> {
        return supabase.signUp(
            email = user.email,
            password = user.password,
            username = user.username,
            fullName = user.fullName
        ).also {
            if (it.first) fetchUsers()
        }
    }

    suspend fun loginUser(identifier: String, password: String): Pair<UserEntity?, String> {
        val result = supabase.signIn(identifier, password)
        if (result.first != null) {
            fetchUsers()
            fetchPosts()
        }
        return result
    }

    suspend fun resetPassword(identifier: String, newPassword: String): Pair<Boolean, String> {
        val user = getUserByUsernameOrEmail(identifier) ?: return Pair(false, "Account not found.")
        val updated = user.copy(password = newPassword)
        val success = supabase.upsertProfile(updated)
        if (success) fetchUsers()
        return Pair(success, if (success) "Password reset successfully!" else "Failed to reset password.")
    }

    // --- USERS ---

    private suspend fun fetchUsers() {
        val list = supabase.getAllProfiles()
        _users.value = list
    }

    fun getAllUsers(): Flow<List<UserEntity>> = _users.asStateFlow()

    fun getUserByUsername(username: String): Flow<UserEntity?> = _users.map { list ->
        list.find { it.username.equals(username, ignoreCase = true) }
    }

    suspend fun getUserDirect(username: String): UserEntity? {
        return supabase.getProfileByUsername(username) ?: _users.value.find { it.username.equals(username, ignoreCase = true) }
    }

    suspend fun updateUser(user: UserEntity, appContext: Context? = context) {
        var avatarUrlToSave = user.avatarUrl
        if (appContext != null && avatarUrlToSave.isNotBlank() && (avatarUrlToSave.startsWith("content://") || avatarUrlToSave.startsWith("file://"))) {
            avatarUrlToSave = supabase.uploadMedia(appContext, "avatars", avatarUrlToSave)
        }
        val cleanUser = user.copy(avatarUrl = avatarUrlToSave)
        val success = supabase.upsertProfile(cleanUser)
        if (success) fetchUsers()
    }

    suspend fun insertUser(user: UserEntity) {
        updateUser(user)
    }

    fun searchUsers(query: String): Flow<List<UserEntity>> = _users.map { list ->
        if (query.isBlank()) list else list.filter {
            it.username.contains(query, ignoreCase = true) || it.fullName.contains(query, ignoreCase = true)
        }
    }

    suspend fun toggleFollowUser(targetUsername: String, currentUsername: String) {
        val user = getUserDirect(targetUsername) ?: return
        val newStatus = when (user.followStatus) {
            "following" -> "none"
            "requested" -> "none"
            else -> if (user.isPrivate) "requested" else "following"
        }
        val delta = if (newStatus == "following") 1 else if (user.followStatus == "following") -1 else 0
        val updatedUser = user.copy(
            followStatus = newStatus,
            followerCount = (user.followerCount + delta).coerceAtLeast(0)
        )
        supabase.upsertProfile(updatedUser)
        fetchUsers()

        if (newStatus == "following" || newStatus == "requested") {
            supabase.addNotification(
                NotificationEntity(
                    recipientUsername = targetUsername,
                    actorUsername = currentUsername,
                    actorAvatar = "",
                    type = if (newStatus == "requested") "follow_request" else "follow",
                    text = if (newStatus == "requested") "requested to follow you" else "started following you"
                )
            )
        }
    }

    suspend fun acceptFollowRequest(actorUsername: String) {
        val user = getUserDirect(actorUsername) ?: return
        supabase.upsertProfile(user.copy(followStatus = "following", followerCount = user.followerCount + 1))
        fetchUsers()
    }

    suspend fun toggleBlockUser(username: String) {
        val user = getUserDirect(username) ?: return
        supabase.upsertProfile(user.copy(isBlocked = !user.isBlocked))
        fetchUsers()
    }

    suspend fun toggleRestrictUser(username: String) {
        val user = getUserDirect(username) ?: return
        supabase.upsertProfile(user.copy(isRestricted = !user.isRestricted))
        fetchUsers()
    }

    suspend fun toggleVerifyUser(username: String) {
        val user = getUserDirect(username) ?: return
        supabase.upsertProfile(user.copy(isVerified = !user.isVerified))
        fetchUsers()
    }

    // --- POSTS ---

    private suspend fun fetchPosts() {
        val list = supabase.getAllPosts()
        _posts.value = list
    }

    fun getAllPosts(): Flow<List<PostEntity>> = _posts.asStateFlow()

    fun getPostsByUsername(username: String): Flow<List<PostEntity>> = _posts.map { list ->
        list.filter { it.username.equals(username, ignoreCase = true) }
    }

    fun getSavedPosts(): Flow<List<PostEntity>> = _posts.map { list ->
        list.filter { it.isSaved }
    }

    fun getArchivedPosts(): Flow<List<PostEntity>> = _posts.map { list ->
        list.filter { it.isArchived }
    }

    suspend fun createPost(post: PostEntity, appContext: Context? = context): Long {
        var mediaUrl = post.mediaUrlsJson
        if (appContext != null && mediaUrl.isNotBlank() && (mediaUrl.startsWith("content://") || mediaUrl.startsWith("file://"))) {
            mediaUrl = supabase.uploadMedia(appContext, "post-media", mediaUrl)
        }
        val cleanPost = post.copy(mediaUrlsJson = mediaUrl)
        val postId = supabase.createPost(cleanPost)
        fetchPosts()
        return postId
    }

    suspend fun updatePost(post: PostEntity) {
        supabase.updatePost(post)
        fetchPosts()
    }

    suspend fun toggleLikePost(post: PostEntity) {
        val newLiked = !post.isLiked
        val newCount = if (newLiked) post.likeCount + 1 else (post.likeCount - 1).coerceAtLeast(0)
        val updated = post.copy(isLiked = newLiked, likeCount = newCount)
        supabase.updatePost(updated)
        fetchPosts()
    }

    suspend fun toggleSavePost(post: PostEntity) {
        val updated = post.copy(isSaved = !post.isSaved)
        supabase.updatePost(updated)
        fetchPosts()
    }

    suspend fun toggleArchivePost(post: PostEntity) {
        val updated = post.copy(isArchived = !post.isArchived)
        supabase.updatePost(updated)
        fetchPosts()
    }

    suspend fun deletePost(postId: Long) {
        supabase.deletePost(postId)
        fetchPosts()
    }

    // --- COMMENTS ---

    fun getCommentsForPost(postId: Long): Flow<List<CommentEntity>> = _commentsMap.map { map ->
        map[postId] ?: emptyList()
    }

    suspend fun fetchComments(postId: Long) {
        val comments = supabase.getCommentsForPost(postId)
        _commentsMap.value = _commentsMap.value.toMutableMap().apply { put(postId, comments) }
    }

    suspend fun addComment(comment: CommentEntity) {
        supabase.addComment(comment)
        fetchComments(comment.postId)
        val post = _posts.value.find { it.id == comment.postId }
        if (post != null) {
            updatePost(post.copy(commentCount = post.commentCount + 1))
        }
    }

    suspend fun deleteComment(commentId: Long) {
        supabase.deleteComment(commentId)
    }

    // --- STORIES ---

    private suspend fun fetchStories() {
        val list = supabase.getAllStories()
        _stories.value = list
    }

    fun getAllStories(): Flow<List<StoryEntity>> = _stories.asStateFlow()

    fun getStoriesByUsername(username: String): Flow<List<StoryEntity>> = _stories.map { list ->
        list.filter { it.username.equals(username, ignoreCase = true) }
    }

    suspend fun createStory(story: StoryEntity, appContext: Context? = context) {
        var mediaUrl = story.mediaUrl
        if (appContext != null && mediaUrl.isNotBlank() && (mediaUrl.startsWith("content://") || mediaUrl.startsWith("file://"))) {
            mediaUrl = supabase.uploadMedia(appContext, "post-media", mediaUrl)
        }
        val cleanStory = story.copy(mediaUrl = mediaUrl)
        supabase.createStory(cleanStory)
        fetchStories()
    }

    suspend fun deleteStory(storyId: Long) {
        supabase.deleteStory(storyId)
        fetchStories()
    }

    // --- HIGHLIGHTS ---

    fun getHighlightsByUsername(username: String): Flow<List<StoryHighlightEntity>> = MutableStateFlow(emptyList<StoryHighlightEntity>()).asStateFlow()

    suspend fun addHighlight(highlight: StoryHighlightEntity) {}

    // --- REELS ---

    private suspend fun fetchReels() {
        val list = supabase.getAllReels()
        _reels.value = list
    }

    fun getAllReels(): Flow<List<ReelEntity>> = _reels.asStateFlow()

    fun getReelsByUsername(username: String): Flow<List<ReelEntity>> = _reels.map { list ->
        list.filter { it.username.equals(username, ignoreCase = true) }
    }

    suspend fun createReel(reel: ReelEntity, appContext: Context? = context) {
        var videoUrl = reel.videoUrl
        if (appContext != null && videoUrl.isNotBlank() && (videoUrl.startsWith("content://") || videoUrl.startsWith("file://"))) {
            videoUrl = supabase.uploadMedia(appContext, "post-media", videoUrl)
        }
        val cleanReel = reel.copy(videoUrl = videoUrl)
        supabase.createReel(cleanReel)
        fetchReels()
    }

    suspend fun toggleLikeReel(reel: ReelEntity) {
        val newLiked = !reel.isLiked
        val newCount = if (newLiked) reel.likeCount + 1 else (reel.likeCount - 1).coerceAtLeast(0)
        fetchReels()
    }

    // --- MESSAGES ---

    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> = _messagesMap.map { map ->
        map[conversationId] ?: emptyList()
    }

    suspend fun fetchMessages(conversationId: String) {
        val list = supabase.getMessagesForConversation(conversationId)
        _messagesMap.value = _messagesMap.value.toMutableMap().apply { put(conversationId, list) }
    }

    suspend fun sendMessage(message: MessageEntity, appContext: Context? = context) {
        var mediaUrl = message.mediaUrl
        if (appContext != null && mediaUrl.isNotBlank() && (mediaUrl.startsWith("content://") || mediaUrl.startsWith("file://"))) {
            mediaUrl = supabase.uploadMedia(appContext, "post-media", mediaUrl)
        }
        val cleanMsg = message.copy(mediaUrl = mediaUrl)
        supabase.sendMessage(cleanMsg)
        fetchMessages(message.conversationId)
    }

    suspend fun deleteMessage(messageId: Long) {
        supabase.deleteMessage(messageId)
    }

    // --- GROUPS ---

    fun getAllGroupChats(): Flow<List<GroupChatEntity>> = MutableStateFlow(emptyList<GroupChatEntity>()).asStateFlow()

    suspend fun createGroupChat(group: GroupChatEntity) {}

    // --- NOTIFICATIONS ---

    private suspend fun fetchNotifications(username: String) {
        if (username.isBlank()) return
        val list = supabase.getNotifications(username)
        _notifications.value = list
    }

    fun getAllNotifications(): Flow<List<NotificationEntity>> = _notifications.asStateFlow()

    suspend fun refreshNotificationsForUser(username: String) {
        fetchNotifications(username)
    }

    suspend fun markNotificationRead(id: Long) {
        val updated = _notifications.value.map { if (it.id == id) it.copy(isRead = true) else it }
        _notifications.value = updated
    }

    suspend fun addNotification(notification: NotificationEntity) {
        supabase.addNotification(notification)
        fetchNotifications(notification.recipientUsername)
    }

    // --- COLLECTIONS ---

    fun getAllCollections(): Flow<List<CollectionEntity>> = MutableStateFlow(emptyList<CollectionEntity>()).asStateFlow()

    suspend fun addCollection(collection: CollectionEntity) {}

    // --- REPORTS ---

    private suspend fun fetchReports() {
        val list = supabase.getAllReports()
        _reports.value = list
    }

    fun getAllReports(): Flow<List<ReportEntity>> = _reports.asStateFlow()

    suspend fun submitReport(report: ReportEntity) {
        supabase.submitReport(report)
        fetchReports()
    }

    suspend fun updateReport(report: ReportEntity) {
        fetchReports()
    }

    // --- SEARCH HISTORY ---

    fun getSearchHistory(): Flow<List<SearchHistoryEntity>> = _searchHistory.asStateFlow()

    suspend fun addSearchQuery(query: String) {
        if (query.isNotBlank()) {
            val list = _searchHistory.value.toMutableList()
            list.removeAll { it.query.equals(query, ignoreCase = true) }
            list.add(0, SearchHistoryEntity(query = query.trim()))
            _searchHistory.value = list
        }
    }

    suspend fun deleteSearchQuery(query: String) {
        val list = _searchHistory.value.filterNot { it.query.equals(query, ignoreCase = true) }
        _searchHistory.value = list
    }

    suspend fun clearSearchHistory() {
        _searchHistory.value = emptyList()
    }
}

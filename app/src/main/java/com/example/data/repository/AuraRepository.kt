package com.example.data.repository

import android.content.Context
import android.util.Log
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
    val supabase = SupabaseService()
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

    fun setSession(userId: String?, token: String?) {
        supabase.currentAuthUserId = userId
        supabase.currentUserToken = token
    }

    fun getUserToken(): String? {
        return supabase.currentUserToken
    }

    suspend fun refreshCurrentProfile(userId: String, token: String): UserEntity? {
        supabase.currentAuthUserId = userId
        supabase.currentUserToken = token
        val profile = supabase.getProfileByUuidWithRetry(userId, maxRetries = 5, delayMs = 1000)
        if (profile != null) {
            fetchUsers(profile.username)
        }
        return profile
    }

    suspend fun getUserByUsernameOrEmail(identifier: String): UserEntity? {
        val clean = identifier.trim().lowercase()
        return supabase.getProfileByUsername(clean) ?: supabase.getProfileByEmail(clean)
    }

    suspend fun registerUser(user: UserEntity): Pair<UserEntity?, String> {
        val res = supabase.signUp(
            email = user.email,
            password = user.password,
            username = user.username,
            fullName = user.fullName
        )
        if (res.first != null) {
            fetchUsers()
        }
        return res
    }

    suspend fun loginUser(identifier: String, password: String): Pair<UserEntity?, String> {
        val result = supabase.signIn(identifier, password)
        if (result.first != null) {
            fetchUsers()
            fetchPosts()
        }
        return result
    }

    suspend fun loginWithToken(accessToken: String): Pair<UserEntity?, String> {
        val result = supabase.signInWithToken(accessToken)
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

    suspend fun fetchUsers(currentUsername: String? = null) {
        val list = supabase.getAllProfiles()
        if (!currentUsername.isNullOrBlank()) {
            val updatedList = list.map { u ->
                if (u.username.equals(currentUsername, ignoreCase = true)) {
                    u
                } else {
                    val status = supabase.getFollowStatus(currentUsername, u.username)
                    u.copy(followStatus = status)
                }
            }
            _users.value = updatedList
        } else {
            _users.value = list
        }
    }

    fun getAllUsers(): Flow<List<UserEntity>> = _users.asStateFlow()

    fun getUserByUsername(username: String): Flow<UserEntity?> = _users.map { list ->
        list.find { it.username.equals(username, ignoreCase = true) }
    }

    suspend fun getUserDirect(username: String, currentUsername: String? = null): UserEntity? {
        val profile = supabase.getProfileByUsername(username) ?: _users.value.find { it.username.equals(username, ignoreCase = true) }
        return if (profile != null && !currentUsername.isNullOrBlank() && !username.equals(currentUsername, ignoreCase = true)) {
            val status = supabase.getFollowStatus(currentUsername, username)
            profile.copy(followStatus = status)
        } else profile
    }

    suspend fun getUserByUuid(uuid: String): UserEntity? {
        if (uuid.isBlank()) return null
        return supabase.getProfileByUuid(uuid) ?: _users.value.find { it.id.equals(uuid, ignoreCase = true) }
    }

    suspend fun getCurrentUserAuthId(): String? {
        return supabase.getCurrentUserAuthId()
    }

    suspend fun updateUser(user: UserEntity, appContext: Context? = context) {
        var avatarUrlToSave = user.avatarUrl
        if (appContext != null && avatarUrlToSave.isNotBlank() && (avatarUrlToSave.startsWith("content://") || avatarUrlToSave.startsWith("file://"))) {
            avatarUrlToSave = supabase.uploadMedia(appContext, "avatars", avatarUrlToSave)
        }
        val cleanUser = user.copy(avatarUrl = avatarUrlToSave)
        val success = supabase.upsertProfile(cleanUser)
        if (success) fetchUsers(user.username)
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
        if (targetUsername.isBlank() || currentUsername.isBlank() || targetUsername.equals(currentUsername, ignoreCase = true)) {
            Log.w("AuraRepository", "toggleFollowUser ignored: self-follow or blank username")
            return
        }

        val targetUser = getUserDirect(targetUsername, currentUsername) ?: return
        val currentUserProfile = getUserDirect(currentUsername)

        // Optimistic UI calculation
        val oldStatus = targetUser.followStatus
        val nextStatus = when (oldStatus) {
            "following", "requested" -> "none"
            else -> if (targetUser.isPrivate) "requested" else "following"
        }

        // Apply optimistic update
        val updatedTargetOpt = targetUser.copy(
            followStatus = nextStatus,
            followerCount = if (nextStatus == "following") targetUser.followerCount + 1 else if (oldStatus == "following") (targetUser.followerCount - 1).coerceAtLeast(0) else targetUser.followerCount
        )
        _users.value = _users.value.map { if (it.username.equals(targetUsername, ignoreCase = true)) updatedTargetOpt else it }

        val realStatus = supabase.toggleFollow(currentUsername, targetUsername, targetUser.isPrivate)

        val updatedTargetFinal = targetUser.copy(
            followStatus = realStatus,
            followerCount = if (realStatus == "following") targetUser.followerCount + 1 else if (oldStatus == "following") (targetUser.followerCount - 1).coerceAtLeast(0) else targetUser.followerCount
        )
        supabase.upsertProfile(updatedTargetFinal)

        if (currentUserProfile != null) {
            val updatedCurrent = currentUserProfile.copy(
                followingCount = if (realStatus == "following") currentUserProfile.followingCount + 1 else if (oldStatus == "following") (currentUserProfile.followingCount - 1).coerceAtLeast(0) else currentUserProfile.followingCount
            )
            supabase.upsertProfile(updatedCurrent)
        }

        fetchUsers(currentUsername)

        if (realStatus == "following" || realStatus == "requested") {
            supabase.addNotification(
                NotificationEntity(
                    recipientUsername = targetUsername,
                    actorUsername = currentUsername,
                    actorAvatar = currentUserProfile?.avatarUrl ?: "",
                    type = if (realStatus == "requested") "follow_request" else "follow",
                    text = if (realStatus == "requested") "requested to follow you" else "started following you"
                )
            )
        }
    }

    suspend fun acceptFollowRequest(actorUsername: String, currentUsername: String) {
        if (actorUsername.isBlank() || currentUsername.isBlank()) return
        val success = supabase.acceptFollowRequest(actorUsername, currentUsername)
        if (success) {
            fetchUsers(currentUsername)
        }
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
        if (appContext != null && mediaUrl.isNotBlank()) {
            val urls = mediaUrl.split(",").map { it.trim() }
            val uploaded = urls.map { u ->
                if (u.startsWith("content://") || u.startsWith("file://")) {
                    supabase.uploadMedia(appContext, "post-media", u)
                } else u
            }
            mediaUrl = uploaded.joinToString(",")
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

    suspend fun fetchMessages(currentUsername: String, conversationId: String) {
        if (currentUsername.isBlank() || conversationId.isBlank()) return
        val list = supabase.getMessagesForConversation(currentUsername, conversationId)
        _messagesMap.value = _messagesMap.value.toMutableMap().apply { put(conversationId, list) }
    }

    suspend fun sendMessage(message: MessageEntity, appContext: Context? = context) {
        var mediaUrl = message.mediaUrl
        if (appContext != null && mediaUrl.isNotBlank() && (mediaUrl.startsWith("content://") || mediaUrl.startsWith("file://"))) {
            mediaUrl = supabase.uploadMedia(appContext, "post-media", mediaUrl)
        }
        val cleanMsg = message.copy(mediaUrl = mediaUrl)
        supabase.sendMessage(cleanMsg)
        fetchMessages(message.senderUsername, message.conversationId)
    }

    suspend fun markMessagesAsRead(currentUsername: String, peerUsername: String) {
        if (currentUsername.isBlank() || peerUsername.isBlank()) return
        val success = supabase.markMessagesAsRead(currentUsername, peerUsername)
        if (success) {
            fetchMessages(currentUsername, peerUsername)
        }
    }

    suspend fun getUnreadCounts(currentUsername: String): Map<String, Int> {
        if (currentUsername.isBlank()) return emptyMap()
        return supabase.getUnreadCounts(currentUsername)
    }

    suspend fun getConversationUuid(username1: String, username2: String): String {
        val user1 = getUserDirect(username1)
        val user2 = getUserDirect(username2)
        var id1 = user1?.id?.takeIf { it.isNotBlank() }
        if (id1.isNullOrBlank()) id1 = supabase.getCurrentUserAuthId()
        if (id1.isNullOrBlank() || id1.equals(username1, ignoreCase = true)) {
            val prof1 = supabase.getProfileByUsername(username1)
            id1 = prof1?.id?.takeIf { it.isNotBlank() }
        }
        if (id1.isNullOrBlank()) {
            id1 = java.util.UUID.nameUUIDFromBytes("user_$username1".toByteArray()).toString()
        }

        var id2 = user2?.id?.takeIf { it.isNotBlank() }
        if (id2.isNullOrBlank() || id2.equals(username2, ignoreCase = true)) {
            val prof2 = supabase.getProfileByUsername(username2)
            id2 = prof2?.id?.takeIf { it.isNotBlank() }
        }
        if (id2.isNullOrBlank()) {
            id2 = java.util.UUID.nameUUIDFromBytes("user_$username2".toByteArray()).toString()
        }

        val sorted = listOf(id1, id2).sorted()
        return try {
            java.util.UUID.nameUUIDFromBytes("conversation_${sorted[0]}_${sorted[1]}".toByteArray()).toString()
        } catch (e: Exception) {
            java.util.UUID.randomUUID().toString()
        }
    }

    suspend fun sendTypingStatus(peerUsername: String, currentUsername: String, isTyping: Boolean) {
        if (peerUsername.isBlank() || currentUsername.isBlank()) return
        val convUuid = getConversationUuid(currentUsername, peerUsername)
        val currentUser = _users.value.find { it.username.equals(currentUsername, ignoreCase = true) }
        var userUuid = currentUser?.id?.takeIf { it.isNotBlank() }
        if (userUuid.isNullOrBlank()) {
            userUuid = supabase.getCurrentUserAuthId()
        }
        if (userUuid.isNullOrBlank()) return

        supabase.sendTypingStatus(convUuid, userUuid, isTyping)
    }

    suspend fun getTypingUsers(peerUsername: String, currentUsername: String): List<String> {
        if (peerUsername.isBlank() || currentUsername.isBlank()) return emptyList()
        val convUuid = getConversationUuid(currentUsername, peerUsername)
        val currentUser = _users.value.find { it.username.equals(currentUsername, ignoreCase = true) }
        var currentUuid = currentUser?.id?.takeIf { it.isNotBlank() }
        if (currentUuid.isNullOrBlank()) {
            currentUuid = supabase.getCurrentUserAuthId() ?: ""
        }

        val typingUuids = supabase.getTypingUsers(convUuid, currentUuid)
        return typingUuids.mapNotNull { uid ->
            getUserByUuid(uid)?.username ?: _users.value.find { it.id.equals(uid, ignoreCase = true) }?.username
        }
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

    // --- CALL SIGNALING ---

    suspend fun createCallRecordEx(
        callerUuid: String,
        receiverUuid: String,
        roomId: String,
        callType: String
    ): Pair<Boolean, String> {
        return supabase.createCallRecordEx(callerUuid, receiverUuid, roomId, callType)
    }

    suspend fun createCallRecord(call: com.example.data.remote.CallRecord): Boolean {
        return supabase.createCallRecord(call)
    }

    suspend fun updateCallStatus(callId: String, status: String): Boolean {
        return supabase.updateCallStatus(callId, status)
    }

    suspend fun getCallRecord(callId: String): com.example.data.remote.CallRecord? {
        return supabase.getCallRecord(callId)
    }

    suspend fun getPendingIncomingCall(receiverId: String): com.example.data.remote.CallRecord? {
        return supabase.getPendingIncomingCall(receiverId)
    }

    suspend fun fetchLiveKitToken(roomName: String, identity: String): String? {
        return supabase.fetchLiveKitToken(roomName, identity)
    }
}

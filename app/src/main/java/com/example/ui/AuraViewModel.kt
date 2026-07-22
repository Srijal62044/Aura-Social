package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.CollectionEntity
import com.example.data.local.CommentEntity
import com.example.data.local.GroupChatEntity
import com.example.data.local.MessageEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PostEntity
import com.example.data.local.ReelEntity
import com.example.data.local.ReportEntity
import com.example.data.local.SessionManager
import com.example.data.local.StoryEntity
import com.example.data.local.StoryHighlightEntity
import com.example.data.local.UserEntity
import com.example.data.repository.AuraRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AuraScreen {
    HOME, EXPLORE, CREATE, REELS, PROFILE, DIRECT_MESSAGES, CHAT_DETAIL,
    NOTIFICATIONS, SETTINGS, ADMIN_DASHBOARD, SAVED_POSTS, EDIT_PROFILE,
    USER_PROFILE, STORY_VIEWER, ARCHIVE
}

enum class AuthState {
    LOGGED_IN, LOGIN, REGISTER, FORGOT_PASSWORD
}

data class CallState(
    val isActive: Boolean = false,
    val isVideo: Boolean = true,
    val peerUsername: String = "",
    val peerAvatar: String = "",
    val isMuted: Boolean = false,
    val isCameraOn: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class AuraViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AuraRepository
    private val sessionManager = SessionManager(application)

    // AUTH STATE
    private val _authState = MutableStateFlow(AuthState.LOGIN)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _currentUsername = MutableStateFlow("")
    val currentUsername: StateFlow<String> = _currentUsername.asStateFlow()

    init {
        val dao = AppDatabase.getDatabase(application).auraDao()
        repository = AuraRepository(dao, application)

        val savedUser = sessionManager.getSessionUsername()
        if (!savedUser.isNullOrBlank()) {
            viewModelScope.launch {
                val dbUser = repository.getUserDirect(savedUser)
                if (dbUser != null) {
                    _currentUsername.value = dbUser.username
                    _authState.value = AuthState.LOGGED_IN
                } else {
                    sessionManager.clearSession()
                    _authState.value = AuthState.LOGIN
                }
            }
        } else {
            _authState.value = AuthState.LOGIN
        }
    }

    val currentUser: StateFlow<UserEntity?> = combine(_currentUsername, repository.getAllUsers()) { username, users ->
        if (username.isBlank()) {
            null
        } else {
            users.find { it.username.equals(username, ignoreCase = true) }
                ?: UserEntity(
                    username = username,
                    fullName = username,
                    email = "",
                    avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500"
                )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // THEME & NAVIGATION
    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _currentScreen = MutableStateFlow(AuraScreen.HOME)
    val currentScreen: StateFlow<AuraScreen> = _currentScreen.asStateFlow()

    // TOAST / FEEDBACK
    private val _userFeedback = MutableStateFlow<String?>(null)
    val userFeedback: StateFlow<String?> = _userFeedback.asStateFlow()

    // DATA STREAMS FROM ROOM
    val allPosts: StateFlow<List<PostEntity>> = repository.getAllPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedPosts: StateFlow<List<PostEntity>> = repository.getSavedPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedPosts: StateFlow<List<PostEntity>> = repository.getArchivedPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStories: StateFlow<List<StoryEntity>> = repository.getAllStories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReels: StateFlow<List<ReelEntity>> = repository.getAllReels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUsers: StateFlow<List<UserEntity>> = repository.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotifications: StateFlow<List<NotificationEntity>> = combine(
        repository.getAllNotifications(),
        _currentUsername
    ) { notifications, username ->
        notifications.filter { it.recipientUsername.isBlank() || it.recipientUsername == username }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCollections: StateFlow<List<CollectionEntity>> = repository.getAllCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGroupChats: StateFlow<List<GroupChatEntity>> = repository.getAllGroupChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReports: StateFlow<List<ReportEntity>> = repository.getAllReports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory = repository.getSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // SELECTED USER FOR PROFILE VIEW
    private val _selectedUsername = MutableStateFlow<String?>(null)
    val selectedUsername: StateFlow<String?> = _selectedUsername.asStateFlow()

    private val _selectedUserProfile = MutableStateFlow<UserEntity?>(null)
    val selectedUserProfile: StateFlow<UserEntity?> = _selectedUserProfile.asStateFlow()

    fun selectUserProfile(username: String) {
        if (username.equals(_currentUsername.value, ignoreCase = true)) {
            _currentScreen.value = AuraScreen.PROFILE
            return
        }
        _selectedUsername.value = username
        viewModelScope.launch {
            val profile = repository.getUserDirect(username)
                ?: UserEntity(
                    username = username,
                    fullName = username,
                    email = "",
                    avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500"
                )
            _selectedUserProfile.value = profile
            _currentScreen.value = AuraScreen.USER_PROFILE
        }
    }

    val selectedUserPosts: StateFlow<List<PostEntity>> = combine(
        allPosts,
        _selectedUsername
    ) { posts, username ->
        if (username.isNullOrEmpty()) emptyList()
        else posts.filter { it.userId.equals(username, ignoreCase = true) || it.username.equals(username, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedUserReels: StateFlow<List<ReelEntity>> = combine(
        allReels,
        _selectedUsername
    ) { reels, username ->
        if (username.isNullOrEmpty()) emptyList()
        else reels.filter { it.username.equals(username, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentUserPosts: StateFlow<List<PostEntity>> = combine(
        allPosts,
        _currentUsername
    ) { posts, username ->
        if (username.isEmpty()) emptyList()
        else posts.filter { it.userId.equals(username, ignoreCase = true) || it.username.equals(username, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentUserReels: StateFlow<List<ReelEntity>> = combine(
        allReels,
        _currentUsername
    ) { reels, username ->
        if (username.isEmpty()) emptyList()
        else reels.filter { it.username.equals(username, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // SELECTED STORY FOR VIEWER
    private val _selectedStoryUser = MutableStateFlow<String?>(null)
    val selectedStoryUser: StateFlow<String?> = _selectedStoryUser.asStateFlow()

    fun openStoryViewer(username: String) {
        _selectedStoryUser.value = username
        _currentScreen.value = AuraScreen.STORY_VIEWER
    }

    // ACTIVE COMMENTS POST
    private val _activePostForComments = MutableStateFlow<PostEntity?>(null)
    val activePostForComments: StateFlow<PostEntity?> = _activePostForComments.asStateFlow()

    private val _commentsForActivePost = MutableStateFlow<List<CommentEntity>>(emptyList())
    val commentsForActivePost: StateFlow<List<CommentEntity>> = _commentsForActivePost.asStateFlow()

    fun openCommentsForPost(post: PostEntity) {
        _activePostForComments.value = post
        viewModelScope.launch {
            repository.getCommentsForPost(post.id).collect {
                _commentsForActivePost.value = it
            }
        }
    }

    fun closeComments() {
        _activePostForComments.value = null
    }

    fun addComment(text: String) {
        val post = _activePostForComments.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            val username = _currentUsername.value
            val user = currentUser.value
            repository.addComment(
                CommentEntity(
                    postId = post.id,
                    username = username,
                    userAvatar = user?.avatarUrl ?: "",
                    text = text.trim(),
                    timestamp = "Just now"
                )
            )
            repository.updatePost(post.copy(commentCount = post.commentCount + 1))
            if (post.username != username) {
                repository.addNotification(
                    NotificationEntity(
                        recipientUsername = post.username,
                        actorUsername = username,
                        actorAvatar = user?.avatarUrl ?: "",
                        type = "comment",
                        targetId = post.id,
                        text = "commented: '${text.trim()}'",
                        timestamp = "Just now"
                    )
                )
            }
            showFeedback("Comment posted!")
        }
    }

    fun deleteComment(comment: CommentEntity) {
        viewModelScope.launch {
            repository.deleteComment(comment.id)
            val post = _activePostForComments.value
            if (post != null) {
                repository.updatePost(post.copy(commentCount = (post.commentCount - 1).coerceAtLeast(0)))
            }
        }
    }

    // DIRECT MESSAGES & CALLS
    private val _selectedConversationId = MutableStateFlow<String?>(null)
    val selectedConversationId: StateFlow<String?> = _selectedConversationId.asStateFlow()

    val activeMessages: StateFlow<List<MessageEntity>> = _selectedConversationId.flatMapLatest { convId ->
        if (convId.isNullOrEmpty()) flowOf(emptyList())
        else repository.getMessagesForConversation(convId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openChat(username: String) {
        _selectedConversationId.value = username
        _currentScreen.value = AuraScreen.CHAT_DETAIL
    }

    fun sendMessage(text: String, mediaUrl: String = "", type: String = "text") {
        val recipient = _selectedConversationId.value ?: return
        if (text.isBlank() && mediaUrl.isBlank()) return
        viewModelScope.launch {
            val sender = _currentUsername.value
            val user = currentUser.value
            repository.sendMessage(
                MessageEntity(
                    conversationId = recipient,
                    senderUsername = sender,
                    recipientUsername = recipient,
                    senderAvatar = user?.avatarUrl ?: "",
                    text = text,
                    mediaUrl = mediaUrl,
                    type = type,
                    isMine = true,
                    timestamp = "Just now"
                )
            )
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    // CALL STATE
    private val _callState = MutableStateFlow(CallState())
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    fun startCall(isVideo: Boolean) {
        val peer = _selectedConversationId.value ?: "User"
        _callState.value = CallState(
            isActive = true,
            isVideo = isVideo,
            peerUsername = peer,
            peerAvatar = ""
        )
    }

    fun endCall() {
        _callState.value = CallState(isActive = false)
    }

    fun toggleMute() {
        _callState.value = _callState.value.copy(isMuted = !_callState.value.isMuted)
    }

    fun toggleCamera() {
        _callState.value = _callState.value.copy(isCameraOn = !_callState.value.isCameraOn)
    }

    // SEARCH
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun performSearch(query: String) {
        if (query.isNotBlank()) {
            viewModelScope.launch {
                repository.addSearchQuery(query)
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { repository.clearSearchHistory() }
    }

    // POST ACTIONS
    fun toggleLikePost(post: PostEntity) {
        viewModelScope.launch {
            val username = _currentUsername.value
            val user = currentUser.value
            repository.toggleLikePost(post)
            if (!post.isLiked && post.username != username) {
                repository.addNotification(
                    NotificationEntity(
                        recipientUsername = post.username,
                        actorUsername = username,
                        actorAvatar = user?.avatarUrl ?: "",
                        type = "like",
                        targetId = post.id,
                        text = "liked your post.",
                        timestamp = "Just now"
                    )
                )
            }
        }
    }

    fun toggleSavePost(post: PostEntity) {
        viewModelScope.launch {
            repository.toggleSavePost(post)
            showFeedback(if (!post.isSaved) "Saved to collection" else "Removed from saved")
        }
    }

    fun toggleArchivePost(post: PostEntity) {
        viewModelScope.launch {
            repository.toggleArchivePost(post)
            showFeedback(if (!post.isArchived) "Post archived" else "Unarchived")
        }
    }

    fun deletePost(postId: Long) {
        viewModelScope.launch {
            repository.deletePost(postId)
            val user = currentUser.value
            if (user != null) {
                repository.updateUser(user.copy(postCount = (user.postCount - 1).coerceAtLeast(0)))
            }
            showFeedback("Post deleted")
        }
    }

    fun createPost(caption: String, location: String, mediaUrl: String, commentsDisabled: Boolean) {
        viewModelScope.launch {
            val username = _currentUsername.value
            if (username.isBlank()) {
                showFeedback("Please log in to share a post.")
                return@launch
            }
            val user = currentUser.value ?: repository.getUserDirect(username) ?: UserEntity(username = username, fullName = username, email = "")

            val newPost = PostEntity(
                userId = username,
                username = username,
                userAvatar = user.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500" },
                isVerified = user.isVerified,
                location = location,
                caption = caption,
                mediaUrlsJson = mediaUrl,
                commentsDisabled = commentsDisabled,
                timestamp = "Just now"
            )
            repository.createPost(newPost)
            repository.updateUser(user.copy(postCount = user.postCount + 1))
            showFeedback("Post published!")
            _currentScreen.value = AuraScreen.HOME
            repository.refreshAllData()
        }
    }

    // STORY ACTIONS
    fun createStory(caption: String, mediaUrl: String, isCloseFriends: Boolean) {
        viewModelScope.launch {
            val username = _currentUsername.value
            if (username.isBlank()) {
                showFeedback("Please log in to share a story.")
                return@launch
            }
            val user = currentUser.value ?: repository.getUserDirect(username) ?: UserEntity(username = username, fullName = username, email = "")

            repository.createStory(
                StoryEntity(
                    username = username,
                    userAvatar = user.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500" },
                    isVerified = user.isVerified,
                    mediaUrl = mediaUrl,
                    caption = caption,
                    isCloseFriends = isCloseFriends,
                    timestamp = "Just now"
                )
            )
            showFeedback("Story added!")
            _currentScreen.value = AuraScreen.HOME
            repository.refreshAllData()
        }
    }

    fun deleteStory(storyId: Long) {
        viewModelScope.launch {
            repository.deleteStory(storyId)
            showFeedback("Story deleted")
            _currentScreen.value = AuraScreen.HOME
        }
    }

    // REEL ACTIONS
    fun toggleLikeReel(reel: ReelEntity) {
        viewModelScope.launch { repository.toggleLikeReel(reel) }
    }

    fun createReel(caption: String, videoUrl: String, thumbnailUrl: String) {
        viewModelScope.launch {
            val username = _currentUsername.value
            if (username.isBlank()) {
                showFeedback("Please log in to share a reel.")
                return@launch
            }
            val user = currentUser.value ?: repository.getUserDirect(username) ?: UserEntity(username = username, fullName = username, email = "")

            repository.createReel(
                ReelEntity(
                    username = username,
                    userAvatar = user.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500" },
                    isVerified = user.isVerified,
                    videoUrl = videoUrl.ifBlank { "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4" },
                    thumbnailUrl = thumbnailUrl,
                    caption = caption
                )
            )
            showFeedback("Reel uploaded!")
            _currentScreen.value = AuraScreen.REELS
            repository.refreshAllData()
        }
    }

    // FOLLOW / RELATIONSHIP
    fun toggleFollowUser(username: String) {
        viewModelScope.launch {
            val current = _currentUsername.value
            val user = currentUser.value
            if (current.isBlank() || current == username) return@launch

            repository.toggleFollowUser(username, current)
            val updated = repository.getUserDirect(username)
            if (updated != null) {
                if (_selectedUsername.value == username) {
                    _selectedUserProfile.value = updated
                }
                if (updated.followStatus == "following" || updated.followStatus == "requested") {
                    repository.addNotification(
                        NotificationEntity(
                            recipientUsername = username,
                            actorUsername = current,
                            actorAvatar = user?.avatarUrl ?: "",
                            type = if (updated.followStatus == "requested") "follow_request" else "follow",
                            text = if (updated.followStatus == "requested") "requested to follow you." else "started following you.",
                            timestamp = "Just now"
                        )
                    )
                }
            }
        }
    }

    fun acceptFollowRequest(actorUsername: String) {
        viewModelScope.launch {
            repository.acceptFollowRequest(actorUsername)
            showFeedback("Accepted follow request from @$actorUsername")
        }
    }

    fun toggleBlockUser(username: String) {
        viewModelScope.launch {
            repository.toggleBlockUser(username)
            showFeedback("Updated block status for @$username")
        }
    }

    fun toggleRestrictUser(username: String) {
        viewModelScope.launch {
            repository.toggleRestrictUser(username)
            showFeedback("Updated restrict status for @$username")
        }
    }

    // EDIT PROFILE
    fun updateProfile(fullName: String, bio: String, website: String, avatarUrl: String, isPrivate: Boolean) {
        viewModelScope.launch {
            val user = currentUser.value
            if (user != null) {
                repository.updateUser(
                    user.copy(
                        fullName = fullName,
                        bio = bio,
                        website = website,
                        avatarUrl = avatarUrl,
                        isPrivate = isPrivate
                    )
                )
                showFeedback("Profile updated!")
                _currentScreen.value = AuraScreen.PROFILE
            }
        }
    }

    // REPORTING & SAFETY
    fun submitReport(contentType: String, contentId: String, reason: String) {
        viewModelScope.launch {
            val username = _currentUsername.value
            repository.submitReport(
                ReportEntity(
                    reporterUsername = username,
                    contentType = contentType,
                    contentId = contentId,
                    reason = reason
                )
            )
            showFeedback("Report submitted. Thank you for keeping Aura safe.")
        }
    }

    // ADMIN MODERATION ACTIONS
    fun toggleVerifyUserAdmin(username: String) {
        viewModelScope.launch {
            repository.toggleVerifyUser(username)
            showFeedback("Updated verification badge for @$username")
        }
    }

    fun updateReportStatus(report: ReportEntity, newStatus: String) {
        viewModelScope.launch {
            repository.updateReport(report.copy(status = newStatus))
            showFeedback("Report status set to $newStatus")
        }
    }

    // NAVIGATION & UI HELPERS
    fun refreshFeed() {
        repository.refreshAllData()
    }

    fun navigateTo(screen: AuraScreen) {
        _currentScreen.value = screen
        refreshFeed()
    }

    fun switchAuthState(state: AuthState) {
        _authError.value = null
        _authState.value = state
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun showFeedback(message: String) {
        _userFeedback.value = message
    }

    fun clearFeedback() {
        _userFeedback.value = null
    }

    fun login(identifier: String, p: String) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val (user, msg) = repository.loginUser(identifier, p)
            _authLoading.value = false
            if (user != null) {
                sessionManager.saveSession(user.username)
                _currentUsername.value = user.username
                _authState.value = AuthState.LOGGED_IN
                _currentScreen.value = AuraScreen.HOME
                showFeedback("Welcome back, ${user.fullName}!")
            } else {
                _authError.value = msg
            }
        }
    }

    fun register(name: String, u: String, e: String, p: String) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val cleanUsername = u.trim().lowercase()
            val newUser = UserEntity(
                username = cleanUsername,
                fullName = name.trim(),
                email = e.trim().lowercase(),
                password = p,
                avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500",
                followerCount = 0,
                followingCount = 0,
                postCount = 0
            )
            val (success, msg) = repository.registerUser(newUser)
            _authLoading.value = false
            if (success) {
                sessionManager.saveSession(cleanUsername)
                _currentUsername.value = cleanUsername
                _authState.value = AuthState.LOGGED_IN
                _currentScreen.value = AuraScreen.HOME
                showFeedback("Account created! Welcome to Aura, ${newUser.fullName}.")
            } else {
                _authError.value = msg
            }
        }
    }

    fun resetPassword(identifier: String, newPass: String) {
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            val (success, msg) = repository.resetPassword(identifier, newPass)
            _authLoading.value = false
            if (success) {
                showFeedback("Password reset successfully! Please log in with your new password.")
                _authState.value = AuthState.LOGIN
            } else {
                _authError.value = msg
            }
        }
    }

    fun logout() {
        sessionManager.clearSession()
        _currentUsername.value = ""
        _authState.value = AuthState.LOGIN
        showFeedback("Logged out successfully")
    }
}

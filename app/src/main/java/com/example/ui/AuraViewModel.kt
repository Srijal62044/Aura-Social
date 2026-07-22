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
import com.example.data.local.StoryEntity
import com.example.data.local.StoryHighlightEntity
import com.example.data.local.UserEntity
import com.example.data.repository.AuraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

class AuraViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: AuraRepository

    init {
        val dao = AppDatabase.getDatabase(application).auraDao()
        repository = AuraRepository(dao)
        viewModelScope.launch {
            repository.checkAndSeedInitialData()
        }
    }

    // AUTH STATE
    private val _authState = MutableStateFlow(AuthState.LOGGED_IN)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUsername = MutableStateFlow("my_username")
    val currentUsername: StateFlow<String> = _currentUsername.asStateFlow()

    val currentUser: StateFlow<UserEntity?> = repository.getUserByUsername("my_username")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserEntity(
            username = "my_username",
            fullName = "Aura Creator",
            email = "creator@aura.app",
            bio = "Exploring art, tech & stories on Aura 🌟",
            website = "https://aura.app/my_username",
            avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500",
            followerCount = 1250,
            followingCount = 420,
            postCount = 12
        ))

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

    val allNotifications: StateFlow<List<NotificationEntity>> = repository.getAllNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        _selectedUsername.value = username
        viewModelScope.launch {
            _selectedUserProfile.value = repository.getUserDirect(username)
            _currentScreen.value = AuraScreen.USER_PROFILE
        }
    }

    val selectedUserPosts: StateFlow<List<PostEntity>> = combine(
        repository.getAllPosts(),
        _selectedUsername
    ) { posts, username ->
        if (username.isNullOrEmpty()) emptyList()
        else posts.filter { it.userId == username }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedUserReels: StateFlow<List<ReelEntity>> = combine(
        repository.getAllReels(),
        _selectedUsername
    ) { reels, username ->
        if (username.isNullOrEmpty()) emptyList()
        else reels.filter { it.username == username }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentUserPosts: StateFlow<List<PostEntity>> = repository.getPostsByUsername("my_username")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentUserReels: StateFlow<List<ReelEntity>> = repository.getReelsByUsername("my_username")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            repository.addComment(
                CommentEntity(
                    postId = post.id,
                    username = "my_username",
                    userAvatar = currentUser.value?.avatarUrl ?: "",
                    text = text.trim()
                )
            )
            repository.updatePost(post.copy(commentCount = post.commentCount + 1))
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
    private val _selectedConversationId = MutableStateFlow<String?>("elena_design")
    val selectedConversationId: StateFlow<String?> = _selectedConversationId.asStateFlow()

    val activeMessages: StateFlow<List<MessageEntity>> = combine(
        repository.getAllPosts(),
        _selectedConversationId
    ) { _, convId ->
        if (convId.isNullOrEmpty()) emptyList()
        else repository.getMessagesForConversation(convId).stateIn(viewModelScope).value
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openChat(conversationId: String) {
        _selectedConversationId.value = conversationId
        _currentScreen.value = AuraScreen.CHAT_DETAIL
    }

    fun sendMessage(text: String, mediaUrl: String = "", type: String = "text") {
        val convId = _selectedConversationId.value ?: return
        if (text.isBlank() && mediaUrl.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(
                MessageEntity(
                    conversationId = convId,
                    senderUsername = "my_username",
                    senderAvatar = currentUser.value?.avatarUrl ?: "",
                    text = text,
                    mediaUrl = mediaUrl,
                    type = type,
                    isMine = true
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
        val peer = _selectedConversationId.value ?: "Elena"
        _callState.value = CallState(
            isActive = true,
            isVideo = isVideo,
            peerUsername = peer,
            peerAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500"
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
        viewModelScope.launch { repository.toggleLikePost(post) }
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
            showFeedback("Post deleted")
        }
    }

    fun createPost(caption: String, location: String, mediaUrl: String, commentsDisabled: Boolean) {
        viewModelScope.launch {
            val user = currentUser.value
            val newPost = PostEntity(
                userId = "my_username",
                username = "my_username",
                userAvatar = user?.avatarUrl ?: "",
                isVerified = user?.isVerified ?: false,
                location = location,
                caption = caption,
                mediaUrlsJson = mediaUrl,
                commentsDisabled = commentsDisabled
            )
            repository.createPost(newPost)
            if (user != null) {
                repository.updateUser(user.copy(postCount = user.postCount + 1))
            }
            showFeedback("Post published!")
            _currentScreen.value = AuraScreen.HOME
        }
    }

    // STORY ACTIONS
    fun createStory(caption: String, mediaUrl: String, isCloseFriends: Boolean) {
        viewModelScope.launch {
            val user = currentUser.value
            repository.createStory(
                StoryEntity(
                    username = "my_username",
                    userAvatar = user?.avatarUrl ?: "",
                    isVerified = user?.isVerified ?: false,
                    mediaUrl = mediaUrl,
                    caption = caption,
                    isCloseFriends = isCloseFriends
                )
            )
            showFeedback("Story added!")
            _currentScreen.value = AuraScreen.HOME
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
            val user = currentUser.value
            repository.createReel(
                ReelEntity(
                    username = "my_username",
                    userAvatar = user?.avatarUrl ?: "",
                    isVerified = user?.isVerified ?: false,
                    videoUrl = videoUrl.ifBlank { "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4" },
                    thumbnailUrl = thumbnailUrl,
                    caption = caption
                )
            )
            showFeedback("Reel uploaded!")
            _currentScreen.value = AuraScreen.REELS
        }
    }

    // FOLLOW / RELATIONSHIP
    fun toggleFollowUser(username: String) {
        viewModelScope.launch {
            repository.toggleFollowUser(username, "my_username")
            val updated = repository.getUserDirect(username)
            if (updated != null && _selectedUsername.value == username) {
                _selectedUserProfile.value = updated
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
            repository.submitReport(
                ReportEntity(
                    reporterUsername = "my_username",
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
    fun navigateTo(screen: AuraScreen) {
        _currentScreen.value = screen
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

    fun login(u: String, p: String) {
        _authState.value = AuthState.LOGGED_IN
        showFeedback("Welcome back to Aura!")
    }

    fun register(name: String, u: String, e: String, p: String) {
        viewModelScope.launch {
            val newUser = UserEntity(
                username = u.ifBlank { "new_user" },
                fullName = name.ifBlank { "New Member" },
                email = e,
                avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500"
            )
            repository.insertUser(newUser)
            _currentUsername.value = newUser.username
            _authState.value = AuthState.LOGGED_IN
            showFeedback("Account created! Welcome to Aura.")
        }
    }

    fun logout() {
        _authState.value = AuthState.LOGIN
        showFeedback("Logged out")
    }
}

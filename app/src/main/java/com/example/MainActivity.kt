package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AuraScreen
import com.example.ui.AuraViewModel
import com.example.ui.AuthState
import com.example.ui.components.AuraBottomNavigation
import com.example.ui.components.AuraTopBar
import com.example.ui.components.CallOverlayModal
import com.example.ui.components.CommentBottomSheet
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.CreateScreen
import com.example.ui.screens.DirectMessagesScreen
import com.example.ui.screens.EditProfileScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReelsScreen
import com.example.ui.screens.SettingsPrivacyScreen
import com.example.ui.screens.StoryViewerScreen
import com.example.ui.theme.AuraPink
import com.example.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AuraViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.updateOnlineStatus(true)
    }

    override fun onPause() {
        super.onPause()
        viewModel.updateOnlineStatus(false)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val data = intent?.data
        if (data != null && data.scheme == "aurasocial" && data.host == "login-callback") {
            android.util.Log.d("MainActivity", "Deep link received: $data")
            
            // Supabase sends access_token, etc., in the fragment (#) or as query parameters. Let's parse both.
            val fragment = data.fragment
            val params = mutableMapOf<String, String>()
            if (!fragment.isNullOrBlank()) {
                fragment.split("&").forEach { pair ->
                    val parts = pair.split("=", limit = 2)
                    if (parts.size == 2) {
                        params[parts[0]] = parts[1]
                    }
                }
            }
            
            val accessToken = data.getQueryParameter("access_token") ?: params["access_token"]
            if (!accessToken.isNullOrBlank()) {
                android.util.Log.d("MainActivity", "Extracted access token successfully from deep link!")
                viewModel.loginWithGoogleToken(accessToken)
            } else {
                android.util.Log.e("MainActivity", "No access token found in redirect URL: $data")
                viewModel.showFeedback("Google Sign-In failed: Access token missing.")
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        try {
            val imageLoader = coil.ImageLoader.Builder(this)
                .okHttpClient {
                    okhttp3.OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            val request = chain.request().newBuilder()
                                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                                .build()
                            chain.proceed(request)
                        }
                        .build()
                }
                .crossfade(true)
                .build()
            coil.Coil.setImageLoader(imageLoader)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
            val authState by viewModel.authState.collectAsStateWithLifecycle()
            val authError by viewModel.authError.collectAsStateWithLifecycle()
            val authLoading by viewModel.authLoading.collectAsStateWithLifecycle()
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
            val currentUsername by viewModel.currentUsername.collectAsStateWithLifecycle()
            val feedback by viewModel.userFeedback.collectAsStateWithLifecycle()

            val allPosts by viewModel.allPosts.collectAsStateWithLifecycle()
            val savedPosts by viewModel.savedPosts.collectAsStateWithLifecycle()
            val allStories by viewModel.allStories.collectAsStateWithLifecycle()
            val allReels by viewModel.allReels.collectAsStateWithLifecycle()
            val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
            val notifications by viewModel.allNotifications.collectAsStateWithLifecycle()
            val reports by viewModel.allReports.collectAsStateWithLifecycle()

            val selectedUserProfile by viewModel.selectedUserProfile.collectAsStateWithLifecycle()
            val selectedUserPosts by viewModel.selectedUserPosts.collectAsStateWithLifecycle()
            val currentUserPosts by viewModel.currentUserPosts.collectAsStateWithLifecycle()
            val currentUserReels by viewModel.currentUserReels.collectAsStateWithLifecycle()

            val isFollowLoading by viewModel.isFollowLoading.collectAsStateWithLifecycle()
            val followersList by viewModel.followersList.collectAsStateWithLifecycle()
            val followingList by viewModel.followingList.collectAsStateWithLifecycle()
            val isLoadingFollowList by viewModel.isLoadingFollowList.collectAsStateWithLifecycle()
            val activeFollowListType by viewModel.activeFollowListType.collectAsStateWithLifecycle()

            val activePostForComments by viewModel.activePostForComments.collectAsStateWithLifecycle()
            val commentsForActivePost by viewModel.commentsForActivePost.collectAsStateWithLifecycle()

            val selectedConversationId by viewModel.selectedConversationId.collectAsStateWithLifecycle()
            val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
            val unreadCountsPerConversation by viewModel.unreadCountsPerConversation.collectAsStateWithLifecycle()
            val totalUnreadMessagesCount by viewModel.totalUnreadMessagesCount.collectAsStateWithLifecycle()
            val isPeerTyping by viewModel.isPeerTyping.collectAsStateWithLifecycle()
            val typingPeerUsername by viewModel.typingPeerUsername.collectAsStateWithLifecycle()
            val callState by viewModel.callState.collectAsStateWithLifecycle()

            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
            val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

            val snackbarHostState = remember { SnackbarHostState() }
            val sheetState = rememberModalBottomSheetState()

            LaunchedEffect(feedback) {
                feedback?.let { msg ->
                    snackbarHostState.showSnackbar(msg)
                    viewModel.clearFeedback()
                }
            }

            AuraTheme(darkTheme = isDarkTheme) {
                if (authState != AuthState.LOGGED_IN) {
                    AuthScreen(
                        authState = authState,
                        errorMessage = authError,
                        isLoading = authLoading,
                        onLogin = { u, p -> viewModel.login(u, p) },
                        onRegister = { n, u, e, p -> viewModel.register(n, u, e, p) },
                        onResetPassword = { u, p -> viewModel.resetPassword(u, p) },
                        onSwitchState = { state -> viewModel.switchAuthState(state) },
                        onGoogleSignIn = {
                            val oauthUrl = viewModel.getGoogleOAuthUrl()
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(oauthUrl))
                                this@MainActivity.startActivity(intent)
                            } catch (e: Exception) {
                                viewModel.showFeedback("Could not open browser for Google sign-in: ${e.localizedMessage}")
                            }
                        }
                    )
                } else {
                    val showTopAndBottomBars = currentScreen in listOf(
                        AuraScreen.HOME,
                        AuraScreen.EXPLORE,
                        AuraScreen.CREATE,
                        AuraScreen.REELS,
                        AuraScreen.PROFILE,
                        AuraScreen.NOTIFICATIONS
                    )

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = if (showTopAndBottomBars) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
                        topBar = {
                            if (showTopAndBottomBars && currentScreen != AuraScreen.REELS) {
                                AuraTopBar(
                                    currentScreen = currentScreen,
                                    unreadNotificationsCount = notifications.count { !it.isRead },
                                    unreadMessagesCount = totalUnreadMessagesCount,
                                    isAdmin = currentUser?.isAdmin ?: true,
                                    onNavigate = { viewModel.navigateTo(it) },
                                    onOpenAdmin = { viewModel.navigateTo(AuraScreen.ADMIN_DASHBOARD) },
                                    onOpenSaved = { viewModel.navigateTo(AuraScreen.SAVED_POSTS) }
                                )
                            }
                        },
                        bottomBar = {
                            if (showTopAndBottomBars) {
                                AuraBottomNavigation(
                                    currentScreen = currentScreen,
                                    currentLanguage = currentLanguage,
                                    onNavigate = { viewModel.navigateTo(it) }
                                )
                            }
                        },
                        snackbarHost = { SnackbarHost(snackbarHostState) }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(if (showTopAndBottomBars) innerPadding else PaddingValues(0.dp))
                        ) {
                            Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                                when (screen) {
                                    AuraScreen.HOME -> HomeScreen(
                                        currentUser = currentUser,
                                        stories = allStories,
                                        posts = allPosts,
                                        onAddStoryClick = { viewModel.navigateTo(AuraScreen.CREATE) },
                                        onStoryClick = { username -> viewModel.openStoryViewer(username) },
                                        onUserClick = { username -> viewModel.selectUserProfile(username) },
                                        onLikeClick = { post -> viewModel.toggleLikePost(post) },
                                        onCommentClick = { post -> viewModel.openCommentsForPost(post) },
                                        onSaveClick = { post -> viewModel.toggleSavePost(post) },
                                        onArchiveClick = { post -> viewModel.toggleArchivePost(post) },
                                        onDeleteClick = { id -> viewModel.deletePost(id) },
                                        onReportClick = { post ->
                                            viewModel.submitReport("post", "${post.id}", "Inappropriate Content")
                                        }
                                    )

                                    AuraScreen.EXPLORE -> ExploreScreen(
                                        searchQuery = searchQuery,
                                        searchHistory = searchHistory,
                                        allUsers = searchResults,
                                        allPosts = allPosts,
                                        currentUser = currentUser,
                                        onQueryChange = { viewModel.updateSearchQuery(it) },
                                        onSearchSubmit = { viewModel.performSearch(it) },
                                        onClearHistory = { viewModel.clearSearchHistory() },
                                        onUserClick = { username -> viewModel.selectUserProfile(username) },
                                        onPostClick = { post -> viewModel.openCommentsForPost(post) },
                                        onRefreshUsers = { viewModel.refreshSearchUsers() }
                                    )

                                    AuraScreen.CREATE -> CreateScreen(
                                        onCreatePost = { caption, loc, url, disabled ->
                                            viewModel.createPost(caption, loc, url, disabled)
                                        },
                                        onCreateStory = { caption, url, isClose ->
                                            viewModel.createStory(caption, url, isClose)
                                        },
                                        onCreateReel = { caption, vUrl, tUrl ->
                                            viewModel.createReel(caption, vUrl, tUrl)
                                        }
                                    )

                                    AuraScreen.REELS -> ReelsScreen(
                                        reels = allReels,
                                        onUserClick = { username -> viewModel.selectUserProfile(username) },
                                        onLikeReel = { reel -> viewModel.toggleLikeReel(reel) },
                                        onCommentClick = { reel -> viewModel.showFeedback("Comments for Reel") },
                                        onSaveReel = { reel -> viewModel.showFeedback("Reel Saved!") }
                                    )

                                    AuraScreen.PROFILE -> ProfileScreen(
                                        user = currentUser,
                                        isSelf = true,
                                        isFollowLoading = isFollowLoading,
                                        followersList = followersList,
                                        followingList = followingList,
                                        isLoadingFollowList = isLoadingFollowList,
                                        activeListType = activeFollowListType,
                                        onFollowersClick = { currentUser?.let { u -> viewModel.openFollowersList(u.id) } },
                                        onFollowingClick = { currentUser?.let { u -> viewModel.openFollowingList(u.id) } },
                                        onCloseFollowList = { viewModel.closeFollowList() },
                                        onUserClick = { u -> viewModel.selectUserProfile(u.username) },
                                        posts = currentUserPosts,
                                        reels = currentUserReels,
                                        savedPosts = savedPosts,
                                        onEditProfileClick = { viewModel.navigateTo(AuraScreen.EDIT_PROFILE) },
                                        onSettingsClick = { viewModel.navigateTo(AuraScreen.SETTINGS) },
                                        onFollowClick = {},
                                        onMessageClick = {},
                                        onBlockClick = {},
                                        onRestrictClick = {},
                                        onReportClick = {},
                                        onPostClick = { post -> viewModel.openCommentsForPost(post) }
                                    )

                                    AuraScreen.USER_PROFILE -> ProfileScreen(
                                        user = selectedUserProfile,
                                        isSelf = false,
                                        isFollowLoading = isFollowLoading,
                                        followersList = followersList,
                                        followingList = followingList,
                                        isLoadingFollowList = isLoadingFollowList,
                                        activeListType = activeFollowListType,
                                        onFollowersClick = { selectedUserProfile?.let { u -> viewModel.openFollowersList(u.id) } },
                                        onFollowingClick = { selectedUserProfile?.let { u -> viewModel.openFollowingList(u.id) } },
                                        onCloseFollowList = { viewModel.closeFollowList() },
                                        onUserClick = { u -> viewModel.selectUserProfile(u.username) },
                                        posts = selectedUserPosts,
                                        reels = emptyList(),
                                        savedPosts = emptyList(),
                                        onEditProfileClick = {},
                                        onSettingsClick = {},
                                        onFollowClick = {
                                            selectedUserProfile?.let { u -> viewModel.toggleFollowUser(u.username) }
                                        },
                                        onMessageClick = {
                                            selectedUserProfile?.let { u -> viewModel.openChat(u.username) }
                                        },
                                        onBlockClick = {
                                            selectedUserProfile?.let { u -> viewModel.toggleBlockUser(u.username) }
                                        },
                                        onRestrictClick = {
                                            selectedUserProfile?.let { u -> viewModel.toggleRestrictUser(u.username) }
                                        },
                                        onReportClick = {
                                            selectedUserProfile?.let { u ->
                                                viewModel.submitReport("user", u.username, "Spam Profile")
                                            }
                                        },
                                        onPostClick = { post -> viewModel.openCommentsForPost(post) }
                                    )

                                    AuraScreen.EDIT_PROFILE -> EditProfileScreen(
                                        user = currentUser,
                                        onBackClick = { viewModel.navigateTo(AuraScreen.PROFILE) },
                                        onSaveProfile = { name, bio, site, url, isPriv ->
                                            viewModel.updateProfile(name, bio, site, url, isPriv)
                                        }
                                    )

                                    AuraScreen.STORY_VIEWER -> StoryViewerScreen(
                                        stories = allStories,
                                        currentUsername = currentUsername,
                                        onClose = { viewModel.navigateTo(AuraScreen.HOME) },
                                        onDeleteStory = { id -> viewModel.deleteStory(id) },
                                        onReplyStory = { text ->
                                            viewModel.sendMessage(text, "", "text")
                                            viewModel.showFeedback("Reply sent to story owner!")
                                        }
                                    )

                                    AuraScreen.DIRECT_MESSAGES -> DirectMessagesScreen(
                                        users = allUsers.filter { it.followStatus == "following" && it.username != currentUsername },
                                        unreadCounts = unreadCountsPerConversation,
                                        onBackClick = { viewModel.navigateTo(AuraScreen.HOME) },
                                        onOpenChat = { username -> viewModel.openChat(username) }
                                    )

                                     AuraScreen.CHAT_DETAIL -> {
                                        val peerUser = allUsers.find { it.username.equals(selectedConversationId, ignoreCase = true) }
                                        val isConversationLoading by viewModel.isConversationLoading.collectAsStateWithLifecycle()
                                        ChatDetailScreen(
                                            conversationId = selectedConversationId ?: "Chat",
                                            peerAvatarUrl = peerUser?.avatarUrl ?: "",
                                            isOnline = peerUser?.isOnline == true,
                                            lastSeen = peerUser?.lastSeen ?: "",
                                            messages = activeMessages,
                                            isPeerTyping = isPeerTyping,
                                            typingPeerUsername = typingPeerUsername,
                                            isConversationLoading = isConversationLoading,
                                            onBackClick = {
                                                viewModel.closeChat()
                                                viewModel.navigateTo(AuraScreen.DIRECT_MESSAGES)
                                            },
                                            onStartCall = { isVideo -> viewModel.startCall(isVideo) },
                                            onSendMessage = { text, media, type, onResult ->
                                                viewModel.sendMessage(text, media, type, onResult)
                                            },
                                            onTyping = { viewModel.onUserTyping() },
                                            onLoadOlderMessages = { viewModel.loadOlderMessages() },
                                            onDeleteMessage = { id -> viewModel.deleteMessage(id) }
                                        )
                                    }

                                    AuraScreen.NOTIFICATIONS -> NotificationsScreen(
                                        notifications = notifications,
                                        onAcceptFollowRequest = { username ->
                                            viewModel.acceptFollowRequest(username)
                                        },
                                        onUserClick = { username -> viewModel.selectUserProfile(username) }
                                    )

                                    AuraScreen.SAVED_POSTS -> ProfileScreen(
                                        user = currentUser,
                                        isSelf = true,
                                        posts = savedPosts,
                                        reels = emptyList(),
                                        savedPosts = savedPosts,
                                        onEditProfileClick = { viewModel.navigateTo(AuraScreen.EDIT_PROFILE) },
                                        onSettingsClick = { viewModel.navigateTo(AuraScreen.SETTINGS) },
                                        onFollowClick = {},
                                        onMessageClick = {},
                                        onBlockClick = {},
                                        onRestrictClick = {},
                                        onReportClick = {},
                                        onPostClick = { post -> viewModel.openCommentsForPost(post) }
                                    )

                                    AuraScreen.SETTINGS -> SettingsPrivacyScreen(
                                        user = currentUser,
                                        isDarkTheme = isDarkTheme,
                                        isAdmin = currentUser?.isAdmin ?: true,
                                        currentLanguage = currentLanguage,
                                        onBackClick = { viewModel.navigateTo(AuraScreen.PROFILE) },
                                        onToggleTheme = { viewModel.toggleTheme() },
                                        onOpenAdminDashboard = { viewModel.navigateTo(AuraScreen.ADMIN_DASHBOARD) },
                                        onLanguageChange = { viewModel.setLanguage(it) },
                                        onLogout = { viewModel.logout() },
                                        onShowFeedback = { msg -> viewModel.showFeedback(msg) }
                                    )

                                    AuraScreen.ADMIN_DASHBOARD -> AdminDashboardScreen(
                                        users = allUsers,
                                        reports = reports,
                                        onBackClick = { viewModel.navigateTo(AuraScreen.SETTINGS) },
                                        onToggleVerify = { username -> viewModel.toggleVerifyUserAdmin(username) },
                                        onUpdateReportStatus = { r, status -> viewModel.updateReportStatus(r, status) }
                                    )

                                    else -> HomeScreen(
                                        currentUser = currentUser,
                                        stories = allStories,
                                        posts = allPosts,
                                        onAddStoryClick = { viewModel.navigateTo(AuraScreen.CREATE) },
                                        onStoryClick = { username -> viewModel.openStoryViewer(username) },
                                        onUserClick = { username -> viewModel.selectUserProfile(username) },
                                        onLikeClick = { post -> viewModel.toggleLikePost(post) },
                                        onCommentClick = { post -> viewModel.openCommentsForPost(post) },
                                        onSaveClick = { post -> viewModel.toggleSavePost(post) },
                                        onArchiveClick = { post -> viewModel.toggleArchivePost(post) },
                                        onDeleteClick = { id -> viewModel.deletePost(id) },
                                        onReportClick = { post ->
                                            viewModel.submitReport("post", "${post.id}", "Inappropriate Content")
                                        }
                                    )
                                }
                            }

                            // Active Comments Modal Sheet
                            if (activePostForComments != null) {
                                CommentBottomSheet(
                                    sheetState = sheetState,
                                    comments = commentsForActivePost,
                                    currentUserAvatar = currentUser?.avatarUrl ?: "",
                                    currentUsername = currentUsername,
                                    onDismiss = { viewModel.closeComments() },
                                    onAddComment = { text -> viewModel.addComment(text) },
                                    onDeleteComment = { comment -> viewModel.deleteComment(comment) }
                                )
                            }

                            // Active Audio/Video Call Overlay
                            CallOverlayModal(
                                callState = callState,
                                liveKitManager = viewModel.liveKitManager,
                                onAcceptCall = { viewModel.acceptCall(this@MainActivity) },
                                onDeclineCall = { viewModel.declineCall() },
                                onCancelCall = { viewModel.cancelCall() },
                                onEndCall = { viewModel.endCall() },
                                onToggleMute = { viewModel.toggleMute() },
                                onToggleCamera = { viewModel.toggleCamera() },
                                onSwitchCamera = { viewModel.switchCamera() },
                                onToggleSpeaker = { viewModel.toggleSpeaker(this@MainActivity) }
                            )
                        }
                    }
                }
            }
        }
    }
}

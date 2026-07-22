package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val authState by viewModel.authState.collectAsStateWithLifecycle()
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

            val activePostForComments by viewModel.activePostForComments.collectAsStateWithLifecycle()
            val commentsForActivePost by viewModel.commentsForActivePost.collectAsStateWithLifecycle()

            val selectedConversationId by viewModel.selectedConversationId.collectAsStateWithLifecycle()
            val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
            val callState by viewModel.callState.collectAsStateWithLifecycle()

            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

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
                        onLogin = { u, p -> viewModel.login(u, p) },
                        onRegister = { n, u, e, p -> viewModel.register(n, u, e, p) },
                        onSwitchState = { newState ->
                            viewModel.login("my_username", "")
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
                        topBar = {
                            if (showTopAndBottomBars && currentScreen != AuraScreen.REELS) {
                                AuraTopBar(
                                    currentScreen = currentScreen,
                                    unreadNotificationsCount = notifications.count { !it.isRead },
                                    unreadMessagesCount = 1,
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
                                    onNavigate = { viewModel.navigateTo(it) }
                                )
                            }
                        },
                        snackbarHost = { SnackbarHost(snackbarHostState) }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
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
                                        allUsers = allUsers,
                                        allPosts = allPosts,
                                        onQueryChange = { viewModel.updateSearchQuery(it) },
                                        onSearchSubmit = { viewModel.performSearch(it) },
                                        onClearHistory = { viewModel.clearSearchHistory() },
                                        onUserClick = { username -> viewModel.selectUserProfile(username) },
                                        onPostClick = { post -> viewModel.openCommentsForPost(post) }
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
                                        users = allUsers.filter { it.username != currentUsername },
                                        onBackClick = { viewModel.navigateTo(AuraScreen.HOME) },
                                        onOpenChat = { username -> viewModel.openChat(username) }
                                    )

                                    AuraScreen.CHAT_DETAIL -> ChatDetailScreen(
                                        conversationId = selectedConversationId ?: "Chat",
                                        messages = activeMessages,
                                        onBackClick = { viewModel.navigateTo(AuraScreen.DIRECT_MESSAGES) },
                                        onStartCall = { isVideo -> viewModel.startCall(isVideo) },
                                        onSendMessage = { text, media, type ->
                                            viewModel.sendMessage(text, media, type)
                                        },
                                        onDeleteMessage = { id -> viewModel.deleteMessage(id) }
                                    )

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
                                        isDarkTheme = isDarkTheme,
                                        isAdmin = currentUser?.isAdmin ?: true,
                                        onBackClick = { viewModel.navigateTo(AuraScreen.PROFILE) },
                                        onToggleTheme = { viewModel.toggleTheme() },
                                        onOpenAdminDashboard = { viewModel.navigateTo(AuraScreen.ADMIN_DASHBOARD) },
                                        onLogout = { viewModel.logout() }
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
                                onEndCall = { viewModel.endCall() },
                                onToggleMute = { viewModel.toggleMute() },
                                onToggleCamera = { viewModel.toggleCamera() }
                            )
                        }
                    }
                }
            }
        }
    }
}

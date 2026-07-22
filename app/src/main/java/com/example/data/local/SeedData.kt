package com.example.data.local

import com.example.R

object SeedData {
    val initialUsers = listOf(
        UserEntity(
            username = "aura",
            fullName = "Aura Official",
            email = "official@aura.app",
            bio = "Official account for Aura Social. Next-generation social experience. ✨",
            website = "https://aura.app",
            avatarUrl = "android.resource://com.aistudio.aurasocial.pxqzv/${R.drawable.img_app_icon_1784694935122}",
            isPrivate = false,
            isVerified = true,
            isAdmin = true,
            followerCount = 1420000,
            followingCount = 42,
            postCount = 128
        ),
        UserEntity(
            username = "alex_tech",
            fullName = "Alex Chen",
            email = "alex@tech.io",
            bio = "AI Engineer & Mobile Creator 🚀 | Building the future of social tech",
            website = "https://alexchen.dev",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500",
            isPrivate = false,
            isVerified = true,
            isAdmin = false,
            followerCount = 89400,
            followingCount = 312,
            postCount = 84,
            followStatus = "following"
        ),
        UserEntity(
            username = "elena_design",
            fullName = "Elena Rostova",
            email = "elena@design.co",
            bio = "Product Designer & Visual Artist 🎨 | Tokyo / SF",
            website = "https://elenarostova.com",
            avatarUrl = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500",
            isPrivate = false,
            isVerified = true,
            isAdmin = false,
            followerCount = 124000,
            followingCount = 450,
            postCount = 210,
            followStatus = "following"
        ),
        UserEntity(
            username = "sam_vlog",
            fullName = "Sam Rivera",
            email = "sam@vlog.com",
            bio = "Traveler & Filmmaker 📸 | Exploring hidden gems worldwide 🌍",
            website = "https://youtube.com/samrivera",
            avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500",
            isPrivate = false,
            isVerified = false,
            isAdmin = false,
            followerCount = 34500,
            followingCount = 612,
            postCount = 95,
            followStatus = "following"
        ),
        UserEntity(
            username = "studio_art",
            fullName = "Lumina Studio",
            email = "hello@lumina.art",
            bio = "Creative direction & digital photography studio 🌆",
            website = "https://lumina.art",
            avatarUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=500",
            isPrivate = true,
            isVerified = true,
            isAdmin = false,
            followerCount = 52100,
            followingCount = 89,
            postCount = 67,
            followStatus = "none"
        )
    )

    val initialPosts = listOf(
        PostEntity(
            id = 101,
            userId = "elena_design",
            username = "elena_design",
            userAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500",
            isVerified = true,
            location = "Tokyo, Japan",
            timestamp = "2h ago",
            caption = "Early morning light pouring into the architectural design studio in Shibuya. Perfection in simplicity. 🌿✨",
            hashtags = "#Tokyo #Design #Architecture #Aesthetics #Aura",
            mediaUrlsJson = "android.resource://com.aistudio.aurasocial.pxqzv/${R.drawable.img_explore_cover_1784694970743},https://images.unsplash.com/photo-1492691527719-9d1e07e534b4?w=800",
            likeCount = 3420,
            commentCount = 184,
            isLiked = true,
            isSaved = true
        ),
        PostEntity(
            id = 102,
            userId = "alex_tech",
            username = "alex_tech",
            userAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500",
            isVerified = true,
            location = "Silicon Valley, CA",
            timestamp = "5h ago",
            caption = "Just shipped our next-gen Compose engine on Aura! Native rendering at 120 FPS feels so incredibly smooth. What feature should we build next? 🔥",
            hashtags = "#MobileDev #Kotlin #Compose #Tech #BuildInPublic",
            mediaUrlsJson = "android.resource://com.aistudio.aurasocial.pxqzv/${R.drawable.img_story_bg_1784694958122}",
            likeCount = 8912,
            commentCount = 420,
            isLiked = false,
            isSaved = false
        ),
        PostEntity(
            id = 103,
            userId = "sam_vlog",
            username = "sam_vlog",
            userAvatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500",
            isVerified = false,
            location = "Swiss Alps",
            timestamp = "1d ago",
            caption = "Lost in the clouds 🏔️ Swiss peaks at dawn hit different when the fog clears.",
            hashtags = "#Travel #Switzerland #Mountains #Photography",
            mediaUrlsJson = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=800",
            likeCount = 12450,
            commentCount = 310,
            isLiked = true,
            isSaved = false
        )
    )

    val initialStories = listOf(
        StoryEntity(
            id = 201,
            username = "aura",
            userAvatar = "android.resource://com.aistudio.aurasocial.pxqzv/${R.drawable.img_app_icon_1784694935122}",
            isVerified = true,
            mediaUrl = "android.resource://com.aistudio.aurasocial.pxqzv/${R.drawable.img_story_bg_1784694958122}",
            caption = "Welcome to Aura Social! Share your story with the world ✨",
            timestamp = "30m ago",
            isCloseFriends = false,
            isViewed = false
        ),
        StoryEntity(
            id = 202,
            username = "elena_design",
            userAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500",
            isVerified = true,
            mediaUrl = "android.resource://com.aistudio.aurasocial.pxqzv/${R.drawable.img_explore_cover_1784694970743}",
            caption = "New design system WIP 🎨",
            timestamp = "2h ago",
            isCloseFriends = true,
            isViewed = false
        ),
        StoryEntity(
            id = 203,
            username = "alex_tech",
            userAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500",
            isVerified = true,
            mediaUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800",
            caption = "Late night coding session 💻",
            timestamp = "4h ago",
            isCloseFriends = false,
            isViewed = true
        )
    )

    val initialReels = listOf(
        ReelEntity(
            id = 301,
            username = "sam_vlog",
            userAvatar = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=500",
            isVerified = false,
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            thumbnailUrl = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=800",
            caption = "3 secret spots in Tokyo you MUST visit in 2026! 🇯🇵✨ Save for your next trip.",
            hashtags = "#Tokyo #TravelReels #Japan #Wanderlust",
            audioTitle = "sam_vlog • Original Audio - Tokyo Ambient Beat",
            likeCount = 48900,
            commentCount = 1280,
            shareCount = 3400,
            isLiked = true,
            isSaved = true,
            isFollowing = true
        ),
        ReelEntity(
            id = 302,
            username = "elena_design",
            userAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500",
            isVerified = true,
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            thumbnailUrl = "android.resource://com.aistudio.aurasocial.pxqzv/${R.drawable.img_explore_cover_1784694970743}",
            caption = "How I design UI components in 60 seconds 🎨 Minimalist layout tutorial.",
            hashtags = "#UIDesign #UX #Figma #Aesthetics",
            audioTitle = "elena_design • Lo-Fi Chill Beats #04",
            likeCount = 29400,
            commentCount = 820,
            shareCount = 1900,
            isLiked = false,
            isSaved = false,
            isFollowing = true
        ),
        ReelEntity(
            id = 303,
            username = "alex_tech",
            userAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500",
            isVerified = true,
            videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            thumbnailUrl = "android.resource://com.aistudio.aurasocial.pxqzv/${R.drawable.img_story_bg_1784694958122}",
            caption = "Building AI Studio apps in real-time! 🚀 Check out this UI animation.",
            hashtags = "#AndroidDev #JetpackCompose #AI",
            audioTitle = "alex_tech • Synthwave Cyberpunk",
            likeCount = 18200,
            commentCount = 540,
            shareCount = 980,
            isLiked = false,
            isSaved = false,
            isFollowing = true
        )
    )

    val initialMessages = listOf(
        MessageEntity(
            id = 401,
            conversationId = "elena_design",
            senderUsername = "elena_design",
            senderAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500",
            text = "Hey! Did you check out the new design updates for Aura?",
            timestamp = "10:30 AM",
            isRead = true,
            isMine = false
        ),
        MessageEntity(
            id = 402,
            conversationId = "elena_design",
            senderUsername = "my_username",
            senderAvatar = "",
            text = "Yes! The gradient transitions look so sleek! 😍",
            timestamp = "10:32 AM",
            isRead = true,
            isMine = true
        ),
        MessageEntity(
            id = 403,
            conversationId = "elena_design",
            senderUsername = "elena_design",
            senderAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500",
            text = "Let's catch up over a video call later today! 📹",
            timestamp = "10:35 AM",
            isRead = false,
            isMine = false
        )
    )

    val initialNotifications = listOf(
        NotificationEntity(
            id = 501,
            actorUsername = "elena_design",
            actorAvatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=500",
            type = "like",
            targetId = 101,
            text = "liked your photo post.",
            timestamp = "15m ago",
            isRead = false
        ),
        NotificationEntity(
            id = 502,
            actorUsername = "alex_tech",
            actorAvatar = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500",
            type = "comment",
            targetId = 101,
            text = "commented: 'Awesome UI design system!'",
            timestamp = "1h ago",
            isRead = false
        ),
        NotificationEntity(
            id = 503,
            actorUsername = "studio_art",
            actorAvatar = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=500",
            type = "follow_request",
            targetId = 0,
            text = "requested to follow you.",
            timestamp = "2h ago",
            isRead = false
        )
    )

    val initialCollections = listOf(
        CollectionEntity(id = 1, name = "Favorites", coverUrl = "android.resource://com.aistudio.aurasocial.pxqzv/${R.drawable.img_explore_cover_1784694970743}"),
        CollectionEntity(id = 2, name = "Inspiration & Design", coverUrl = "android.resource://com.aistudio.aurasocial.pxqzv/${R.drawable.img_story_bg_1784694958122}")
    )
}

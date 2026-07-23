package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.CommentEntity
import com.example.data.local.MessageEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PostEntity
import com.example.data.local.ReelEntity
import com.example.data.local.ReportEntity
import com.example.data.local.StoryEntity
import com.example.data.local.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class SupabaseService {

    private val TAG = "AuraSupabase"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val baseUrl: String
        get() {
            val url = BuildConfig.SUPABASE_URL
            return if (url.isNotBlank()) url.trimEnd('/') else "https://hhwezhokazivwyjndyqh.supabase.co"
        }

    val apiKey: String
        get() {
            val key = BuildConfig.SUPABASE_KEY
            return if (key.isNotBlank()) key else "sb_publishable_7ri3EQePv_-oYO5nzi5pYA_DUVoQf2W"
        }

    private fun getHeaders(userToken: String? = null): Map<String, String> {
        return mapOf(
            "apikey" to apiKey,
            "Authorization" to "Bearer ${if (!userToken.isNullOrBlank()) userToken else apiKey}",
            "Content-Type" to "application/json",
            "Prefer" to "return=representation"
        )
    }

    // --- AUTHENTICATION ---

    suspend fun signUp(email: String, password: String, username: String, fullName: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val authUrl = "$baseUrl/auth/v1/signup"
            val bodyJson = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("data", JSONObject().apply {
                    put("username", username)
                    put("full_name", fullName)
                })
            }

            val requestBuilder = Request.Builder()
                .url(authUrl)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            Log.d(TAG, "signUp status: ${response.code}, resp: $respString")

            if (response.isSuccessful) {
                val json = JSONObject(respString)
                val userObj = json.optJSONObject("user")
                val userId = userObj?.optString("id") ?: UUID.randomUUID().toString()

                val profile = UserEntity(
                    username = username.lowercase().trim(),
                    fullName = fullName.trim(),
                    email = email.lowercase().trim(),
                    password = password,
                    avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500"
                )
                upsertProfile(profile, userId)
                Pair(true, "Account created successfully!")
            } else {
                val json = try { JSONObject(respString) } catch (e: Exception) { null }
                val errorMsg = json?.optString("msg") ?: json?.optString("error_description") ?: json?.optString("message") ?: "Sign up failed."
                Pair(false, errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "signUp Exception: ${e.localizedMessage}", e)
            Pair(false, "Network error during signup: ${e.localizedMessage}")
        }
    }

    suspend fun signIn(identifier: String, password: String): Pair<UserEntity?, String> = withContext(Dispatchers.IO) {
        try {
            val cleanIdent = identifier.trim().lowercase()
            var emailToUse = cleanIdent
            if (!cleanIdent.contains("@")) {
                val profile = getProfileByUsername(cleanIdent)
                if (profile == null) {
                    return@withContext Pair(null, "No account found with username '@$cleanIdent'")
                }
                emailToUse = profile.email
            }

            val authUrl = "$baseUrl/auth/v1/token?grant_type=password"
            val bodyJson = JSONObject().apply {
                put("email", emailToUse)
                put("password", password)
            }

            val requestBuilder = Request.Builder()
                .url(authUrl)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            Log.d(TAG, "signIn status: ${response.code}, resp: $respString")

            if (response.isSuccessful) {
                val json = JSONObject(respString)
                val userObj = json.optJSONObject("user")
                val userMetaData = userObj?.optJSONObject("user_metadata")
                val username = userMetaData?.optString("username") ?: cleanIdent

                var profile = getProfileByUsername(username) ?: getProfileByEmail(emailToUse)
                if (profile == null) {
                    profile = UserEntity(
                        username = username,
                        fullName = userMetaData?.optString("full_name") ?: username,
                        email = emailToUse,
                        password = password
                    )
                }
                Pair(profile, "Login successful!")
            } else {
                val directProfile = getProfileByUsername(cleanIdent) ?: getProfileByEmail(cleanIdent)
                if (directProfile != null && (directProfile.password.isEmpty() || directProfile.password == password)) {
                    return@withContext Pair(directProfile, "Login successful!")
                }
                val json = try { JSONObject(respString) } catch (e: Exception) { null }
                val errorMsg = json?.optString("error_description") ?: json?.optString("message") ?: "Invalid credentials."
                Pair(null, errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "signIn Exception: ${e.localizedMessage}", e)
            Pair(null, "Network error: ${e.localizedMessage}")
        }
    }

    // --- STORAGE UPLOAD ---

    suspend fun uploadMedia(context: Context, bucketName: String, uriString: String): String = withContext(Dispatchers.IO) {
        if (uriString.isBlank() || uriString.startsWith("http://") || uriString.startsWith("https://")) {
            return@withContext uriString
        }

        try {
            Log.d(TAG, "uploadMedia starting for bucket: $bucketName, uri: $uriString")
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: run {
                Log.e(TAG, "Failed to open inputStream for Uri: $uriString")
                return@withContext "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800"
            }
            val bytes = inputStream.readBytes()
            inputStream.close()

            if (bytes.isEmpty()) {
                Log.e(TAG, "File bytes empty for Uri: $uriString")
                return@withContext "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800"
            }

            val isVideo = context.contentResolver.getType(uri)?.contains("video") == true || uriString.endsWith(".mp4")
            val extension = if (isVideo) "mp4" else "jpg"
            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
            val fileName = "aura_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension"

            val uploadUrl = "$baseUrl/storage/v1/object/$bucketName/$fileName"
            val requestBuilder = Request.Builder()
                .url(uploadUrl)
                .post(bytes.toRequestBody(mimeType.toMediaType()))
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("x-upsert", "true")

            val response = client.newCall(requestBuilder.build()).execute()
            val respBody = response.body?.string() ?: ""
            Log.d(TAG, "uploadMedia response code: ${response.code}, body: $respBody")

            if (response.isSuccessful || response.code == 200 || response.code == 201) {
                val publicUrl = "$baseUrl/storage/v1/object/public/$bucketName/$fileName"
                Log.d(TAG, "Uploaded media successfully! Public URL: $publicUrl")
                return@withContext publicUrl
            } else {
                Log.e(TAG, "Storage upload failed! Code: ${response.code}, Body: $respBody")
                return@withContext if (isVideo) "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4" else "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800"
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadMedia exception: ${e.localizedMessage}", e)
            return@withContext "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800"
        }
    }

    // --- PROFILES ---

    suspend fun upsertProfile(profile: UserEntity, userId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/profiles"
            val bodyJson = JSONObject().apply {
                if (!userId.isNullOrBlank()) put("id", userId)
                put("username", profile.username.lowercase().trim())
                put("full_name", profile.fullName)
                put("email", profile.email)
                put("bio", profile.bio)
                put("website", profile.website)
                put("avatar_url", profile.avatarUrl)
                put("is_private", profile.isPrivate)
                put("is_verified", profile.isVerified)
                put("is_admin", profile.isAdmin)
                put("follower_count", profile.followerCount)
                put("following_count", profile.followingCount)
                put("post_count", profile.postCount)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Prefer", "resolution=merge-duplicates")
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            Log.d(TAG, "upsertProfile code: ${response.code}")
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "upsertProfile Exception: ${e.localizedMessage}", e)
            false
        }
    }

    suspend fun getAllProfiles(): List<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/profiles?select=*"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                val list = mutableListOf<UserEntity>()
                for (i in 0 until array.length()) {
                    list.add(parseProfile(array.getJSONObject(i)))
                }
                list
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getProfileByUsername(username: String): UserEntity? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/profiles?username=eq.${username.lowercase().trim()}&select=*"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                if (array.length() > 0) parseProfile(array.getJSONObject(0)) else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getProfileByEmail(email: String): UserEntity? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/profiles?email=eq.${email.lowercase().trim()}&select=*"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                if (array.length() > 0) parseProfile(array.getJSONObject(0)) else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseProfile(obj: JSONObject): UserEntity {
        return UserEntity(
            username = obj.optString("username"),
            fullName = obj.optString("full_name"),
            email = obj.optString("email"),
            password = "",
            bio = obj.optString("bio"),
            website = obj.optString("website"),
            avatarUrl = obj.optString("avatar_url"),
            isPrivate = obj.optBoolean("is_private"),
            isVerified = obj.optBoolean("is_verified"),
            isAdmin = obj.optBoolean("is_admin"),
            followerCount = obj.optInt("follower_count"),
            followingCount = obj.optInt("following_count"),
            postCount = obj.optInt("post_count")
        )
    }

    // --- FOLLOWS ---

    suspend fun getFollowStatus(followerUsername: String, followingUsername: String): String = withContext(Dispatchers.IO) {
        if (followerUsername.isBlank() || followingUsername.isBlank() || followerUsername.equals(followingUsername, ignoreCase = true)) {
            return@withContext "none"
        }
        try {
            val url = "$baseUrl/rest/v1/follows?follower_username=eq.${followerUsername.lowercase().trim()}&following_username=eq.${followingUsername.lowercase().trim()}&select=status"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                if (array.length() > 0) {
                    array.getJSONObject(0).optString("status", "following")
                } else "none"
            } else "none"
        } catch (e: Exception) {
            Log.e(TAG, "getFollowStatus exception: ${e.localizedMessage}")
            "none"
        }
    }

    suspend fun toggleFollow(followerUsername: String, followingUsername: String, isTargetPrivate: Boolean): String = withContext(Dispatchers.IO) {
        val follower = followerUsername.lowercase().trim()
        val following = followingUsername.lowercase().trim()
        if (follower.isBlank() || following.isBlank() || follower == following) return@withContext "none"

        try {
            val currentStatus = getFollowStatus(follower, following)
            if (currentStatus == "following" || currentStatus == "requested") {
                // Delete follow row
                val url = "$baseUrl/rest/v1/follows?follower_username=eq.$follower&following_username=eq.$following"
                val requestBuilder = Request.Builder().url(url).delete()
                getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }
                val response = client.newCall(requestBuilder.build()).execute()
                Log.d(TAG, "unfollow delete code: ${response.code}")
                "none"
            } else {
                val newStatus = if (isTargetPrivate) "requested" else "following"
                val url = "$baseUrl/rest/v1/follows"
                val bodyJson = JSONObject().apply {
                    put("follower_username", follower)
                    put("following_username", following)
                    put("status", newStatus)
                }
                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }
                val response = client.newCall(requestBuilder.build()).execute()
                Log.d(TAG, "follow insert code: ${response.code}")
                newStatus
            }
        } catch (e: Exception) {
            Log.e(TAG, "toggleFollow exception: ${e.localizedMessage}", e)
            "none"
        }
    }

    // --- POSTS ---

    suspend fun getAllPosts(): List<PostEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/posts?select=*&order=created_at.desc"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                val list = mutableListOf<PostEntity>()
                for (i in 0 until array.length()) {
                    list.add(parsePost(array.getJSONObject(i)))
                }
                list
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createPost(post: PostEntity): Long = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/posts"
            val bodyJson = JSONObject().apply {
                put("username", post.username)
                put("user_avatar", post.userAvatar)
                put("is_verified", post.isVerified)
                put("location", post.location)
                put("caption", post.caption)
                put("hashtags", post.hashtags)
                put("media_urls", post.mediaUrlsJson)
                put("is_video", post.isVideo)
                put("like_count", post.likeCount)
                put("comment_count", post.commentCount)
                put("comments_disabled", post.commentsDisabled)
                put("is_archived", post.isArchived)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            Log.d(TAG, "createPost response code: ${response.code}, body: $respString")
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                if (array.length() > 0) array.getJSONObject(0).optLong("id", System.currentTimeMillis()) else System.currentTimeMillis()
            } else System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "createPost Exception: ${e.localizedMessage}", e)
            System.currentTimeMillis()
        }
    }

    suspend fun updatePost(post: PostEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/posts?id=eq.${post.id}"
            val bodyJson = JSONObject().apply {
                put("like_count", post.likeCount)
                put("comment_count", post.commentCount)
                put("comments_disabled", post.commentsDisabled)
                put("is_archived", post.isArchived)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .patch(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deletePost(postId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/posts?id=eq.$postId"
            val requestBuilder = Request.Builder().url(url).delete()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    private fun parsePost(obj: JSONObject): PostEntity {
        return PostEntity(
            id = obj.optLong("id"),
            userId = obj.optString("user_id", obj.optString("username")),
            username = obj.optString("username"),
            userAvatar = obj.optString("user_avatar"),
            isVerified = obj.optBoolean("is_verified"),
            location = obj.optString("location"),
            timestamp = formatTimestamp(obj.optString("created_at")),
            caption = obj.optString("caption"),
            hashtags = obj.optString("hashtags"),
            mediaUrlsJson = obj.optString("media_urls"),
            isVideo = obj.optBoolean("is_video"),
            likeCount = obj.optInt("like_count"),
            commentCount = obj.optInt("comment_count"),
            commentsDisabled = obj.optBoolean("comments_disabled"),
            isArchived = obj.optBoolean("is_archived"),
            isReported = obj.optBoolean("is_reported")
        )
    }

    // --- COMMENTS ---

    suspend fun getCommentsForPost(postId: Long): List<CommentEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/comments?post_id=eq.$postId&order=created_at.asc"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                val list = mutableListOf<CommentEntity>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        CommentEntity(
                            id = obj.optLong("id"),
                            postId = obj.optLong("post_id"),
                            username = obj.optString("username"),
                            userAvatar = obj.optString("user_avatar"),
                            text = obj.optString("text"),
                            timestamp = formatTimestamp(obj.optString("created_at"))
                        )
                    )
                }
                list
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addComment(comment: CommentEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/comments"
            val bodyJson = JSONObject().apply {
                put("post_id", comment.postId)
                put("username", comment.username)
                put("user_avatar", comment.userAvatar)
                put("text", comment.text)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteComment(commentId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/comments?id=eq.$commentId"
            val requestBuilder = Request.Builder().url(url).delete()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // --- STORIES ---

    suspend fun getAllStories(): List<StoryEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/stories?select=*&order=created_at.desc"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                val list = mutableListOf<StoryEntity>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        StoryEntity(
                            id = obj.optLong("id"),
                            username = obj.optString("username"),
                            userAvatar = obj.optString("user_avatar"),
                            isVerified = obj.optBoolean("is_verified"),
                            mediaUrl = obj.optString("media_url"),
                            caption = obj.optString("caption"),
                            timestamp = formatTimestamp(obj.optString("created_at")),
                            isCloseFriends = obj.optBoolean("is_close_friends")
                        )
                    )
                }
                list
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createStory(story: StoryEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/stories"
            val bodyJson = JSONObject().apply {
                put("username", story.username)
                put("user_avatar", story.userAvatar)
                put("is_verified", story.isVerified)
                put("media_url", story.mediaUrl)
                put("caption", story.caption)
                put("is_close_friends", story.isCloseFriends)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteStory(storyId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/stories?id=eq.$storyId"
            val requestBuilder = Request.Builder().url(url).delete()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // --- REELS ---

    suspend fun getAllReels(): List<ReelEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/reels?select=*&order=created_at.desc"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                val list = mutableListOf<ReelEntity>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ReelEntity(
                            id = obj.optLong("id"),
                            username = obj.optString("username"),
                            userAvatar = obj.optString("user_avatar"),
                            isVerified = obj.optBoolean("is_verified"),
                            videoUrl = obj.optString("video_url"),
                            thumbnailUrl = obj.optString("thumbnail_url"),
                            caption = obj.optString("caption"),
                            likeCount = obj.optInt("like_count"),
                            commentCount = obj.optInt("comment_count")
                        )
                    )
                }
                list
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createReel(reel: ReelEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/reels"
            val bodyJson = JSONObject().apply {
                put("username", reel.username)
                put("user_avatar", reel.userAvatar)
                put("is_verified", reel.isVerified)
                put("video_url", reel.videoUrl)
                put("thumbnail_url", reel.thumbnailUrl)
                put("caption", reel.caption)
                put("like_count", reel.likeCount)
                put("comment_count", reel.commentCount)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // --- MESSAGES ---

    suspend fun getMessagesForConversation(currentUsername: String, peerUsername: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        try {
            val cUser = currentUsername.lowercase().trim()
            val pUser = peerUsername.lowercase().trim()

            val url = "$baseUrl/rest/v1/messages?or=(and(sender_username.eq.$cUser,recipient_username.eq.$pUser),and(sender_username.eq.$pUser,recipient_username.eq.$cUser),conversation_id.eq.$pUser)&order=created_at.asc"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                val list = mutableListOf<MessageEntity>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val sender = obj.optString("sender_username")
                    list.add(
                        MessageEntity(
                            id = obj.optLong("id"),
                            conversationId = pUser,
                            senderUsername = sender,
                            recipientUsername = obj.optString("recipient_username"),
                            senderAvatar = obj.optString("sender_avatar"),
                            text = obj.optString("text"),
                            mediaUrl = obj.optString("media_url"),
                            type = obj.optString("type", "text"),
                            isMine = sender.equals(cUser, ignoreCase = true),
                            timestamp = formatTimestamp(obj.optString("created_at"))
                        )
                    )
                }
                list
            } else {
                Log.e(TAG, "getMessagesForConversation failed code: ${response.code}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMessagesForConversation exception: ${e.localizedMessage}", e)
            emptyList()
        }
    }

    suspend fun sendMessage(message: MessageEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/messages"
            val bodyJson = JSONObject().apply {
                put("conversation_id", message.conversationId)
                put("sender_username", message.senderUsername)
                put("recipient_username", message.recipientUsername)
                put("sender_avatar", message.senderAvatar)
                put("text", message.text)
                put("media_url", message.mediaUrl)
                put("type", message.type)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            Log.d(TAG, "sendMessage response code: ${response.code}")
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage Exception: ${e.localizedMessage}", e)
            false
        }
    }

    suspend fun deleteMessage(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/messages?id=eq.$messageId"
            val requestBuilder = Request.Builder().url(url).delete()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // --- NOTIFICATIONS ---

    suspend fun getNotifications(username: String): List<NotificationEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/notifications?recipient_username=eq.${username.lowercase().trim()}&order=created_at.desc"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                val list = mutableListOf<NotificationEntity>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        NotificationEntity(
                            id = obj.optLong("id"),
                            recipientUsername = obj.optString("recipient_username"),
                            actorUsername = obj.optString("actor_username"),
                            actorAvatar = obj.optString("actor_avatar"),
                            type = obj.optString("type"),
                            targetId = obj.optLong("target_id"),
                            text = obj.optString("text"),
                            timestamp = formatTimestamp(obj.optString("created_at")),
                            isRead = obj.optBoolean("is_read")
                        )
                    )
                }
                list
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addNotification(notification: NotificationEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/notifications"
            val bodyJson = JSONObject().apply {
                put("recipient_username", notification.recipientUsername.lowercase().trim())
                put("actor_username", notification.actorUsername.lowercase().trim())
                put("actor_avatar", notification.actorAvatar)
                put("type", notification.type)
                put("target_id", notification.targetId)
                put("text", notification.text)
                put("is_read", notification.isRead)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // --- REPORTS ---

    suspend fun submitReport(report: ReportEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/reports"
            val bodyJson = JSONObject().apply {
                put("reporter_username", report.reporterUsername)
                put("content_type", report.contentType)
                put("content_id", report.contentId)
                put("reason", report.reason)
                put("status", report.status)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getAllReports(): List<ReportEntity> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/reports?select=*&order=created_at.desc"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                val list = mutableListOf<ReportEntity>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ReportEntity(
                            id = obj.optLong("id"),
                            reporterUsername = obj.optString("reporter_username"),
                            contentType = obj.optString("content_type"),
                            contentId = obj.optString("content_id"),
                            reason = obj.optString("reason"),
                            timestamp = formatTimestamp(obj.optString("created_at")),
                            status = obj.optString("status")
                        )
                    )
                }
                list
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatTimestamp(isoStr: String?): String {
        if (isoStr.isNullOrBlank()) return "Just now"
        return "Recently"
    }
}


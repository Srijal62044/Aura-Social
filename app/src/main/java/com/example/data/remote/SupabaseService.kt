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

    @Volatile
    var currentAuthUserId: String? = null
    @Volatile
    var currentUserToken: String? = null

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
        val token = if (!userToken.isNullOrBlank()) userToken else if (!currentUserToken.isNullOrBlank()) currentUserToken else apiKey
        return mapOf(
            "apikey" to apiKey,
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json",
            "Prefer" to "return=representation"
        )
    }

    // --- AUTHENTICATION ---

    suspend fun signUp(email: String, password: String, username: String, fullName: String): Pair<UserEntity?, String> = withContext(Dispatchers.IO) {
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
                val token = json.optString("access_token")
                val userObj = json.optJSONObject("user")
                val userId = userObj?.optString("id") ?: ""
                if (!token.isNullOrBlank()) currentUserToken = token
                if (!userId.isNullOrBlank()) currentAuthUserId = userId

                val profileToUpsert = UserEntity(
                    id = userId,
                    username = username.lowercase().trim(),
                    fullName = fullName.trim(),
                    email = email.lowercase().trim(),
                    password = password,
                    avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500"
                )
                upsertProfile(profileToUpsert, userId)

                // Wait for profile trigger and refetch the profile with a short retry mechanism
                val profile = getProfileByUuidWithRetry(userId, maxRetries = 10, delayMs = 1000) ?: profileToUpsert

                Pair(profile, "Account created successfully!")
            } else {
                val json = try { JSONObject(respString) } catch (e: Exception) { null }
                val errorMsg = json?.optString("msg") ?: json?.optString("error_description") ?: json?.optString("message") ?: "Sign up failed."
                Pair(null, errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "signUp Exception: ${e.localizedMessage}", e)
            Pair(null, "Network error during signup: ${e.localizedMessage}")
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
                val token = json.optString("access_token")
                val userObj = json.optJSONObject("user")
                val userId = userObj?.optString("id") ?: ""
                if (!token.isNullOrBlank()) currentUserToken = token
                if (!userId.isNullOrBlank()) currentAuthUserId = userId

                // Query the profile by UUID: Always use profiles.id = auth user.id
                val profile = getProfileByUuidWithRetry(userId, maxRetries = 10, delayMs = 1000)
                if (profile != null) {
                    Pair(profile, "Login successful!")
                } else {
                    Pair(null, "No username found or profile not created in 'profiles' table for UUID: $userId")
                }
            } else {
                val json = try { JSONObject(respString) } catch (e: Exception) { null }
                val errorMsg = json?.optString("error_description") ?: json?.optString("message") ?: "Invalid credentials."
                Pair(null, errorMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "signIn Exception: ${e.localizedMessage}", e)
            Pair(null, "Network error: ${e.localizedMessage}")
        }
    }

    suspend fun signInWithToken(accessToken: String): Pair<UserEntity?, String> = withContext(Dispatchers.IO) {
        try {
            if (accessToken.isBlank()) {
                return@withContext Pair(null, "Access token is empty.")
            }
            currentUserToken = accessToken

            // 1. Get user details from auth.uid() using the token
            val authUrl = "$baseUrl/auth/v1/user"
            val requestBuilder = Request.Builder().url(authUrl).get()
            getHeaders(accessToken).forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            Log.d(TAG, "signInWithToken user status: ${response.code}, resp: $respString")

            if (!response.isSuccessful) {
                val json = try { JSONObject(respString) } catch (e: Exception) { null }
                val errorMsg = json?.optString("error_description") ?: json?.optString("message") ?: "Failed to retrieve authenticated user."
                return@withContext Pair(null, errorMsg)
            }

            val json = JSONObject(respString)
            val userId = json.optString("id")
            if (userId.isNullOrBlank()) {
                return@withContext Pair(null, "User ID not found in Supabase Auth response.")
            }
            currentAuthUserId = userId

            val email = json.optString("email")
            val userMetadata = json.optJSONObject("user_metadata")
            val fullName = userMetadata?.optString("full_name") ?: userMetadata?.optString("name") ?: email.substringBefore("@")

            // Generate clean username
            var username = userMetadata?.optString("username") ?: ""
            if (username.isBlank()) {
                val rawUsername = email.substringBefore("@").lowercase().filter { it.isLetterOrDigit() }
                username = if (rawUsername.length in 3..20) rawUsername else "user" + UUID.randomUUID().toString().take(6)
            }
            username = username.lowercase().trim()

            val avatarUrl = userMetadata?.optString("avatar_url")
                ?: userMetadata?.optString("picture")
                ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500"

            // 2. Query public.profiles using auth.uid()
            val existingProfile = getProfileByUuidWithRetry(userId, maxRetries = 3, delayMs = 500)
            if (existingProfile != null) {
                return@withContext Pair(existingProfile, "Login successful!")
            }

            // 3. If no profile exists, create it automatically
            val profileToCreate = UserEntity(
                id = userId,
                username = username,
                fullName = fullName,
                email = email,
                password = "",
                avatarUrl = avatarUrl
            )

            val upsertSuccess = upsertProfile(profileToCreate, userId)
            if (upsertSuccess) {
                val refetchedProfile = getProfileByUuidWithRetry(userId, maxRetries = 10, delayMs = 1000) ?: profileToCreate
                Pair(refetchedProfile, "Login successful! New profile created.")
            } else {
                Pair(profileToCreate, "Login successful! (Profile creation pending/partial)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "signInWithToken Exception: ${e.localizedMessage}", e)
            Pair(null, "OAuth authentication error: ${e.localizedMessage}")
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
                val targetId = if (!userId.isNullOrBlank()) userId else profile.id
                if (!targetId.isNullOrBlank()) put("id", targetId)
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

    suspend fun getProfileByUuid(uuid: String): UserEntity? = withContext(Dispatchers.IO) {
        if (uuid.isBlank()) return@withContext null
        try {
            val url = "$baseUrl/rest/v1/profiles?id=eq.$uuid&select=*"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                if (array.length() > 0) parseProfile(array.getJSONObject(0)) else null
            } else {
                val errJson = try { JSONObject(respString) } catch (e: Exception) { null }
                val code = errJson?.optString("code") ?: ""
                val message = errJson?.optString("message") ?: ""
                val details = errJson?.optString("details") ?: ""
                val hint = errJson?.optString("hint") ?: ""
                Log.e(TAG, "console.error: code=$code, message=$message, details=$details, hint=$hint")
                System.err.println("console.error: code=$code, message=$message, details=$details, hint=$hint")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getProfileByUuid Exception: ${e.localizedMessage}", e)
            System.err.println("console.error: Exception: ${e.localizedMessage}")
            null
        }
    }

    suspend fun getProfileByUuidWithRetry(uuid: String, maxRetries: Int = 5, delayMs: Long = 1000): UserEntity? = withContext(Dispatchers.IO) {
        if (uuid.isBlank()) return@withContext null
        var lastErrorJson: String? = null
        for (attempt in 1..maxRetries) {
            try {
                val url = "$baseUrl/rest/v1/profiles?id=eq.$uuid&select=*"
                val requestBuilder = Request.Builder().url(url).get()
                getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

                val response = client.newCall(requestBuilder.build()).execute()
                val respString = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val array = JSONArray(respString)
                    if (array.length() > 0) {
                        return@withContext parseProfile(array.getJSONObject(0))
                    }
                } else {
                    lastErrorJson = respString
                    val errJson = try { JSONObject(respString) } catch (e: Exception) { null }
                    val code = errJson?.optString("code") ?: ""
                    val message = errJson?.optString("message") ?: ""
                    val details = errJson?.optString("details") ?: ""
                    val hint = errJson?.optString("hint") ?: ""
                    Log.e(TAG, "console.error (attempt $attempt): code=$code, message=$message, details=$details, hint=$hint")
                    System.err.println("console.error: code=$code, message=$message, details=$details, hint=$hint")
                }
            } catch (e: Exception) {
                lastErrorJson = e.localizedMessage
                Log.e(TAG, "getProfileByUuidWithRetry Exception (attempt $attempt): ${e.localizedMessage}", e)
                System.err.println("console.error: Exception (attempt $attempt): ${e.localizedMessage}")
            }
            if (attempt < maxRetries) {
                kotlinx.coroutines.delay(delayMs)
            }
        }
        null
    }

    suspend fun getCurrentUserAuthId(): String? = withContext(Dispatchers.IO) {
        if (!currentAuthUserId.isNullOrBlank()) return@withContext currentAuthUserId
        val token = currentUserToken
        if (!token.isNullOrBlank()) {
            try {
                val url = "$baseUrl/auth/v1/user"
                val requestBuilder = Request.Builder().url(url).get()
                getHeaders(token).forEach { (k, v) -> requestBuilder.addHeader(k, v) }

                val response = client.newCall(requestBuilder.build()).execute()
                val respString = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(respString)
                    val id = json.optString("id")
                    if (id.isNotBlank()) {
                        currentAuthUserId = id
                        return@withContext id
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "getCurrentUserAuthId error: ${e.localizedMessage}")
            }
        }
        return@withContext null
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
            id = obj.optString("id"),
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
        val follower = followerUsername.lowercase().trim()
        val following = followingUsername.lowercase().trim()
        try {
            val followerProfile = getProfileByUsername(follower) ?: return@withContext "none"
            val followingProfile = getProfileByUsername(following) ?: return@withContext "none"
            val followerId = followerProfile.id
            val followingId = followingProfile.id
            if (followerId.isBlank() || followingId.isBlank()) return@withContext "none"

            // 1. Check follows table
            val followsUrl = "$baseUrl/rest/v1/follows?follower_id=eq.$followerId&following_id=eq.$followingId&select=id"
            val req1 = Request.Builder().url(followsUrl).get()
            getHeaders().forEach { (k, v) -> req1.addHeader(k, v) }
            val resp1 = client.newCall(req1.build()).execute()
            val body1 = resp1.body?.string() ?: ""
            if (resp1.isSuccessful && JSONArray(body1).length() > 0) {
                return@withContext "following"
            }

            // 2. Check follow_requests table
            val reqsUrl = "$baseUrl/rest/v1/follow_requests?requester_id=eq.$followerId&target_id=eq.$followingId&select=id"
            val req2 = Request.Builder().url(reqsUrl).get()
            getHeaders().forEach { (k, v) -> req2.addHeader(k, v) }
            val resp2 = client.newCall(req2.build()).execute()
            val body2 = resp2.body?.string() ?: ""
            if (resp2.isSuccessful && JSONArray(body2).length() > 0) {
                return@withContext "requested"
            }

            "none"
        } catch (e: Exception) {
            Log.e(TAG, "getFollowStatus exception for $follower -> $following: ${e.localizedMessage}", e)
            "none"
        }
    }

    suspend fun toggleFollow(followerUsername: String, followingUsername: String, isTargetPrivate: Boolean): String = withContext(Dispatchers.IO) {
        val follower = followerUsername.lowercase().trim()
        val following = followingUsername.lowercase().trim()
        if (follower.isBlank() || following.isBlank() || follower == following) {
            Log.w(TAG, "toggleFollow ignored: invalid parameters or self-follow attempt ($follower -> $following)")
            return@withContext "none"
        }

        try {
            val followerProfile = getProfileByUsername(follower) ?: return@withContext "none"
            val followingProfile = getProfileByUsername(following) ?: return@withContext "none"
            val followerId = followerProfile.id
            val followingId = followingProfile.id
            if (followerId.isBlank() || followingId.isBlank()) return@withContext "none"

            val currentStatus = getFollowStatus(follower, following)
            if (currentStatus == "following" || currentStatus == "requested") {
                // Delete from follows and follow_requests
                val delFollowsUrl = "$baseUrl/rest/v1/follows?follower_id=eq.$followerId&following_id=eq.$followingId"
                val reqDel1 = Request.Builder().url(delFollowsUrl).delete()
                getHeaders().forEach { (k, v) -> reqDel1.addHeader(k, v) }
                val respDel1 = client.newCall(reqDel1.build()).execute()
                Log.d(TAG, "unfollow follows delete code: ${respDel1.code}")

                val delReqsUrl = "$baseUrl/rest/v1/follow_requests?requester_id=eq.$followerId&target_id=eq.$followingId"
                val reqDel2 = Request.Builder().url(delReqsUrl).delete()
                getHeaders().forEach { (k, v) -> reqDel2.addHeader(k, v) }
                val respDel2 = client.newCall(reqDel2.build()).execute()
                Log.d(TAG, "unfollow follow_requests delete code: ${respDel2.code}")

                "none"
            } else {
                val newStatus = if (isTargetPrivate) "requested" else "following"
                if (isTargetPrivate) {
                    val bodyReq = JSONObject().apply {
                        put("requester_id", followerId)
                        put("target_id", followingId)
                    }
                    val req = Request.Builder().url("$baseUrl/rest/v1/follow_requests").post(bodyReq.toString().toRequestBody("application/json".toMediaType()))
                    getHeaders().forEach { (k, v) -> req.addHeader(k, v) }
                    val resp = client.newCall(req.build()).execute()
                    val respStr = resp.body?.string() ?: ""
                    Log.d(TAG, "insert follow_requests response: ${resp.code}, body: $respStr")
                } else {
                    val bodyFol = JSONObject().apply {
                        put("follower_id", followerId)
                        put("following_id", followingId)
                    }
                    val req = Request.Builder().url("$baseUrl/rest/v1/follows").post(bodyFol.toString().toRequestBody("application/json".toMediaType()))
                    getHeaders().forEach { (k, v) -> req.addHeader(k, v) }
                    val resp = client.newCall(req.build()).execute()
                    val respStr = resp.body?.string() ?: ""
                    Log.d(TAG, "insert follows response: ${resp.code}, body: $respStr")
                }

                newStatus
            }
        } catch (e: Exception) {
            Log.e(TAG, "toggleFollow Exception for $follower -> $following: ${e.localizedMessage}", e)
            "none"
        }
    }

    suspend fun acceptFollowRequest(actorUsername: String, currentUsername: String): Boolean = withContext(Dispatchers.IO) {
        val actor = actorUsername.lowercase().trim()
        val current = currentUsername.lowercase().trim()
        try {
            val actorProfile = getProfileByUsername(actor) ?: return@withContext false
            val currentProfile = getProfileByUsername(current) ?: return@withContext false
            val actorId = actorProfile.id
            val currentId = currentProfile.id
            if (actorId.isBlank() || currentId.isBlank()) return@withContext false

            // Delete from follow_requests
            val delUrl = "$baseUrl/rest/v1/follow_requests?requester_id=eq.$actorId&target_id=eq.$currentId"
            val reqDel = Request.Builder().url(delUrl).delete()
            getHeaders().forEach { (k, v) -> reqDel.addHeader(k, v) }
            client.newCall(reqDel.build()).execute()

            // Insert into follows
            val body = JSONObject().apply {
                put("follower_id", actorId)
                put("following_id", currentId)
            }
            val insUrl = "$baseUrl/rest/v1/follows"
            val reqIns = Request.Builder().url(insUrl).post(body.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> reqIns.addHeader(k, v) }
            val resp = client.newCall(reqIns.build()).execute()
            Log.d(TAG, "acceptFollowRequest follows insert code: ${resp.code}")

            resp.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "acceptFollowRequest exception: ${e.localizedMessage}", e)
            false
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
            if (cUser.isBlank() || pUser.isBlank()) return@withContext emptyList()

            val currentProfile = getProfileByUsername(cUser) ?: return@withContext emptyList()
            val peerProfile = getProfileByUsername(pUser) ?: return@withContext emptyList()
            val currentId = currentProfile.id
            val peerId = peerProfile.id
            if (currentId.isBlank() || peerId.isBlank()) return@withContext emptyList()

            val sorted = listOf(currentId, peerId).sorted()
            val convUuid = try {
                java.util.UUID.nameUUIDFromBytes("conversation_${sorted[0]}_${sorted[1]}".toByteArray()).toString()
            } catch (e: Exception) {
                java.util.UUID.randomUUID().toString()
            }

            val url = "$baseUrl/rest/v1/messages?or=(conversation_uuid.eq.$convUuid,and(sender_id.eq.$currentId,recipient_id.eq.$peerId),and(sender_id.eq.$peerId,recipient_id.eq.$currentId))&order=created_at.asc"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                val list = mutableListOf<MessageEntity>()
                val seenIds = mutableSetOf<Long>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optLong("id")
                    if (id != 0L && seenIds.contains(id)) continue
                    if (id != 0L) seenIds.add(id)

                    val sender = obj.optString("sender_username").ifBlank {
                        val sId = obj.optString("sender_id")
                        if (sId == currentId) cUser else pUser
                    }
                    val readAtStr = obj.optString("read_at").takeIf { it.isNotBlank() && it != "null" }
                    val delAtStr = obj.optString("delivered_at").takeIf { it.isNotBlank() && it != "null" }
                    val rawStatus = obj.optString("status", "")
                    
                    val calculatedStatus = when {
                        !readAtStr.isNullOrBlank() || rawStatus == "read" -> "read"
                        !delAtStr.isNullOrBlank() || rawStatus == "delivered" -> "delivered"
                        rawStatus.isNotBlank() -> rawStatus
                        else -> "sent"
                    }

                    list.add(
                        MessageEntity(
                            id = id,
                            conversationId = pUser,
                            senderUsername = sender,
                            recipientUsername = obj.optString("recipient_username").ifBlank {
                                val rId = obj.optString("recipient_id")
                                if (rId == currentId) cUser else pUser
                            },
                            senderAvatar = obj.optString("sender_avatar").ifBlank {
                                if (sender.equals(cUser, ignoreCase = true)) currentProfile.avatarUrl else peerProfile.avatarUrl
                            },
                            text = obj.optString("text"),
                            mediaUrl = obj.optString("media_url"),
                            type = obj.optString("type", "text"),
                            timestamp = formatTimestamp(obj.optString("created_at")),
                            isRead = calculatedStatus == "read",
                            readAt = readAtStr,
                            deliveredAt = delAtStr,
                            status = calculatedStatus,
                            isMine = sender.equals(cUser, ignoreCase = true)
                        )
                    )
                }
                list
            } else {
                Log.e(TAG, "getMessagesForConversation failed code: ${response.code}, body: $respString")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMessagesForConversation exception: ${e.localizedMessage}", e)
            emptyList()
        }
    }

    suspend fun sendMessage(message: MessageEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val cUser = message.senderUsername.lowercase().trim()
            val pUser = message.recipientUsername.lowercase().trim()
            val currentProfile = getProfileByUsername(cUser)
            val peerProfile = getProfileByUsername(pUser)
            val currentId = currentProfile?.id ?: currentAuthUserId ?: ""
            val peerId = peerProfile?.id ?: ""
            if (currentId.isBlank() || peerId.isBlank()) {
                Log.e(TAG, "sendMessage failed: empty user UUIDs (sender=$currentId, recipient=$peerId)")
                return@withContext false
            }

            val sorted = listOf(currentId, peerId).sorted()
            val convUuid = try {
                java.util.UUID.nameUUIDFromBytes("conversation_${sorted[0]}_${sorted[1]}".toByteArray()).toString()
            } catch (e: Exception) {
                java.util.UUID.randomUUID().toString()
            }

            val url = "$baseUrl/rest/v1/messages"
            val bodyJson = JSONObject().apply {
                put("conversation_uuid", convUuid)
                put("sender_id", currentId)
                put("recipient_id", peerId)
                put("sender_username", cUser)
                put("recipient_username", pUser)
                put("sender_avatar", message.senderAvatar)
                put("text", message.text)
                put("media_url", message.mediaUrl)
                put("type", message.type)
                put("status", "sent")
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respStr = response.body?.string() ?: ""
            Log.d(TAG, "sendMessage response code: ${response.code}, body: $respStr")
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage Exception: ${e.localizedMessage}", e)
            false
        }
    }

    suspend fun markMessagesAsRead(currentUsername: String, peerUsername: String): Boolean = withContext(Dispatchers.IO) {
        val cUser = currentUsername.lowercase().trim()
        val pUser = peerUsername.lowercase().trim()
        if (cUser.isBlank() || pUser.isBlank()) return@withContext false

        try {
            val currentProfile = getProfileByUsername(cUser)
            val peerProfile = getProfileByUsername(pUser)
            val currentId = currentProfile?.id ?: ""
            val peerId = peerProfile?.id ?: ""
            if (currentId.isBlank() || peerId.isBlank()) return@withContext false

            val nowIso = java.time.Instant.now().toString()
            val url = "$baseUrl/rest/v1/messages?recipient_id=eq.$currentId&sender_id=eq.$peerId&read_at=is.null"
            val bodyJson = JSONObject().apply {
                put("read_at", nowIso)
                put("status", "read")
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .patch(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            Log.d(TAG, "markMessagesAsRead response code: ${response.code}")
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "markMessagesAsRead Exception: ${e.localizedMessage}", e)
            false
        }
    }

    suspend fun getUnreadCounts(currentUsername: String): Map<String, Int> = withContext(Dispatchers.IO) {
        val cUser = currentUsername.lowercase().trim()
        if (cUser.isBlank()) return@withContext emptyMap()

        try {
            val currentProfile = getProfileByUsername(cUser)
            val currentId = currentProfile?.id ?: ""
            if (currentId.isBlank()) return@withContext emptyMap()

            val url = "$baseUrl/rest/v1/messages?recipient_id=eq.$currentId&read_at=is.null&select=sender_id,sender_username,id"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                val counts = mutableMapOf<String, Int>()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    var sender = item.optString("sender_username").lowercase().trim()
                    if (sender.isBlank()) {
                        val senderId = item.optString("sender_id")
                        if (senderId.isNotBlank()) {
                            val prof = getProfileByUuid(senderId)
                            if (prof != null) {
                                sender = prof.username.lowercase().trim()
                            }
                        }
                    }
                    if (sender.isNotBlank()) {
                        counts[sender] = (counts[sender] ?: 0) + 1
                    }
                }
                counts
            } else emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "getUnreadCounts Exception: ${e.localizedMessage}")
            emptyMap()
        }
    }

    suspend fun sendTypingStatus(conversationUuid: String, userUuid: String, isTyping: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (conversationUuid.isBlank() || userUuid.isBlank()) return@withContext false

        try {
            val url = "$baseUrl/rest/v1/typing_status"
            val bodyJson = JSONObject().apply {
                put("conversation_id", conversationUuid)
                put("user_id", userUuid)
                put("is_typing", isTyping)
                put("updated_at", java.time.Instant.now().toString())
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Prefer", "resolution=merge-duplicates")
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respStr = response.body?.string() ?: ""
            if (response.code == 404) {
                Log.w(TAG, "sendTypingStatus: typing_status table not found on Supabase (404)")
                return@withContext false
            }
            Log.d(TAG, "sendTypingStatus code: ${response.code}, body: $respStr")
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "sendTypingStatus exception: ${e.localizedMessage}")
            false
        }
    }

    suspend fun getTypingUsers(conversationUuid: String, currentUserUuid: String): List<String> = withContext(Dispatchers.IO) {
        if (conversationUuid.isBlank()) return@withContext emptyList()

        try {
            val url = "$baseUrl/rest/v1/typing_status?conversation_id=eq.$conversationUuid&user_id=neq.$currentUserUuid&is_typing=eq.true&select=user_id,updated_at"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.code == 404) {
                return@withContext emptyList()
            }
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                val activeTypingUserIds = mutableListOf<String>()
                val now = System.currentTimeMillis()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val uId = obj.optString("user_id")
                    val updatedStr = obj.optString("updated_at")
                    val timestamp = try {
                        java.time.Instant.parse(updatedStr).toEpochMilli()
                    } catch (e: Exception) { now }

                    // Automatically clear typing after 2 seconds of inactivity
                    if (now - timestamp < 2000) {
                        activeTypingUserIds.add(uId)
                    }
                }
                activeTypingUserIds
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
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

    // --- CALLS & LIVEKIT SIGNALING ---

    suspend fun createCallRecordEx(
        callerUuid: String,
        receiverUuid: String,
        roomId: String,
        callType: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (callerUuid.isBlank() || receiverUuid.isBlank()) {
            return@withContext Pair(false, "Please sign in again.")
        }
        Log.d(TAG, "Creating call record: caller_id=$callerUuid, receiver_id=$receiverUuid, room_id=$roomId, call_type=$callType")
        try {
            val url = "$baseUrl/rest/v1/calls"
            val bodyJson = JSONObject().apply {
                put("caller_id", callerUuid)
                put("receiver_id", receiverUuid)
                put("room_id", roomId)
                put("room_name", roomId)
                put("call_type", callType)
                put("status", "ringing")
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respBody = response.body?.string() ?: ""
            Log.d(TAG, "createCallRecord status code: ${response.code}")

            if (response.isSuccessful || response.code == 201) {
                Pair(true, "Call initiated successfully")
            } else {
                Log.e(TAG, "createCallRecord error code: ${response.code}, body: $respBody")
                Pair(false, "Supabase Call Error (${response.code}): $respBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "createCallRecord exception: ${e.localizedMessage}", e)
            Pair(false, "Call creation exception: ${e.localizedMessage}")
        }
    }

    suspend fun createCallRecord(call: CallRecord): Boolean {
        val (success, _) = createCallRecordEx(
            callerUuid = call.callerId,
            receiverUuid = call.receiverId,
            roomId = call.roomName.ifBlank { call.id },
            callType = call.callType
        )
        return success
    }

    suspend fun updateCallStatus(callId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/calls?id=eq.$callId"
            val bodyJson = JSONObject().apply {
                put("status", status)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .patch(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            Log.d(TAG, "updateCallStatus status: ${response.code}")
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "updateCallStatus exception: ${e.localizedMessage}", e)
            false
        }
    }

    suspend fun getCallRecord(callId: String): CallRecord? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/rest/v1/calls?id=eq.$callId&select=*"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                if (array.length() > 0) parseCallRecord(array.getJSONObject(0)) else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "getCallRecord exception: ${e.localizedMessage}")
            null
        }
    }

    suspend fun getPendingIncomingCall(receiverId: String): CallRecord? = withContext(Dispatchers.IO) {
        if (receiverId.isBlank()) return@withContext null
        try {
            val url = "$baseUrl/rest/v1/calls?receiver_id=eq.${receiverId.lowercase().trim()}&status=eq.ringing&order=created_at.desc&limit=1"
            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(respString)
                if (array.length() > 0) parseCallRecord(array.getJSONObject(0)) else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchLiveKitToken(roomName: String, identity: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/functions/v1/get-livekit-token"
            val bodyJson = JSONObject().apply {
                put("roomName", roomName)
                put("identity", identity)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(respString)
                json.optString("token").takeIf { it.isNotBlank() }
            } else {
                Log.e(TAG, "fetchLiveKitToken code: ${response.code}, body: $respString")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchLiveKitToken exception: ${e.localizedMessage}")
            null
        }
    }

    private fun parseCallRecord(obj: JSONObject): CallRecord {
        val roomId = obj.optString("room_id").ifBlank { obj.optString("room_name") }
        return CallRecord(
            id = obj.optString("id"),
            roomName = roomId,
            callerId = obj.optString("caller_id"),
            receiverId = obj.optString("receiver_id"),
            callType = obj.optString("call_type", "audio"),
            status = obj.optString("status", "ringing"),
            createdAt = obj.optString("created_at")
        )
    }

    private fun formatTimestamp(isoStr: String?): String {
        if (isoStr.isNullOrBlank()) return "Just now"
        return "Recently"
    }
}

data class CallRecord(
    val id: String = "",
    val roomName: String = "",
    val callerId: String = "",
    val receiverId: String = "",
    val callType: String = "audio",
    val status: String = "ringing",
    val createdAt: String = ""
)


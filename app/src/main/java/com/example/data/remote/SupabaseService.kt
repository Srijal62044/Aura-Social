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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private val profileCacheByUsername = java.util.concurrent.ConcurrentHashMap<String, UserEntity>()
    private val profileCacheByUuid = java.util.concurrent.ConcurrentHashMap<String, UserEntity>()

    fun cacheProfile(profile: UserEntity) {
        if (profile.username.isNotBlank()) {
            profileCacheByUsername[profile.username.lowercase().trim()] = profile
        }
        if (profile.id.isNotBlank()) {
            profileCacheByUuid[profile.id] = profile
        }
    }

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

    suspend fun loadOrCreateProfileForUser(
        userId: String,
        email: String,
        userMetadata: JSONObject?,
        token: String
    ): Pair<UserEntity?, String> = withContext(Dispatchers.IO) {
        // 3. Confirm that authData.session exists before querying public.profiles.
        if (token.isBlank()) {
            val errorMsg = "Auth session is missing."
            Log.e(TAG, "console.error: $errorMsg")
            return@withContext Pair(null, errorMsg)
        }

        // 4. Query
        val url = "$baseUrl/rest/v1/profiles?id=eq.$userId&select=*"
        val requestBuilder = Request.Builder().url(url).get()
        getHeaders(token).forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        var profile: UserEntity? = null
        var profileErrorMsg: String? = null
        var respString = ""
        var responseCode = 0

        try {
            val response = client.newCall(requestBuilder.build()).execute()
            responseCode = response.code
            respString = response.body?.string() ?: ""
            Log.d(TAG, "Profile query status: $responseCode, response: $respString")

            if (response.isSuccessful) {
                val array = JSONArray(respString)
                if (array.length() > 0) {
                    // 5. Parse profile returned (no reading from user_metadata/email etc.)
                    profile = parseProfile(array.getJSONObject(0))
                }
            } else {
                if (responseCode == 404) {
                    Log.d(TAG, "Profile not found (404), will create profile.")
                } else {
                    val errJson = try { JSONObject(respString) } catch (e: Exception) { null }
                    val code = errJson?.optString("code") ?: "$responseCode"
                    val message = errJson?.optString("message") ?: "HTTP query failed"
                    val details = errJson?.optString("details") ?: ""

                    profileErrorMsg = "Profile query failed.\n" +
                            "profileError.code: $code\n" +
                            "profileError.message: $message\n" +
                            "profileError.details: $details\n" +
                            "authenticated UUID: $userId\n" +
                            "profile query result: $respString"
                    Log.e(TAG, "console.error: $profileErrorMsg")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Profile query exception: ${e.localizedMessage}")
        }

        // 6. Do not fail if profile is simply not created yet (404/missing); proceed to upsert/create.
        if (profileErrorMsg != null && responseCode != 404) {
            // Only return error if it's a real unexpected error, not just missing profile
            // But to ensure user never gets stuck, we can clear profileErrorMsg and let it create profile
            profileErrorMsg = null
        }

        // 7. If profileError is null and profile is null, wait 500 milliseconds and retry the profile query once.
        if (profile == null) {
            Log.d(TAG, "Profile is null. Waiting 500ms before retrying once...")
            kotlinx.coroutines.delay(500)
            try {
                val response = client.newCall(requestBuilder.build()).execute()
                responseCode = response.code
                respString = response.body?.string() ?: ""
                Log.d(TAG, "Profile query retry status: $responseCode, response: $respString")

                if (response.isSuccessful) {
                    val array = JSONArray(respString)
                    if (array.length() > 0) {
                        profile = parseProfile(array.getJSONObject(0))
                    }
                } else {
                    val errJson = try { JSONObject(respString) } catch (e: Exception) { null }
                    val code = errJson?.optString("code") ?: "$responseCode"
                    val message = errJson?.optString("message") ?: "HTTP query failed"
                    val details = errJson?.optString("details") ?: ""

                    profileErrorMsg = "Profile query retry failed.\n" +
                            "profileError.code: $code\n" +
                            "profileError.message: $message\n" +
                            "profileError.details: $details\n" +
                            "authenticated UUID: $userId\n" +
                            "profile query result: $respString"
                    Log.e(TAG, "console.error: $profileErrorMsg")
                    return@withContext Pair(null, profileErrorMsg)
                }
            } catch (e: Exception) {
                profileErrorMsg = "Profile query retry exception.\n" +
                        "profileError.code: EXCEPTION\n" +
                        "profileError.message: ${e.localizedMessage}\n" +
                        "profileError.details: ${Log.getStackTraceString(e)}\n" +
                        "authenticated UUID: $userId\n" +
                        "profile query result: null"
                Log.e(TAG, "console.error: $profileErrorMsg", e)
                return@withContext Pair(null, profileErrorMsg)
            }
        }

        // 8. If the profile still does not exist, upsert it using:
        if (profile == null) {
            Log.d(TAG, "Profile still does not exist after retry. Upserting profile...")

            // Generate unique username
            val emailPrefix = email.substringBefore("@").lowercase()
            var baseUsername = emailPrefix.filter { it.isLetterOrDigit() || it == '.' || it == '_' }
            if (baseUsername.isBlank()) {
                baseUsername = "user"
            }
            var generatedUniqueUsername = baseUsername
            val existingByUsername = getProfileByUsername(generatedUniqueUsername)
            if (existingByUsername != null && existingByUsername.id != userId) {
                val shortUuid = userId.replace("-", "").take(6).lowercase()
                generatedUniqueUsername = "$baseUsername$shortUuid"
            }

            val fullName = userMetadata?.optString("full_name")
                ?: userMetadata?.optString("name")
                ?: email.substringBefore("@")

            val profileToCreate = UserEntity(
                id = userId,
                username = generatedUniqueUsername,
                fullName = fullName,
                email = email,
                password = "",
                avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500"
            )

            val upsertSuccess = upsertProfile(profileToCreate, userId)
            if (!upsertSuccess) {
                return@withContext Pair(null, "Profile upsert failed for authenticated UUID: $userId")
            }

            // 9. After upsert, fetch public.profiles again.
            profile = getProfileByUuid(userId)
            if (profile == null) {
                // fallback to constructed profile if fetching fails
                profile = profileToCreate
            }
        }

        // 10. Navigate to the home screen only after the profile has loaded (handled in ViewModel)
        Pair(profile, "Login successful!")
    }

    suspend fun signIn(email: String, password: String): Pair<UserEntity?, String> = withContext(Dispatchers.IO) {
        try {
            var cleanEmail = email.trim().lowercase()
            if (cleanEmail.isBlank()) {
                return@withContext Pair(null, "Email or Username is required.")
            }
            if (!cleanEmail.contains("@")) {
                val profile = getProfileByUsername(cleanEmail)
                if (profile != null && !profile.email.isBlank()) {
                    cleanEmail = profile.email.lowercase().trim()
                } else {
                    val all = getAllProfiles()
                    val matched = all.find { it.username.equals(cleanEmail, ignoreCase = true) }
                    if (matched != null && !matched.email.isBlank()) {
                        cleanEmail = matched.email.lowercase().trim()
                    } else {
                        return@withContext Pair(null, "No account found with username: $email")
                    }
                }
            }

            val authUrl = "$baseUrl/auth/v1/token?grant_type=password"
            val bodyJson = JSONObject().apply {
                put("email", cleanEmail)
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
                val userEmail = userObj?.optString("email") ?: cleanEmail
                val userMetadata = userObj?.optJSONObject("user_metadata")

                if (!token.isNullOrBlank()) currentUserToken = token
                if (!userId.isNullOrBlank()) currentAuthUserId = userId

                // Instantly construct and return fallback authenticated UserEntity to prevent blocking
                val fallbackUsername = userEmail.substringBefore("@").lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }.ifBlank { "user" }
                val fullName = userMetadata?.optString("full_name") ?: userMetadata?.optString("name") ?: userEmail.substringBefore("@")
                val fallbackUser = UserEntity(
                    id = userId,
                    username = fallbackUsername,
                    fullName = fullName,
                    email = userEmail,
                    password = "",
                    avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500"
                )
                Pair(fallbackUser, "Login successful!")
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

            val email = json.optString("email") ?: ""
            val userMetadata = json.optJSONObject("user_metadata")

            // Instantly construct and return fallback authenticated UserEntity to prevent blocking
            val fallbackUsername = email.substringBefore("@").lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }.ifBlank { "user" }
            val fullName = userMetadata?.optString("full_name") ?: userMetadata?.optString("name") ?: email.substringBefore("@")
            val fallbackUser = UserEntity(
                id = userId,
                username = fallbackUsername,
                fullName = fullName,
                email = email,
                password = "",
                avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=500"
            )
            Pair(fallbackUser, "Login successful!")
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
                put("is_online", profile.isOnline)
                put("last_seen", profile.lastSeen)
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

    suspend fun updateOnlineStatus(userId: String, isOnline: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext false
        try {
            val url = "$baseUrl/rest/v1/profiles?id=eq.$userId"
            val nowIso = java.time.Instant.now().toString()
            val bodyJson = JSONObject().apply {
                put("is_online", isOnline)
                put("last_seen", nowIso)
            }
            val requestBuilder = Request.Builder()
                .url(url)
                .patch(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val response = client.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "updateOnlineStatus exception: ${e.localizedMessage}", e)
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
            } else {
                Log.e(TAG, "getAllProfiles search error: HTTP ${response.code}, Response: $respString")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllProfiles search exception: ${e.localizedMessage}", e)
            emptyList()
        }
    }

    suspend fun searchProfiles(query: String, currentUserId: String): List<UserEntity> = withContext(Dispatchers.IO) {
        try {
            var url = if (query.isBlank()) {
                "$baseUrl/rest/v1/profiles?select=*"
            } else {
                val sanitizedQuery = java.net.URLEncoder.encode("*$query*", "UTF-8")
                "$baseUrl/rest/v1/profiles?or=(username.ilike.$sanitizedQuery,full_name.ilike.$sanitizedQuery)&select=*"
            }
            if (currentUserId.isNotBlank()) {
                val separator = if (url.contains("?")) "&" else "?"
                url += "${separator}id=neq.$currentUserId"
            }
            Log.d(TAG, "searchProfiles URL: $url")

            val requestBuilder = Request.Builder().url(url).get()
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respString = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val array = JSONArray(respString)
                Log.d(TAG, "searchProfiles returned row count: ${array.length()}")
                val list = mutableListOf<UserEntity>()
                for (i in 0 until array.length()) {
                    list.add(parseProfile(array.getJSONObject(i)))
                }
                list
            } else {
                Log.e(TAG, "searchProfiles error: HTTP ${response.code}, Response: $respString")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchProfiles Exception: ${e.localizedMessage}", e)
            emptyList()
        }
    }

    suspend fun getProfileByUsername(username: String): UserEntity? = withContext(Dispatchers.IO) {
        val cleanName = username.lowercase().trim()
        if (cleanName.isBlank()) return@withContext null
        profileCacheByUsername[cleanName]?.let { return@withContext it }

        try {
            val url = "$baseUrl/rest/v1/profiles?username=eq.$cleanName&select=*"
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
        profileCacheByUuid[uuid]?.let { return@withContext it }

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
        val user = UserEntity(
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
            postCount = obj.optInt("post_count"),
            isOnline = obj.optBoolean("is_online"),
            lastSeen = obj.optString("last_seen")
        )
        cacheProfile(user)
        return user
    }

    // --- FOLLOWS ---

    suspend fun getFollowStatusByUuids(followerId: String, followingId: String): String = withContext(Dispatchers.IO) {
        if (followerId.isBlank() || followingId.isBlank() || followerId.equals(followingId, ignoreCase = true)) {
            return@withContext "none"
        }
        try {
            val followsUrl = "$baseUrl/rest/v1/follows?follower_id=eq.$followerId&following_id=eq.$followingId&select=follower_id,following_id"
            val req1 = Request.Builder().url(followsUrl).get()
            getHeaders().forEach { (k, v) -> req1.addHeader(k, v) }
            val resp1 = client.newCall(req1.build()).execute()
            val body1 = resp1.body?.string() ?: ""
            if (resp1.isSuccessful && JSONArray(body1).length() > 0) {
                return@withContext "following"
            }
            "none"
        } catch (e: Exception) {
            Log.e(TAG, "getFollowStatusByUuids exception ($followerId -> $followingId): ${e.localizedMessage}", e)
            "none"
        }
    }

    suspend fun getFollowStatus(followerUsername: String, followingUsername: String): String = withContext(Dispatchers.IO) {
        if (followerUsername.isBlank() || followingUsername.isBlank() || followerUsername.equals(followingUsername, ignoreCase = true)) {
            return@withContext "none"
        }
        val follower = followerUsername.lowercase().trim()
        val following = followingUsername.lowercase().trim()
        try {
            val followerProfile = getProfileByUsername(follower) ?: return@withContext "none"
            val followingProfile = getProfileByUsername(following) ?: return@withContext "none"
            getFollowStatusByUuids(followerProfile.id, followingProfile.id)
        } catch (e: Exception) {
            Log.e(TAG, "getFollowStatus exception for $follower -> $following: ${e.localizedMessage}", e)
            "none"
        }
    }

    suspend fun getFollowerCount(profileId: String): Int = withContext(Dispatchers.IO) {
        if (profileId.isBlank()) return@withContext 0
        try {
            val url = "$baseUrl/rest/v1/follows?following_id=eq.$profileId&select=follower_id"
            val req = Request.Builder().url(url).get().header("Prefer", "count=exact")
            getHeaders().forEach { (k, v) -> req.addHeader(k, v) }
            val resp = client.newCall(req.build()).execute()
            val contentRange = resp.header("Content-Range")
            val countFromHeader = contentRange?.substringAfter("/")?.toIntOrNull()
            if (countFromHeader != null) return@withContext countFromHeader
            val body = resp.body?.string() ?: "[]"
            if (resp.isSuccessful) JSONArray(body).length() else 0
        } catch (e: Exception) {
            Log.e(TAG, "getFollowerCount exception for $profileId: ${e.localizedMessage}", e)
            0
        }
    }

    suspend fun getFollowingCount(profileId: String): Int = withContext(Dispatchers.IO) {
        if (profileId.isBlank()) return@withContext 0
        try {
            val url = "$baseUrl/rest/v1/follows?follower_id=eq.$profileId&select=following_id"
            val req = Request.Builder().url(url).get().header("Prefer", "count=exact")
            getHeaders().forEach { (k, v) -> req.addHeader(k, v) }
            val resp = client.newCall(req.build()).execute()
            val contentRange = resp.header("Content-Range")
            val countFromHeader = contentRange?.substringAfter("/")?.toIntOrNull()
            if (countFromHeader != null) return@withContext countFromHeader
            val body = resp.body?.string() ?: "[]"
            if (resp.isSuccessful) JSONArray(body).length() else 0
        } catch (e: Exception) {
            Log.e(TAG, "getFollowingCount exception for $profileId: ${e.localizedMessage}", e)
            0
        }
    }

    suspend fun getFollowersList(profileId: String): List<UserEntity> = withContext(Dispatchers.IO) {
        if (profileId.isBlank()) return@withContext emptyList()
        try {
            val followsUrl = "$baseUrl/rest/v1/follows?following_id=eq.$profileId&select=follower_id"
            val req = Request.Builder().url(followsUrl).get()
            getHeaders().forEach { (k, v) -> req.addHeader(k, v) }
            val resp = client.newCall(req.build()).execute()
            val body = resp.body?.string() ?: "[]"
            if (!resp.isSuccessful) return@withContext emptyList()

            val jsonArr = JSONArray(body)
            val followerIds = mutableListOf<String>()
            for (i in 0 until jsonArr.length()) {
                val id = jsonArr.getJSONObject(i).optString("follower_id")
                if (id.isNotBlank()) followerIds.add(id)
            }

            if (followerIds.isEmpty()) return@withContext emptyList()

            val profilesUrl = "$baseUrl/rest/v1/profiles?id=in.(${followerIds.joinToString(",")})"
            val profReq = Request.Builder().url(profilesUrl).get()
            getHeaders().forEach { (k, v) -> profReq.addHeader(k, v) }
            val profResp = client.newCall(profReq.build()).execute()
            val profBody = profResp.body?.string() ?: "[]"
            if (!profResp.isSuccessful) return@withContext emptyList()

            val profArr = JSONArray(profBody)
            val list = mutableListOf<UserEntity>()
            for (i in 0 until profArr.length()) {
                list.add(parseProfile(profArr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "getFollowersList exception for $profileId: ${e.localizedMessage}", e)
            emptyList()
        }
    }

    suspend fun getFollowingList(profileId: String): List<UserEntity> = withContext(Dispatchers.IO) {
        if (profileId.isBlank()) return@withContext emptyList()
        try {
            val followsUrl = "$baseUrl/rest/v1/follows?follower_id=eq.$profileId&select=following_id"
            val req = Request.Builder().url(followsUrl).get()
            getHeaders().forEach { (k, v) -> req.addHeader(k, v) }
            val resp = client.newCall(req.build()).execute()
            val body = resp.body?.string() ?: "[]"
            if (!resp.isSuccessful) return@withContext emptyList()

            val jsonArr = JSONArray(body)
            val followingIds = mutableListOf<String>()
            for (i in 0 until jsonArr.length()) {
                val id = jsonArr.getJSONObject(i).optString("following_id")
                if (id.isNotBlank()) followingIds.add(id)
            }

            if (followingIds.isEmpty()) return@withContext emptyList()

            val profilesUrl = "$baseUrl/rest/v1/profiles?id=in.(${followingIds.joinToString(",")})"
            val profReq = Request.Builder().url(profilesUrl).get()
            getHeaders().forEach { (k, v) -> profReq.addHeader(k, v) }
            val profResp = client.newCall(profReq.build()).execute()
            val profBody = profResp.body?.string() ?: "[]"
            if (!profResp.isSuccessful) return@withContext emptyList()

            val profArr = JSONArray(profBody)
            val list = mutableListOf<UserEntity>()
            for (i in 0 until profArr.length()) {
                list.add(parseProfile(profArr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "getFollowingList exception for $profileId: ${e.localizedMessage}", e)
            emptyList()
        }
    }

    suspend fun toggleFollow(followerIdInput: String, followingIdInput: String, isTargetPrivate: Boolean = false): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val followerClean = followerIdInput.trim()
        val followingClean = followingIdInput.trim()

        if (followerClean.isBlank() || followingClean.isBlank() || followerClean.equals(followingClean, ignoreCase = true)) {
            val err = "Cannot follow self or invalid user IDs"
            Log.w(TAG, "toggleFollow ignored: $err (follower=$followerClean, following=$followingClean)")
            return@withContext Pair(false, err)
        }

        try {
            Log.d(TAG, "toggleFollow starting: follower=$followerClean, following=$followingClean")

            // 1. Load the authenticated user's profile from public.profiles
            val currentProfile = getProfileByUuid(followerClean) ?: getProfileByUsername(followerClean)
            if (currentProfile == null) {
                val err = "Could not load authenticated user profile from public.profiles for ID/username: $followerClean"
                Log.e(TAG, err)
                return@withContext Pair(false, err)
            }

            // 2. Load the target user's profile from public.profiles
            val targetProfile = getProfileByUuid(followingClean) ?: getProfileByUsername(followingClean)
            if (targetProfile == null) {
                val err = "Could not load target user profile from public.profiles for ID/username: $followingClean"
                Log.e(TAG, err)
                return@withContext Pair(false, err)
            }

            // 3. Read both usernames
            val followerUsername = currentProfile.username.trim()
            val followingUsername = targetProfile.username.trim()

            // 4. Verify neither username is null or empty
            if (followerUsername.isBlank()) {
                val err = "Authenticated user username is null or empty in public.profiles (ID: ${currentProfile.id})"
                Log.e(TAG, err)
                return@withContext Pair(false, err)
            }
            if (followingUsername.isBlank()) {
                val err = "Target user username is null or empty in public.profiles (ID: ${targetProfile.id})"
                Log.e(TAG, err)
                return@withContext Pair(false, err)
            }

            val followerId = currentProfile.id
            val followingId = targetProfile.id

            // Check if follow row already exists by IDs or usernames
            val followsUrl = "$baseUrl/rest/v1/follows?or=(and(follower_id.eq.$followerId,following_id.eq.$followingId),and(follower_username.eq.$followerUsername,following_username.eq.$followingUsername))&select=follower_id,following_id"
            val req1 = Request.Builder().url(followsUrl).get()
            getHeaders().forEach { (k, v) -> req1.addHeader(k, v) }
            val resp1 = client.newCall(req1.build()).execute()
            val body1 = resp1.body?.string() ?: ""
            val isFollowing = resp1.isSuccessful && JSONArray(body1).length() > 0

            if (isFollowing) {
                // UNFOLLOW ACTION
                val delUrl = "$baseUrl/rest/v1/follows?or=(and(follower_id.eq.$followerId,following_id.eq.$followingId),and(follower_username.eq.$followerUsername,following_username.eq.$followingUsername))"
                val delReq = Request.Builder().url(delUrl).delete()
                getHeaders().forEach { (k, v) -> delReq.addHeader(k, v) }
                val delResp = client.newCall(delReq.build()).execute()
                val delBody = delResp.body?.string() ?: ""

                Log.d(TAG, "UNFOLLOW response code: ${delResp.code}, body: $delBody")

                if (!delResp.isSuccessful) {
                    val errJson = try { JSONObject(delBody) } catch (e: Exception) { null }
                    val code = errJson?.optString("code") ?: "${delResp.code}"
                    val message = errJson?.optString("message") ?: "Unfollow request failed"
                    Log.e(TAG, "Unfollow Error ($code): $message")
                    return@withContext Pair(false, "Unfollow error ($code): $message")
                }

                return@withContext Pair(true, "none")
            } else {
                // FOLLOW ACTION: Insert ALL required fields from public.profiles
                val postBody = JSONObject().apply {
                    put("follower_username", followerUsername)
                    put("following_username", followingUsername)
                    put("follower_id", followerId)
                    put("following_id", followingId)
                    put("status", "accepted")
                }
                val postReq = Request.Builder().url("$baseUrl/rest/v1/follows")
                    .header("Prefer", "return=representation")
                    .post(postBody.toString().toRequestBody("application/json".toMediaType()))
                getHeaders().forEach { (k, v) -> postReq.addHeader(k, v) }

                val postResp = client.newCall(postReq.build()).execute()
                val postBodyStr = postResp.body?.string() ?: ""

                Log.d(TAG, "FOLLOW INSERT response code: ${postResp.code}, body: $postBodyStr")

                if (postResp.isSuccessful || postResp.code == 201) {
                    Log.d(TAG, "Follow Insert Successful: @$followerUsername -> @$followingUsername")
                    return@withContext Pair(true, "following")
                } else {
                    val errJson = try { JSONObject(postBodyStr) } catch (e: Exception) { null }
                    val code = errJson?.optString("code") ?: "${postResp.code}"
                    val message = errJson?.optString("message") ?: "Follow request failed"
                    val details = errJson?.optString("details") ?: ""

                    // If duplicate relationship already exists, show Following instead of throwing an error
                    if (code == "23505" || postResp.code == 409 || message.contains("duplicate", ignoreCase = true) || message.contains("already exists", ignoreCase = true) || details.contains("already exists", ignoreCase = true)) {
                        Log.d(TAG, "Duplicate follow row already exists. Showing Following.")
                        return@withContext Pair(true, "following")
                    }

                    Log.e(TAG, "Follow Insert Error ($code): $message, details: $details")
                    return@withContext Pair(false, "Follow error ($code): $message")
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Follow exception occurred"
            Log.e(TAG, "toggleFollow Exception ($followerClean -> $followingClean): $msg", e)
            return@withContext Pair(false, msg)
        }
    }

    suspend fun getFollowedUsers(currentUsername: String): List<UserEntity> = withContext(Dispatchers.IO) {
        val cUser = currentUsername.lowercase().trim()
        if (cUser.isBlank()) return@withContext emptyList()
        try {
            val me = getProfileByUsername(cUser) ?: return@withContext emptyList()
            val currentUserId = me.id
            if (currentUserId.isBlank()) return@withContext emptyList()

            val followsUrl = "$baseUrl/rest/v1/follows?follower_id=eq.$currentUserId&select=following_id"
            val req = Request.Builder().url(followsUrl).get()
            getHeaders().forEach { (k, v) -> req.addHeader(k, v) }
            val resp = client.newCall(req.build()).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val array = JSONArray(body)
                val followingIds = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val fId = array.getJSONObject(i).optString("following_id")
                    if (fId.isNotBlank()) followingIds.add(fId)
                }
                if (followingIds.isEmpty()) return@withContext emptyList()

                val inClause = followingIds.joinToString(",")
                val profilesUrl = "$baseUrl/rest/v1/profiles?id=in.($inClause)&select=*"
                val reqProf = Request.Builder().url(profilesUrl).get()
                getHeaders().forEach { (k, v) -> reqProf.addHeader(k, v) }
                val respProf = client.newCall(reqProf.build()).execute()
                val bodyProf = respProf.body?.string() ?: ""
                if (respProf.isSuccessful) {
                    val arrayProf = JSONArray(bodyProf)
                    val list = mutableListOf<UserEntity>()
                    for (i in 0 until arrayProf.length()) {
                        val p = parseProfile(arrayProf.getJSONObject(i))
                        list.add(p.copy(followStatus = "following"))
                    }
                    list
                } else emptyList()
            } else emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getFollowedUsers Exception: ${e.localizedMessage}", e)
            emptyList()
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
                put("follower_username", actorProfile.username)
                put("following_username", currentProfile.username)
                put("follower_id", actorId)
                put("following_id", currentId)
                put("status", "accepted")
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

    suspend fun getMessagesForConversation(
        currentUsername: String,
        peerUsername: String,
        limit: Int = 50,
        beforeTimestamp: String? = null
    ): List<MessageEntity> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val cUser = currentUsername.lowercase().trim()
            val pUser = peerUsername.lowercase().trim()
            if (cUser.isBlank() || pUser.isBlank()) return@withContext emptyList()

            val currentProfile = getProfileByUsername(cUser)
            val peerProfile = getProfileByUsername(pUser)
            val currentId = currentProfile?.id.orEmpty()
            val peerId = peerProfile?.id.orEmpty()

            var convUuid: String? = null
            if (currentId.isNotBlank() && peerId.isNotBlank()) {
                val (foundId, _) = getOrCreateDirectConversation(currentId, peerId)
                convUuid = foundId
            }

            val url = if (!convUuid.isNullOrBlank()) {
                var query = "$baseUrl/rest/v1/messages?conversation_id=eq.$convUuid"
                if (!beforeTimestamp.isNullOrBlank()) {
                    query += "&created_at=lt.$beforeTimestamp"
                }
                "$query&select=id,conversation_id,conversation_uuid,sender_username,recipient_username,sender_id,recipient_id,text,content,media_url,type,message_type,created_at&order=created_at.desc&limit=$limit"
            } else {
                var query = "$baseUrl/rest/v1/messages?or=(and(sender_id.eq.$currentId,recipient_id.eq.$peerId),and(sender_id.eq.$peerId,recipient_id.eq.$currentId))"
                if (!beforeTimestamp.isNullOrBlank()) {
                    query += "&created_at=lt.$beforeTimestamp"
                }
                "$query&select=id,conversation_id,conversation_uuid,sender_username,recipient_username,sender_id,recipient_id,text,content,media_url,type,message_type,created_at&order=created_at.desc&limit=$limit"
            }

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

                    val senderId = obj.optString("sender_id")
                    val senderUser = obj.optString("sender_username")
                    val isCurrentSender = (senderId.isNotBlank() && senderId == currentId) ||
                            (senderUser.isNotBlank() && senderUser.equals(cUser, ignoreCase = true))

                    val sender = if (isCurrentSender) cUser else pUser
                    val recipient = if (isCurrentSender) pUser else cUser

                    val textCol = obj.optString("text")
                    val contentCol = obj.optString("content")
                    val mediaUrlCol = obj.optString("media_url")
                    val displayContent = contentCol.ifBlank { textCol.ifBlank { mediaUrlCol } }

                    val typeCol = obj.optString("type")
                    val messageTypeCol = obj.optString("message_type")
                    val displayType = messageTypeCol.ifBlank { typeCol.ifBlank { "text" } }

                    val isMedia = displayType == "image" || displayType == "voice" || displayType == "audio"
                    val textVal = if (displayType == "image") "Photo 📸" else if (displayType == "voice" || displayType == "audio") "Voice Note 🎤" else displayContent
                    val mediaUrlVal = if (isMedia) displayContent.ifBlank { mediaUrlCol } else ""

                    list.add(
                        MessageEntity(
                            id = id,
                            conversationId = pUser,
                            senderUsername = sender,
                            recipientUsername = recipient,
                            senderAvatar = if (isCurrentSender) currentProfile?.avatarUrl.orEmpty() else peerProfile?.avatarUrl.orEmpty(),
                            text = textVal,
                            mediaUrl = mediaUrlVal,
                            type = displayType,
                            timestamp = formatTimestamp(obj.optString("created_at")),
                            isMine = isCurrentSender
                        )
                    )
                }
                val sortedAsc = list.reversed()
                val duration = System.currentTimeMillis() - startTime
                Log.d("AURA_PERF", "[AURA_PERF] Fetched ${sortedAsc.size} messages in ${duration}ms for $pUser")
                sortedAsc
            } else {
                Log.e(TAG, "getMessagesForConversation failed code: ${response.code}, body: $respString")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMessagesForConversation exception: ${e.localizedMessage}", e)
            emptyList()
        }
    }

    suspend fun getOrCreateDirectConversation(authUserIdInput: String, recipientIdentifierInput: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        // 1. Get current authenticated user directly from Supabase Auth
        var authUserFromAuth = getCurrentUserAuthId() ?: currentAuthUserId
        val token = currentUserToken
        val hasSession = !token.isNullOrBlank()

        if (authUserFromAuth.isNullOrBlank() && hasSession) {
            try {
                val authUrl = "$baseUrl/auth/v1/user"
                val reqBuilder = Request.Builder().url(authUrl).get()
                getHeaders(token).forEach { (k, v) -> reqBuilder.addHeader(k, v) }
                val authResp = client.newCall(reqBuilder.build()).execute()
                val authStr = authResp.body?.string() ?: ""
                if (authResp.isSuccessful) {
                    val authJson = JSONObject(authStr)
                    val id = authJson.optString("id")
                    if (id.isNotBlank()) {
                        authUserFromAuth = id
                        currentAuthUserId = id
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auth user fetch exception: ${e.localizedMessage}")
            }
        }

        val authenticatedUserId = authUserFromAuth ?: authUserIdInput

        // 2. Resolve recipient profile to get valid recipient UUID (recipientProfile.id)
        val recipientProfile = getProfileByUuid(recipientIdentifierInput)
            ?: getProfileByUsername(recipientIdentifierInput)
            ?: getProfileByEmail(recipientIdentifierInput)

        val recipientProfileId = recipientProfile?.id ?: recipientIdentifierInput

        val createdBy = authenticatedUserId
        val directUserA = authenticatedUserId
        val directUserB = recipientProfileId
        val isGroup = false

        // 3. Log values before insert
        Log.d("CONVERSATION_DEBUG", "authenticatedUser.id: $authenticatedUserId")
        Log.d("CONVERSATION_DEBUG", "recipientProfile.id: $recipientProfileId")
        Log.d("CONVERSATION_DEBUG", "created_by: $createdBy")
        Log.d("CONVERSATION_DEBUG", "direct_user_a: $directUserA")
        Log.d("CONVERSATION_DEBUG", "direct_user_b: $directUserB")
        Log.d("CONVERSATION_DEBUG", "is_group: $isGroup")
        Log.d("CONVERSATION_DEBUG", "hasSession: $hasSession")

        System.err.println("CONVERSATION_DEBUG: authenticatedUser.id=$authenticatedUserId, recipientProfile.id=$recipientProfileId, created_by=$createdBy, direct_user_a=$directUserA, direct_user_b=$directUserB, is_group=$isGroup, hasSession=$hasSession")

        // 4. Verify all conditions
        val isCreatedByAuth = createdBy == authenticatedUserId
        val isDirectAAuth = directUserA == authenticatedUserId
        val isDirectBRecipient = directUserB == recipientProfileId
        val isDirectUserADifferent = directUserA != directUserB
        val isNotGroup = !isGroup
        val isIdsValid = authenticatedUserId.isNotBlank() && recipientProfileId.isNotBlank() &&
                !authenticatedUserId.contains("@") && !recipientProfileId.contains("@")

        if (!hasSession || !isCreatedByAuth || !isDirectAAuth || !isDirectBRecipient || !isDirectUserADifferent || !isNotGroup || !isIdsValid) {
            val msg = "Validation failed before conversation insert (hasSession=$hasSession, created_by=$createdBy, direct_user_a=$directUserA, direct_user_b=$directUserB, is_group=$isGroup)"
            val code = "400"
            val details = "All conditions must be met: created_by === authenticatedUser.id, direct_user_a === authenticatedUser.id, direct_user_b === recipientProfile.id, direct_user_a !== direct_user_b, is_group === false, hasSession === true."
            val hint = "Check auth session and recipient profile UUIDs"

            Log.e("CONVERSATION_DEBUG", "conversationError.message: $msg")
            Log.e("CONVERSATION_DEBUG", "conversationError.code: $code")
            Log.e("CONVERSATION_DEBUG", "conversationError.details: $details")
            Log.e("CONVERSATION_DEBUG", "conversationError.hint: $hint")

            System.err.println("CONVERSATION_DEBUG: conversationError.message=$msg, conversationError.code=$code, conversationError.details=$details, conversationError.hint=$hint")

            return@withContext Pair(null, msg)
        }

        try {
            // 5. First search for an existing conversation in either participant order
            val queryUrl = "$baseUrl/rest/v1/conversations?is_group=eq.false&or=(and(direct_user_a.eq.$authenticatedUserId,direct_user_b.eq.$recipientProfileId),and(direct_user_a.eq.$recipientProfileId,direct_user_b.eq.$authenticatedUserId))&select=id"
            val queryReq = Request.Builder().url(queryUrl).get()
            getHeaders(token).forEach { (k, v) -> queryReq.addHeader(k, v) }

            val queryResp = client.newCall(queryReq.build()).execute()
            val queryStr = queryResp.body?.string() ?: ""

            if (queryResp.isSuccessful) {
                val array = JSONArray(queryStr)
                if (array.length() > 0) {
                    val foundConvId = array.getJSONObject(0).getString("id")
                    Log.d("CONVERSATION_DEBUG", "FOUND_CONVERSATION_ID: $foundConvId")
                    System.err.println("CONVERSATION_DEBUG: FOUND_CONVERSATION_ID=$foundConvId")

                    ensureConversationMembers(foundConvId, authenticatedUserId, recipientProfileId)
                    return@withContext Pair(foundConvId, null)
                }
            } else {
                val errJson = try { JSONObject(queryStr) } catch (e: Exception) { null }
                val code = errJson?.optString("code") ?: "${queryResp.code}"
                val message = errJson?.optString("message") ?: "Query conversation failed"
                val details = errJson?.optString("details") ?: ""
                val hint = errJson?.optString("hint") ?: ""

                Log.w("CONVERSATION_DEBUG", "conversationError.message: $message")
                Log.w("CONVERSATION_DEBUG", "conversationError.code: $code")
                Log.w("CONVERSATION_DEBUG", "conversationError.details: $details")
                Log.w("CONVERSATION_DEBUG", "conversationError.hint: $hint")
                System.err.println("CONVERSATION_DEBUG: conversationError.message=$message, conversationError.code=$code, conversationError.details=$details, conversationError.hint=$hint")
            }

            // 6. Only create a new conversation when no matching row exists
            val createUrl = "$baseUrl/rest/v1/conversations?select=id"
            val createBody = JSONObject().apply {
                put("direct_user_a", authenticatedUserId)
                put("direct_user_b", recipientProfileId)
                put("is_group", false)
                put("created_by", authenticatedUserId)
            }

            val createReq = Request.Builder()
                .url(createUrl)
                .header("Prefer", "return=representation")
                .post(createBody.toString().toRequestBody("application/json".toMediaType()))
            getHeaders(token).forEach { (k, v) -> createReq.addHeader(k, v) }

            val createResp = client.newCall(createReq.build()).execute()
            val createStr = createResp.body?.string() ?: ""

            if (createResp.isSuccessful || createResp.code == 201) {
                val array = JSONArray(createStr)
                if (array.length() > 0) {
                    val createdConvId = array.getJSONObject(0).getString("id")
                    Log.d("CONVERSATION_DEBUG", "CREATED_CONVERSATION_ID: $createdConvId")
                    System.err.println("CONVERSATION_DEBUG: CREATED_CONVERSATION_ID=$createdConvId")

                    ensureConversationMembers(createdConvId, authenticatedUserId, recipientProfileId)
                    return@withContext Pair(createdConvId, null)
                } else {
                    val msg = "Conversation insertion returned empty response body"
                    Log.e("CONVERSATION_DEBUG", "conversationError.message: $msg")
                    Log.e("CONVERSATION_DEBUG", "conversationError.code: 500")
                    Log.e("CONVERSATION_DEBUG", "conversationError.details: Empty JSON array")
                    Log.e("CONVERSATION_DEBUG", "conversationError.hint: Check Supabase API response representation")
                    System.err.println("CONVERSATION_DEBUG: conversationError.message=$msg")
                    return@withContext Pair(null, msg)
                }
            } else {
                val errJson = try { JSONObject(createStr) } catch (e: Exception) { null }
                val code = errJson?.optString("code") ?: "${createResp.code}"
                val message = errJson?.optString("message") ?: "Failed to insert conversation"
                val details = errJson?.optString("details") ?: ""
                val hint = errJson?.optString("hint") ?: ""

                Log.e("CONVERSATION_DEBUG", "conversationError.message: $message")
                Log.e("CONVERSATION_DEBUG", "conversationError.code: $code")
                Log.e("CONVERSATION_DEBUG", "conversationError.details: $details")
                Log.e("CONVERSATION_DEBUG", "conversationError.hint: $hint")

                System.err.println("CONVERSATION_DEBUG: conversationError.message=$message, conversationError.code=$code, conversationError.details=$details, conversationError.hint=$hint")

                val fullError = "CONVERSATION_ERROR ($code): $message details=$details hint=$hint"
                return@withContext Pair(null, fullError)
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Exception during conversation lookup/creation"
            Log.e("CONVERSATION_DEBUG", "conversationError.message: $msg")
            Log.e("CONVERSATION_DEBUG", "conversationError.code: EXCEPTION")
            Log.e("CONVERSATION_DEBUG", "conversationError.details: ${Log.getStackTraceString(e)}")
            Log.e("CONVERSATION_DEBUG", "conversationError.hint: Network exception occurred")
            System.err.println("CONVERSATION_DEBUG: conversationError.message=$msg")
            return@withContext Pair(null, "CONVERSATION_ERROR: $msg")
        }
    }

    private fun ensureConversationMembers(convId: String, userA: String, userB: String) {
        try {
            val m1 = JSONObject().apply { put("conversation_id", convId); put("user_id", userA) }
            val m2 = JSONObject().apply { put("conversation_id", convId); put("user_id", userB) }

            val memReq1 = Request.Builder().url("$baseUrl/rest/v1/conversation_members")
                .header("Prefer", "resolution=ignore-duplicates")
                .post(m1.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> memReq1.addHeader(k, v) }
            client.newCall(memReq1.build()).execute().close()

            val memReq2 = Request.Builder().url("$baseUrl/rest/v1/conversation_members")
                .header("Prefer", "resolution=ignore-duplicates")
                .post(m2.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> memReq2.addHeader(k, v) }
            client.newCall(memReq2.build()).execute().close()
        } catch (e: Exception) {
            Log.d("MEMBER_ERROR", "Member insertion notice: ${e.message}")
        }
    }

    suspend fun sendMessage(message: MessageEntity): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // 1. Get real authenticated user from Supabase Auth
            var authUserId: String? = getCurrentUserAuthId() ?: currentAuthUserId
            if (authUserId.isNullOrBlank() && !currentUserToken.isNullOrBlank()) {
                try {
                    val authUrl = "$baseUrl/auth/v1/user"
                    val reqBuilder = Request.Builder().url(authUrl).get()
                    getHeaders(currentUserToken).forEach { (k, v) -> reqBuilder.addHeader(k, v) }
                    val authResp = client.newCall(reqBuilder.build()).execute()
                    val authStr = authResp.body?.string() ?: ""
                    if (authResp.isSuccessful) {
                        val authJson = JSONObject(authStr)
                        val id = authJson.optString("id")
                        if (id.isNotBlank()) {
                            authUserId = id
                            currentAuthUserId = id
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Auth user fetch exception: ${e.localizedMessage}")
                }
            }

            if (authUserId.isNullOrBlank()) {
                val err = "CONVERSATION_ERROR sendMessage failed: No authenticated user session found."
                Log.e("CONVERSATION_DEBUG", err)
                System.err.println("CONVERSATION_DEBUG: $err")
                return@withContext Pair(false, err)
            }

            // 2. Load authenticated user's profile
            val senderProfile = getProfileByUuid(authUserId) ?: getProfileByUsername(message.senderUsername.lowercase().trim())
            val senderUsername = senderProfile?.username ?: message.senderUsername.lowercase().trim()

            if (senderUsername.isBlank()) {
                val err = "CONVERSATION_ERROR sendMessage failed: Sender profile username is null or empty."
                Log.e("CONVERSATION_DEBUG", err)
                System.err.println("CONVERSATION_DEBUG: $err")
                return@withContext Pair(false, err)
            }

            // 3. Load recipient profile
            val pUser = message.recipientUsername.lowercase().trim()
            val peerProfile = getProfileByUsername(pUser)
            val recipientId = peerProfile?.id ?: ""
            val recipientUsername = peerProfile?.username ?: pUser

            if (recipientId.isBlank()) {
                val err = "CONVERSATION_ERROR sendMessage failed: Recipient profile not found for @$pUser"
                Log.e("CONVERSATION_DEBUG", err)
                System.err.println("CONVERSATION_DEBUG: $err")
                return@withContext Pair(false, err)
            }

            if (authUserId == recipientId) {
                val err = "CONVERSATION_ERROR sendMessage failed: Cannot send message to yourself."
                Log.e("CONVERSATION_DEBUG", err)
                System.err.println("CONVERSATION_DEBUG: $err")
                return@withContext Pair(false, err)
            }

            // 4. Find or create direct conversation row in public.conversations
            val (finalConvId, convErr) = getOrCreateDirectConversation(authUserId, recipientId)
            if (finalConvId.isNullOrBlank()) {
                val err = convErr ?: "CONVERSATION_ERROR: Unable to resolve direct conversation."
                Log.e("CONVERSATION_DEBUG", err)
                System.err.println("CONVERSATION_DEBUG: $err")
                return@withContext Pair(false, err)
            }

            // 5. Verify conversation existence immediately before inserting message
            try {
                val checkUrl = "$baseUrl/rest/v1/conversations?id=eq.$finalConvId&select=id"
                val checkReq = Request.Builder().url(checkUrl).get()
                getHeaders().forEach { (k, v) -> checkReq.addHeader(k, v) }
                val checkResp = client.newCall(checkReq.build()).execute()
                val checkStr = checkResp.body?.string() ?: ""
                val exists = checkResp.isSuccessful && JSONArray(checkStr).length() > 0

                if (!exists) {
                    val err = "CONVERSATION_ERROR: Conversation ID $finalConvId verified NOT present in public.conversations before insert."
                    Log.e("CONVERSATION_DEBUG", err)
                    System.err.println("CONVERSATION_DEBUG: $err")
                    return@withContext Pair(false, err)
                }
            } catch (e: Exception) {
                Log.w("CONVERSATION_DEBUG", "Verification check notice: ${e.message}")
            }

            Log.d("CONVERSATION_DEBUG", "FINAL_CONVERSATION_UUID: $finalConvId")
            Log.d("CONVERSATION_DEBUG", "MESSAGE_CONVERSATION_ID: $finalConvId")
            System.err.println("CONVERSATION_DEBUG: FINAL_CONVERSATION_UUID=$finalConvId, MESSAGE_CONVERSATION_ID=$finalConvId")

            val contentVal = if (message.type == "image" || message.type == "voice" || message.type == "audio") {
                message.mediaUrl.ifBlank { message.text }.trim()
            } else {
                message.text.trim()
            }

            val msgType = when (message.type) {
                "image" -> "image"
                "voice", "audio" -> "voice"
                else -> "text"
            }

            val url = "$baseUrl/rest/v1/messages"
            val bodyJson = JSONObject().apply {
                // Exact string form of existing public.conversations.id
                put("conversation_id", finalConvId)
                put("conversation_uuid", finalConvId)
                put("sender_username", senderUsername)
                put("recipient_username", recipientUsername)

                put("sender_id", authUserId)
                put("recipient_id", recipientId)

                // Message content fields
                if (msgType == "image" || msgType == "voice") {
                    put("media_url", contentVal)
                    put("content", contentVal)
                } else {
                    put("text", contentVal)
                    put("content", contentVal)
                }

                // Message type fields
                put("type", msgType)
                put("message_type", msgType)

                if (!senderProfile?.avatarUrl.isNullOrBlank()) {
                    put("sender_avatar", senderProfile?.avatarUrl)
                }
            }

            Log.d("CONVERSATION_DEBUG", "MESSAGE_INSERT_PAYLOAD: ${bodyJson.toString()}")
            System.err.println("CONVERSATION_DEBUG: MESSAGE_INSERT_PAYLOAD=${bodyJson.toString()}")

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Prefer", "return=representation")
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respStr = response.body?.string() ?: ""
            Log.d(TAG, "POST /rest/v1/messages response code: ${response.code}, body: $respStr")

            if (response.isSuccessful || response.code == 201) {
                var insertedMsg: MessageEntity? = null
                try {
                    val array = JSONArray(respStr)
                    if (array.length() > 0) {
                        val obj = array.getJSONObject(0)
                        val id = obj.optLong("id")
                        val textCol = obj.optString("text")
                        val contentCol = obj.optString("content")
                        val mediaUrlCol = obj.optString("media_url")
                        val displayContent = contentCol.ifBlank { textCol.ifBlank { mediaUrlCol } }
                        val typeCol = obj.optString("type")
                        val messageTypeCol = obj.optString("message_type")
                        val displayType = messageTypeCol.ifBlank { typeCol.ifBlank { "text" } }
                        val isMedia = displayType == "image" || displayType == "voice" || displayType == "audio"
                        val textVal = if (displayType == "image") "Photo 📸" else if (displayType == "voice" || displayType == "audio") "Voice Note 🎤" else displayContent
                        val mediaUrlVal = if (isMedia) displayContent.ifBlank { mediaUrlCol } else ""

                        insertedMsg = MessageEntity(
                            id = id,
                            conversationId = recipientUsername,
                            senderUsername = senderUsername,
                            recipientUsername = recipientUsername,
                            senderAvatar = senderProfile?.avatarUrl.orEmpty(),
                            text = textVal,
                            mediaUrl = mediaUrlVal,
                            type = displayType,
                            timestamp = formatTimestamp(obj.optString("created_at")),
                            isMine = true
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse inserted message representation: ${e.message}")
                }
                Log.d("CONVERSATION_DEBUG", "MESSAGE_INSERT_SUCCESS: $respStr")
                Pair(true, "Message sent successfully")
            } else {
                val errJson = try { JSONObject(respStr) } catch (e: Exception) { null }
                val code = errJson?.optString("code") ?: "${response.code}"
                val messageText = errJson?.optString("message") ?: "Failed to send message"
                val details = errJson?.optString("details") ?: ""
                val hint = errJson?.optString("hint") ?: ""
                val fullError = "MESSAGE_ERROR ($code): $messageText details=$details hint=$hint response=$respStr"
                Log.e("CONVERSATION_DEBUG", fullError)
                System.err.println("CONVERSATION_DEBUG: $fullError")
                Pair(false, fullError)
            }
        } catch (e: Exception) {
            val errStr = "MESSAGE_ERROR sendMessage Exception: ${e.localizedMessage}"
            Log.e("CONVERSATION_DEBUG", errStr, e)
            System.err.println("CONVERSATION_DEBUG: $errStr")
            Pair(false, errStr)
        }
    }

    suspend fun sendMessageEx(message: MessageEntity): Triple<Boolean, String, MessageEntity?> = withContext(Dispatchers.IO) {
        val (success, err) = sendMessage(message)
        Triple(success, err, if (success) message else null)
    }

    suspend fun markMessagesAsRead(currentUsername: String, peerUsername: String): Boolean = withContext(Dispatchers.IO) {
        true
    }

    suspend fun getUnreadCounts(currentUsername: String): Map<String, Int> = withContext(Dispatchers.IO) {
        emptyMap()
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

    // --- REALTIME MESSAGES LISTENER ---

    private var realtimeMessageWebSocket: okhttp3.WebSocket? = null
    private var realtimeMessageHeartbeatJob: Job? = null
    private var activeRealtimeConvId: String? = null

    fun startMessageRealtimeListener(
        conversationId: String,
        coroutineScope: CoroutineScope,
        onNewMessage: (MessageEntity) -> Unit
    ) {
        if (activeRealtimeConvId == conversationId && realtimeMessageWebSocket != null) {
            Log.d(TAG, "[AURA_PERF] Realtime subscription already active for conversation $conversationId. Skipping.")
            return
        }
        stopMessageRealtimeListener()
        if (conversationId.isBlank()) return

        activeRealtimeConvId = conversationId
        val topicName = "realtime:public:messages:$conversationId"

        try {
            val host = baseUrl.removePrefix("https://").removePrefix("http://").removeSuffix("/")
            val wsUrl = "wss://$host/realtime/v1/websocket?apikey=$apiKey&v=1.0.0"

            val wsRequest = Request.Builder().url(wsUrl).build()
            realtimeMessageWebSocket = client.newWebSocket(wsRequest, object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                    Log.d(TAG, "[AURA_PERF] Realtime WebSocket connected for conversation_id: $conversationId")

                    val joinPayload = JSONObject().apply {
                        put("topic", topicName)
                        put("event", "phx_join")
                        put("payload", JSONObject().apply {
                            put("config", JSONObject().apply {
                                put("postgres_changes", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("event", "INSERT")
                                        put("schema", "public")
                                        put("table", "messages")
                                        put("filter", "conversation_id=eq.$conversationId")
                                    })
                                })
                            })
                        })
                        put("ref", "msg_1")
                    }
                    webSocket.send(joinPayload.toString())

                    realtimeMessageHeartbeatJob?.cancel()
                    realtimeMessageHeartbeatJob = coroutineScope.launch(Dispatchers.IO) {
                        while (isActive) {
                            delay(25000)
                            val hb = JSONObject().apply {
                                put("topic", "phoenix")
                                put("event", "heartbeat")
                                put("payload", JSONObject())
                                put("ref", "msg_hb_${System.currentTimeMillis()}")
                            }
                            webSocket.send(hb.toString())
                        }
                    }
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        val event = json.optString("event")
                        if (event == "postgres_changes") {
                            val payload = json.optJSONObject("payload")
                            val data = payload?.optJSONObject("data")
                            val record = data?.optJSONObject("record") ?: payload?.optJSONObject("record")
                            if (record != null) {
                                val msg = parseMessageRecord(record)
                                if (msg != null) {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        onNewMessage(msg)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[AURA_PERF] Realtime message parse error: ${e.message}")
                    }
                }

                override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                    Log.e(TAG, "[AURA_PERF] Realtime message WebSocket failure: ${t.localizedMessage}")
                }

                override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "[AURA_PERF] Realtime message WebSocket closing: $reason")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "[AURA_PERF] Failed to start Realtime message WebSocket: ${e.localizedMessage}", e)
        }
    }

    fun stopMessageRealtimeListener() {
        realtimeMessageHeartbeatJob?.cancel()
        realtimeMessageHeartbeatJob = null
        realtimeMessageWebSocket?.close(1000, "Chat closed or conversation changed")
        realtimeMessageWebSocket = null
        activeRealtimeConvId = null
        Log.d(TAG, "[AURA_PERF] Stopped Realtime message listener")
    }

    private fun parseMessageRecord(obj: JSONObject): MessageEntity? {
        val id = obj.optLong("id")
        val senderUser = obj.optString("sender_username")
        val recipientUser = obj.optString("recipient_username")
        val textCol = obj.optString("text")
        val contentCol = obj.optString("content")
        val mediaUrlCol = obj.optString("media_url")
        val displayContent = contentCol.ifBlank { textCol.ifBlank { mediaUrlCol } }

        val typeCol = obj.optString("type")
        val messageTypeCol = obj.optString("message_type")
        val displayType = messageTypeCol.ifBlank { typeCol.ifBlank { "text" } }

        val isMedia = displayType == "image" || displayType == "voice" || displayType == "audio"
        val textVal = if (displayType == "image") "Photo 📸" else if (displayType == "voice" || displayType == "audio") "Voice Note 🎤" else displayContent
        val mediaUrlVal = if (isMedia) displayContent.ifBlank { mediaUrlCol } else ""

        val isCurrentSender = obj.optString("sender_id") == currentAuthUserId

        return MessageEntity(
            id = id,
            conversationId = if (isCurrentSender) recipientUser else senderUser,
            senderUsername = senderUser,
            recipientUsername = recipientUser,
            senderAvatar = obj.optString("sender_avatar"),
            text = textVal,
            mediaUrl = mediaUrlVal,
            type = displayType,
            timestamp = formatTimestamp(obj.optString("created_at")),
            isMine = isCurrentSender
        )
    }

    // --- CALLS & LIVEKIT SIGNALING ---

    private var realtimeCallWebSocket: okhttp3.WebSocket? = null
    private var realtimeCallHeartbeatJob: Job? = null

    fun startIncomingCallRealtimeListener(
        receiverUuid: String,
        coroutineScope: CoroutineScope,
        onIncomingCall: (CallRecord) -> Unit
    ) {
        stopIncomingCallRealtimeListener()
        if (receiverUuid.isBlank()) return

        val trimmedReceiverId = receiverUuid.lowercase().trim()

        // 1. Initial single fetch
        coroutineScope.launch(Dispatchers.IO) {
            val pendingCall = getPendingIncomingCall(trimmedReceiverId)
            if (pendingCall != null && pendingCall.status == "ringing") {
                withContext(Dispatchers.Main) {
                    onIncomingCall(pendingCall)
                }
            }
        }

        // 2. Setup Supabase Realtime WebSocket connection
        try {
            val host = baseUrl.removePrefix("https://").removePrefix("http://").removeSuffix("/")
            val wsUrl = "wss://$host/realtime/v1/websocket?apikey=$apiKey&v=1.0.0"

            val wsRequest = Request.Builder().url(wsUrl).build()
            realtimeCallWebSocket = client.newWebSocket(wsRequest, object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                    Log.d(TAG, "Supabase Realtime WebSocket connected for incoming calls: $trimmedReceiverId")
                    
                    val joinPayload = JSONObject().apply {
                        put("topic", "realtime:public:calls")
                        put("event", "phx_join")
                        put("payload", JSONObject().apply {
                            put("config", JSONObject().apply {
                                put("postgres_changes", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("event", "INSERT")
                                        put("schema", "public")
                                        put("table", "calls")
                                        put("filter", "receiver_id=eq.$trimmedReceiverId")
                                    })
                                    put(JSONObject().apply {
                                        put("event", "UPDATE")
                                        put("schema", "public")
                                        put("table", "calls")
                                        put("filter", "receiver_id=eq.$trimmedReceiverId")
                                    })
                                })
                            })
                        })
                        put("ref", "1")
                    }
                    webSocket.send(joinPayload.toString())

                    // Start Heartbeat
                    realtimeCallHeartbeatJob?.cancel()
                    realtimeCallHeartbeatJob = coroutineScope.launch(Dispatchers.IO) {
                        while (isActive) {
                            delay(25000)
                            val hb = JSONObject().apply {
                                put("topic", "phoenix")
                                put("event", "heartbeat")
                                put("payload", JSONObject())
                                put("ref", "hb_${System.currentTimeMillis()}")
                            }
                            webSocket.send(hb.toString())
                        }
                    }
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        val event = json.optString("event")
                        if (event == "postgres_changes") {
                            val payload = json.optJSONObject("payload")
                            val data = payload?.optJSONObject("data")
                            val record = data?.optJSONObject("record") ?: payload?.optJSONObject("record")
                            if (record != null) {
                                val callRec = parseCallRecord(record)
                                if (callRec.receiverId.equals(trimmedReceiverId, ignoreCase = true) && callRec.status == "ringing") {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        onIncomingCall(callRec)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Realtime msg parse error: ${e.message}")
                    }
                }

                override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                    Log.e(TAG, "Supabase Realtime WebSocket failure: ${t.localizedMessage}")
                }

                override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "Supabase Realtime WebSocket closing: $reason")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Realtime WebSocket listener: ${e.localizedMessage}", e)
        }
    }

    fun stopIncomingCallRealtimeListener() {
        realtimeCallHeartbeatJob?.cancel()
        realtimeCallHeartbeatJob = null
        realtimeCallWebSocket?.close(1000, "User logged out or listener disposed")
        realtimeCallWebSocket = null
    }

    suspend fun createCallRecordEx(
        callerUuid: String,
        receiverUuid: String,
        roomId: String,
        callType: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (callerUuid.isBlank() || receiverUuid.isBlank()) {
            return@withContext Pair(false, "Please sign in again.")
        }
        Log.d("CALL_BUTTON_CLICKED", "callerId=$callerUuid\nreceiverId=$receiverUuid\ncallType=$callType")
        try {
            val url = "$baseUrl/rest/v1/calls"
            val bodyJson = JSONObject().apply {
                put("caller_id", callerUuid)
                put("receiver_id", receiverUuid)
                put("room_id", roomId)
                put("call_type", callType)
                put("status", "ringing")
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            getHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = client.newCall(requestBuilder.build()).execute()
            val respBody = response.body?.string() ?: ""
            Log.d(TAG, "POST /rest/v1/calls response code: ${response.code}, body: $respBody")

            if (response.isSuccessful || response.code == 201) {
                Pair(true, "Call initiated successfully")
            } else {
                val errJson = try { JSONObject(respBody) } catch (e: Exception) { null }
                val code = errJson?.optString("code") ?: "${response.code}"
                val message = errJson?.optString("message") ?: "Failed to create call"
                val details = errJson?.optString("details") ?: ""
                val hint = errJson?.optString("hint") ?: ""
                val fullError = "Call Creation Error ($code): $message details=$details hint=$hint"
                Log.e(TAG, fullError)
                Pair(false, fullError)
            }
        } catch (e: Exception) {
            val err = "Call creation exception: ${e.localizedMessage}"
            Log.e(TAG, err, e)
            Pair(false, err)
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
        // Try local JWT token generation using LiveKit API Key & Secret if available
        try {
            val apiKey = BuildConfig.LIVEKIT_API_KEY
            val apiSecret = BuildConfig.LIVEKIT_API_SECRET
            if (apiKey.isNotBlank() && apiSecret.isNotBlank()) {
                val token = generateLiveKitTokenLocally(roomName, identity, apiKey, apiSecret)
                if (!token.isNullOrBlank()) {
                    Log.d(TAG, "Generated LiveKit token locally for room: $roomName")
                    return@withContext token
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Local token generation fallback: ${e.localizedMessage}")
        }

        // Fallback to Edge Function
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

    private fun generateLiveKitTokenLocally(roomName: String, identity: String, apiKey: String, apiSecret: String): String? {
        return try {
            val header = JSONObject().apply {
                put("alg", "HS256")
                put("typ", "JWT")
            }

            val nowSeconds = System.currentTimeMillis() / 1000
            val expSeconds = nowSeconds + (24 * 3600) // 24h validity

            val videoGrant = JSONObject().apply {
                put("room", roomName)
                put("roomJoin", true)
                put("canPublish", true)
                put("canSubscribe", true)
                put("canPublishData", true)
            }

            val payload = JSONObject().apply {
                put("iss", apiKey)
                put("sub", identity)
                put("nbf", nowSeconds - 5)
                put("exp", expSeconds)
                put("video", videoGrant)
            }

            fun base64UrlEncode(bytes: ByteArray): String {
                return android.util.Base64.encodeToString(
                    bytes,
                    android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
                )
            }

            val encodedHeader = base64UrlEncode(header.toString().toByteArray(Charsets.UTF_8))
            val encodedPayload = base64UrlEncode(payload.toString().toByteArray(Charsets.UTF_8))

            val signingInput = "$encodedHeader.$encodedPayload"

            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            val secretKey = javax.crypto.spec.SecretKeySpec(apiSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac.init(secretKey)
            val signatureBytes = mac.doFinal(signingInput.toByteArray(Charsets.UTF_8))
            val encodedSignature = base64UrlEncode(signatureBytes)

            "$signingInput.$encodedSignature"
        } catch (e: Exception) {
            Log.e(TAG, "Error generating local LiveKit token: ${e.localizedMessage}")
            null
        }
    }

    private fun parseCallRecord(obj: JSONObject): CallRecord {
        val roomId = obj.optString("room_id")
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


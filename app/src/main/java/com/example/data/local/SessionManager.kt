package com.example.data.local

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("aura_auth_session", Context.MODE_PRIVATE)

    fun saveSession(userId: String, token: String, username: String) {
        prefs.edit()
            .putString("LOGGED_IN_USER_ID", userId)
            .putString("LOGGED_IN_TOKEN", token)
            .putString("LOGGED_IN_USERNAME", username)
            .apply()
    }

    fun getSessionUserId(): String? {
        return prefs.getString("LOGGED_IN_USER_ID", null)
    }

    fun getSessionToken(): String? {
        return prefs.getString("LOGGED_IN_TOKEN", null)
    }

    fun getSessionUsername(): String? {
        return prefs.getString("LOGGED_IN_USERNAME", null)
    }

    fun clearSession() {
        prefs.edit()
            .remove("LOGGED_IN_USER_ID")
            .remove("LOGGED_IN_TOKEN")
            .remove("LOGGED_IN_USERNAME")
            .apply()
    }
}

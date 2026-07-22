package com.example.data.local

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("aura_auth_session", Context.MODE_PRIVATE)

    fun saveSession(username: String) {
        prefs.edit().putString("LOGGED_IN_USERNAME", username).apply()
    }

    fun getSessionUsername(): String? {
        return prefs.getString("LOGGED_IN_USERNAME", null)
    }

    fun clearSession() {
        prefs.edit().remove("LOGGED_IN_USERNAME").apply()
    }
}

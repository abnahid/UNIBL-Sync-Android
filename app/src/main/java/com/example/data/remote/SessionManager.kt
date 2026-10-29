package com.example.data.remote

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSession(
    val id: String,
    val email: String,
    val name: String,
    val role: String // "ADMIN" or "EDITOR"
)

class SessionManager(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _sessionState = MutableStateFlow(loadSession())
    val sessionState: StateFlow<UserSession?> = _sessionState.asStateFlow()

    fun loadSession(): UserSession? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        val name = prefs.getString(KEY_USER_NAME, "") ?: ""
        val role = prefs.getString(KEY_USER_ROLE, "EDITOR") ?: "EDITOR"
        return UserSession(id = id, email = email, name = name, role = role)
    }

    fun getSessionToken(): String? = prefs.getString(KEY_SESSION_TOKEN, null)

    fun getSessionCookies(): Set<String> = prefs.getStringSet(KEY_SESSION_COOKIES, emptySet()) ?: emptySet()

    fun saveCookies(cookies: List<String>) {
        if (cookies.isEmpty()) return
        val existing = getSessionCookies().toMutableSet()
        existing.addAll(cookies)
        prefs.edit().putStringSet(KEY_SESSION_COOKIES, existing).apply()
    }

    fun saveSession(user: UserSession, token: String? = null, cookies: List<String> = emptyList()) {
        val editor = prefs.edit()
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_NAME, user.name)
            .putString(KEY_USER_ROLE, user.role)

        if (token != null) {
            editor.putString(KEY_SESSION_TOKEN, token)
        }
        if (cookies.isNotEmpty()) {
            val set = getSessionCookies().toMutableSet()
            set.addAll(cookies)
            editor.putStringSet(KEY_SESSION_COOKIES, set)
        }
        editor.apply()
        _sessionState.value = user
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_ROLE)
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_SESSION_COOKIES)
            .apply()
        _sessionState.value = null
    }

    fun isLoggedIn(): Boolean = _sessionState.value != null

    fun isAdmin(): Boolean = _sessionState.value?.role.equals("ADMIN", ignoreCase = true)

    companion object {
        private const val PREFS_NAME = "unibl_auth_session"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_SESSION_COOKIES = "session_cookies"

        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager =
            instance ?: synchronized(this) {
                instance ?: SessionManager(context).also { instance = it }
            }
    }
}

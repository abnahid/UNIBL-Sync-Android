package com.example.data.remote

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AuthApiClient(
    private val context: Context,
    private val baseUrl: String = BuildConfig.PUBLIC_API_BASE_URL,
    private val rootUrl: String = BuildConfig.PUBLIC_ROUTE_URL,
    private val client: OkHttpClient = NetworkClientProvider.getClient(context)
) {
    private val sessionManager = SessionManager.getInstance(context)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Authenticates a university administrator or staff member.
     * Uses Better Auth (`/api/auth/sign-in/email`) with seamless fallback to `/api/v1/auth/login`.
     */
    suspend fun login(email: String, password: String): Result<UserSession> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Email and password cannot be empty."))
        }

        val requestJson = JSONObject().apply {
            put("email", cleanEmail)
            put("password", password)
        }.toString()

        val normalizedRoot = rootUrl.trimEnd('/')

        // 1. Try Better Auth primary staff sign-in
        try {
            val betterAuthRequest = Request.Builder()
                .url("$normalizedRoot/api/auth/sign-in/email")
                .header("Accept", "application/json")
                .header("Origin", normalizedRoot)
                .post(requestJson.toRequestBody(jsonMediaType))
                .build()

            client.newCall(betterAuthRequest).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val userObj = json.optJSONObject("user")
                    val sessionObj = json.optJSONObject("session")
                    if (userObj != null) {
                        val roleStr = userObj.optString("role", "EDITOR").uppercase()
                        val mappedRole = if (roleStr == "ADMIN") "ADMIN" else "EDITOR"
                        val user = UserSession(
                            id = userObj.optString("id", cleanEmail),
                            email = userObj.optString("email", cleanEmail),
                            name = userObj.optString("name", cleanEmail.substringBefore('@')),
                            role = mappedRole
                        )
                        val token = sessionObj?.optString("token")
                        sessionManager.saveSession(user, token = token)
                        return@withContext Result.success(user)
                    }
                } else if (response.code != 404 && response.code != 405) {
                    val errMsg = parseErrorMessage(bodyStr, "Invalid email or password")
                    return@withContext Result.failure(ApiException(response.code, errMsg))
                }
            }
        } catch (e: Exception) {
            // If Better Auth endpoint had connection issue, proceed to check API v1
        }

        // 2. Fallback to API v1 auth endpoint
        try {
            val apiBase = baseUrl.trimEnd('/')
            val v1Request = Request.Builder()
                .url("$apiBase/auth/login")
                .header("Accept", "application/json")
                .header("Origin", normalizedRoot)
                .post(requestJson.toRequestBody(jsonMediaType))
                .build()

            client.newCall(v1Request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val data = json.optJSONObject("data")
                    val userObj = data?.optJSONObject("user")
                    if (userObj != null) {
                        val user = UserSession(
                            id = userObj.optString("id", cleanEmail),
                            email = userObj.optString("email", cleanEmail),
                            name = userObj.optString("name", cleanEmail.substringBefore('@')),
                            role = userObj.optString("role", "EDITOR").uppercase()
                        )
                        sessionManager.saveSession(user)
                        return@withContext Result.success(user)
                    }
                }

                val errMsg = parseErrorMessage(bodyStr, "Invalid email or password")
                return@withContext Result.failure(ApiException(response.code, errMsg))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * Verifies if the stored session is still active and authorized on the backend.
     */
    suspend fun checkSession(): Result<UserSession?> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) {
            return@withContext Result.success(null)
        }

        val apiBase = baseUrl.trimEnd('/')
        try {
            val request = Request.Builder()
                .url("$apiBase/auth/me")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = JSONObject(response.body?.string().orEmpty())
                    val userObj = body.optJSONObject("data")?.optJSONObject("user")
                    if (userObj != null) {
                        val user = UserSession(
                            id = userObj.getString("id"),
                            email = userObj.getString("email"),
                            name = userObj.optString("name", ""),
                            role = userObj.optString("role", "EDITOR").uppercase()
                        )
                        sessionManager.saveSession(user)
                        return@withContext Result.success(user)
                    }
                } else if (response.code == 401 || response.code == 403) {
                    // Session expired on server
                    sessionManager.clearSession()
                    return@withContext Result.success(null)
                }
            }
        } catch (_: Exception) {
            // Keep local cached session offline if network fails temporarily
        }

        return@withContext Result.success(sessionManager.loadSession())
    }

    /**
     * Signs out the user from both Better Auth and the local application.
     */
    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        val normalizedRoot = rootUrl.trimEnd('/')
        val apiBase = baseUrl.trimEnd('/')

        try {
            // Call Better Auth sign-out
            val signOutRequest = Request.Builder()
                .url("$normalizedRoot/api/auth/sign-out")
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            client.newCall(signOutRequest).execute().close()
        } catch (_: Exception) { }

        try {
            // Call v1 auth logout
            val v1Logout = Request.Builder()
                .url("$apiBase/auth/login")
                .delete()
                .build()
            client.newCall(v1Logout).execute().close()
        } catch (_: Exception) { }

        sessionManager.clearSession()
        Result.success(Unit)
    }

    private fun parseErrorMessage(body: String, defaultMessage: String): String {
        return try {
            val json = JSONObject(body)
            json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optJSONObject("error")?.optString("message")
                ?: defaultMessage
        } catch (_: Exception) {
            defaultMessage
        }
    }
}

package com.example.util

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Checks the public release endpoint without requiring an account or push token. */
object ReleaseUpdateChecker {
    private const val PREFS = "release_updates"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"

    private val client = OkHttpClient()

    suspend fun notifyIfNewRelease(context: android.content.Context) = withContext(Dispatchers.IO) {
        val apiBaseUrl = BuildConfig.PUBLIC_API_BASE_URL.trimEnd('/')
        if (apiBaseUrl.isBlank() || apiBaseUrl.contains("your-public-api.example.com")) return@withContext

        try {
            val request = Request.Builder()
                .url("$apiBaseUrl/releases?latest=true")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext

            val body = response.body?.string() ?: return@withContext
            val release = JSONObject(body).optJSONObject("data") ?: return@withContext
            val version = release.optString("version").trim()
            if (version.isBlank() || compareVersions(version, BuildConfig.VERSION_NAME) <= 0) {
                return@withContext
            }

            val preferences = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            if (preferences.getString(KEY_LAST_NOTIFIED_VERSION, null) == version) return@withContext

            val apkUrl = release.optString("apkUrl").trim()
            if (apkUrl.isBlank()) return@withContext

            val downloadUrl = if (apkUrl.startsWith("http://") || apkUrl.startsWith("https://")) {
                apkUrl
            } else {
                "${BuildConfig.PUBLIC_ROUTE_URL.trimEnd('/')}/${apkUrl.trimStart('/')}"
            }

            NotificationHelper.sendAppUpdateNotification(context, version, downloadUrl)
            preferences.edit().putString(KEY_LAST_NOTIFIED_VERSION, version).apply()
        } catch (_: Exception) {
            // A failed optional update check must never block the timetable app.
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.removePrefix("v").split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val rightParts = right.removePrefix("v").split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val comparison = (leftParts.getOrElse(index) { 0 }).compareTo(rightParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }
}

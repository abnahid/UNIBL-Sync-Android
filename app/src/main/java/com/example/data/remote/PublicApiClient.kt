package com.example.data.remote

import com.example.BuildConfig
import com.example.data.model.AnnouncementEntity
import com.example.data.model.ClassEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val statusCode: Int, message: String) : IOException(message) {
    val isTemporary: Boolean get() = statusCode == 429 || statusCode >= 500
}

/** Client for public, read-only API resources. It does not carry staff credentials. */
interface PublicDataSource {
    suspend fun fetchClasses(): List<ClassEntity>
    suspend fun fetchAnnouncements(): List<AnnouncementEntity>
}

class PublicApiClient(
    private val baseUrl: String = BuildConfig.PUBLIC_API_BASE_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) : PublicDataSource {
    override suspend fun fetchClasses(): List<ClassEntity> = fetchAll("classes", ::parseClass)

    override suspend fun fetchAnnouncements(): List<AnnouncementEntity> = fetchAll("announcements", ::parseAnnouncement)

    private suspend fun <T> fetchAll(path: String, parse: (JSONObject) -> T): List<T> = withContext(Dispatchers.IO) {
        val normalizedBase = baseUrl.trimEnd('/')
        require(normalizedBase.startsWith("https://")) { "A production API URL must use HTTPS." }
        val rows = mutableListOf<T>()
        var page = 1
        var totalPages: Int
        do {
            val request = Request.Builder()
                .url("$normalizedBase/$path?page=$page&pageSize=200")
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiException(response.code, "Request to $path failed")
                val payload = JSONObject(response.body?.string().orEmpty())
                val data = payload.optJSONArray("data") ?: JSONArray()
                repeat(data.length()) { index -> rows += parse(data.getJSONObject(index)) }
                totalPages = payload.optJSONObject("meta")?.optInt("totalPages", 1)?.coerceAtLeast(1) ?: 1
            }
            page += 1
        } while (page <= totalPages)
        rows
    }

    private fun parseClass(json: JSONObject) = ClassEntity(
        id = json.getString("id"),
        subject = json.getString("subject"),
        classType = json.getString("classType"),
        instructor = json.optionalString("instructor"),
        sourceDate = json.getString("sourceDate"),
        sourceStartTime = json.getString("sourceStartTime"),
        sourceEndTime = json.getString("sourceEndTime"),
        sourceTimezone = json.getString("sourceTimezone"),
        utcStartEpochMs = json.getLong("utcStartEpochMs"),
        utcEndEpochMs = json.getLong("utcEndEpochMs"),
        groupName = json.optionalString("group", "All"),
        attendanceMode = json.optionalString("attendanceMode"),
        platform = json.optionalString("platform"),
        teamsChannel = json.optionalString("teamsChannel"),
        locationOrUrl = json.optionalString("locationOrUrl"),
        status = json.optString("status", ClassEntity.STATUS_SCHEDULED),
        notes = json.optionalString("notes"),
        updatedAtEpochMs = json.optLong("updatedAtEpochMs", System.currentTimeMillis())
    )

    private fun parseAnnouncement(json: JSONObject) = AnnouncementEntity(
        id = json.getString("id"),
        title = json.getString("title"),
        message = json.getString("message"),
        priority = json.optString("priority", "NORMAL"),
        targetGroup = json.optString("targetGroup", "All"),
        createdAtEpochMs = json.optLong("createdAtEpochMs", System.currentTimeMillis())
    )

    private fun JSONObject.optionalString(name: String, default: String = ""): String =
        if (isNull(name)) default else optString(name, default)
}

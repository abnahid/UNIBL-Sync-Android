package com.example.data.remote

import android.content.Context
import com.example.BuildConfig
import com.example.data.model.AnnouncementEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClassEntity
import com.example.data.model.VisitorEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AdminApiClient(
    private val context: Context,
    private val baseUrl: String = BuildConfig.PUBLIC_API_BASE_URL,
    private val client: OkHttpClient = NetworkClientProvider.getClient(context)
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun createClass(classEntity: ClassEntity): ClassEntity = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("subject", classEntity.subject)
            put("classType", normalizeClassType(classEntity.classType))
            if (classEntity.instructor.isNotBlank()) put("instructor", classEntity.instructor)
            put("sourceDate", classEntity.sourceDate)
            put("sourceStartTime", normalizeTime(classEntity.sourceStartTime))
            put("sourceEndTime", normalizeTime(classEntity.sourceEndTime))
            put("group", normalizeGroup(classEntity.groupName))
            put("attendanceMode", normalizeMode(classEntity.attendanceMode))
            if (classEntity.platform.isNotBlank()) put("platform", classEntity.platform)
            if (classEntity.teamsChannel.isNotBlank()) put("teamsChannel", classEntity.teamsChannel)
            if (classEntity.locationOrUrl.isNotBlank()) put("locationOrUrl", classEntity.locationOrUrl)
            put("status", classEntity.status)
            put("notify", true)
            if (classEntity.notes.isNotBlank()) put("notes", classEntity.notes)
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/classes")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val err = parseError(body, "Failed to create class (${response.code})")
                throw ApiException(response.code, err)
            }
            val json = JSONObject(body).getJSONObject("data")
            parseClass(json)
        }
    }

    suspend fun updateClass(classEntity: ClassEntity): Unit = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("subject", classEntity.subject)
            put("classType", normalizeClassType(classEntity.classType))
            if (classEntity.instructor.isNotBlank()) put("instructor", classEntity.instructor)
            put("sourceDate", classEntity.sourceDate)
            put("sourceStartTime", normalizeTime(classEntity.sourceStartTime))
            put("sourceEndTime", normalizeTime(classEntity.sourceEndTime))
            put("group", normalizeGroup(classEntity.groupName))
            put("attendanceMode", normalizeMode(classEntity.attendanceMode))
            if (classEntity.platform.isNotBlank()) put("platform", classEntity.platform)
            if (classEntity.teamsChannel.isNotBlank()) put("teamsChannel", classEntity.teamsChannel)
            if (classEntity.locationOrUrl.isNotBlank()) put("locationOrUrl", classEntity.locationOrUrl)
            put("status", classEntity.status)
            if (classEntity.notes.isNotBlank()) put("notes", classEntity.notes)
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/classes/${classEntity.id}")
            .header("Accept", "application/json")
            .patch(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = parseError(response.body?.string().orEmpty(), "Failed to update class")
                throw ApiException(response.code, err)
            }
        }
    }

    suspend fun rescheduleClass(
        classId: String,
        newDate: String,
        newStartTime: String,
        newEndTime: String,
        reason: String
    ): Unit = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("sourceDate", newDate)
            put("sourceStartTime", normalizeTime(newStartTime))
            put("sourceEndTime", normalizeTime(newEndTime))
            put("status", ClassEntity.STATUS_RESCHEDULED)
            if (reason.isNotBlank()) put("notes", "Rescheduled: $reason")
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/classes/$classId")
            .header("Accept", "application/json")
            .patch(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = parseError(response.body?.string().orEmpty(), "Failed to reschedule class")
                throw ApiException(response.code, err)
            }
        }
    }

    suspend fun cancelClass(classId: String, reason: String): Unit = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("status", ClassEntity.STATUS_CANCELLED)
            if (reason.isNotBlank()) put("notes", "Cancelled: $reason")
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/classes/$classId")
            .header("Accept", "application/json")
            .patch(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = parseError(response.body?.string().orEmpty(), "Failed to cancel class")
                throw ApiException(response.code, err)
            }
        }
    }

    suspend fun deleteClass(classId: String): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/classes/$classId")
            .delete()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val err = parseError(response.body?.string().orEmpty(), "Failed to delete class")
                throw ApiException(response.code, err)
            }
        }
    }

    suspend fun postAnnouncement(
        title: String,
        message: String,
        priority: String = "NORMAL",
        targetGroup: String = "ALL"
    ): AnnouncementEntity = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("title", title)
            put("message", message)
            put("priority", if (priority.equals("URGENT", ignoreCase = true)) "URGENT" else "NORMAL")
            put("targetGroup", normalizeGroup(targetGroup))
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/announcements")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val err = parseError(body, "Failed to post announcement")
                throw ApiException(response.code, err)
            }
            val json = JSONObject(body).getJSONObject("data")
            AnnouncementEntity(
                id = json.getString("id"),
                title = json.getString("title"),
                message = json.getString("message"),
                priority = json.optString("priority", "NORMAL"),
                targetGroup = json.optString("targetGroup", "ALL"),
                createdAtEpochMs = json.optLong("createdAtEpochMs", System.currentTimeMillis())
            )
        }
    }

    suspend fun fetchAuditLogs(): List<AuditLogEntity> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/admin/audit-logs?page=1&pageSize=50")
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val json = JSONObject(response.body?.string().orEmpty())
            val rows = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<AuditLogEntity>()
            repeat(rows.length()) { i ->
                val r = rows.getJSONObject(i)
                list.add(
                    AuditLogEntity(
                        id = r.getString("id"),
                        action = r.getString("action"),
                        subject = r.getString("subject"),
                        details = r.opt("details")?.toString().orEmpty(),
                        timestampEpochMs = r.optLong("atEpochMs", System.currentTimeMillis())
                    )
                )
            }
            list
        }
    }

    suspend fun fetchVisitors(): List<VisitorEntity> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/visitors?page=1&pageSize=50")
            .header("Accept", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val json = JSONObject(response.body?.string().orEmpty())
            val rows = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<VisitorEntity>()
            repeat(rows.length()) { i ->
                val r = rows.getJSONObject(i)
                list.add(
                    VisitorEntity(
                        id = r.getString("id"),
                        name = r.getString("name"),
                        email = r.getString("email"),
                        purpose = r.optString("purpose", ""),
                        targetGroup = r.optString("targetGroup", "ALL"),
                        registeredAtEpochMs = r.optLong("registeredAtEpochMs", System.currentTimeMillis())
                    )
                )
            }
            list
        }
    }

    private fun normalizeClassType(type: String): String {
        val upper = type.uppercase().replace(" ", "_")
        return when {
            upper.contains("MAKE_UP") && upper.contains("PRACTICAL") -> "MAKE_UP_PRACTICAL"
            upper.contains("MAKE_UP") -> "MAKE_UP_LECTURE"
            upper.contains("PRACTICAL") -> "PRACTICAL"
            upper.contains("SEMINAR") -> "SEMINAR"
            upper.contains("EXAM") -> "EXAM_REVIEW"
            else -> "LECTURE"
        }
    }

    private fun normalizeGroup(group: String): String {
        val clean = group.uppercase().replace(" ", "_")
        return if (clean.startsWith("GROUP_")) clean else if (clean == "ALL") "ALL" else "ALL"
    }

    private fun normalizeMode(mode: String): String {
        val clean = mode.uppercase()
        return when {
            clean.contains("ONLINE") && (clean.contains("IN PERSON") || clean.contains("IN-PERSON") || clean.contains("HYBRID")) -> "HYBRID"
            clean.contains("ONLINE") -> "ONLINE"
            else -> "IN_PERSON"
        }
    }

    private fun normalizeTime(timeStr: String): String {
        val clean = timeStr.trim().uppercase()
        // If already HH:mm
        if (clean.matches(Regex("^\\d{1,2}:\\d{2}$"))) {
            val parts = clean.split(":")
            return String.format("%02d:%02d", parts[0].toInt(), parts[1].toInt())
        }
        // If 12-hour e.g. 5:00 PM
        val match = Regex("^(\\d{1,2}):(\\d{2})\\s*(AM|PM)$").find(clean)
        if (match != null) {
            var hour = match.groupValues[1].toInt()
            val min = match.groupValues[2].toInt()
            val amPm = match.groupValues[3]
            if (amPm == "PM" && hour < 12) hour += 12
            if (amPm == "AM" && hour == 12) hour = 0
            return String.format("%02d:%02d", hour, min)
        }
        return "09:00"
    }

    private fun parseClass(json: JSONObject) = ClassEntity(
        id = json.getString("id"),
        subject = json.getString("subject"),
        classType = json.optString("classType", "Lecture"),
        instructor = if (json.isNull("instructor")) "" else json.optString("instructor", ""),
        sourceDate = json.getString("sourceDate"),
        sourceStartTime = json.getString("sourceStartTime"),
        sourceEndTime = json.getString("sourceEndTime"),
        sourceTimezone = json.optString("sourceTimezone", "Europe/Sarajevo"),
        utcStartEpochMs = json.optLong("utcStartEpochMs", 0L),
        utcEndEpochMs = json.optLong("utcEndEpochMs", 0L),
        groupName = if (json.isNull("group")) "All" else json.optString("group", "All"),
        attendanceMode = if (json.isNull("attendanceMode")) "In person + Online" else json.optString("attendanceMode", "In person + Online"),
        platform = if (json.isNull("platform")) ClassEntity.PLATFORM_TEAMS else json.optString("platform", ClassEntity.PLATFORM_TEAMS),
        teamsChannel = if (json.isNull("teamsChannel")) "General" else json.optString("teamsChannel", "General"),
        locationOrUrl = if (json.isNull("locationOrUrl")) "" else json.optString("locationOrUrl", ""),
        status = json.optString("status", ClassEntity.STATUS_SCHEDULED),
        notes = if (json.isNull("notes")) "" else json.optString("notes", ""),
        updatedAtEpochMs = json.optLong("updatedAtEpochMs", System.currentTimeMillis())
    )

    private fun parseError(body: String, defaultMsg: String): String {
        return try {
            val json = JSONObject(body)
            json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optJSONObject("error")?.optString("message")
                ?: defaultMsg
        } catch (_: Exception) {
            defaultMsg
        }
    }
}

package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.ClassEntity
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object IcsExporter {
    fun generateIcsContent(classes: List<ClassEntity>): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//ClassTime//University Schedule//EN")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("METHOD:PUBLISH")

        for (item in classes) {
            val startUtc = Instant.ofEpochMilli(item.utcStartEpochMs).atOffset(ZoneOffset.UTC).format(formatter)
            val endUtc = Instant.ofEpochMilli(item.utcEndEpochMs).atOffset(ZoneOffset.UTC).format(formatter)

            sb.appendLine("BEGIN:VEVENT")
            sb.appendLine("UID:${item.id}@classtime.app")
            sb.appendLine("DTSTAMP:$startUtc")
            sb.appendLine("DTSTART:$startUtc")
            sb.appendLine("DTEND:$endUtc")
            sb.appendLine("SUMMARY:${item.subject} (${item.classType})")
            val desc = buildString {
                append("Type: ${item.classType}\\n")
                if (item.instructor.isNotBlank()) append("Instructor: ${item.instructor}\\n")
                append("Group: ${item.groupName}\\n")
                append("Mode: ${item.attendanceMode}\\n")
                if (item.locationOrUrl.isNotBlank()) append("Location/URL: ${item.locationOrUrl}\\n")
                if (item.notes.isNotBlank()) append("Notes: ${item.notes}\\n")
                append("Timings originally announced in ${item.sourceTimezone}")
            }
            sb.appendLine("DESCRIPTION:$desc")
            if (item.locationOrUrl.isNotBlank()) {
                sb.appendLine("LOCATION:${item.locationOrUrl}")
            }
            sb.appendLine("STATUS:CONFIRMED")
            sb.appendLine("END:VEVENT")
        }

        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    fun shareIcsFile(context: Context, classes: List<ClassEntity>) {
        try {
            val icsContent = generateIcsContent(classes)
            val cacheFile = File(context.cacheDir, "ClassTime_Schedule.ics")
            cacheFile.writeText(icsContent)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ClassTime Timetable Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(sendIntent, "Export Calendar (.ics)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Fallback plain share if FileProvider is unavailable
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, generateIcsContent(classes))
                putExtra(Intent.EXTRA_SUBJECT, "ClassTime Schedule (.ics)")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Timetable ICS"))
        }
    }
}

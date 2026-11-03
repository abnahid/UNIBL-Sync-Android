package com.abnahid.uniblsync.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.abnahid.uniblsync.data.model.ClassEntity

object MeetingLauncher {
    const val TEAMS_PACKAGE = "com.microsoft.teams"

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Only genuine http(s) links are treated as meeting destinations. */
    fun isUsableMeetingUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
            android.util.Patterns.WEB_URL.matcher(trimmed).matches()
    }

    /**
     * Opens a real meeting URL. Never fabricates a Teams address: classes
     * without a published link must not be joinable through fake URLs.
     */
    fun openMeeting(
        context: Context,
        url: String,
        platform: String = ClassEntity.PLATFORM_TEAMS,
        subject: String = "Class"
    ) {
        if (!isUsableMeetingUrl(url)) {
            Toast.makeText(
                context,
                subject.ifBlank { "This class" }.let { "The meeting link for \"$it\" has not been provided yet." },
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val targetUrl = url.trim()
        val uri = Uri.parse(targetUrl)
        val isTeams = platform.contains("Teams", ignoreCase = true) ||
            targetUrl.contains("teams.microsoft.com", ignoreCase = true)

        // Try the Teams app first for Teams links, then fall back to the browser.
        if (isTeams && isAppInstalled(context, TEAMS_PACKAGE) &&
            targetUrl.startsWith("https://teams.microsoft.com")
        ) {
            try {
                val teamsUri = targetUrl.replaceFirst("https://", "msteams://")
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(teamsUri)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        setPackage(TEAMS_PACKAGE)
                    }
                )
                return
            } catch (_: Exception) {
                // Deep link failed — continue to the browser fallback below.
            }
        }

        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open the meeting link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

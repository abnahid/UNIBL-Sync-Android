package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.example.data.model.ClassEntity

object MeetingLauncher {
    const val TEAMS_PACKAGE = "com.microsoft.teams"
    const val TEAMS_DEFAULT_BASE_URL = "https://teams.microsoft.com/l/meetup-join"

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Opens Microsoft Teams directly on Android device.
     * Uses Teams app intent if installed, otherwise opens in browser or prompts user.
     */
    fun openMeeting(
        context: Context,
        url: String,
        platform: String = ClassEntity.PLATFORM_TEAMS,
        subject: String = "Class"
    ) {
        val targetUrl = when {
            url.isNotBlank() -> url
            platform == ClassEntity.PLATFORM_TEAMS -> "https://teams.microsoft.com/l/channel/19%3Aclasstime_${subject.lowercase().replace(" ", "_")}@thread.tacv2/General"
            else -> "https://teams.microsoft.com"
        }

        val uri = Uri.parse(targetUrl)

        // If Microsoft Teams
        if (platform == ClassEntity.PLATFORM_TEAMS || targetUrl.contains("teams.microsoft.com")) {
            // Try Teams app deep link scheme msteams:// if web scheme
            val teamsAppUri = if (targetUrl.startsWith("https://teams.microsoft.com")) {
                Uri.parse(targetUrl.replaceFirst("https://", "msteams://"))
            } else {
                uri
            }

            // 1. Try launching Teams app directly via deep link or package
            val teamsIntent = Intent(Intent.ACTION_VIEW, teamsAppUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (isAppInstalled(context, TEAMS_PACKAGE)) {
                    setPackage(TEAMS_PACKAGE)
                }
            }

            try {
                context.startActivity(teamsIntent)
                return
            } catch (_: Exception) {
                // Teams deep link failed, fallback to standard web URL
            }
        }

        // Standard browser fallback
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Builds standard Microsoft Teams channel link for a university subject.
     */
    fun getTeamsChannelUrl(subject: String, channelName: String = "General"): String {
        val cleanSubject = subject.lowercase().replace(" ", "_")
        val encodedChannel = java.net.URLEncoder.encode(channelName, "UTF-8")
        return "https://teams.microsoft.com/l/channel/19%3Aclasstime_${cleanSubject}@thread.tacv2/$encodedChannel"
    }
}

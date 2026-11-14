package com.abnahid.uniblsync.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.abnahid.uniblsync.MainActivity
import com.abnahid.uniblsync.R

object NotificationHelper {
    // Dedicated versioned channels for each reminder stage + silent equivalents
    const val CHANNEL_ADVANCE_REMINDERS_ID = "unibl_sync_advance_reminders_v3"
    const val CHANNEL_ADVANCE_SILENT_ID = "unibl_sync_advance_silent_v3"
    const val CHANNEL_CLASS_START_ID = "unibl_sync_class_start_v3"
    const val CHANNEL_CLASS_START_SILENT_ID = "unibl_sync_class_start_silent_v3"
    const val CHANNEL_UPDATES_ID = "unibl_sync_updates_v2"

    // Short, non-continuous vibration patterns
    private val ADVANCE_VIBRATION_PATTERN = longArrayOf(0, 220)
    private val CLASS_START_VIBRATION_PATTERN = longArrayOf(0, 300, 140, 300)

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notificationAudioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val advanceSound = rawSoundUri(context, "unibl_advance_chime")
            ?: Settings.System.DEFAULT_NOTIFICATION_URI
        val startSound = rawSoundUri(context, "unibl_class_start")
            ?: Settings.System.DEFAULT_NOTIFICATION_URI

        // 1. Class Advance Reminders (gentle sound, short vibration)
        val advanceChannel = NotificationChannel(
            CHANNEL_ADVANCE_REMINDERS_ID,
            "Class Advance Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Gentle reminders delivered before your scheduled classes start"
            enableVibration(true)
            vibrationPattern = ADVANCE_VIBRATION_PATTERN
            setSound(advanceSound, notificationAudioAttrs)
        }

        // 1b. Class Advance Reminders (sound off in app preferences)
        val advanceSilentChannel = NotificationChannel(
            CHANNEL_ADVANCE_SILENT_ID,
            "Class Advance Reminders (Silent)",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Silent advance reminders when sound is disabled in app settings"
            enableVibration(true)
            vibrationPattern = ADVANCE_VIBRATION_PATTERN
            setSound(null, null)
        }

        // 2. Class Starting Now (distinctive short sound, short vibration)
        val classStartChannel = NotificationChannel(
            CHANNEL_CLASS_START_ID,
            "Class Starting Now",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Distinctive one-time alert when your class is starting now"
            enableVibration(true)
            vibrationPattern = CLASS_START_VIBRATION_PATTERN
            setSound(startSound, notificationAudioAttrs)
        }

        // 2b. Class Starting Now (sound off in app preferences)
        val classStartSilentChannel = NotificationChannel(
            CHANNEL_CLASS_START_SILENT_ID,
            "Class Starting Now (Silent)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Silent class-start alerts when sound is disabled in app settings"
            enableVibration(true)
            vibrationPattern = CLASS_START_VIBRATION_PATTERN
            setSound(null, null)
        }

        // 3. University Announcements & Updates
        val updatesChannel = NotificationChannel(
            CHANNEL_UPDATES_ID,
            "UNIBL Sync announcements & updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts for rescheduled, cancelled, or newly published classes"
            setSound(advanceSound, notificationAudioAttrs)
        }

        notificationManager.createNotificationChannel(advanceChannel)
        notificationManager.createNotificationChannel(advanceSilentChannel)
        notificationManager.createNotificationChannel(classStartChannel)
        notificationManager.createNotificationChannel(classStartSilentChannel)
        notificationManager.createNotificationChannel(updatesChannel)
    }

    private fun rawSoundUri(context: Context, resourceName: String): Uri? {
        val resourceId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
        return if (resourceId == 0) null else Uri.parse("android.resource://${context.packageName}/$resourceId")
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notificationIdFor(occurrenceKey: String, stage: String): Int {
        val stageBit = if (stage == "FINAL") 1 else 0
        return ((occurrenceKey.hashCode() and 0x3FFFFFFF) shl 1) or stageBit
    }

    /**
     * Stage 1: Gentle advance reminder delivered once before class start
     * (either on schedule or via missed-reminder catch-up).
     */
    fun sendAdvanceReminderNotification(
        context: Context,
        notificationId: Int,
        subject: String,
        minutesUntilStart: Int,
        localTimeDisplay: String,
        locationOrUrl: String,
        isCatchUp: Boolean = false,
        soundEnabled: Boolean = true,
        vibrate: Boolean = true
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        createNotificationChannels(context)

        val channelId = if (soundEnabled) CHANNEL_ADVANCE_REMINDERS_ID else CHANNEL_ADVANCE_SILENT_ID
        val title = "Upcoming Class: ${subject.ifBlank { "Class" }}"
        val leadSentence = if (isCatchUp) {
            val unit = if (minutesUntilStart == 1) "minute" else "minutes"
            "Your $subject class starts in $minutesUntilStart $unit!"
        } else {
            val unit = if (minutesUntilStart == 1) "minute" else "minutes"
            "$subject class starts in $minutesUntilStart $unit."
        }

        val extras = buildList {
            if (localTimeDisplay.isNotBlank()) add(localTimeDisplay)
            val meetingUrl = validMeetingUrl(locationOrUrl)
            if (meetingUrl != null) {
                add("Online meeting")
            } else if (locationOrUrl.isNotBlank()) {
                add(locationOrUrl)
            }
        }
        val body = if (extras.isEmpty()) leadSentence else "$leadSentence • ${extras.joinToString(" • ")}"

        val builder = baseClassNotificationBuilder(
            context = context,
            channelId = channelId,
            notificationId = notificationId,
            title = title,
            body = body,
            soundEnabled = soundEnabled,
            vibrate = vibrate,
            vibrationPattern = ADVANCE_VIBRATION_PATTERN,
            soundUri = rawSoundUri(context, "unibl_advance_chime"),
            meetingUrl = validMeetingUrl(locationOrUrl),
            priority = NotificationCompat.PRIORITY_HIGH
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, builder.build())
        return true
    }

    /**
     * Stage 2: Prominent one-time notification delivered at the exact class start time
     * (or once within the 5-minute grace period).
     *
     * Never loops audio, never vibrates continuously, and requires no Dismiss action.
     */
    fun sendClassStartNotification(
        context: Context,
        notificationId: Int,
        subject: String,
        localTimeDisplay: String,
        locationOrUrl: String,
        soundEnabled: Boolean = true,
        vibrate: Boolean = true
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        createNotificationChannels(context)

        val channelId = if (soundEnabled) CHANNEL_CLASS_START_ID else CHANNEL_CLASS_START_SILENT_ID
        val className = subject.ifBlank { "Your class" }
        val title = "Your class is starting now!"
        val mainBody = "$className is starting. Join your class now."

        val contextLine = buildList {
            if (localTimeDisplay.isNotBlank()) add(localTimeDisplay)
            val meetingUrl = validMeetingUrl(locationOrUrl)
            if (meetingUrl != null) {
                add("Online meeting link attached")
            } else if (locationOrUrl.isNotBlank()) {
                add(locationOrUrl)
            }
        }.joinToString(" • ")

        val fullBody = if (contextLine.isBlank()) mainBody else "$mainBody\n$contextLine"

        val builder = baseClassNotificationBuilder(
            context = context,
            channelId = channelId,
            notificationId = notificationId,
            title = title,
            body = mainBody,
            bigBody = fullBody,
            soundEnabled = soundEnabled,
            vibrate = vibrate,
            vibrationPattern = CLASS_START_VIBRATION_PATTERN,
            soundUri = rawSoundUri(context, "unibl_class_start"),
            meetingUrl = validMeetingUrl(locationOrUrl),
            priority = NotificationCompat.PRIORITY_HIGH
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, builder.build())
        return true
    }

    /**
     * General reminder helper retained for LRM live tests and instant previews.
     */
    fun sendClassReminderNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        vibrate: Boolean = true,
        meetingUrl: String? = null
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannels(context)

        val builder = baseClassNotificationBuilder(
            context = context,
            channelId = CHANNEL_CLASS_START_ID,
            notificationId = notificationId,
            title = "UNIBL Sync • $title",
            body = message,
            soundEnabled = true,
            vibrate = vibrate,
            vibrationPattern = CLASS_START_VIBRATION_PATTERN,
            soundUri = rawSoundUri(context, "unibl_class_start"),
            meetingUrl = validMeetingUrl(meetingUrl.orEmpty()),
            priority = NotificationCompat.PRIORITY_HIGH
        )

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    private fun baseClassNotificationBuilder(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        bigBody: String = body,
        soundEnabled: Boolean,
        vibrate: Boolean,
        vibrationPattern: LongArray,
        soundUri: Uri?,
        meetingUrl: String?,
        priority: Int
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigBody))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        if (meetingUrl != null) {
            val joinIntent = Intent(Intent.ACTION_VIEW, Uri.parse(meetingUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val joinPendingIntent = PendingIntent.getActivity(
                context,
                notificationId xor meetingUrl.hashCode(),
                joinIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Join Class", joinPendingIntent)
        }

        if (!soundEnabled && !vibrate) {
            builder.setSilent(true)
        } else {
            if (!soundEnabled) {
                builder.setSound(null)
            } else if (soundUri != null) {
                builder.setSound(soundUri)
            }
            if (vibrate) {
                builder.setVibrate(vibrationPattern)
            } else {
                builder.setVibrate(longArrayOf(0L))
            }
        }

        return builder
    }

    private fun validMeetingUrl(locationOrUrl: String): String? =
        locationOrUrl.trim().takeIf {
            it.startsWith("http://", ignoreCase = true) ||
                it.startsWith("https://", ignoreCase = true)
        }

    fun sendAppUpdateNotification(context: Context, version: String, downloadUrl: String) {
        if (!hasNotificationPermission(context)) return

        createNotificationChannels(context)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            version.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val message = "Updated to your APK: version $version. Tap to download."
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("UNIBL Sync updated")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(version.hashCode(), notification)
    }

    fun sendAnnouncementNotification(context: Context, announcementId: String, title: String, message: String) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannels(context)
        val stableId = announcementId.hashCode() and Int.MAX_VALUE
        val contentIntent = PendingIntent.getActivity(
            context, stableId, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val readIntent = PendingIntent.getBroadcast(
            context,
            stableId,
            Intent(context, AnnouncementActionReceiver::class.java).putExtra(AnnouncementActionReceiver.EXTRA_ID, announcementId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("UNIBL Sync • $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Read", readIntent)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(stableId, notification)
    }
}

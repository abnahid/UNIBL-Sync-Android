package com.abnahid.uniblsync.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioAttributes
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.abnahid.uniblsync.MainActivity
import com.abnahid.uniblsync.R

object NotificationHelper {
    // Versioned IDs allow the new sound policy without deleting existing user channels.
    const val CHANNEL_REMINDERS_ID = "unibl_sync_reminders_v2"
    const val CHANNEL_UPDATES_ID = "unibl_sync_updates_v2"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS_ID,
                "UNIBL Sync class reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you before your classes start with customized lead time"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(customSoundUri(context) ?: android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
            }

            val updatesChannel = NotificationChannel(
                CHANNEL_UPDATES_ID,
                "UNIBL Sync announcements & updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for rescheduled, cancelled, or newly published classes"
                setSound(customSoundUri(context) ?: android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
            }

            notificationManager.createNotificationChannel(reminderChannel)
            notificationManager.createNotificationChannel(updatesChannel)
        }
    }

    private fun customSoundUri(context: Context): Uri? {
        // Drop unibl_notification.wav into res/raw when the final sound asset is supplied.
        val resourceId = context.resources.getIdentifier("unibl_notification", "raw", context.packageName)
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

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("UNIBL Sync • $title")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // A Join action is only added for a real http(s) meeting link.
        val isRealUrl = meetingUrl != null &&
            (meetingUrl.startsWith("http://") || meetingUrl.startsWith("https://"))
        if (isRealUrl) {
            val joinIntent = Intent(Intent.ACTION_VIEW, Uri.parse(meetingUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val joinPendingIntent = PendingIntent.getActivity(
                context,
                notificationId xor (meetingUrl?.hashCode() ?: 0),
                joinIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Join now", joinPendingIntent)
        }

        if (vibrate) {
            builder.setVibrate(longArrayOf(0, 500, 200, 500))
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
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

package com.abnahid.uniblsync.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fired by AlarmManager at a class reminder time. The alarm is scheduled by the
 * OS so this runs even after the app process was killed, as long as Android
 * permits delivery (note: force-stopped apps stop receiving alarms).
 */
class ClassReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CLASS_REMINDER) return

        val reminderKey = intent.getStringExtra(EXTRA_REMINDER_KEY)
            ?: intent.data?.pathSegments?.lastOrNull().orEmpty()

        val subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty()
        val classType = intent.getStringExtra(EXTRA_CLASS_TYPE).orEmpty()
        val groupName = intent.getStringExtra(EXTRA_GROUP).orEmpty()
        val sourceStartTime = intent.getStringExtra(EXTRA_START_TIME).orEmpty()
        val locationOrUrl = intent.getStringExtra(EXTRA_LOCATION_OR_URL).orEmpty()
        val leadMinutes = intent.getIntExtra(EXTRA_LEAD_MINUTES, 0)
        val isAlarmEnabled = intent.getBooleanExtra(EXTRA_ALARM_ENABLED, true)
        val vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)

        val detail = buildList {
            add("Starts in $leadMinutes min • ${DisplayFormatter.label(classType)}".trim())
            if (!groupName.isBlankOrAll()) add(DisplayFormatter.group(groupName))
            sourceStartTime.takeIf { it.isNotBlank() }?.let { add("University time $it") }
            locationOrUrl.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { add(it) }
        }

        NotificationHelper.sendClassReminderNotification(
            context = context,
            notificationId = ReminderScheduler.requestCodeFor(reminderKey.ifBlank { subject }),
            title = subject.ifBlank { "Class reminder" },
            message = detail.joinToString(" • ").ifBlank { "Your class is about to start." },
            vibrate = vibrate,
            meetingUrl = locationOrUrl.takeIf {
                it.startsWith("http://") || it.startsWith("https://")
            }
        )
    }

    private fun String?.isBlankOrAll(): Boolean =
        isNullOrBlank() || equals("All", ignoreCase = true)

    companion object {
        const val ACTION_CLASS_REMINDER = "com.abnahid.uniblsync.ACTION_CLASS_REMINDER"

        const val EXTRA_REMINDER_KEY = "reminderKey"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_CLASS_TYPE = "classType"
        const val EXTRA_GROUP = "groupName"
        const val EXTRA_START_TIME = "sourceStartTime"
        const val EXTRA_END_TIME = "sourceEndTime"
        const val EXTRA_MODE = "attendanceMode"
        const val EXTRA_LOCATION_OR_URL = "locationOrUrl"
        const val EXTRA_LEAD_MINUTES = "leadMinutes"
        const val EXTRA_ALARM_ENABLED = "alarmEnabled"
        const val EXTRA_VIBRATE = "vibrate"
    }
}

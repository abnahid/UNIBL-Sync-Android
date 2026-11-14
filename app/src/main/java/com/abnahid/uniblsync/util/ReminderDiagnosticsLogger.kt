package com.abnahid.uniblsync.util

import android.content.Context
import android.util.Log
import com.abnahid.uniblsync.data.local.AppDatabase
import com.abnahid.uniblsync.data.model.ReminderDiagnosticLogEntity

/**
 * Structured logging helper for class reminder scheduling, permission checks,
 * catch-up recovery, receiver execution, and notification delivery.
 *
 * Avoids logging sensitive student information.
 */
object ReminderDiagnosticsLogger {
    private const val TAG = "UniblReminder"

    suspend fun log(
        context: Context,
        database: AppDatabase,
        eventType: String,
        classId: String,
        subject: String,
        stage: String,
        sourceTimezone: String,
        localClassTime: String,
        advanceTriggerMs: Long,
        finalTriggerMs: Long,
        resultStatus: String,
        message: String,
        repeatCount: Int = 0,
        dismissed: Boolean = false
    ) {
        val exactGranted = ReminderScheduler.canScheduleExactAlarms(context)
        val notifGranted = NotificationHelper.hasNotificationPermission(context)

        Log.i(
            TAG,
            "event=$eventType classId=$classId subject=$subject stage=$stage " +
                "srcTz=$sourceTimezone localTime=$localClassTime " +
                "advMs=$advanceTriggerMs finalMs=$finalTriggerMs " +
                "result=$resultStatus exactPerm=$exactGranted notifPerm=$notifGranted " +
                "repeat=$repeatCount dismissed=$dismissed msg=$message"
        )

        runCatching {
            val dao = database.reminderDiagnosticLogDao()
            dao.insert(
                ReminderDiagnosticLogEntity(
                    timestampEpochMs = System.currentTimeMillis(),
                    eventType = eventType,
                    classId = classId,
                    subject = subject,
                    stage = stage,
                    sourceTimezone = sourceTimezone,
                    localClassTime = localClassTime,
                    advanceTriggerMs = advanceTriggerMs,
                    finalTriggerMs = finalTriggerMs,
                    resultStatus = resultStatus,
                    exactAlarmGranted = exactGranted,
                    notificationPermissionGranted = notifGranted,
                    repeatCount = repeatCount,
                    dismissed = dismissed,
                    message = message
                )
            )
            dao.trimToLatest(150)
        }
    }
}

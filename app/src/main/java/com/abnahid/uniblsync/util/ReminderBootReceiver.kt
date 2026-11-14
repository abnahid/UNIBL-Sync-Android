package com.abnahid.uniblsync.util

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abnahid.uniblsync.data.local.AppDatabase
import com.abnahid.uniblsync.data.schedule.ReminderReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores and reconciles two-stage reminders after device reboot, app update,
 * system time or timezone changes, and exact-alarm permission grants.
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val validTriggers = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
        if (intent.action !in validTriggers) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(appContext)
                ReminderReconciler.reconcileArmedAlarms(appContext, database)
            } catch (_: Exception) {
                // Never crash on system broadcasts; reminders reconcile again on next launch.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

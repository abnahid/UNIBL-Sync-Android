package com.abnahid.uniblsync.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abnahid.uniblsync.data.local.AppDatabase
import com.abnahid.uniblsync.data.schedule.ReminderReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores reminders after a device reboot (and after system time or timezone
 * changes). Reads the persisted scheduled reminders, drops expired ones and
 * re-arms the rest with AlarmManager so reminders survive process death and
 * restarts without ever needing the app open.
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val validTriggers = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        if (intent.action !in validTriggers) return

        val appContext = context.applicationContext
        // goAsync keeps delivery alive while the (small) DB read and alarm
        // re-arming finish on a background thread.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(appContext)
                ReminderReconciler.reconcileArmedAlarms(appContext, database)
            } catch (_: Exception) {
                // Never crash on system broadcasts; reminders are re-armed again
                // on the next app launch if anything went wrong here.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

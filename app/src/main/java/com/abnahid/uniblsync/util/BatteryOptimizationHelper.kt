package com.abnahid.uniblsync.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

/**
 * Manages battery optimization status, Doze mode exemptions, and OEM-specific
 * background management settings (AutoStart, App Protection) to ensure offline
 * reminders trigger reliably on all devices.
 */
object BatteryOptimizationHelper {

    /**
     * Checks if the app is currently exempt from Android's Battery Optimization (Doze mode).
     * Returns true on Android versions prior to Marshmallow (API 23) or when whitelisted.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Launches the system prompt requesting to ignore battery optimizations for this app.
     * Falls back to the general battery optimization list if direct intent is unavailable.
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false

        // Attempt 1: Direct dialog prompt via ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        } catch (_: Exception) {
            // Permission or resolution failed, try fallback
        }

        // Attempt 2: Settings list of all apps
        try {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (fallbackIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(fallbackIntent)
                return true
            }
        } catch (_: Exception) {
            // Fallback failed
        }

        // Attempt 3: App info page where user can tap "Battery" -> "Unrestricted"
        return openAppDetailsSettings(context)
    }

    /**
     * Opens the app's system details page where users can manually set battery usage to "Unrestricted".
     */
    fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns the device manufacturer in lowercase (e.g., "xiaomi", "samsung", "huawei", "oppo").
     */
    fun getManufacturer(): String = Build.MANUFACTURER.lowercase(Locale.ROOT)

    /**
     * Checks if the device is known to have aggressive third-party battery management / killer policies.
     */
    fun isAggressiveOemDevice(): Boolean {
        val manufacturer = getManufacturer()
        return manufacturer.contains("xiaomi") ||
                manufacturer.contains("redmi") ||
                manufacturer.contains("huawei") ||
                manufacturer.contains("honor") ||
                manufacturer.contains("oppo") ||
                manufacturer.contains("vivo") ||
                manufacturer.contains("realme") ||
                manufacturer.contains("oneplus") ||
                manufacturer.contains("samsung")
    }

    /**
     * Attempts to open manufacturer-specific background manager or "AutoStart" settings.
     * Useful for Xiaomi, Huawei, Oppo, Vivo, OnePlus, Samsung.
     */
    fun openManufacturerSettings(context: Context): Boolean {
        val manufacturer = getManufacturer()

        val intents = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                )
            )

            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                    )
                )
            )

            manufacturer.contains("oppo") || manufacturer.contains("realme") -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"
                    )
                )
            )

            manufacturer.contains("vivo") -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                )
            )

            manufacturer.contains("oneplus") -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                )
            )

            manufacturer.contains("samsung") -> listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.sm",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                )
            )

            else -> emptyList()
        }

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {
                // Try next candidate
            }
        }

        // Default fallback to app details if no OEM screen resolved
        return openAppDetailsSettings(context)
    }
}

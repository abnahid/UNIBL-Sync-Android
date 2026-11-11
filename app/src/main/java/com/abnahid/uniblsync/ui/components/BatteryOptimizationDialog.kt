package com.abnahid.uniblsync.ui.components

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abnahid.uniblsync.ui.theme.AmberWarning
import com.abnahid.uniblsync.ui.theme.EmeraldSuccess
import com.abnahid.uniblsync.util.BatteryOptimizationHelper
import com.abnahid.uniblsync.util.NotificationHelper
import com.abnahid.uniblsync.util.ReminderScheduler

@Composable
fun BatteryOptimizationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var hasNotificationPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    var hasExactAlarmPermission by remember {
        mutableStateOf(ReminderScheduler.canScheduleExactAlarms(context))
    }
    var isBatteryOptimizedIgnored by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    val isAggressiveOem = remember { BatteryOptimizationHelper.isAggressiveOemDevice() }
    val manufacturer = remember { BatteryOptimizationHelper.getManufacturer().replaceFirstChar { it.uppercase() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("battery_optimization_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Reliable Offline Reminders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "To ensure reminders ring on time even when the device is asleep, offline, or battery saver is active, configure these Android system settings:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Notification Permission
                PermissionStepItem(
                    stepNumber = "1",
                    title = "Notification Permission",
                    description = if (hasNotificationPermission) "Allowed • Reminders can display in status bar"
                    else "Required so reminder banners and sounds can be delivered",
                    isGranted = hasNotificationPermission,
                    icon = Icons.Default.Notifications,
                    actionText = "Allow",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Exact Alarms
                PermissionStepItem(
                    stepNumber = "2",
                    title = "Exact Alarms (Android 12+)",
                    description = if (hasExactAlarmPermission) "Allowed • Alarms will ring precisely at class lead time"
                    else "Prevents Android from grouping or deferring your class reminder",
                    isGranted = hasExactAlarmPermission,
                    icon = Icons.Default.Alarm,
                    actionText = "Grant",
                    onAction = {
                        ReminderScheduler.openExactAlarmSettings(context)
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Battery Optimization / Doze exemption
                PermissionStepItem(
                    stepNumber = "3",
                    title = "Disable Battery Saver",
                    description = if (isBatteryOptimizedIgnored) "Unrestricted • System Doze will not kill scheduled reminders"
                    else "Allow UNIBL Sync to run in the background without battery limits",
                    isGranted = isBatteryOptimizedIgnored,
                    icon = Icons.Default.Power,
                    actionText = "Exempt",
                    onAction = {
                        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                    }
                )

                // 4. OEM AutoStart Tips (Xiaomi, Samsung, Huawei, etc.)
                if (isAggressiveOem) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "$manufacturer Device Notice",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$manufacturer aggressively closes inactive apps. Tap below to enable AutoStart and keep UNIBL Sync alive.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { BatteryOptimizationHelper.openManufacturerSettings(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open $manufacturer Settings")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("battery_dialog_done_button")
            ) {
                Text("Got It")
            }
        }
    )
}

@Composable
private fun PermissionStepItem(
    stepNumber: String,
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    actionText: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) EmeraldSuccess.copy(alpha = 0.10f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) EmeraldSuccess.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else icon,
                    contentDescription = null,
                    tint = if (isGranted) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isGranted) EmeraldSuccess else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAction,
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(actionText, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

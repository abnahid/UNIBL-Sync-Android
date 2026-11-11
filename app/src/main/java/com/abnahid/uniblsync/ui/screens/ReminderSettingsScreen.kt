package com.abnahid.uniblsync.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abnahid.uniblsync.ui.LrmTestUiState
import com.abnahid.uniblsync.ui.components.BatteryOptimizationDialog
import com.abnahid.uniblsync.ui.theme.EmeraldSuccess
import com.abnahid.uniblsync.ui.theme.RoseError
import com.abnahid.uniblsync.ui.theme.AmberWarning
import com.abnahid.uniblsync.util.BatteryOptimizationHelper
import com.abnahid.uniblsync.util.NotificationHelper
import com.abnahid.uniblsync.util.ReminderScheduler
import com.abnahid.uniblsync.util.SoundPreviewPlayer
import com.abnahid.uniblsync.util.TimezoneHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderSettingsScreen(
    selectedTimezone: String,
    globalLeadMinutes: Int,
    selectedTone: String,
    isAlarmEnabled: Boolean,
    isVibrateEnabled: Boolean,
    isSoundPlaying: Boolean,
    lrmTestState: LrmTestUiState = LrmTestUiState(),
    onLeadMinutesChange: (Int) -> Unit,
    onToneChange: (String) -> Unit,
    onAlarmToggle: (Boolean) -> Unit,
    onVibrateToggle: (Boolean) -> Unit,
    onPlayTone: (String) -> Unit,
    onStopTone: () -> Unit,
    onStartLrmTest: (Int) -> Unit = {},
    onCancelLrmTest: () -> Unit = {},
    onSetLrmDuration: (Int) -> Unit = {},
    onSendTestReminder: () -> Unit,
    onChangeTimezoneClick: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }
    var isBatteryOptimizedIgnored by remember { mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) }
    var showBatteryOptimizationGuide by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val leadOptions = listOf(5, 10, 15, 20, 30, 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("reminder_settings_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Smart Reminders & Sound Studio",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Configure how and when you want to be alerted before classes begin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Notification Permission Banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (hasPermission) EmeraldSuccess.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (hasPermission) EmeraldSuccess else RoseError,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (hasPermission) "Notifications Enabled" else "Notification Permission Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (hasPermission) EmeraldSuccess else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (hasPermission) "Ready to receive class reminders" else "Grant permission to receive exact class alarm alerts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!hasPermission) {
                    Button(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        modifier = Modifier.testTag("request_permission_button")
                    ) {
                        Text("Allow")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Exact Alarm Capability Status (Android 12+ SCHEDULE_EXACT_ALARM)
        val exactAlarmsAvailable = remember { ReminderScheduler.canScheduleExactAlarms(context) }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (exactAlarmsAvailable) EmeraldSuccess.copy(alpha = 0.12f)
                else AmberWarning.copy(alpha = 0.12f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = if (exactAlarmsAvailable) EmeraldSuccess else AmberWarning,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (exactAlarmsAvailable) "Exact alarms available"
                        else "Exact alarms restricted",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (exactAlarmsAvailable)
                            "Reminders will ring at the exact minute you choose."
                        else
                            "Reminders may be delayed a few minutes by Android. Allow exact alarms for on-the-minute delivery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!exactAlarmsAvailable) {
                    Button(
                        onClick = { ReminderScheduler.openExactAlarmSettings(context) },
                        modifier = Modifier.testTag("grant_exact_alarm_button")
                    ) {
                        Text("Allow")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Battery Optimization (Doze Mode & Power Saving) Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isBatteryOptimizedIgnored) EmeraldSuccess.copy(alpha = 0.12f)
                else AmberWarning.copy(alpha = 0.12f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("battery_optimization_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Power,
                    contentDescription = null,
                    tint = if (isBatteryOptimizedIgnored) EmeraldSuccess else AmberWarning,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isBatteryOptimizedIgnored) "Battery Saver: Unrestricted"
                        else "Battery Optimization: Active",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isBatteryOptimizedIgnored)
                            "UNIBL Sync can wake the device and trigger offline alarms on time."
                        else
                            "Android Doze mode may kill alarms when phone is idle or sleeping. Tap Allow to exempt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isBatteryOptimizedIgnored) {
                    Button(
                        onClick = {
                            BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                        },
                        modifier = Modifier.testTag("grant_battery_optimization_button")
                    ) {
                        Text("Exempt")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick checklist banner for all offline reminder requirements
        OutlinedButton(
            onClick = { showBatteryOptimizationGuide = true },
            modifier = Modifier.fillMaxWidth().testTag("open_checklist_button")
        ) {
            Icon(imageVector = Icons.Default.Rule, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Offline Reminders Setup Checklist")
        }

        if (showBatteryOptimizationGuide) {
            BatteryOptimizationDialog(
                onDismiss = {
                    showBatteryOptimizationGuide = false
                    isBatteryOptimizedIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                    hasPermission = NotificationHelper.hasNotificationPermission(context)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Timezone Preference Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Active Timezone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${TimezoneHelper.getTimezoneShortLabel(selectedTimezone)} ($selectedTimezone)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                OutlinedButton(onClick = onChangeTimezoneClick) {
                    Text("Change")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Global Lead Minutes Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Default Reminder Time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "Applies automatically to newly published classes unless individually modified.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    leadOptions.forEach { mins ->
                        FilterChip(
                            selected = globalLeadMinutes == mins,
                            onClick = { onLeadMinutesChange(mins) },
                            label = { Text("${mins}m before") }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sound Studio & Available Tones
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Alarm Sound & Tones", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }

                    Switch(
                        checked = isAlarmEnabled,
                        onCheckedChange = onAlarmToggle
                    )
                }

                Text(
                    text = "Preview original sound tones crafted for class start alerts:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                SoundPreviewPlayer.AVAILABLE_TONES.forEach { tone ->
                    val isSelected = selectedTone == tone
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                            .clickable { onToneChange(tone) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onToneChange(tone) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tone,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isSoundPlaying) onStopTone() else onPlayTone(tone)
                            },
                            modifier = Modifier.testTag("preview_tone_${tone.lowercase().replace(" ", "_")}")
                        ) {
                            Icon(
                                imageVector = if (isSoundPlaying && isSelected) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Play/Stop",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Vibration Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vibration", style = MaterialTheme.typography.bodyMedium)
                    }

                    Switch(
                        checked = isVibrateEnabled,
                        onCheckedChange = onVibrateToggle
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // LRM (Live Reminder Module) Setting Area
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (lrmTestState.isActive)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lrm_setting_area")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (lrmTestState.isActive) Icons.Default.Timer else Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "LRM • Live Reminder Tester",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Test real device wake-up & offline alarm delivery",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (lrmTestState.isActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AmberWarning.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "TEST RUNNING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AmberWarning
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (lrmTestState.isActive) {
                    // Active countdown state
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Alarm Scheduled To Ring In",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val mins = lrmTestState.remainingSeconds / 60
                            val secs = lrmTestState.remainingSeconds % 60
                            Text(
                                text = "%02d:%02d".format(mins, secs),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "👉 Leave or close the app now! Lock your screen. Once 00:00 is reached, the device will wake up and ring on time.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onCancelLrmTest,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lrm_cancel_button")
                    ) {
                        Icon(imageVector = Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel Test Alarm")
                    }
                } else {
                    // Inactive configuration state
                    Text(
                        text = "Set test reminder countdown time:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val testDurations = listOf(1, 2, 5)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        testDurations.forEach { mins ->
                            FilterChip(
                                selected = lrmTestState.durationMinutes == mins,
                                onClick = { onSetLrmDuration(mins) },
                                label = { Text("$mins minute" + if (mins > 1) "s" else "") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { onStartLrmTest(lrmTestState.durationMinutes) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lrm_start_test_button")
                    ) {
                        Icon(imageVector = Icons.Default.AlarmOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Activate ${lrmTestState.durationMinutes}-Minute Test Reminder")
                    }

                    if (lrmTestState.lastDeliveredEpochMs > 0L) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val timeStr = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
                            .format(Date(lrmTestState.lastDeliveredEpochMs))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmeraldSuccess.copy(alpha = 0.12f))
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Last test passed at $timeStr! Background alarms verified.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldSuccess,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Instant Preview Ping Button
        OutlinedButton(
            onClick = onSendTestReminder,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("test_reminder_button")
        ) {
            Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Instant Notification Sound Preview")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

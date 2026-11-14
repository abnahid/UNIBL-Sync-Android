package com.abnahid.uniblsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abnahid.uniblsync.data.model.ClassReminderStateEntity
import com.abnahid.uniblsync.data.model.ReminderDiagnosticLogEntity
import com.abnahid.uniblsync.ui.theme.AmberWarning
import com.abnahid.uniblsync.ui.theme.EmeraldSuccess
import com.abnahid.uniblsync.ui.theme.RoseError
import com.abnahid.uniblsync.util.BatteryOptimizationHelper
import com.abnahid.uniblsync.util.NotificationHelper
import com.abnahid.uniblsync.util.ReminderScheduler
import com.abnahid.uniblsync.util.TimezoneHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReminderDiagnosticsDialog(
    selectedTimezone: String,
    reminderStates: List<ClassReminderStateEntity>,
    diagnosticLogs: List<ReminderDiagnosticLogEntity>,
    onReconcileNow: () -> Unit,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val notifGranted = NotificationHelper.hasNotificationPermission(context)
    val exactAlarmGranted = ReminderScheduler.canScheduleExactAlarms(context)
    val batteryExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "Reminder Diagnostics",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Reminder Diagnostics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Permission summary pills
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PermissionStatusRow("Notifications", notifGranted)
                        PermissionStatusRow("Exact Alarms", exactAlarmGranted)
                        PermissionStatusRow("Battery Unrestricted", batteryExempt)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("States (${reminderStates.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Logs (${diagnosticLogs.size})") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedTab == 0) {
                        if (reminderStates.isEmpty()) {
                            item {
                                Text(
                                    text = "No two-stage reminder states recorded yet. Tap Reconcile to scan upcoming classes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        } else {
                            items(reminderStates, key = { it.occurrenceKey }) { state ->
                                ReminderStateDiagnosticItem(state, selectedTimezone)
                            }
                        }
                    } else {
                        if (diagnosticLogs.isEmpty()) {
                            item {
                                Text(
                                    text = "No reminder events logged yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        } else {
                            items(diagnosticLogs, key = { it.id }) { log ->
                                ReminderLogItem(log)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReconcileNow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("diagnostics_reconcile_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reconcile")
                    }
                    OutlinedButton(
                        onClick = onClearLogs,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("diagnostics_clear_logs_button")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Logs")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("diagnostics_close_button")
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun PermissionStatusRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (granted) EmeraldSuccess else AmberWarning,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (granted) "GRANTED" else "RESTRICTED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (granted) EmeraldSuccess else RoseError
            )
        }
    }
}

@Composable
private fun ReminderStateDiagnosticItem(
    state: ClassReminderStateEntity,
    userTimezone: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.subject,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.classDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Start: ${TimezoneHelper.formatToLocalTime(state.classStartEpochMs, userTimezone)} (${TimezoneHelper.getTimezoneShortLabel(userTimezone)}) • Src: ${state.sourceTimezone}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            Text(
                text = "Stage 1 (Advance): ${state.advanceScheduledStatus} • Delivered=${state.advanceDelivered}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "Stage 2 (Final): ${state.finalScheduledStatus} • Delivered=${state.finalDelivered}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "RepeatCount=${state.repeatCount} • Dismissed=${state.dismissed}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReminderLogItem(log: ReminderDiagnosticLogEntity) {
    val timeText = remember(log.timestampEpochMs) {
        SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(log.timestampEpochMs))
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${log.eventType} • ${log.stage}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${log.subject} (${log.localClassTime})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${log.resultStatus}: ${log.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

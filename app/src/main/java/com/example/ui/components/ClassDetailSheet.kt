package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.ClassEntity
import com.example.data.model.ReminderPreferenceEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.SkyInfo
import com.example.util.IcsExporter
import com.example.util.MeetingLauncher
import com.example.util.TimezoneHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClassDetailSheet(
    classEntity: ClassEntity,
    userTimezone: String,
    currentTimeMs: Long,
    preference: ReminderPreferenceEntity?,
    sheetState: SheetState,
    onSaveReminder: (leadMinutes: Int, isAlarm: Boolean, tone: String, vibrate: Boolean) -> Unit,
    onPlayTone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var leadMinutes by remember { mutableIntStateOf(preference?.leadMinutes ?: 20) }
    var alarmEnabled by remember { mutableStateOf(preference?.isAlarmEnabled ?: true) }
    var vibrateEnabled by remember { mutableStateOf(preference?.vibrate ?: true) }
    val tone = preference?.soundTone ?: "Gentle Chime"

    val reminderOptions = listOf(5, 10, 15, 20, 30, 60)

    val (bosniaTimeStr, localTimeStr) = TimezoneHelper.getDualTimeDisplay(
        classEntity.utcStartEpochMs,
        classEntity.sourceTimezone,
        userTimezone
    )
    val localEndFormatted = TimezoneHelper.formatToLocalTime(classEntity.utcEndEpochMs, userTimezone)
    val localDateFormatted = TimezoneHelper.formatToLocalDate(classEntity.utcStartEpochMs, userTimezone)

    val isUpcoming = classEntity.utcStartEpochMs > currentTimeMs
    val isOngoing = classEntity.utcStartEpochMs <= currentTimeMs && classEntity.utcEndEpochMs >= currentTimeMs
    val isCancelled = classEntity.status == ClassEntity.STATUS_CANCELLED
    val isRescheduled = classEntity.status == ClassEntity.STATUS_RESCHEDULED

    val isTeams = classEntity.platform == ClassEntity.PLATFORM_TEAMS || classEntity.locationOrUrl.contains("teams.microsoft.com")
    val teamsBrandColor = Color(0xFF5B5FC7)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Status and Mode Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                isTeams -> teamsBrandColor.copy(alpha = 0.15f)
                                classEntity.attendanceMode.contains("Online") -> EmeraldSuccess.copy(alpha = 0.15f)
                                classEntity.attendanceMode.contains("In-Person") -> SkyInfo.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isTeams) "Microsoft Teams (${classEntity.teamsChannel})" else classEntity.attendanceMode,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isTeams) teamsBrandColor else if (classEntity.attendanceMode.contains("Online")) EmeraldSuccess else SkyInfo
                    )
                }

                // Status Pill
                val statusBg = when {
                    isCancelled -> RoseError.copy(alpha = 0.15f)
                    isRescheduled -> AmberWarning.copy(alpha = 0.15f)
                    isOngoing -> EmeraldSuccess.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
                val statusColor = when {
                    isCancelled -> RoseError
                    isRescheduled -> AmberWarning
                    isOngoing -> EmeraldSuccess
                    else -> MaterialTheme.colorScheme.secondary
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when {
                            isCancelled -> "CANCELLED"
                            isRescheduled -> "RESCHEDULED"
                            isOngoing -> "LIVE NOW"
                            else -> classEntity.status
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subject and Type
            Text(
                text = classEntity.subject,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "${classEntity.classType} • Group: ${classEntity.groupName} • Platform: ${classEntity.platform}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            if (classEntity.instructor.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = classEntity.instructor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time & Timezone Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = localDateFormatted,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Your Local Time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$localTimeStr - $localEndFormatted",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "University (Source)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${classEntity.sourceStartTime} - ${classEntity.sourceEndTime}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = classEntity.sourceTimezone,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Meeting or Classroom Location Card
            Spacer(modifier = Modifier.height(12.dp))
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (isTeams || classEntity.locationOrUrl.startsWith("http")) Icons.Default.Link else Icons.Default.Place,
                            contentDescription = null,
                            tint = if (isTeams) teamsBrandColor else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isTeams) "Microsoft Teams Channel" else "Location / Room",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isTeams) "#${classEntity.teamsChannel} Channel" else classEntity.locationOrUrl.ifBlank { "Online Meeting" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isTeams || classEntity.locationOrUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                MeetingLauncher.openMeeting(
                                    context = context,
                                    url = classEntity.locationOrUrl,
                                    platform = classEntity.platform,
                                    subject = classEntity.subject
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTeams) teamsBrandColor else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("join_class_button")
                        ) {
                            Text(if (isTeams) "Open Teams" else "Join")
                        }
                    }
                }
            }

            if (classEntity.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Notes & Instructions",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = classEntity.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Reminders Section (Only for active upcoming/ongoing classes)
            if (!isCancelled) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Smart Reminder & Alarm",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Notify me before this class starts in my local time:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    reminderOptions.forEach { mins ->
                        FilterChip(
                            selected = leadMinutes == mins,
                            onClick = {
                                leadMinutes = mins
                                onSaveReminder(mins, alarmEnabled, tone, vibrateEnabled)
                            },
                            label = { Text("${mins}m before") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Alarm Sound ($tone)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { onPlayTone(tone) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Play", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test")
                        }
                        Switch(
                            checked = alarmEnabled,
                            onCheckedChange = {
                                alarmEnabled = it
                                onSaveReminder(leadMinutes, it, tone, vibrateEnabled)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        IcsExporter.shareIcsFile(context, listOf(classEntity))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_single_ics_button")
                ) {
                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export .ICS")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

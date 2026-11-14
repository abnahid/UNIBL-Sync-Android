package com.abnahid.uniblsync.ui.components

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.material3.TextButton
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
import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.data.model.ReminderPreferenceEntity
import com.abnahid.uniblsync.ui.theme.AmberWarning
import com.abnahid.uniblsync.ui.theme.EmeraldSuccess
import com.abnahid.uniblsync.ui.theme.RoseError
import com.abnahid.uniblsync.ui.theme.SkyInfo
import com.abnahid.uniblsync.util.IcsExporter
import com.abnahid.uniblsync.util.MeetingLauncher
import com.abnahid.uniblsync.util.SoundPreviewPlayer
import com.abnahid.uniblsync.util.TimezoneHelper

private const val STARTING_SOON_WINDOW_MS = 30L * 60_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClassDetailSheet(
    classEntity: ClassEntity,
    userTimezone: String,
    currentTimeMs: Long,
    preference: ReminderPreferenceEntity?,
    defaultLeadMinutes: Int = 20,
    sheetState: SheetState,
    onSaveReminder: (leadMinutes: Int, isAlarm: Boolean, tone: String, vibrate: Boolean) -> Unit,
    onPlayTone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var leadMinutes by remember { mutableIntStateOf(preference?.leadMinutes ?: defaultLeadMinutes) }
    var alarmEnabled by remember { mutableStateOf(preference?.isAlarmEnabled ?: true) }
    var vibrateEnabled by remember { mutableStateOf(preference?.vibrate ?: true) }
    var selectedTone by remember { mutableStateOf(preference?.soundTone ?: "Gentle Chime") }
    var touched by remember { mutableStateOf(false) }

    val reminderOptions = listOf(5, 10, 15, 20, 30, 60)

    val localTimeStr = TimezoneHelper.formatToLocalTime(classEntity.utcStartEpochMs, userTimezone)
    val localEndFormatted = TimezoneHelper.formatToLocalTime(classEntity.utcEndEpochMs, userTimezone)
    val localDateFormatted = TimezoneHelper.formatToLocalDate(classEntity.utcStartEpochMs, userTimezone)

    // Status derived from the real timestamps, never from stored strings.
    val isCancelled = classEntity.status == ClassEntity.STATUS_CANCELLED
    val inProgress = !isCancelled && currentTimeMs >= classEntity.utcStartEpochMs && currentTimeMs <= classEntity.utcEndEpochMs
    val completed = !isCancelled && currentTimeMs > classEntity.utcEndEpochMs
    val startingSoon = !isCancelled && !inProgress &&
        currentTimeMs >= classEntity.utcStartEpochMs - STARTING_SOON_WINDOW_MS &&
        currentTimeMs < classEntity.utcStartEpochMs

    val meetingUrl = classEntity.locationOrUrl
        .takeIf { it.startsWith("http://") || it.startsWith("https://") }
    val isOnlineLike = classEntity.attendanceMode.contains("Online", ignoreCase = true) ||
        classEntity.platform.contains("Teams", ignoreCase = true)
    val hasRoom = classEntity.locationOrUrl.isNotBlank() && meetingUrl == null
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Status and Mode Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                meetingUrl != null -> MaterialTheme.colorScheme.secondaryContainer
                                isOnlineLike -> EmeraldSuccess.copy(alpha = 0.15f)
                                else -> SkyInfo.copy(alpha = 0.15f)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when {
                            meetingUrl != null && classEntity.platform.isNotBlank() ->
                                "${classEntity.platform} • Meeting link available"
                            meetingUrl != null -> "Meeting link available"
                            classEntity.attendanceMode.isNotBlank() -> classEntity.attendanceMode
                            else -> "Class"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            meetingUrl != null -> MaterialTheme.colorScheme.secondary
                            isOnlineLike -> EmeraldSuccess
                            else -> SkyInfo
                        }
                    )
                }

                val (statusBg, statusColor, statusText) = when {
                    isCancelled -> Triple(RoseError.copy(alpha = 0.15f), RoseError, "CANCELLED")
                    inProgress -> Triple(EmeraldSuccess.copy(alpha = 0.15f), EmeraldSuccess, "IN PROGRESS")
                    startingSoon -> Triple(AmberWarning.copy(alpha = 0.15f), AmberWarning, "STARTING SOON")
                    completed -> Triple(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        "COMPLETED"
                    )
                    else -> Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.secondary,
                        "SCHEDULED"
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
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

            val metaLine = buildList {
                add(classEntity.classType)
                add("Group: ${DisplayFormatterGroup(classEntity.groupName)}")
                if (classEntity.platform.isNotBlank()) add(classEntity.platform)
            }.joinToString(" • ")
            Text(
                text = metaLine,
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
                                text = "Local Time (${TimezoneHelper.getTimezoneShortLabel(userTimezone)})",
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
                                text = "University Time (${TimezoneHelper.getTimezoneShortLabel(classEntity.sourceTimezone)})",
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

            // Meeting or Classroom Location Card — only real, meaningful data.
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (meetingUrl != null) Icons.Default.Link else Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (meetingUrl != null) "Online meeting" else "Location",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            when {
                                meetingUrl != null -> Text(
                                    text = meetingUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                hasRoom -> Text(
                                    text = classEntity.locationOrUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                else -> Text(
                                    text = "Not provided yet",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    if (meetingUrl != null && !isCancelled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                MeetingLauncher.openMeeting(
                                    context = context,
                                    url = meetingUrl,
                                    platform = classEntity.platform,
                                    subject = classEntity.subject
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("join_class_button")
                        ) {
                            Text(if (classEntity.platform.contains("Teams", true)) "Open Teams" else "Join now")
                        }
                    }

                    if (meetingUrl == null && isOnlineLike && !isCancelled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The meeting link for this class has not been provided yet. Ask your professor or check the faculty notices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

            // Reminders Section (only while the class can still happen)
            if (!isCancelled && !completed) {
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
                                touched = true
                                onSaveReminder(mins, alarmEnabled, selectedTone, vibrateEnabled)
                            },
                            label = { Text("${mins}m before") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = reminderSavedText(classEntity, leadMinutes, userTimezone, alarmEnabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    "Alarm sound",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SoundPreviewPlayer.AVAILABLE_TONES) { tone ->
                        FilterChip(
                            selected = selectedTone == tone,
                            onClick = {
                                selectedTone = tone
                                touched = true
                                onSaveReminder(leadMinutes, alarmEnabled, tone, vibrateEnabled)
                            },
                            label = { Text(tone) }
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
                        Text("Alarm + sound", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { onPlayTone(selectedTone) },
                            modifier = Modifier.padding(end = 8.dp),
                            enabled = alarmEnabled
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Test sound",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test")
                        }
                        Switch(
                            checked = alarmEnabled,
                            onCheckedChange = {
                                alarmEnabled = it
                                touched = true
                                onSaveReminder(leadMinutes, it, selectedTone, vibrateEnabled)
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Vibration", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = vibrateEnabled,
                        onCheckedChange = {
                            vibrateEnabled = it
                            touched = true
                            onSaveReminder(leadMinutes, alarmEnabled, selectedTone, it)
                        }
                    )
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
                    onClick = {
                        // Confirm persistence of any changed reminder setting before closing.
                        if (touched || preference != null) {
                            onSaveReminder(leadMinutes, alarmEnabled, selectedTone, vibrateEnabled)
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("done_button")
                ) {
                    Text("Done")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun DisplayFormatterGroup(value: String): String =
    if (value.equals("All", ignoreCase = true)) "All Groups" else value

private fun reminderSavedText(
    classEntity: ClassEntity,
    leadMinutes: Int,
    userTimezone: String,
    alarmEnabled: Boolean
): String {
    val advanceMs = classEntity.utcStartEpochMs - leadMinutes * 60_000L
    val startMs = classEntity.utcStartEpochMs
    val now = System.currentTimeMillis()
    val startLocal = TimezoneHelper.formatToLocalTime(startMs, userTimezone)
    return when {
        alarmEnabled && advanceMs > now ->
            "Two-stage reminders armed • Advance at ${TimezoneHelper.formatToLocalTime(advanceMs, userTimezone)} & final start alert at $startLocal."
        alarmEnabled && startMs > now ->
            "Advance window passed • Catch-up advance active & final class-start alert armed for $startLocal."
        startMs > now ->
            "Advance sound muted • Final class-start alert remains armed for $startLocal."
        else ->
            "Class start time has already passed."
    }
}

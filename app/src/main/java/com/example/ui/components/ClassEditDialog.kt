package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassEntity
import com.example.util.MeetingLauncher
import com.example.util.TimezoneHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassEditDialog(
    initialClass: ClassEntity? = null,
    onSave: (
        subject: String,
        classType: String,
        instructor: String,
        date: String,
        startTime: String,
        endTime: String,
        timezone: String,
        group: String,
        mode: String,
        platform: String,
        teamsChannel: String,
        locationOrUrl: String,
        notes: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var subject by remember { mutableStateOf(initialClass?.subject ?: "") }
    var classType by remember { mutableStateOf(initialClass?.classType ?: "Lecture") }
    var instructor by remember { mutableStateOf(initialClass?.instructor ?: "") }
    var date by remember { mutableStateOf(initialClass?.sourceDate ?: "2026-10-26") }
    var startTime by remember { mutableStateOf(initialClass?.sourceStartTime ?: "05:00 PM") }
    var endTime by remember { mutableStateOf(initialClass?.sourceEndTime ?: "06:30 PM") }
    var timezone by remember { mutableStateOf(initialClass?.sourceTimezone ?: TimezoneHelper.BOSNIA_TIMEZONE) }
    var group by remember { mutableStateOf(initialClass?.groupName ?: "All") }
    var mode by remember { mutableStateOf(initialClass?.attendanceMode ?: ClassEntity.MODE_HYBRID) }
    var platform by remember { mutableStateOf(initialClass?.platform ?: ClassEntity.PLATFORM_TEAMS) }
    var teamsChannel by remember { mutableStateOf(initialClass?.teamsChannel ?: "General") }
    var locationOrUrl by remember { mutableStateOf(initialClass?.locationOrUrl ?: "") }
    var notes by remember { mutableStateOf(initialClass?.notes ?: "") }

    var classTypeExpanded by remember { mutableStateOf(false) }
    var groupExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    var platformExpanded by remember { mutableStateOf(false) }
    var channelExpanded by remember { mutableStateOf(false) }

    val classTypes = listOf("Lecture", "Practical", "Make up Lecture", "Make up practical", "Seminar")
    val groups = listOf("All", "Group 1", "Group 2", "Group 3", "Group 4")
    val modes = listOf(ClassEntity.MODE_HYBRID, ClassEntity.MODE_ONLINE, ClassEntity.MODE_IN_PERSON)
    val platforms = listOf(ClassEntity.PLATFORM_TEAMS, ClassEntity.PLATFORM_MEET, ClassEntity.PLATFORM_ZOOM, ClassEntity.PLATFORM_CLASSROOM)
    val channelOptions = listOf("General", "Lectures", "Practicals", "Discussion & Q&A")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialClass == null) "Schedule New Class" else "Edit Class Details")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = {
                        subject = it
                        if (locationOrUrl.isBlank() && platform == ClassEntity.PLATFORM_TEAMS) {
                            locationOrUrl = MeetingLauncher.getTeamsChannelUrl(it, teamsChannel)
                        }
                    },
                    label = { Text("Subject / Course Name *") },
                    placeholder = { Text("e.g. Anatomy, Medical Physics") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("class_subject_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Class Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = classTypeExpanded,
                    onExpandedChange = { classTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = classType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Class Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classTypeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = classTypeExpanded,
                        onDismissRequest = { classTypeExpanded = false }
                    ) {
                        classTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    classType = type
                                    classTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = instructor,
                    onValueChange = { instructor = it },
                    label = { Text("Instructor / Faculty") },
                    placeholder = { Text("e.g. Prof. Ahmed, Dr. Smith") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = timezone,
                        onValueChange = { timezone = it },
                        label = { Text("Source Timezone") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start (05:00 PM)") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End (06:30 PM)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Platform Dropdown (Microsoft Teams, etc.)
                ExposedDropdownMenuBox(
                    expanded = platformExpanded,
                    onExpandedChange = { platformExpanded = it }
                ) {
                    OutlinedTextField(
                        value = platform,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Online Platform / App") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = platformExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = platformExpanded,
                        onDismissRequest = { platformExpanded = false }
                    ) {
                        platforms.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    platform = p
                                    platformExpanded = false
                                    if (p == ClassEntity.PLATFORM_TEAMS && locationOrUrl.isBlank() && subject.isNotBlank()) {
                                        locationOrUrl = MeetingLauncher.getTeamsChannelUrl(subject, teamsChannel)
                                    }
                                }
                            )
                        }
                    }
                }

                if (platform == ClassEntity.PLATFORM_TEAMS) {
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = channelExpanded,
                        onExpandedChange = { channelExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = teamsChannel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Teams Channel") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = channelExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = channelExpanded,
                            onDismissRequest = { channelExpanded = false }
                        ) {
                            channelOptions.forEach { ch ->
                                DropdownMenuItem(
                                    text = { Text("#$ch") },
                                    onClick = {
                                        teamsChannel = ch
                                        channelExpanded = false
                                        if (subject.isNotBlank()) {
                                            locationOrUrl = MeetingLauncher.getTeamsChannelUrl(subject, ch)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Group Dropdown
                ExposedDropdownMenuBox(
                    expanded = groupExpanded,
                    onExpandedChange = { groupExpanded = it }
                ) {
                    OutlinedTextField(
                        value = group,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Group") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = groupExpanded,
                        onDismissRequest = { groupExpanded = false }
                    ) {
                        groups.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g) },
                                onClick = {
                                    group = g
                                    groupExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mode Dropdown
                ExposedDropdownMenuBox(
                    expanded = modeExpanded,
                    onExpandedChange = { modeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = mode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Attendance Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false }
                    ) {
                        modes.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    mode = m
                                    modeExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = locationOrUrl,
                    onValueChange = { locationOrUrl = it },
                    label = { Text("Microsoft Teams Link or Room Location") },
                    placeholder = { Text("https://teams.microsoft.com/l/... or Hall 3B") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Additional Notes for Students") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (subject.isNotBlank()) {
                        val finalUrl = if (locationOrUrl.isBlank() && platform == ClassEntity.PLATFORM_TEAMS) {
                            MeetingLauncher.getTeamsChannelUrl(subject, teamsChannel)
                        } else {
                            locationOrUrl
                        }
                        onSave(
                            subject,
                            classType,
                            instructor,
                            date,
                            startTime,
                            endTime,
                            timezone,
                            group,
                            mode,
                            platform,
                            teamsChannel,
                            finalUrl,
                            notes
                        )
                        onDismiss()
                    }
                },
                enabled = subject.isNotBlank(),
                modifier = Modifier.testTag("save_class_submit")
            ) {
                Text(if (initialClass == null) "Publish Class" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_class_edit")
            ) {
                Text("Cancel")
            }
        }
    )
}

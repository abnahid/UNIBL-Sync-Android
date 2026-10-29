package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClassEntity
import com.example.data.model.VisitorEntity
import com.example.ui.components.ClassEditDialog
import com.example.ui.components.RescheduleDialog
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.util.TimezoneHelper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    classes: List<ClassEntity>,
    auditLogs: List<AuditLogEntity>,
    visitors: List<VisitorEntity> = emptyList(),
    onCreateClass: (
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
    onUpdateClass: (ClassEntity) -> Unit,
    onRescheduleClass: (classEntity: ClassEntity, newDate: String, newStartTime: String, newEndTime: String, reason: String) -> Unit,
    onCancelClass: (classEntity: ClassEntity, reason: String) -> Unit,
    onDeleteClass: (ClassEntity) -> Unit,
    onSeedUniversitySchedule: () -> Unit,
    onClearAll: () -> Unit,
    onPostAnnouncement: (title: String, message: String, priority: String, targetGroup: String) -> Unit,
    onRegisterVisitor: (name: String, email: String, purpose: String, group: String) -> Unit = { _, _, _, _ -> },
    onExitAdmin: () -> Unit
) {
    var selectedAdminTab by remember { mutableIntStateOf(0) } // 0: Classes, 1: Post Notice, 2: Audit Logs, 3: Visitors
    var showCreateDialog by remember { mutableStateOf(false) }
    var classToEdit by remember { mutableStateOf<ClassEntity?>(null) }
    var classToReschedule by remember { mutableStateOf<ClassEntity?>(null) }
    var classToCancel by remember { mutableStateOf<ClassEntity?>(null) }
    var cancelReason by remember { mutableStateOf("") }

    // Announcement creation state
    var noticeTitle by remember { mutableStateOf("") }
    var noticeMessage by remember { mutableStateOf("") }
    var noticePriority by remember { mutableStateOf("NORMAL") }
    var noticeTargetGroup by remember { mutableStateOf("All") }
    var noticePostedMessage by remember { mutableStateOf<String?>(null) }

    // Visitor registration state
    var visitorName by remember { mutableStateOf("") }
    var visitorEmail by remember { mutableStateOf("") }
    var visitorPurpose by remember { mutableStateOf("") }
    var visitorGroup by remember { mutableStateOf("All") }
    var visitorSuccessMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            if (selectedAdminTab == 0) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("admin_fab_create_class")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create Class")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("admin_dashboard_root")
        ) {
            // Admin Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Admin Management Portal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Source schedule timezone: Europe/Sarajevo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onExitAdmin,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("admin_exit_button")
                ) {
                    Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Exit", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Quick Stats Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(title = "Total Classes", value = classes.size.toString(), modifier = Modifier.weight(1f))
                StatCard(
                    title = "Active",
                    value = classes.count { it.status == ClassEntity.STATUS_SCHEDULED }.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Rescheduled",
                    value = classes.count { it.status == ClassEntity.STATUS_RESCHEDULED }.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick Actions: Seed Official Schedule or Clear
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSeedUniversitySchedule,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_seed_schedule_button")
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load Official Schedule", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onClearAll,
                    modifier = Modifier.testTag("admin_clear_all_button")
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Admin Navigation Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedAdminTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedAdminTab == 0,
                    onClick = { selectedAdminTab = 0 },
                    text = { Text("Classes (${classes.size})") },
                    modifier = Modifier.testTag("admin_tab_classes")
                )
                Tab(
                    selected = selectedAdminTab == 1,
                    onClick = { selectedAdminTab = 1 },
                    text = { Text("Post Notice") },
                    modifier = Modifier.testTag("admin_tab_notices")
                )
                Tab(
                    selected = selectedAdminTab == 2,
                    onClick = { selectedAdminTab = 2 },
                    text = { Text("Audit Trail") },
                    modifier = Modifier.testTag("admin_tab_audit")
                )
                Tab(
                    selected = selectedAdminTab == 3,
                    onClick = { selectedAdminTab = 3 },
                    text = { Text("Visitors (${visitors.size})") },
                    modifier = Modifier.testTag("admin_tab_visitors")
                )
            }

            // Tab Content
            when (selectedAdminTab) {
                0 -> {
                    // Class Management List
                    if (classes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No Classes in Database", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap 'Load Official Schedule' to import the Oct-Nov 2026 timetable, or '+' to schedule a class.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            items(classes, key = { it.id }) { item ->
                                AdminClassCard(
                                    classEntity = item,
                                    onEdit = { classToEdit = item },
                                    onReschedule = { classToReschedule = item },
                                    onCancel = { classToCancel = item },
                                    onDuplicate = {
                                        onCreateClass(
                                            item.subject,
                                            item.classType,
                                            item.instructor,
                                            item.sourceDate,
                                            item.sourceStartTime,
                                            item.sourceEndTime,
                                            item.sourceTimezone,
                                            item.groupName,
                                            item.attendanceMode,
                                            item.platform,
                                            item.teamsChannel,
                                            item.locationOrUrl,
                                            item.notes
                                        )
                                    },
                                    onDelete = { onDeleteClass(item) }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(72.dp))
                            }
                        }
                    }
                }

                1 -> {
                    // Announcement Manager
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Publish Urgent Announcement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Broadcast schedule notices to all students or specific groups.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = noticeTitle,
                            onValueChange = { noticeTitle = it },
                            label = { Text("Announcement Title") },
                            placeholder = { Text("e.g. Room Change for Anatomy") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = noticeMessage,
                            onValueChange = { noticeMessage = it },
                            label = { Text("Message Body") },
                            placeholder = { Text("Enter detailed notice instructions...") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = noticePriority,
                                onValueChange = { noticePriority = it },
                                label = { Text("Priority (NORMAL / URGENT)") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = noticeTargetGroup,
                                onValueChange = { noticeTargetGroup = it },
                                label = { Text("Target Group (All, Group 1)") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (noticeTitle.isNotBlank() && noticeMessage.isNotBlank()) {
                                    onPostAnnouncement(noticeTitle, noticeMessage, noticePriority, noticeTargetGroup)
                                    noticePostedMessage = "Announcement published successfully!"
                                    noticeTitle = ""
                                    noticeMessage = ""
                                }
                            },
                            enabled = noticeTitle.isNotBlank() && noticeMessage.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("publish_announcement_button")
                        ) {
                            Icon(imageVector = Icons.Default.Campaign, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Broadcast Announcement")
                        }

                        if (noticePostedMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = noticePostedMessage ?: "",
                                color = EmeraldSuccess,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                2 -> {
                    // Audit Logs
                    if (auditLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No audit log entries recorded yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            items(auditLogs, key = { it.id }) { log ->
                                val timeFormatted = try {
                                    val zdt = Instant.ofEpochMilli(log.timestampEpochMs).atZone(ZoneId.systemDefault())
                                    zdt.format(DateTimeFormatter.ofPattern("MMM d, yyyy • hh:mm:ss a", Locale.ENGLISH))
                                } catch (_: Exception) {
                                    ""
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${log.action}: ${log.subject}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = when (log.action) {
                                                    "CANCELLED" -> RoseError
                                                    "RESCHEDULED" -> AmberWarning
                                                    "CREATED", "IMPORTED" -> EmeraldSuccess
                                                    else -> MaterialTheme.colorScheme.primary
                                                }
                                            )
                                            Text(
                                                text = timeFormatted,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = log.details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // Visitor Registration & List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.PersonAdd,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Register Visitor / Guest Student",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Add visitor records to grant timetable tracking and schedule updates.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedTextField(
                                        value = visitorName,
                                        onValueChange = { visitorName = it },
                                        label = { Text("Visitor Full Name *") },
                                        placeholder = { Text("e.g. Dr. Alex Vance / Sara Petrovic") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("visitor_name_input")
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = visitorEmail,
                                        onValueChange = { visitorEmail = it },
                                        label = { Text("Email or Contact Phone *") },
                                        placeholder = { Text("e.g. visitor@university.edu / +387 61 ...") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("visitor_contact_input")
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = visitorPurpose,
                                        onValueChange = { visitorPurpose = it },
                                        label = { Text("Purpose of Visit / Study *") },
                                        placeholder = { Text("e.g. Guest Observer, Auditing Anatomy, Medical Physics Lab") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("visitor_purpose_input")
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = visitorGroup,
                                        onValueChange = { visitorGroup = it },
                                        label = { Text("Assigned Group") },
                                        placeholder = { Text("All, Group 1, Group 2, Group 3, Group 4") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("visitor_group_input")
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Button(
                                        onClick = {
                                            if (visitorName.isNotBlank() && visitorEmail.isNotBlank() && visitorPurpose.isNotBlank()) {
                                                onRegisterVisitor(visitorName, visitorEmail, visitorPurpose, visitorGroup.ifBlank { "All" })
                                                visitorSuccessMessage = "Registered visitor '${visitorName}' successfully!"
                                                visitorName = ""
                                                visitorEmail = ""
                                                visitorPurpose = ""
                                                visitorGroup = "All"
                                            }
                                        },
                                        enabled = visitorName.isNotBlank() && visitorEmail.isNotBlank() && visitorPurpose.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth().testTag("register_visitor_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Register Visitor")
                                    }

                                    if (visitorSuccessMessage != null) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = visitorSuccessMessage ?: "",
                                            color = EmeraldSuccess,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Registered Visitors (${visitors.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (visitors.isEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No visitors registered yet.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(visitors, key = { it.id }) { visitor ->
                                val dateStr = try {
                                    val zdt = Instant.ofEpochMilli(visitor.registeredAtEpochMs).atZone(ZoneId.systemDefault())
                                    zdt.format(DateTimeFormatter.ofPattern("MMM d, yyyy • hh:mm a", Locale.ENGLISH))
                                } catch (_: Exception) {
                                    ""
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = visitor.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Group: ${visitor.targetGroup}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Contact: ${visitor.email}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Purpose: ${visitor.purpose}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (dateStr.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Registered: $dateStr",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showCreateDialog) {
        ClassEditDialog(
            initialClass = null,
            onSave = { subject, type, instructor, date, start, end, tz, group, mode, platform, channel, loc, notes ->
                onCreateClass(subject, type, instructor, date, start, end, tz, group, mode, platform, channel, loc, notes)
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    if (classToEdit != null) {
        ClassEditDialog(
            initialClass = classToEdit,
            onSave = { subject, type, instructor, date, start, end, tz, group, mode, platform, channel, loc, notes ->
                onUpdateClass(
                    classToEdit!!.copy(
                        subject = subject,
                        classType = type,
                        instructor = instructor,
                        sourceDate = date,
                        sourceStartTime = start,
                        sourceEndTime = end,
                        sourceTimezone = tz,
                        groupName = group,
                        attendanceMode = mode,
                        platform = platform,
                        teamsChannel = channel,
                        locationOrUrl = loc,
                        notes = notes
                    )
                )
                classToEdit = null
            },
            onDismiss = { classToEdit = null }
        )
    }

    if (classToReschedule != null) {
        RescheduleDialog(
            classEntity = classToReschedule!!,
            onConfirm = { newDate, newStart, newEnd, reason ->
                onRescheduleClass(classToReschedule!!, newDate, newStart, newEnd, reason)
                classToReschedule = null
            },
            onDismiss = { classToReschedule = null }
        )
    }

    if (classToCancel != null) {
        AlertDialog(
            onDismissRequest = { classToCancel = null },
            icon = { Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = RoseError) },
            title = { Text("Cancel Class: ${classToCancel!!.subject}") },
            text = {
                Column {
                    Text(
                        text = "Cancelling this class will automatically notify all enrolled students and delete pending alarms/reminders.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("Cancellation Reason") },
                        placeholder = { Text("e.g. Instructor ill / university holiday") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelClass(classToCancel!!, cancelReason.ifBlank { "University cancellation" })
                        classToCancel = null
                        cancelReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                ) {
                    Text("Confirm Cancellation")
                }
            },
            dismissButton = {
                TextButton(onClick = { classToCancel = null }) {
                    Text("Back")
                }
            }
        )
    }
}

@Composable
fun AdminClassCard(
    classEntity: ClassEntity,
    onEdit: () -> Unit,
    onReschedule: () -> Unit,
    onCancel: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .testTag("admin_class_card_${classEntity.id.take(6)}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${classEntity.sourceDate} • ${classEntity.sourceStartTime} - ${classEntity.sourceEndTime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = classEntity.status,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when (classEntity.status) {
                        ClassEntity.STATUS_CANCELLED -> RoseError
                        ClassEntity.STATUS_RESCHEDULED -> AmberWarning
                        else -> EmeraldSuccess
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = classEntity.subject,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${classEntity.classType} • Group: ${classEntity.groupName} • ${classEntity.attendanceMode}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onReschedule) {
                    Icon(imageVector = Icons.Default.Update, contentDescription = "Reschedule", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDuplicate) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(20.dp))
                }
                if (classEntity.status != ClassEntity.STATUS_CANCELLED) {
                    IconButton(onClick = onCancel) {
                        Icon(imageVector = Icons.Default.Cancel, contentDescription = "Cancel", tint = RoseError, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

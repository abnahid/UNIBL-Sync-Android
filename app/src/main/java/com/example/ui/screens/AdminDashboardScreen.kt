package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.AnnouncementEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.ClassEntity
import com.example.data.model.VisitorEntity
import com.example.data.remote.UserSession
import com.example.ui.components.ClassEditDialog
import com.example.ui.components.RescheduleDialog
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    userSession: UserSession?,
    classes: List<ClassEntity>,
    announcements: List<AnnouncementEntity>,
    auditLogs: List<AuditLogEntity>,
    visitors: List<VisitorEntity>,
    isSyncing: Boolean,
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
    onPostAnnouncement: (title: String, message: String, priority: String, targetGroup: String) -> Unit,
    onRegisterVisitor: (name: String, email: String, purpose: String, group: String) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onExitAdmin: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Classes, 1: Notices, 2: Audit, 3: Visitors

    var showCreateDialog by remember { mutableStateOf(false) }
    var classToEdit by remember { mutableStateOf<ClassEntity?>(null) }
    var classToReschedule by remember { mutableStateOf<ClassEntity?>(null) }
    var classToCancel by remember { mutableStateOf<ClassEntity?>(null) }
    var classToDelete by remember { mutableStateOf<ClassEntity?>(null) }
    var cancelReason by remember { mutableStateOf("") }

    var showNoticeDialog by remember { mutableStateOf(false) }
    var noticeTitle by remember { mutableStateOf("") }
    var noticeMessage by remember { mutableStateOf("") }
    var noticePriority by remember { mutableStateOf("NORMAL") }
    var noticeGroup by remember { mutableStateOf("All") }

    var showVisitorDialog by remember { mutableStateOf(false) }
    var visitorName by remember { mutableStateOf("") }
    var visitorEmail by remember { mutableStateOf("") }
    var visitorPurpose by remember { mutableStateOf("") }
    var visitorGroup by remember { mutableStateOf("All") }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("admin_fab_create_class")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Class")
                }
            } else if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showNoticeDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Default.Campaign, contentDescription = "Post Notice")
                }
            } else if (selectedTab == 3) {
                FloatingActionButton(
                    onClick = { showVisitorDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Register Visitor")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("admin_dashboard_screen")
        ) {
            // Staff Profile Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onExitAdmin) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Student Mode")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = userSession?.name?.ifBlank { "Staff Admin" } ?: "Staff Admin",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${userSession?.email ?: ""} • Role: ${userSession?.role ?: "STAFF"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onRefresh) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = onSignOut) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "Sign Out",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val url = "${BuildConfig.PUBLIC_ROUTE_URL.trimEnd('/')}/admin"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Web Admin", style = MaterialTheme.typography.labelSmall)
                        }

                        TextButton(
                            onClick = onExitAdmin,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Switch to Student View", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Classes (${classes.size})") },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Notices (${announcements.size})") },
                    icon = { Icon(Icons.Default.Campaign, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Audit") },
                    icon = { Icon(Icons.Default.History, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Visitors") },
                    icon = { Icon(Icons.Default.People, contentDescription = null) }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // Classes List
                    if (classes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No Classes in Timetable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Tap '+' to schedule an official university class.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            items(classes, key = { it.id }) { c ->
                                AdminClassCard(
                                    classEntity = c,
                                    onEdit = { classToEdit = c },
                                    onReschedule = { classToReschedule = c },
                                    onCancel = { classToCancel = c },
                                    onDelete = { classToDelete = c }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(72.dp)) }
                        }
                    }
                }
                1 -> {
                    // Notices
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        items(announcements, key = { it.id }) { a ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(a.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(a.priority, color = if (a.priority == "URGENT") RoseError else EmeraldSuccess, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(a.message, style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Target: ${a.targetGroup}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(72.dp)) }
                    }
                }
                2 -> {
                    // Audit logs
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        items(auditLogs, key = { it.id }) { log ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(log.action, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        val timeStr = try {
                                            Instant.ofEpochMilli(log.timestampEpochMs)
                                                .atZone(ZoneId.systemDefault())
                                                .format(DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.ENGLISH))
                                        } catch (_: Exception) { "" }
                                        Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(log.subject, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    if (log.details.isNotBlank()) {
                                        Text(log.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Visitors
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        items(visitors, key = { it.id }) { v ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(v.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text(v.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Purpose: ${v.purpose} • Target: ${v.targetGroup}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }

    // Create Class Dialog
    if (showCreateDialog) {
        ClassEditDialog(
            initialClass = null,
            onSave = { subject, type, inst, date, start, end, tz, grp, mode, plat, chan, loc, notes ->
                onCreateClass(subject, type, inst, date, start, end, tz, grp, mode, plat, chan, loc, notes)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    // Edit Class Dialog
    if (classToEdit != null) {
        ClassEditDialog(
            initialClass = classToEdit,
            onSave = { subject, type, inst, date, start, end, tz, grp, mode, plat, chan, loc, notes ->
                onUpdateClass(
                    classToEdit!!.copy(
                        subject = subject,
                        classType = type,
                        instructor = inst,
                        sourceDate = date,
                        sourceStartTime = start,
                        sourceEndTime = end,
                        sourceTimezone = tz,
                        groupName = grp,
                        attendanceMode = mode,
                        platform = plat,
                        teamsChannel = chan,
                        locationOrUrl = loc,
                        notes = notes
                    )
                )
                classToEdit = null
            },
            onDismiss = { classToEdit = null }
        )
    }

    // Reschedule Dialog
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

    // Cancel Class Dialog
    if (classToCancel != null) {
        AlertDialog(
            onDismissRequest = { classToCancel = null },
            icon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = RoseError) },
            title = { Text("Cancel Scheduled Class") },
            text = {
                Column {
                    Text("Are you sure you want to cancel '${classToCancel!!.subject}' on ${classToCancel!!.sourceDate}?")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("Cancellation Reason") },
                        placeholder = { Text("e.g. Faculty medical leave") },
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
                    Text("Cancel Class")
                }
            },
            dismissButton = {
                TextButton(onClick = { classToCancel = null }) { Text("Dismiss") }
            }
        )
    }

    // Delete Class Confirmation
    if (classToDelete != null) {
        AlertDialog(
            onDismissRequest = { classToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Class") },
            text = { Text("Are you sure you want to permanently delete '${classToDelete!!.subject}' from the university schedule?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteClass(classToDelete!!)
                        classToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { classToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Post Notice Dialog
    if (showNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showNoticeDialog = false },
            title = { Text("Publish University Notice") },
            text = {
                Column {
                    OutlinedTextField(
                        value = noticeTitle,
                        onValueChange = { noticeTitle = it },
                        label = { Text("Notice Title *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noticeMessage,
                        onValueChange = { noticeMessage = it },
                        label = { Text("Message *") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noticeTitle.isNotBlank() && noticeMessage.isNotBlank()) {
                            onPostAnnouncement(noticeTitle.trim(), noticeMessage.trim(), noticePriority, noticeGroup)
                            noticeTitle = ""
                            noticeMessage = ""
                            showNoticeDialog = false
                        }
                    }
                ) {
                    Text("Publish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoticeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Register Visitor Dialog
    if (showVisitorDialog) {
        AlertDialog(
            onDismissRequest = { showVisitorDialog = false },
            title = { Text("Register Visitor") },
            text = {
                Column {
                    OutlinedTextField(
                        value = visitorName,
                        onValueChange = { visitorName = it },
                        label = { Text("Visitor Name *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = visitorEmail,
                        onValueChange = { visitorEmail = it },
                        label = { Text("Email *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = visitorPurpose,
                        onValueChange = { visitorPurpose = it },
                        label = { Text("Purpose") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (visitorName.isNotBlank() && visitorEmail.isNotBlank()) {
                            onRegisterVisitor(visitorName.trim(), visitorEmail.trim(), visitorPurpose.trim(), visitorGroup)
                            visitorName = ""
                            visitorEmail = ""
                            visitorPurpose = ""
                            showVisitorDialog = false
                        }
                    }
                ) {
                    Text("Register")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVisitorDialog = false }) { Text("Cancel") }
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
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onReschedule) {
                    Icon(imageVector = Icons.Default.Update, contentDescription = "Reschedule", modifier = Modifier.size(18.dp))
                }
                if (classEntity.status != ClassEntity.STATUS_CANCELLED) {
                    IconButton(onClick = onCancel) {
                        Icon(imageVector = Icons.Default.Cancel, contentDescription = "Cancel", tint = RoseError, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

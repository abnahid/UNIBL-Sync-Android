package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import com.example.util.MeetingLauncher
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AnnouncementEntity
import com.example.data.model.ClassEntity
import com.example.data.model.ReminderPreferenceEntity
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoDark
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.RoseError
import com.example.ui.theme.SkyInfo
import com.example.util.TimezoneHelper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StudentDashboardScreen(
    classes: List<ClassEntity>,
    nextClass: ClassEntity?,
    ongoingClass: ClassEntity?,
    announcements: List<AnnouncementEntity>,
    preferences: List<ReminderPreferenceEntity>,
    selectedTimezone: String,
    currentTimeMs: Long,
    onClassClick: (ClassEntity) -> Unit,
    onChangeTimezoneClick: () -> Unit
) {
    val context = LocalContext.current

    val currentClockFormatted = try {
        val zone = ZoneId.of(selectedTimezone)
        val zdt = Instant.ofEpochMilli(currentTimeMs).atZone(zone)
        zdt.format(DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH))
    } catch (_: Exception) {
        ""
    }

    val currentDateFormatted = try {
        val zone = ZoneId.of(selectedTimezone)
        val zdt = Instant.ofEpochMilli(currentTimeMs).atZone(zone)
        zdt.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH))
    } catch (_: Exception) {
        ""
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("student_dashboard_scroll")
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))

            // Greeting & Timezone Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ClassTime Dashboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = currentDateFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SuggestionChip(
                    onClick = onChangeTimezoneClick,
                    label = {
                        Text(
                            text = TimezoneHelper.getTimezoneShortLabel(selectedTimezone),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.testTag("open_timezone_picker")
                )
            }

            // Live Digital Clock Strip
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Live Student Clock",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = currentClockFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Live Ongoing Class Alert
        if (ongoingClass != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = EmeraldSuccess.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .clickable { onClassClick(ongoingClass) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = "Live",
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "CLASS IN SESSION NOW",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldSuccess
                                )
                                Text(
                                    text = ongoingClass.subject,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Ends at ${TimezoneHelper.formatToLocalTime(ongoingClass.utcEndEpochMs, selectedTimezone)} (${TimezoneHelper.getTimezoneShortLabel(selectedTimezone)})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        val isTeams = ongoingClass.platform == ClassEntity.PLATFORM_TEAMS || ongoingClass.locationOrUrl.contains("teams.microsoft.com")
                        if (ongoingClass.locationOrUrl.isNotBlank() || isTeams) {
                            Button(
                                onClick = {
                                    MeetingLauncher.openMeeting(context, ongoingClass.locationOrUrl, ongoingClass.platform, ongoingClass.subject)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isTeams) Color(0xFF5B5FC7) else EmeraldSuccess)
                            ) {
                                Text(if (isTeams) "Join Teams" else "Join")
                            }
                        }
                    }
                }
            }
        }

        // Next Upcoming Class Hero Card
        item {
            if (nextClass != null) {
                val remainingMs = (nextClass.utcStartEpochMs - currentTimeMs).coerceAtLeast(0L)
                val hours = remainingMs / (1000 * 60 * 60)
                val minutes = (remainingMs / (1000 * 60)) % 60
                val seconds = (remainingMs / 1000) % 60

                val countdownText = when {
                    hours > 0 -> String.format(Locale.ENGLISH, "%02dh %02dm %02ds", hours, minutes, seconds)
                    minutes > 0 -> String.format(Locale.ENGLISH, "%02dm %02ds", minutes, seconds)
                    else -> String.format(Locale.ENGLISH, "%02ds", seconds)
                }

                val pref = preferences.find { it.classId == nextClass.id }
                val leadMins = pref?.leadMinutes ?: 20

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClassClick(nextClass) }
                        .testTag("next_class_hero_card"),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(IndigoDark, IndigoPrimary)
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "NEXT UPCOMING CLASS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = countdownText,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = nextClass.subject,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Text(
                                text = "${nextClass.classType} • Group: ${nextClass.groupName} • ${nextClass.attendanceMode}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Dual Time Box
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "Your Local Time",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = TimezoneHelper.formatToLocalTime(nextClass.utcStartEpochMs, selectedTimezone),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = TimezoneHelper.getTimezoneShortLabel(selectedTimezone),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.85f)
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "University Announced Time",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = nextClass.sourceStartTime,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Bosnia (${nextClass.sourceTimezone})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.85f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action and reminder pill
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Reminder: ${leadMins}m before",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }

                                val isTeams = nextClass.platform == ClassEntity.PLATFORM_TEAMS || nextClass.locationOrUrl.contains("teams.microsoft.com")
                                if (nextClass.locationOrUrl.isNotBlank() || isTeams) {
                                    Button(
                                        onClick = {
                                            MeetingLauncher.openMeeting(context, nextClass.locationOrUrl, nextClass.platform, nextClass.subject)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isTeams) Color(0xFF5B5FC7) else Color.White,
                                            contentColor = if (isTeams) Color.White else IndigoDark
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("join_teams_hero_button")
                                    ) {
                                        Icon(imageVector = Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isTeams) "Open on Teams" else "Open Link", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Empty next class card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (classes.isEmpty()) "No Classes Scheduled Yet" else "No More Upcoming Classes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (classes.isEmpty()) {
                                "The university timetable is currently blank. Administrators can publish classes or load the official schedule in Admin Portal."
                            } else {
                                "You're all caught up for upcoming sessions! Check the timetable tab for complete schedule history."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Microsoft Teams Academic Channels Hub
        if (classes.isNotEmpty()) {
            item {
                MicrosoftTeamsChannelsCard(
                    classes = classes,
                    userTimezone = selectedTimezone,
                    currentTimeMs = currentTimeMs,
                    onOpenChannel = { subject, url ->
                        MeetingLauncher.openMeeting(context, url, ClassEntity.PLATFORM_TEAMS, subject)
                    }
                )
                Spacer(modifier = Modifier.height(18.dp))
            }
        }

        // Recent Announcements Banner
        if (announcements.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recent Announcements",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val latest = announcements.take(2)
                latest.forEach { ann ->
                    OutlinedCard(
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
                                Text(
                                    text = ann.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ann.priority == "URGENT") RoseError else MaterialTheme.colorScheme.onSurface
                                )
                                if (ann.priority == "URGENT") {
                                    Text(
                                        text = "URGENT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = RoseError
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ann.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Schedule Stream / All upcoming classes
        item {
            Text(
                text = "Upcoming Academic Schedule",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val upcomingList = classes.filter { it.utcStartEpochMs > currentTimeMs }.take(5)
        if (upcomingList.isEmpty() && classes.isNotEmpty()) {
            item {
                Text(
                    text = "All scheduled classes for this period have concluded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(upcomingList) { item ->
                ClassTimelineCard(
                    classEntity = item,
                    userTimezone = selectedTimezone,
                    onClick = { onClassClick(item) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ClassTimelineCard(
    classEntity: ClassEntity,
    userTimezone: String,
    onClick: () -> Unit
) {
    val localTime = TimezoneHelper.formatToLocalTime(classEntity.utcStartEpochMs, userTimezone)
    val localDate = TimezoneHelper.formatToLocalDate(classEntity.utcStartEpochMs, userTimezone)
    val shortTz = TimezoneHelper.getTimezoneShortLabel(userTimezone)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(90.dp)) {
                Text(
                    text = localTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = shortTz,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = classEntity.sourceStartTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = classEntity.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${classEntity.classType} • ${classEntity.groupName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = localDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (classEntity.status == ClassEntity.STATUS_RESCHEDULED) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AmberWarning.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "UPDATED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AmberWarning
                    )
                }
            }
        }
    }
}

@Composable
fun MicrosoftTeamsChannelsCard(
    classes: List<ClassEntity>,
    userTimezone: String,
    currentTimeMs: Long,
    onOpenChannel: (subject: String, url: String) -> Unit
) {
    val teamsBrandColor = Color(0xFF5B5FC7)
    val subjects = classes.map { it.subject }.distinct().take(5)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("teams_channels_hub_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(teamsBrandColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "T",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Microsoft Teams Channels",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Direct app launcher • Auto local time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(teamsBrandColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Teams",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = teamsBrandColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            subjects.forEach { subject ->
                val nextForSubject = classes
                    .filter { it.subject == subject && it.utcStartEpochMs > currentTimeMs && it.status != ClassEntity.STATUS_CANCELLED }
                    .minByOrNull { it.utcStartEpochMs }

                val channelName = nextForSubject?.teamsChannel ?: "General"
                val channelUrl = nextForSubject?.locationOrUrl ?: MeetingLauncher.getTeamsChannelUrl(subject, channelName)

                val localTimeFormatted = if (nextForSubject != null) {
                    val time = TimezoneHelper.formatToLocalTime(nextForSubject.utcStartEpochMs, userTimezone)
                    val date = TimezoneHelper.formatToLocalDate(nextForSubject.utcStartEpochMs, userTimezone)
                    "$date • $time"
                } else {
                    "No upcoming session"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = subject,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "#$channelName • $localTimeFormatted",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (nextForSubject != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { onOpenChannel(subject, channelUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = teamsBrandColor),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("open_teams_${subject.lowercase().take(5)}")
                    ) {
                        Text("Open", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

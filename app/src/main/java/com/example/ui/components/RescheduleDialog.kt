package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

@Composable
fun RescheduleDialog(
    classEntity: ClassEntity,
    onConfirm: (newDate: String, newStartTime: String, newEndTime: String, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var newDate by remember { mutableStateOf(classEntity.sourceDate) }
    var newStartTime by remember { mutableStateOf(classEntity.sourceStartTime) }
    var newEndTime by remember { mutableStateOf(classEntity.sourceEndTime) }
    var reason by remember { mutableStateOf("Faculty schedule adjustment") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Update,
                contentDescription = "Reschedule",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Reschedule ${classEntity.subject}") },
        text = {
            Column {
                Text(
                    text = "Updating the class time will automatically notify enrolled students, recalculate reminder triggers in their local timezones, and log an audit entry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = newDate,
                    onValueChange = { newDate = it },
                    label = { Text("New Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newStartTime,
                        onValueChange = { newStartTime = it },
                        label = { Text("Start Time") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = newEndTime,
                        onValueChange = { newEndTime = it },
                        label = { Text("End Time") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for Rescheduling") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(newDate, newStartTime, newEndTime, reason)
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_reschedule_button")
            ) {
                Text("Confirm & Notify")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

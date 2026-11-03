package com.abnahid.uniblsync.data.schedule

import com.abnahid.uniblsync.data.model.ClassEntity

/** Cross-source occurrence deduplication. Backend rows win over local fallback rows. */
object ScheduleMerger {
    fun merge(synced: List<ClassEntity>, generated: List<ClassEntity>): List<ClassEntity> {
        val result = LinkedHashMap<String, ClassEntity>()
        synced.forEach { result.putIfAbsent(identity(it), it) }
        generated.forEach { result.putIfAbsent(identity(it), it) }
        return result.values.sortedWith(compareBy({ it.utcStartEpochMs }, { it.subject }))
    }

    fun identity(item: ClassEntity): String = listOf(
        item.subject.trim().lowercase().replace(Regex("\\s+"), " "),
        item.sourceDate,
        item.sourceStartTime.trim().toMinutes().toString(),
        item.groupName.trim().lowercase().replace(Regex("\\s+"), " ")
    ).joinToString("|")

    private fun String.toMinutes(): Int {
        val parts = trim().split(Regex(":|\\s+"))
        val hour = parts.firstOrNull()?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val pm = uppercase().contains("PM")
        val normalizedHour = when {
            pm && hour < 12 -> hour + 12
            !pm && hour == 12 && uppercase().contains("AM") -> 0
            else -> hour
        }
        return normalizedHour * 60 + minute
    }
}

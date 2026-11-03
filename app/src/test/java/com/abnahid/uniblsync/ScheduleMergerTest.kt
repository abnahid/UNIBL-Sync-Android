package com.abnahid.uniblsync

import com.abnahid.uniblsync.data.model.ClassEntity
import com.abnahid.uniblsync.data.schedule.ScheduleMerger
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleMergerTest {
    private fun item(id: String, group: String = "All", subject: String = "Medical Society") = ClassEntity(
        id = id, subject = subject, classType = "Lecture", sourceDate = "2026-11-03",
        sourceStartTime = "13:00", sourceEndTime = "14:00", utcStartEpochMs = 1L,
        utcEndEpochMs = 2L, groupName = group
    )

    @Test fun backendOccurrenceWinsOverSeedWithDifferentId() {
        val result = ScheduleMerger.merge(listOf(item("backend-1")), listOf(item("rec:seed:2026-11-03")))
        assertEquals(listOf("backend-1"), result.map { it.id })
    }

    @Test fun distinctGroupsRemainDistinct() {
        val result = ScheduleMerger.merge(emptyList(), listOf(item("a", "Group 1"), item("b", "Group 2")))
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test fun repeatedSameSourceDoesNotDuplicate() {
        val result = ScheduleMerger.merge(listOf(item("a"), item("a")), emptyList())
        assertEquals(1, result.size)
    }
}

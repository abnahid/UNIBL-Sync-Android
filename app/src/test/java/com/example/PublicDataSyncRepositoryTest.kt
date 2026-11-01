package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.AnnouncementEntity
import com.example.data.model.ClassEntity
import com.example.data.remote.PublicDataSource
import com.example.data.repository.ClassRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class PublicDataSyncRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var source: FakePublicDataSource
    private lateinit var repository: ClassRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        source = FakePublicDataSource()
        repository = ClassRepository(
            database, database.classDao(), database.reminderDao(), database.announcementDao(),
            database.auditLogDao(), database.visitorDao(), source
        )
    }

    @After fun tearDown() = database.close()

    @Test fun `refresh replaces a class with its server update without duplicates`() = runBlocking {
        source.classes = listOf(sampleClass(start = 100L))
        source.announcements = listOf(sampleAnnouncement())
        repository.refreshPublicData()

        source.classes = listOf(sampleClass(start = 200L))
        repository.refreshPublicData()

        val classes = database.classDao().getAllClasses().first()
        assertEquals(1, classes.size)
        assertEquals(200L, classes.single().utcStartEpochMs)
    }

    @Test fun `failed refresh preserves cached classes`() = runBlocking {
        database.classDao().insertClass(sampleClass(start = 100L))
        source.failure = IOException("offline")

        try {
            repository.refreshPublicData()
        } catch (_: IOException) {
            // Expected: callers/workers decide retry policy; the cache must remain intact.
        }

        val classes = database.classDao().getAllClasses().first()
        assertEquals(1, classes.size)
        assertEquals(100L, classes.single().utcStartEpochMs)
    }

    @Test fun `refresh preserves announcement read state`() = runBlocking {
        database.announcementDao().insertAnnouncement(sampleAnnouncement().copy(isRead = true))
        source.announcements = listOf(sampleAnnouncement().copy(message = "Updated"))
        repository.refreshPublicData()

        val announcement = database.announcementDao().getAllAnnouncements().first().single()
        assertTrue(announcement.isRead)
        assertEquals("Updated", announcement.message)
    }

    private fun sampleClass(start: Long) = ClassEntity(
        id = "class-1", subject = "Anatomy", classType = "Lecture", sourceDate = "2026-10-26",
        sourceStartTime = "10:00", sourceEndTime = "11:00", utcStartEpochMs = start, utcEndEpochMs = start + 1
    )

    private fun sampleAnnouncement() = AnnouncementEntity("announcement-1", "Notice", "Original")

    private class FakePublicDataSource : PublicDataSource {
        var classes = emptyList<ClassEntity>()
        var announcements = emptyList<AnnouncementEntity>()
        var failure: IOException? = null
        override suspend fun fetchClasses(): List<ClassEntity> = failure?.let { throw it } ?: classes
        override suspend fun fetchAnnouncements(): List<AnnouncementEntity> = failure?.let { throw it } ?: announcements
    }
}

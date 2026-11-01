package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.AnnouncementEntity
import com.example.data.model.ClassEntity
import com.example.data.remote.ApiException
import com.example.data.remote.PublicDataSource
import com.example.data.remote.SessionManager
import com.example.data.remote.UserSession
import com.example.data.repository.ClassRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class FullApplicationFlowTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var fakeApi: TestPublicDataSource
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: ClassRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeApi = TestPublicDataSource()
        sessionManager = SessionManager(context)
        sessionManager.clearSession()

        repository = ClassRepository(
            database = database,
            classDao = database.classDao(),
            reminderDao = database.reminderDao(),
            announcementDao = database.announcementDao(),
            auditLogDao = database.auditLogDao(),
            visitorDao = database.visitorDao(),
            apiClient = fakeApi,
            adminApiClient = null,
            sessionManager = sessionManager
        )
    }

    @After
    fun tearDown() {
        sessionManager.clearSession()
        database.close()
    }

    @Test
    fun testNonEmptyScheduleDisplayedAfterSynchronization() = runBlocking {
        fakeApi.classes = listOf(
            ClassEntity(
                id = "c1",
                subject = "Anatomy",
                classType = "Lecture",
                sourceDate = "2026-10-30",
                sourceStartTime = "09:00",
                sourceEndTime = "10:30",
                utcStartEpochMs = 1793343600000L,
                utcEndEpochMs = 1793349000000L,
                groupName = "Group 1",
                attendanceMode = "In person + Online"
            ),
            ClassEntity(
                id = "c2",
                subject = "Medical Physics",
                classType = "Lecture",
                sourceDate = "2026-10-30",
                sourceStartTime = "11:00",
                sourceEndTime = "12:30",
                utcStartEpochMs = 1793350800000L,
                utcEndEpochMs = 1793356200000L,
                groupName = "All",
                attendanceMode = "Online"
            )
        )

        repository.refreshPublicData()

        val stored = repository.allClasses.first()
        assertEquals(2, stored.size)
        assertEquals("Anatomy", stored[0].subject)
        assertEquals("Medical Physics", stored[1].subject)
    }

    @Test
    fun testFailedApiRequestDoesNotSilentlyBecomeEmptySuccess() = runBlocking {
        // Pre-populate cache
        val initialClass = ClassEntity(
            id = "cached-1",
            subject = "Histology",
            classType = "Lecture",
            sourceDate = "2026-10-30",
            sourceStartTime = "14:00",
            sourceEndTime = "15:30",
            utcStartEpochMs = 1793361600000L,
            utcEndEpochMs = 1793367000000L
        )
        database.classDao().insertClass(initialClass)

        // API fails
        fakeApi.failure = ApiException(503, "Service Unavailable")

        try {
            repository.refreshPublicData()
            fail("Expected exception when API fails")
        } catch (e: ApiException) {
            assertEquals(503, e.statusCode)
            assertTrue(e.isTemporary)
        }

        // Cache must remain intact
        val cached = repository.allClasses.first()
        assertEquals(1, cached.size)
        assertEquals("Histology", cached[0].subject)
    }

    @Test
    fun testUpdatedClassTimesReplaceOldCachedValues() = runBlocking {
        val original = ClassEntity(
            id = "c-update",
            subject = "Biology",
            classType = "Lecture",
            sourceDate = "2026-10-30",
            sourceStartTime = "09:00",
            sourceEndTime = "10:30",
            utcStartEpochMs = 1000L,
            utcEndEpochMs = 2000L
        )
        fakeApi.classes = listOf(original)
        repository.refreshPublicData()

        // Updated time from server
        val updated = original.copy(
            sourceStartTime = "10:00",
            sourceEndTime = "11:30",
            utcStartEpochMs = 3000L,
            utcEndEpochMs = 4000L
        )
        fakeApi.classes = listOf(updated)
        repository.refreshPublicData()

        val list = repository.allClasses.first()
        assertEquals(1, list.size)
        assertEquals("10:00", list[0].sourceStartTime)
        assertEquals(3000L, list[0].utcStartEpochMs)
    }

    @Test
    fun testNoticesLoadFromEndpoint() = runBlocking {
        fakeApi.announcements = listOf(
            AnnouncementEntity(
                id = "notice-1",
                title = "Midterm Schedule",
                message = "Midterm exams will begin next week.",
                priority = "URGENT",
                targetGroup = "All",
                createdAtEpochMs = 1000L
            )
        )
        repository.refreshPublicData()

        val list = repository.allAnnouncements.first()
        assertEquals(1, list.size)
        assertEquals("Midterm Schedule", list[0].title)
        assertEquals("URGENT", list[0].priority)
    }

    @Test
    fun testAuthenticationStateAndSessionManagement() {
        assertFalse(sessionManager.isLoggedIn())
        assertNull(sessionManager.loadSession())

        // Save session
        val user = UserSession(
            id = "staff-1",
            email = "admin@unibl.edu",
            name = "Dr. Admin",
            role = "ADMIN"
        )
        sessionManager.saveSession(user, token = "test-token-xyz")

        assertTrue(sessionManager.isLoggedIn())
        assertTrue(sessionManager.isAdmin())
        assertEquals("admin@unibl.edu", sessionManager.loadSession()?.email)
        assertEquals("test-token-xyz", sessionManager.getSessionToken())

        // Clear session on logout
        sessionManager.clearSession()
        assertFalse(sessionManager.isLoggedIn())
        assertNull(sessionManager.loadSession())
        assertNull(sessionManager.getSessionToken())
    }

    @Test
    fun testNoFirebaseReferencesRemain() {
        // Confirm that Firebase classes and packages are NOT referenced
        val clazz = try {
            Class.forName("com.google.firebase.FirebaseApp")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        assertFalse("Firebase must not be loaded on classpath", clazz)
    }

    private class TestPublicDataSource : PublicDataSource {
        var classes = emptyList<ClassEntity>()
        var announcements = emptyList<AnnouncementEntity>()
        var failure: Exception? = null

        override suspend fun fetchClasses(): List<ClassEntity> {
            failure?.let { throw it }
            return classes
        }

        override suspend fun fetchAnnouncements(): List<AnnouncementEntity> {
            failure?.let { throw it }
            return announcements
        }
    }
}

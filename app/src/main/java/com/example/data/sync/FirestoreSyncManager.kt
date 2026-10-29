package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.AnnouncementEntity
import com.example.data.model.ClassEntity
import com.example.util.TimezoneHelper
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.resume
import kotlinx.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Cloud sync between the public Firestore database (managed from the web
 * admin dashboard at /admin) and this app's local Room database.
 *
 * How it works
 * ------------
 *  - The website admin dashboard is the single source of truth: classes,
 *    announcements and app info live in Firestore.
 *  - On app launch (and when the admin taps "Load Official Schedule") this
 *    manager downloads the latest data over the internet and stores it in
 *    Room, so the app keeps working fully OFFLINE afterwards.
 *  - Only records that came from the cloud are ever replaced/removed, so
 *    anything created locally on the device is never lost.
 *  - Firestore's own offline cache is enabled as well (unlimited cache),
 *    so even a first-session download survives a brief connection loss.
 */
object FirestoreSyncManager {

    private const val TAG = "FirestoreSync"
    private const val PREFS = "classtime_cloud_sync"
    private const val KEY_LAST_SYNC = "last_sync_epoch_ms"
    private const val KEY_CLOUD_CLASS_IDS = "cloud_class_ids"
    private const val KEY_CLOUD_ANNOUNCEMENT_IDS = "cloud_announcement_ids"

    /** Don't auto-sync more often than this (manual "Load" always forces). */
    private const val THROTTLE_MS = 10 * 60 * 1000L

    sealed class SyncUiState {
        data object Idle : SyncUiState()
        data object Syncing : SyncUiState()
        data class Success(
            val syncedAtEpochMs: Long,
            val classCount: Int,
            val announcementCount: Int
        ) : SyncUiState()
        data class Error(val message: String) : SyncUiState()
    }

    private val _state = MutableStateFlow<SyncUiState>(SyncUiState.Idle)

    /** Observe this to show sync progress / results in the UI. */
    val state: StateFlow<SyncUiState> = _state

    private fun firestore(): FirebaseFirestore =
        FirebaseFirestore.getInstance().also { db ->
            try {
                db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
            } catch (e: Exception) {
                Log.w(TAG, "Could not configure Firestore cache", e)
            }
        }

    /** Await a Play-Services Task inside coroutines (no extra dependency needed). */
    private suspend fun <T> Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
            addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }

    /**
     * Pulls the published schedule + notices from the public database and
     * stores them in the local Room database.
     *
     * @param force when false, skips if we already synced recently.
     */
    suspend fun sync(context: Context, force: Boolean = false): SyncUiState =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            if (_state.value is SyncUiState.Syncing) return@withContext _state.value

            if (!force) {
                val last = prefs.getLong(KEY_LAST_SYNC, 0L)
                val recent = _state.value is SyncUiState.Success &&
                        System.currentTimeMillis() - last < THROTTLE_MS
                if (recent) return@withContext _state.value
            }

            _state.value = SyncUiState.Syncing
            Log.i(TAG, "Cloud sync started${if (force) " (forced)" else ""}…")

            try {
                val db = firestore()
                val classSnap = db.collection("classes").get(Source.SERVER).awaitTask()
                val annSnap = db.collection("announcements").get(Source.SERVER).awaitTask()

                val cloudClasses = classSnap.documents.mapNotNull { it.toClassEntity() }
                val cloudAnnouncements = annSnap.documents.mapNotNull { it.toAnnouncementEntity() }

                val database = AppDatabase.getDatabase(appContext)
                val classDao = database.classDao()
                val announcementDao = database.announcementDao()

                // 1) Upsert everything the admin published (covers the whole
                //    schedule, so at minimum the next week is cached offline).
                if (cloudClasses.isNotEmpty()) classDao.insertClasses(cloudClasses)
                cloudAnnouncements.forEach { cloud ->
                    // Keep the user's local "read" flag: never un-read a notice
                    // that was already read on this device.
                    val existing = announcementDao.getAnnouncementById(cloud.id)
                    val toStore = if (existing?.isRead == true) cloud.copy(isRead = true) else cloud
                    announcementDao.insertAnnouncement(toStore)
                }

                // 2) Remove records the admin deleted remotely — but only
                //    records we previously received from the cloud, so local
                //    data is preserved.
                val prevClassIds = prefs.getStringSet(KEY_CLOUD_CLASS_IDS, emptySet()) ?: emptySet()
                val prevAnnIds = prefs.getStringSet(KEY_CLOUD_ANNOUNCEMENT_IDS, emptySet()) ?: emptySet()
                val newClassIds = cloudClasses.map { it.id }.toSet()
                val newAnnIds = cloudAnnouncements.map { it.id }.toSet()

                (prevClassIds - newClassIds).forEach { classDao.deleteClassById(it) }
                (prevAnnIds - newAnnIds).forEach { announcementDao.deleteAnnouncement(it) }

                // 3) Remember what the cloud contains + when we synced.
                prefs.edit()
                    .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                    .putStringSet(KEY_CLOUD_CLASS_IDS, newClassIds)
                    .putStringSet(KEY_CLOUD_ANNOUNCEMENT_IDS, newAnnIds)
                    .apply()

                val result = SyncUiState.Success(
                    syncedAtEpochMs = System.currentTimeMillis(),
                    classCount = cloudClasses.size,
                    announcementCount = cloudAnnouncements.size
                )
                _state.value = result
                Log.i(TAG, "Cloud sync OK: ${cloudClasses.size} classes, ${cloudAnnouncements.size} notices")
                result
            } catch (e: Exception) {
                val result = SyncUiState.Error(e.message ?: "Cloud sync failed")
                _state.value = result
                Log.e(TAG, "Cloud sync failed", e)
                result
            }
        }

    /** When did the app last talk to the public database? (0 = never) */
    fun lastSyncEpochMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC, 0L)

    // ─────────────────────── Firestore → Room mapping ───────────────────────

    private fun QueryDocumentSnapshot.toClassEntity(): ClassEntity? {
        val sourceDate = getString("sourceDate") ?: return null
        val sourceTimezone = getString("sourceTimezone") ?: TimezoneHelper.BOSNIA_TIMEZONE
        val sourceStartTime = getString("sourceStartTime") ?: return null
        val sourceEndTime = getString("sourceEndTime") ?: sourceStartTime

        val utcStart = getLong("utcStartEpochMs")
            ?: TimezoneHelper.toUtcEpochMs(sourceDate, sourceStartTime, sourceTimezone)
        val utcEnd = getLong("utcEndEpochMs")
            ?: TimezoneHelper.toUtcEpochMs(sourceDate, sourceEndTime, sourceTimezone)

        return ClassEntity(
            id = id,
            subject = getString("subject") ?: "Untitled class",
            classType = getString("classType") ?: "Lecture",
            instructor = getString("instructor") ?: "",
            sourceDate = sourceDate,
            sourceStartTime = sourceStartTime,
            sourceEndTime = sourceEndTime,
            sourceTimezone = sourceTimezone,
            utcStartEpochMs = utcStart,
            utcEndEpochMs = utcEnd,
            groupName = getString("groupName") ?: "All",
            attendanceMode = getString("attendanceMode") ?: ClassEntity.MODE_HYBRID,
            platform = getString("platform") ?: ClassEntity.PLATFORM_TEAMS,
            teamsChannel = getString("teamsChannel") ?: "General",
            locationOrUrl = getString("locationOrUrl") ?: "",
            status = getString("status") ?: ClassEntity.STATUS_SCHEDULED,
            notes = getString("notes") ?: "",
            updatedAtEpochMs = getLong("updatedAtEpochMs") ?: System.currentTimeMillis()
        )
    }

    private fun QueryDocumentSnapshot.toAnnouncementEntity(): AnnouncementEntity =
        AnnouncementEntity(
            id = id,
            title = getString("title") ?: "Notice",
            message = getString("message") ?: "",
            priority = getString("priority") ?: "NORMAL",
            targetGroup = getString("targetGroup") ?: "All",
            createdAtEpochMs = getLong("createdAtEpochMs") ?: System.currentTimeMillis(),
            isRead = getBoolean("isRead") ?: false
        )
}

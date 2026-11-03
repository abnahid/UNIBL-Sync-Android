package com.abnahid.uniblsync.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.abnahid.uniblsync.data.local.AppDatabase
import com.abnahid.uniblsync.data.remote.ApiException
import com.abnahid.uniblsync.data.repository.ClassRepository
import java.io.IOException

class PublicDataSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val database = AppDatabase.getDatabase(applicationContext)
        val apiClient = com.abnahid.uniblsync.data.remote.PublicApiClient(
            client = com.abnahid.uniblsync.data.remote.NetworkClientProvider.getClient(applicationContext)
        )
        ClassRepository(
            database = database,
            classDao = database.classDao(),
            reminderDao = database.reminderDao(),
            announcementDao = database.announcementDao(),
            auditLogDao = database.auditLogDao(),
            visitorDao = database.visitorDao(),
            apiClient = apiClient,
            appContext = applicationContext
        ).refreshPublicData()
        Result.success()
    } catch (error: ApiException) {
        if (error.isTemporary) Result.retry() else Result.failure()
    } catch (_: IOException) {
        Result.retry()
    }
}

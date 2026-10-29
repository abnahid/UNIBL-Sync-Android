package com.example.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.remote.ApiException
import com.example.data.repository.ClassRepository
import java.io.IOException

class PublicDataSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val database = AppDatabase.getDatabase(applicationContext)
        val apiClient = com.example.data.remote.PublicApiClient(
            client = com.example.data.remote.NetworkClientProvider.getClient(applicationContext)
        )
        ClassRepository(
            database = database,
            classDao = database.classDao(),
            reminderDao = database.reminderDao(),
            announcementDao = database.announcementDao(),
            auditLogDao = database.auditLogDao(),
            visitorDao = database.visitorDao(),
            apiClient = apiClient
        ).refreshPublicData()
        Result.success()
    } catch (error: ApiException) {
        if (error.isTemporary) Result.retry() else Result.failure()
    } catch (_: IOException) {
        Result.retry()
    }
}

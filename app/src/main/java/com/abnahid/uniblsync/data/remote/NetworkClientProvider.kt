package com.abnahid.uniblsync.data.remote

import android.content.Context
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClientProvider {
    @Volatile
    private var client: OkHttpClient? = null

    fun getClient(context: Context): OkHttpClient {
        return client ?: synchronized(this) {
            client ?: buildClient(context.applicationContext).also { client = it }
        }
    }

    private fun buildClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            // Public timetable consumption is intentionally unauthenticated.
            // Staff credentials and cookies never enter the Android client.
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "UNIBL-Sync-Android/1.0").build())
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

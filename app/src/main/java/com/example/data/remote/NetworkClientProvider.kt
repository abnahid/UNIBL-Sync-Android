package com.example.data.remote

import android.content.Context
import okhttp3.Interceptor
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
        val sessionManager = SessionManager.getInstance(context)
        val cookieJar = PersistentCookieJar(sessionManager)

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()

            // Attach bearer token if present
            sessionManager.getSessionToken()?.let { token ->
                if (token.isNotBlank() && original.header("Authorization") == null) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }

            requestBuilder.header("User-Agent", "UNIBL-Sync-Android/1.0")
            chain.proceed(requestBuilder.build())
        }

        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

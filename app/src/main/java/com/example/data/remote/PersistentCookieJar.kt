package com.example.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent CookieJar that saves session cookies into SessionManager
 * so that Better Auth (`better-auth.session_token`) and API v1 (`unibl_session`)
 * cookies persist across application restarts.
 */
class PersistentCookieJar(private val sessionManager: SessionManager) : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    init {
        // Hydrate from persistent store
        val saved = sessionManager.getSessionCookies()
        for (cookieHeader in saved) {
            val dummyUrl = HttpUrl.Builder().scheme("https").host("unibl-sync.vercel.app").build()
            Cookie.parse(dummyUrl, cookieHeader)?.let { cookie ->
                val hostCookies = cookieStore.getOrPut(cookie.domain) { ConcurrentHashMap() }
                hostCookies[cookie.name] = cookie
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val hostCookies = cookieStore.getOrPut(url.host) { ConcurrentHashMap() }
        val cookieHeaders = mutableListOf<String>()

        for (cookie in cookies) {
            if (cookie.expiresAt < System.currentTimeMillis()) {
                hostCookies.remove(cookie.name)
            } else {
                hostCookies[cookie.name] = cookie
                cookieHeaders.add(cookie.toString())
            }
        }
        if (cookieHeaders.isNotEmpty()) {
            sessionManager.saveCookies(cookieHeaders)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val hostCookies = cookieStore[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val valid = mutableListOf<Cookie>()
        val expired = mutableListOf<String>()

        for ((name, cookie) in hostCookies) {
            if (cookie.expiresAt < now) {
                expired.add(name)
            } else if (cookie.matches(url)) {
                valid.add(cookie)
            }
        }
        for (name in expired) {
            hostCookies.remove(name)
        }
        return valid
    }

    fun clear() {
        cookieStore.clear()
        sessionManager.clearSession()
    }
}

package com.seb.sdpr.data.remote

import okhttp3.Interceptor
import okhttp3.Response

internal class PlexHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("X-Plex-Product", "Plex Valet")
            .addHeader("X-Plex-Platform", "Android")
            .addHeader("X-Plex-Device", "Android")
            // Keep the controller's legacy identity across the rename, with a single header value.
            .header("X-Plex-Client-Identifier", "sdpr-android-v1")
            .build()
        return chain.proceed(request)
    }
}

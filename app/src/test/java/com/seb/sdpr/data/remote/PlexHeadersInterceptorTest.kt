package com.seb.sdpr.data.remote

import java.util.Collections
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PlexHeadersInterceptorTest {
    private fun intercepted(request: Request): Request {
        lateinit var captured: Request
        val client = OkHttpClient.Builder()
            .addInterceptor(PlexHeadersInterceptor())
            .addInterceptor { chain ->
                captured = chain.request()
                response(captured, "{}")
            }
            .build()
        client.newCall(request).execute().use { }
        return captured
    }

    private fun response(request: Request, body: String): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    @Test
    fun `browse queue and playback all send exactly one consistent controller identifier`() = runBlocking {
        val requests = Collections.synchronizedList(mutableListOf<Request>())
        val client = OkHttpClient.Builder()
            .addInterceptor(PlexHeadersInterceptor())
            .addInterceptor { chain ->
                val request = chain.request()
                requests.add(request)
                val body = when (request.url.encodedPath) {
                    "/playQueues" -> """{"MediaContainer":{"playQueueID":42}}"""
                    else -> """{"MediaContainer":{"machineIdentifier":"server-123"}}"""
                }
                response(request, body)
            }
            .build()
        try {
            val api = Retrofit.Builder()
                .baseUrl("http://plex.test/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PlexApi::class.java)

            api.getServerInfo("http://plex.test/", "test-token")
            assertEquals(42L, api.createPlayQueue("http://plex.test/playQueues", "test-token").mediaContainer?.playQueueID)
            api.playMedia("http://plex.test/player/playback/playMedia", "test-token", "tv-123").close()

            assertEquals(3, requests.size)
            for (request in requests) {
                assertEquals(listOf("sdpr-android-v1"), request.headers.values("X-Plex-Client-Identifier"))
                assertEquals("test-token", request.header("X-Plex-Token"))
            }
            assertNull(requests[0].header("X-Plex-Target-Client-Identifier"))
            assertNull(requests[1].header("X-Plex-Target-Client-Identifier"))
            assertEquals("tv-123", requests[2].header("X-Plex-Target-Client-Identifier"))
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun `stale and duplicate controller identifiers are replaced`() {
        val request = intercepted(Request.Builder()
            .url("http://plex.test/playQueues")
            .addHeader("X-Plex-Client-Identifier", "stale-controller")
            .addHeader("x-plex-client-identifier", "another-controller")
            .build())
        assertEquals(listOf("sdpr-android-v1"), request.headers.values("X-Plex-Client-Identifier"))
    }

    @Test
    fun `the chosen player and authentication token are preserved`() {
        val request = intercepted(Request.Builder()
            .url("http://plex.test/player/playback/playMedia")
            .header("X-Plex-Target-Client-Identifier", "living-room-tv")
            .header("X-Plex-Token", "test-token")
            .build())
        assertEquals("living-room-tv", request.header("X-Plex-Target-Client-Identifier"))
        assertEquals("test-token", request.header("X-Plex-Token"))
    }

    @Test
    fun `ordinary requests keep product headers without acquiring a playback target`() {
        val request = intercepted(Request.Builder().url("http://plex.test/library/sections").build())
        assertEquals("Plex Valet", request.header("X-Plex-Product"))
        assertEquals("Android", request.header("X-Plex-Platform"))
        assertEquals("Android", request.header("X-Plex-Device"))
        assertNull(request.header("X-Plex-Target-Client-Identifier"))
    }
}

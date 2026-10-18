package com.seb.sdpr.ui.result

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ClientDiscoveryErrorTest {
    @Test
    fun `authentication failures direct the user to the token setting`() {
        for (code in listOf(401, 403)) {
            val message = clientDiscoveryErrorMessage(HttpException(Response.error<Any>(code, "Denied".toResponseBody())))
            assertTrue(message.contains("refusé"))
            assertTrue(message.contains("token"))
            assertFalse(message.contains("Aucun lecteur"))
        }
    }

    @Test
    fun `unreachable hosts are reported as connectivity failures`() {
        for (error in listOf(IOException(), ConnectException(), UnknownHostException())) {
            val message = clientDiscoveryErrorMessage(error)
            assertTrue(message.contains("connexion réseau"))
            assertFalse(message.contains("Aucun lecteur"))
        }
    }

    @Test
    fun `timeouts have a specific message despite being IO exceptions`() {
        val message = clientDiscoveryErrorMessage(SocketTimeoutException())
        assertTrue(message.contains("ne répond pas à temps"))
        assertFalse(message.contains("Aucun lecteur"))
    }

    @Test
    fun `server failures report the status without blaming the token`() {
        for (code in listOf(404, 500, 502, 503)) {
            val message = clientDiscoveryErrorMessage(HttpException(Response.error<Any>(code, "Error".toResponseBody())))
            assertTrue(message.contains("HTTP $code"))
            assertFalse(message.contains("token"))
            assertFalse(message.contains("Aucun lecteur"))
        }
    }

    @Test
    fun `invalid connection parameters direct the user to settings`() {
        val message = clientDiscoveryErrorMessage(IllegalArgumentException("Invalid URL"))
        assertTrue(message.contains("Configuration Plex invalide"))
        assertTrue(message.contains("paramètres"))
    }

    @Test
    fun `unexpected failures do not expose raw request details`() {
        val message = clientDiscoveryErrorMessage(IllegalStateException("http://private-server/?X-Plex-Token=secret-token"))
        assertTrue(message.contains("Impossible de récupérer"))
        assertFalse(message.contains("private-server"))
        assertFalse(message.contains("secret-token"))
    }
}

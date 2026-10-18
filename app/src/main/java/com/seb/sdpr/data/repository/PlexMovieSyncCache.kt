package com.seb.sdpr.data.repository

import com.google.gson.Gson
import com.seb.sdpr.data.remote.PlexMovie
import java.security.MessageDigest

/** A versioned fingerprint of the list metadata; no credentials are persisted. */
internal class PlexMovieSyncCache(private val serverId: String?) {
    private val gson = Gson()

    fun fingerprint(sectionId: String, movie: PlexMovie): String? {
        // Without a server identity or modification marker, always re-read details.
        if (serverId.isNullOrBlank() || (movie.updatedAt ?: 0L) <= 0L) return null
        // Watch counts change independently of metadata and are updated on every sync.
        val snapshot = gson.toJson(movie.copy(viewCount = null))
        val bytes = gson.toJson(listOf("plex-valet-details-v1", serverId, sectionId, snapshot))
            .toByteArray(Charsets.UTF_8)
        val hex = "0123456789abcdef"
        return buildString(64) {
            for (byte in MessageDigest.getInstance("SHA-256").digest(bytes)) {
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}

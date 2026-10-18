package com.seb.sdpr.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey val ratingKey: String,
    val title: String,
    val year: Int?,
    val durationMs: Long?,
    val director: String?,
    val contentRating: String?,
    val genres: String,
    val collections: String,
    val rating: Float?,
    val audienceRating: Float?,
    val summary: String?,
    val thumbPath: String?,
    val audioLanguages: String,
    val subtitleLanguages: String,
    val resolution: String?,
    val hdrFormat: String?,
    val bitrateKbps: Int?,
    val viewCount: Int,
    val lastSyncedAt: Long,
    val rottenTomatoesRating: Float? = null,
    val imdbRating: Float? = null,
    val syncFingerprint: String? = null
)

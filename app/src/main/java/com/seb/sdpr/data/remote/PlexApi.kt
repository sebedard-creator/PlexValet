package com.seb.sdpr.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Url

interface PlexApi {
    @Headers("Accept: application/json")
    @GET
    suspend fun getServerInfo(
        @Url url: String,
        @Header("X-Plex-Token") token: String
    ): PlexServerResponse

    @Headers("Accept: application/json")
    @GET
    suspend fun getSections(
        @Url url: String,
        @Header("X-Plex-Token") token: String
    ): PlexSectionsResponse

    @Headers("Accept: application/json")
    @GET
    suspend fun getMovies(
        @Url url: String,
        @Header("X-Plex-Token") token: String
    ): PlexMoviesResponse

    @Headers("Accept: application/json")
    @GET
    suspend fun getCollections(
        @Url url: String,
        @Header("X-Plex-Token") token: String
    ): PlexCollectionsResponse

    @Headers("Accept: application/json")
    @GET
    suspend fun getClients(
        @Url url: String,
        @Header("X-Plex-Token") token: String
    ): PlexClientsResponse

    @GET
    suspend fun getClientsRaw(
        @Url url: String,
        @Header("X-Plex-Token") token: String
    ): okhttp3.ResponseBody

    @Headers("Accept: application/json")
    @retrofit2.http.POST
    suspend fun createPlayQueue(
        @Url url: String,
        @Header("X-Plex-Token") token: String
    ): PlexPlayQueueResponse

    @Headers("Accept: application/json")
    @GET
    suspend fun playMedia(
        @Url url: String,
        @Header("X-Plex-Token") token: String,
        @Header("X-Plex-Target-Client-Identifier") targetClientIdentifier: String
    ): okhttp3.ResponseBody
}

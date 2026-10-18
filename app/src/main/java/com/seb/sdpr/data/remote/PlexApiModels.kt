package com.seb.sdpr.data.remote

import com.google.gson.annotations.SerializedName

data class PlexServerResponse(
    @SerializedName("MediaContainer") val mediaContainer: ServerContainer?
)
data class ServerContainer(
    @SerializedName("machineIdentifier") val machineIdentifier: String?,
    @SerializedName("friendlyName") val friendlyName: String?
)

data class PlexSectionsResponse(
    @SerializedName("MediaContainer") val mediaContainer: SectionsContainer?
)
data class SectionsContainer(
    @SerializedName("Directory") val directories: List<PlexSection>?
)
data class PlexSection(
    @SerializedName("key")   val key: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("type")  val type: String?
)

data class PlexMoviesResponse(
    @SerializedName("MediaContainer") val mediaContainer: MoviesContainer?
)
data class MoviesContainer(
    @SerializedName("size")      val size: Int?,
    @SerializedName("totalSize") val totalSize: Int?,
    @SerializedName("Metadata")  val metadata: List<PlexMovie>?
)

data class PlexCollectionsResponse(
    @SerializedName("MediaContainer") val mediaContainer: CollectionsContainer?
)

data class CollectionsContainer(
    @SerializedName("size")      val size: Int?,
    @SerializedName("totalSize") val totalSize: Int?,
    @SerializedName("Metadata")  val metadata: List<PlexCollection>?
)

data class PlexMovie(
    @SerializedName("ratingKey")     val ratingKey: String?,
    @SerializedName("title")         val title: String?,
    @SerializedName("year")          val year: Int?,
    @SerializedName("duration")      val duration: Long?,
    @SerializedName("contentRating") val contentRating: String?,
    @SerializedName("summary")       val summary: String?,
    @SerializedName("thumb")         val thumb: String?,
    @SerializedName("rating")        val rating: Float?,
    @SerializedName("audienceRating")val audienceRating: Float?,
    @SerializedName("viewCount")     val viewCount: Int?,
    @SerializedName("Genre")         val genres: List<PlexTag>?,
    @SerializedName("Collection")    val collections: List<PlexTag>?,
    @SerializedName("Director")      val directors: List<PlexTag>?,
    @SerializedName("Media")         val media: List<PlexMedia>?,
    @SerializedName("ratingImage")   val ratingImage: String? = null,
    @SerializedName("audienceRatingImage") val audienceRatingImage: String? = null,
    @SerializedName("Rating")        val ratings: List<PlexRating>? = null,
    @SerializedName("updatedAt")     val updatedAt: Long? = null,
    @SerializedName("addedAt")       val addedAt: Long? = null,
    @SerializedName("guid")          val guid: String? = null
)

data class PlexRating(
    @SerializedName("image") val image: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("value") val value: Float?
)

data class PlexCollection(
    @SerializedName("ratingKey") val ratingKey: String?,
    @SerializedName("content")   val content: String?,
    @SerializedName("title")     val title: String?,
    @SerializedName("smart")     val smart: Boolean?
)
data class PlexTag(@SerializedName("tag") val tag: String?)
data class PlexMedia(
    @SerializedName("bitrate")         val bitrate: Int?,
    @SerializedName("videoDynamicRange") val videoDynamicRange: String?,
    @SerializedName("videoDynamicRangeDisplay") val videoDynamicRangeDisplay: String?,
    @SerializedName("hdr")             val hdr: String?,
    @SerializedName("videoResolution") val videoResolution: String?,
    @SerializedName("Part")            val parts: List<PlexPart>?,
    @SerializedName("id")              val id: String? = null
)
data class PlexPart(
    @SerializedName("Stream") val streams: List<PlexStream>?,
    @SerializedName("id") val id: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("size") val size: Long? = null
)
data class PlexStream(
    @SerializedName("streamType")  val streamType: Int?,
    @SerializedName("language")    val language: String?,
    @SerializedName("languageTag") val languageTag: String?,
    @SerializedName("displayTitle") val displayTitle: String?,
    @SerializedName("extendedDisplayTitle") val extendedDisplayTitle: String?
)

data class PlexClientsResponse(
    @SerializedName("MediaContainer") val mediaContainer: ClientsContainer?
)

data class ClientsContainer(
    @SerializedName("Server") val servers: List<PlexClient>?
)

data class PlexClient(
    @SerializedName("name") val name: String?,
    @SerializedName("address") val address: String?,
    @SerializedName("machineIdentifier") val machineIdentifier: String?,
    @SerializedName("product") val product: String?,
    @SerializedName("deviceClass") val deviceClass: String?,
    @SerializedName("state") val state: String?
)

data class PlexPlayQueueResponse(
    @SerializedName("MediaContainer") val mediaContainer: PlayQueueContainer?
)

data class PlayQueueContainer(
    @SerializedName("playQueueID") val playQueueID: Long?,
    @SerializedName("playQueueSelectedItemID") val playQueueSelectedItemID: Long?
)

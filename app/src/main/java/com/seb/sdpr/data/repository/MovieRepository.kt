package com.seb.sdpr.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.seb.sdpr.data.local.MovieDao
import com.seb.sdpr.data.local.MovieEntity
import com.seb.sdpr.data.remote.PlexApi
import com.seb.sdpr.data.remote.PlexClient
import com.seb.sdpr.data.remote.PlexMovie
import com.seb.sdpr.domain.model.Movie
import com.seb.sdpr.domain.model.LibrarySyncProgress
import com.seb.sdpr.domain.model.LibrarySyncStage
import com.seb.sdpr.util.normalizeLanguageCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieRepository @Inject constructor(
    private val plexApi: PlexApi,
    private val movieDao: MovieDao
) {
    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type
    private val detailsLoader = PlexMovieDetailsLoader()

    fun getAllMovies(): Flow<List<Movie>> = movieDao.getAllMovies().map { it.map(::toDomain) }
    suspend fun getAllMoviesOnce(): List<Movie> = movieDao.getAllMoviesOnce().map(::toDomain)
    suspend fun getMovieByKey(key: String): Movie? = movieDao.getByKey(key)?.let(::toDomain)
    suspend fun getCount(): Int = movieDao.getCount()
    suspend fun getMoviesByKeys(keys: Collection<String>): List<Movie> {
        val keySet = keys.toSet()
        return getAllMoviesOnce().filter { it.ratingKey in keySet }
    }

    suspend fun syncLibrary(
        serverIp: String,
        token: String,
        forceFull: Boolean = false,
        onProgress: (LibrarySyncProgress) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(LibrarySyncProgress())
            val startedAt = System.nanoTime()
            val base = "http://$serverIp:32400"
            val serverId = runCatching {
                plexApi.getServerInfo("$base/", token).mediaContainer?.machineIdentifier
            }.onFailure { if (it is CancellationException) throw it }.getOrNull()
            val syncCache = PlexMovieSyncCache(serverId)
            val cachedByKey = if (forceFull) emptyMap() else movieDao.getAllMoviesOnce().associateBy { it.ratingKey }
            var reusedCount = 0

            val sections = plexApi.getSections("$base/library/sections", token)
                .mediaContainer?.directories
                ?.filter { it.type == "movie" }
                ?: error("Aucune section de films trouvée")
            check(sections.isNotEmpty()) {
                "Aucune section de films trouvée. Bibliothèque locale conservée."
            }

            val all = mutableListOf<MovieEntity>()

            for ((sectionIndex, section) in sections.withIndex()) {
                fun report(stage: LibrarySyncStage, completed: Int = 0, total: Int? = null) {
                    onProgress(LibrarySyncProgress(stage, completed, total, sectionIndex + 1, sections.size))
                }
                val id = section.key?.takeIf { it.isNotBlank() }
                    ?: error("Section de films sans identifiant. Bibliothèque locale conservée.")
                val pageSize = 1000
                val sectionMovies = linkedMapOf<String, MovieEntity>()
                val pagination = PlexMoviePageValidator(section.title ?: id)

                while (true) {
                    report(LibrarySyncStage.MOVIES, sectionMovies.size, pagination.total)
                    val url = "$base/library/sections/$id/all?type=1&X-Plex-Container-Start=${pagination.nextStart}&X-Plex-Container-Size=$pageSize"
                    val resp = plexApi.getMovies(url, token)
                    val batch = pagination.validate(resp.mediaContainer)
                    val previousCount = sectionMovies.size
                    report(LibrarySyncStage.MOVIES, previousCount, pagination.total)
                    val fingerprints = batch.associate { checkNotNull(it.ratingKey) to syncCache.fingerprint(id, it) }
                    val reusable = batch.mapNotNull { movie ->
                        val key = checkNotNull(movie.ratingKey)
                        val fingerprint = fingerprints[key]
                        cachedByKey[key]?.takeIf { fingerprint != null && it.syncFingerprint == fingerprint }
                            ?.let { key to it }
                    }.toMap()
                    reusedCount += reusable.size
                    if (reusable.isNotEmpty()) {
                        report(LibrarySyncStage.MOVIES, previousCount + reusable.size, pagination.total)
                    }
                    val detailedBatch = detailsLoader.load(batch.filter { it.ratingKey !in reusable }, onProgress = { completed ->
                        report(LibrarySyncStage.MOVIES, previousCount + reusable.size + completed, pagination.total)
                    }) { ratingKey ->
                        plexApi.getMovies("$base/library/metadata/$ratingKey", token)
                            .mediaContainer
                            ?.metadata
                            ?.firstOrNull()
                    }

                    val detailsByKey = detailedBatch.associate { it.first.ratingKey to it.second }
                    for (movie in batch) {
                        val ratingKey = checkNotNull(movie.ratingKey)
                        val title = checkNotNull(movie.title)
                        val cached = reusable[ratingKey]
                        if (cached != null) {
                            sectionMovies[ratingKey] = cached.copy(
                                viewCount = movie.viewCount ?: 0,
                                lastSyncedAt = System.currentTimeMillis()
                            )
                            continue
                        }
                        val detailedMovie = detailsByKey[ratingKey]

                        val genres = resolveGenres(
                            detailedMovie = detailedMovie,
                            fallbackMovie = movie
                        )
                        val collections = resolveCollections(detailedMovie, movie)
                        val langs = resolveAudioLanguages(
                            detailedMovie = detailedMovie,
                            fallbackMovie = movie
                        )
                        val subtitleLangs = resolveSubtitleLanguages(
                            detailedMovie = detailedMovie,
                            fallbackMovie = movie
                        )
                        val director = resolveDirector(detailedMovie, movie)
                        val hdrFormat = resolveHdrFormat(detailedMovie, movie)
                        val bitrateKbps = resolveBitrateKbps(detailedMovie, movie)
                        val scores = resolvePlexMovieScores(detailedMovie, movie)

                        val res = when ((detailedMovie?.media?.firstOrNull()?.videoResolution ?: movie.media?.firstOrNull()?.videoResolution)?.lowercase()) {
                            "4k", "2160" -> "4K"
                            "1080"       -> "1080p"
                            "720"        -> "720p"
                            else         -> "SD"
                        }

                        sectionMovies[ratingKey] = MovieEntity(
                            ratingKey      = ratingKey,
                            title          = title,
                            year           = movie.year,
                            durationMs     = movie.duration,
                            director       = director,
                            contentRating  = movie.contentRating,
                            genres         = gson.toJson(genres),
                            collections    = gson.toJson(collections),
                            rating         = movie.rating,
                            audienceRating = movie.audienceRating,
                            summary        = movie.summary,
                            thumbPath      = movie.thumb,
                            audioLanguages = gson.toJson(langs),
                            subtitleLanguages = gson.toJson(subtitleLangs),
                            resolution     = res,
                            hdrFormat      = hdrFormat,
                            bitrateKbps    = bitrateKbps,
                            viewCount      = movie.viewCount ?: 0,
                            lastSyncedAt   = System.currentTimeMillis(),
                            rottenTomatoesRating = scores.rottenTomatoesCritics,
                            imdbRating     = scores.imdb,
                            // A failed detail request must be retried on the next refresh.
                            syncFingerprint = fingerprints[ratingKey].takeIf { detailedMovie != null }
                        )
                    }

                    if (pagination.isComplete) break
                }

                enrichWithLibraryCollections(
                    base = base,
                    token = token,
                    sectionId = id,
                    sectionMovies = sectionMovies,
                    onProgress = { completed, total -> report(LibrarySyncStage.COLLECTIONS, completed, total) }
                )

                all += sectionMovies.values
            }

            currentCoroutineContext().ensureActive()
            onProgress(LibrarySyncProgress(LibrarySyncStage.SAVING))
            movieDao.replaceAll(all)
            val durationMs = (System.nanoTime() - startedAt) / 1_000_000
            Log.i(
                "PlexSync",
                "Bibliothèque synchronisée : ${all.size} films en ${durationMs} ms " +
                    "($reusedCount réutilisés, ${all.size - reusedCount} détails relus ; ${if (forceFull) "complet" else "rapide"}) " +
                    "(détails : ${PlexMovieDetailsLoader.MAX_CONCURRENT_DETAILS} requêtes simultanées max)"
            )
            all.size
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }
    }

    suspend fun fetchCollectionCatalog(
        serverIp: String,
        token: String,
        onProgress: (LibrarySyncProgress) -> Unit = {}
    ): List<PlexCollectionCatalogItem> {
        onProgress(LibrarySyncProgress(LibrarySyncStage.CATALOG))
        val base = "http://$serverIp:32400"
        val sections = plexApi.getSections("$base/library/sections", token)
            .mediaContainer?.directories
            ?.filter { it.type == "movie" }
            .orEmpty()

        val catalog = linkedMapOf<String, PlexCollectionCatalogItem>()

        for ((sectionIndex, section) in sections.withIndex()) {
            fun report(completed: Int = 0, total: Int? = null) {
                onProgress(LibrarySyncProgress(LibrarySyncStage.CATALOG, completed, total, sectionIndex + 1, sections.size))
            }
            currentCoroutineContext().ensureActive()
            report()
            val sectionId = section.key ?: continue
            val collections = runCatching {
                plexApi.getCollections("$base/library/sections/$sectionId/all?type=18", token)
                    .mediaContainer
                    ?.metadata
                    .orEmpty()
            }.onFailure { if (it is CancellationException) throw it }.getOrDefault(emptyList())

            report(0, collections.size)
            collections.forEachIndexed { index, collection ->
                currentCoroutineContext().ensureActive()
                val title = collection.title?.trim().orEmpty()
                val ratingKey = collection.ratingKey
                if (ratingKey == null || title.isBlank()) {
                    report(index + 1, collections.size)
                    return@forEachIndexed
                }

                val detailedCollection = runCatching {
                    plexApi.getCollections("$base/library/metadata/$ratingKey", token)
                        .mediaContainer
                        ?.metadata
                        ?.firstOrNull()
                }.onFailure { if (it is CancellationException) throw it }.getOrNull()

                catalog[title.lowercase()] = PlexCollectionCatalogItem(
                    title = title,
                    ratingKey = ratingKey,
                    smart = (detailedCollection?.smart ?: collection.smart) == true,
                    content = detailedCollection?.content ?: collection.content,
                    sectionId = sectionId
                )
                report(index + 1, collections.size)
            }
        }

        return catalog.values.sortedBy { it.title.lowercase() }
    }

    suspend fun getMoviesFromPlexCollection(
        serverIp: String,
        token: String,
        collectionRatingKey: String,
        content: String? = null
    ): List<Movie> {
        val base = "http://$serverIp:32400"
        val members = if (!content.isNullOrBlank()) {
            val contentUrl = toPlexHttpUrl(base, content)

            runCatching {
                plexApi.getMovies(contentUrl, token)
                    .mediaContainer
                    ?.metadata
                    .orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val fallbackMembers = if (members.isEmpty()) {
            runCatching {
                plexApi.getMovies("$base/library/metadata/$collectionRatingKey/children", token)
                    .mediaContainer
                    ?.metadata
                    .orEmpty()
            }.getOrDefault(emptyList())
        } else {
            members
        }

        val memberKeys = fallbackMembers.mapNotNull { it.ratingKey }.distinct()
        if (memberKeys.isEmpty()) return emptyList()

        return getMoviesByKeys(memberKeys)
    }

    suspend fun buildSmartCollectionDiagnostic(
        serverIp: String,
        token: String,
        collection: PlexCollectionCatalogItem
    ): String {
        val base = "http://$serverIp:32400"
        val detailedCollection = runCatching {
            plexApi.getCollections("$base/library/metadata/${collection.ratingKey}", token)
                .mediaContainer
                ?.metadata
                ?.firstOrNull()
        }.getOrNull()

        val effectiveContent = detailedCollection?.content ?: collection.content

        val contentMembers = if (!effectiveContent.isNullOrBlank()) {
            val contentUrl = toPlexHttpUrl(base, effectiveContent)
            runCatching {
                plexApi.getMovies(contentUrl, token)
                    .mediaContainer
                    ?.metadata
                    .orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val childrenMembers = runCatching {
            plexApi.getMovies("$base/library/metadata/${collection.ratingKey}/children", token)
                .mediaContainer
                ?.metadata
                .orEmpty()
        }.getOrDefault(emptyList())

        val contentKeys = contentMembers.mapNotNull { it.ratingKey }.take(10)
        val childrenKeys = childrenMembers.mapNotNull { it.ratingKey }.take(10)
        val localMatches = getMoviesByKeys((contentKeys + childrenKeys).distinct()).map { it.title }.take(10)

        return buildString {
            appendLine("Diagnostic smart collection")
            appendLine("title=${collection.title}")
            appendLine("ratingKey=${collection.ratingKey}")
            appendLine("catalog.smart=${collection.smart}")
            appendLine("catalog.content=${collection.content ?: "<null>"}")
            appendLine("detail.smart=${detailedCollection?.smart}")
            appendLine("detail.content=${detailedCollection?.content ?: "<null>"}")
            appendLine("detail.content.http=${effectiveContent?.let { toPlexHttpUrl(base, it) } ?: "<null>"}")
            appendLine("content.count=${contentMembers.size}")
            appendLine("content.keys=${if (contentKeys.isEmpty()) "<none>" else contentKeys.joinToString()}")
            appendLine("children.count=${childrenMembers.size}")
            appendLine("children.keys=${if (childrenKeys.isEmpty()) "<none>" else childrenKeys.joinToString()}")
            appendLine("local.matches=${if (localMatches.isEmpty()) "<none>" else localMatches.joinToString()}")
        }.trim()
    }

    private fun toPlexHttpUrl(base: String, raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed

        val libraryPath = "/library/"
        val libraryIndex = trimmed.indexOf(libraryPath)
        if (libraryIndex >= 0) {
            return "$base${trimmed.substring(libraryIndex)}"
        }

        return "$base${if (trimmed.startsWith("/")) trimmed else "/$trimmed"}"
    }

    private fun toDomain(e: MovieEntity): Movie {
        val genres: List<String> = gson.fromJson(e.genres, listType) ?: emptyList()
        val collections: List<String> = gson.fromJson(e.collections, listType) ?: emptyList()
        val langs: List<String>  = (gson.fromJson<List<String>>(e.audioLanguages, listType) ?: emptyList())
            .map(::normalizeLanguageCode)
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("fr") }
        val subtitles: List<String> = (gson.fromJson<List<String>>(e.subtitleLanguages, listType) ?: emptyList())
            .map(::normalizeLanguageCode)
            .filter { it.isNotBlank() }
        return Movie(
            ratingKey      = e.ratingKey,
            title          = e.title,
            year           = e.year,
            durationMs     = e.durationMs,
            director       = e.director,
            contentRating  = e.contentRating,
            genres         = genres,
            collections    = collections,
            rating         = e.rating,
            audienceRating = e.audienceRating,
            summary        = e.summary,
            thumbPath      = e.thumbPath,
            audioLanguages = langs,
            subtitleLanguages = subtitles,
            resolution     = e.resolution,
            hdrFormat      = e.hdrFormat,
            bitrateKbps    = e.bitrateKbps,
            viewCount      = e.viewCount,
            lastSyncedAt   = e.lastSyncedAt,
            rottenTomatoesRating = e.rottenTomatoesRating,
            imdbRating     = e.imdbRating
        )
    }

    private fun resolveAudioLanguages(
        detailedMovie: PlexMovie?,
        fallbackMovie: PlexMovie
    ): List<String> {
        return extractAudioLanguages(detailedMovie)
            ?: extractAudioLanguages(fallbackMovie)
            ?: listOf("fr")
    }

    private fun resolveGenres(
        detailedMovie: PlexMovie?,
        fallbackMovie: PlexMovie
    ): List<String> {
        return extractGenres(detailedMovie)
            ?: extractGenres(fallbackMovie)
            ?: emptyList()
    }

    private fun resolveCollections(
        detailedMovie: PlexMovie?,
        fallbackMovie: PlexMovie
    ): List<String> {
        return extractCollections(detailedMovie)
            ?: extractCollections(fallbackMovie)
            ?: emptyList()
    }

    private fun resolveSubtitleLanguages(
        detailedMovie: PlexMovie?,
        fallbackMovie: PlexMovie
    ): List<String> {
        return extractSubtitleLanguages(detailedMovie)
            ?: extractSubtitleLanguages(fallbackMovie)
            ?: emptyList()
    }

    private fun resolveDirector(
        detailedMovie: PlexMovie?,
        fallbackMovie: PlexMovie
    ): String? {
        return extractDirector(detailedMovie)
            ?: extractDirector(fallbackMovie)
    }

    private fun resolveBitrateKbps(
        detailedMovie: PlexMovie?,
        fallbackMovie: PlexMovie
    ): Int? {
        return extractBitrateKbps(detailedMovie)
            ?: extractBitrateKbps(fallbackMovie)
    }

    private fun resolveHdrFormat(
        detailedMovie: PlexMovie?,
        fallbackMovie: PlexMovie
    ): String? {
        return extractHdrFormat(detailedMovie)
            ?: extractHdrFormat(fallbackMovie)
    }

    private fun extractAudioLanguages(movie: PlexMovie?): List<String>? {
        val languages = movie?.media
            ?.flatMap { it.parts ?: emptyList() }
            ?.flatMap { it.streams ?: emptyList() }
            ?.filter { it.streamType == 2 }
            ?.map { stream -> normalizeLanguageCode(stream.language ?: stream.languageTag ?: "") }
            ?.filter { it.isNotBlank() }
            ?.distinct()

        return languages?.takeIf { it.isNotEmpty() }
    }

    private fun extractGenres(movie: PlexMovie?): List<String>? {
        val genres = movie?.genres
            ?.mapNotNull { it.tag?.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()

        return genres?.takeIf { it.isNotEmpty() }
    }

    private fun extractCollections(movie: PlexMovie?): List<String>? {
        val collections = movie?.collections
            ?.mapNotNull { it.tag?.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()

        return collections?.takeIf { it.isNotEmpty() }
    }

    private fun extractSubtitleLanguages(movie: PlexMovie?): List<String>? {
        val languages = movie?.media
            ?.flatMap { it.parts ?: emptyList() }
            ?.flatMap { it.streams ?: emptyList() }
            ?.filter { it.streamType == 3 }
            ?.map { stream -> normalizeLanguageCode(stream.language ?: stream.languageTag ?: "") }
            ?.filter { it.isNotBlank() }
            ?.distinct()

        return languages?.takeIf { it.isNotEmpty() }
    }

    private fun extractDirector(movie: PlexMovie?): String? =
        movie?.directors
            ?.mapNotNull { it.tag?.trim() }
            ?.firstOrNull { it.isNotBlank() }

    private fun extractBitrateKbps(movie: PlexMovie?): Int? =
        movie?.media
            ?.mapNotNull { it.bitrate }
            ?.maxOrNull()

    private fun extractHdrFormat(movie: PlexMovie?): String? {
        val media = movie?.media.orEmpty()

        media.mapNotNull { normalizeHdrLabel(it.videoDynamicRangeDisplay) }
            .firstOrNull()
            ?.let { return it }

        media.mapNotNull { normalizeHdrLabel(it.videoDynamicRange) }
            .firstOrNull()
            ?.let { return it }

        media.mapNotNull { normalizeHdrLabel(it.hdr) }
            .firstOrNull()
            ?.let { return it }

        return media
            .flatMap { it.parts ?: emptyList() }
            .flatMap { it.streams ?: emptyList() }
            .filter { it.streamType == 1 }
            .mapNotNull { stream ->
                normalizeHdrLabel(stream.extendedDisplayTitle)
                    ?: normalizeHdrLabel(stream.displayTitle)
            }
            .firstOrNull()
    }

    private fun normalizeHdrLabel(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        val upper = value.uppercase()

        return when {
            "DOVI" in upper || "DOLBY VISION" in upper -> "DOVI"
            "HDR10+" in upper -> "HDR10+"
            "HDR10" in upper -> "HDR10"
            "HLG" in upper -> "HLG"
            "HDR" in upper -> "HDR"
            else -> null
        }
    }

    private suspend fun enrichWithLibraryCollections(
        base: String,
        token: String,
        sectionId: String,
        sectionMovies: MutableMap<String, MovieEntity>,
        onProgress: (Int, Int?) -> Unit
    ) {
        onProgress(0, null)
        val collectionsUrl = "$base/library/sections/$sectionId/all?type=18"
        val collections = try {
            val container = plexApi.getCollections(collectionsUrl, token).mediaContainer ?: return
            val entries = container.metadata.orEmpty()
            check(container.size == null || container.size == entries.size)
            check(container.totalSize == null || container.totalSize == entries.size)
            entries
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the previous memberships if the collection list is unavailable.
            return
        }

        onProgress(0, collections.size)
        // Rebuild memberships, including removals, even for movies whose details were reused.
        val refreshedCollections = sectionMovies.keys.associateWith { mutableSetOf<String>() }
        var complete = true
        for ((index, collection) in collections.withIndex()) {
            currentCoroutineContext().ensureActive()
            val collectionName = collection.title?.trim().orEmpty()
            val collectionKey = collection.ratingKey
            if (collectionKey == null || collectionName.isBlank()) {
                complete = false
                onProgress(index + 1, collections.size)
                continue
            }

            val members = try {
                val container = plexApi.getMovies("$base/library/metadata/$collectionKey/children", token)
                    .mediaContainer ?: error("Missing collection members")
                val entries = container.metadata.orEmpty()
                check(container.size == null || container.size == entries.size)
                check(container.totalSize == null || container.totalSize == entries.size)
                entries
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                complete = false
                onProgress(index + 1, collections.size)
                continue
            }

            for (member in members) {
                val memberKey = member.ratingKey ?: continue
                refreshedCollections[memberKey]?.add(collectionName)
            }
            onProgress(index + 1, collections.size)
        }
        if (complete) {
            for ((key, names) in refreshedCollections) {
                sectionMovies[key] = sectionMovies.getValue(key).copy(
                    collections = gson.toJson(names.sortedBy { it.lowercase() })
                )
            }
        }
    }

    suspend fun getClients(serverIp: String, token: String): List<PlexClient> {
        val base = "http://$serverIp:32400"
        val xmlString = plexApi.getClientsRaw("$base/clients", token).use { it.string() }
        val clients = mutableListOf<PlexClient>()
        val regex = "<(?:Server|Device|Player)([^>]+)>".toRegex()
        val matches = regex.findAll(xmlString)
        for (match in matches) {
            val attrs = match.groupValues[1]
            val name = "name=\"([^\"]+)\"".toRegex().find(attrs)?.groupValues?.get(1)
            val machineId = "machineIdentifier=\"([^\"]+)\"".toRegex().find(attrs)?.groupValues?.get(1)
                ?: "clientIdentifier=\"([^\"]+)\"".toRegex().find(attrs)?.groupValues?.get(1)
            val product = "product=\"([^\"]+)\"".toRegex().find(attrs)?.groupValues?.get(1)
            if (name != null && machineId != null) {
                clients.add(PlexClient(name = name, address = null, machineIdentifier = machineId, product = product, deviceClass = null, state = null))
            }
        }
        return clients
    }

    suspend fun castMovie(
        serverIp: String,
        token: String,
        serverMachineId: String,
        ratingKey: String,
        targetClientMachineId: String
    ): Boolean = runCatching {
        val base = "http://$serverIp:32400"

        val itemUri = "library://x/item/%2Flibrary%2Fmetadata%2F$ratingKey"
        val encodedItemUri = java.net.URLEncoder.encode(itemUri, "UTF-8")
        val queueUrl = "$base/playQueues?type=video&uri=$encodedItemUri&shuffle=0&repeat=0"

        val queueResponse = plexApi.createPlayQueue(
            url = queueUrl,
            token = token
        )

        val playQueueId = queueResponse.mediaContainer?.playQueueID ?: error("No play queue created")

        val encodedKey = java.net.URLEncoder.encode("/library/metadata/$ratingKey", "UTF-8")
        val containerKey = java.net.URLEncoder.encode("/playQueues/$playQueueId", "UTF-8")

        val playUrl = "$base/player/playback/playMedia" +
                "?key=$encodedKey" +
                "&offset=0" +
                "&machineIdentifier=$serverMachineId" +
                "&protocol=http" +
                "&containerKey=$containerKey" +
                "&commandID=1"

        plexApi.playMedia(
            url = playUrl,
            token = token,
            targetClientIdentifier = targetClientMachineId
        )
        true
    }.onFailure {
        it.printStackTrace()
    }.getOrDefault(false)
}

data class PlexCollectionCatalogItem(
    val title: String,
    val ratingKey: String,
    val smart: Boolean,
    val content: String?,
    val sectionId: String
)

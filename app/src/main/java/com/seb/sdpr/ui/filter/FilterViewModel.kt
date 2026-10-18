package com.seb.sdpr.ui.filter

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.seb.sdpr.data.PreferenceKeys
import com.seb.sdpr.data.repository.ClaudeMovieAdvisorRepository
import com.seb.sdpr.data.repository.MovieRepository
import com.seb.sdpr.data.repository.PlexCollectionCatalogItem
import com.seb.sdpr.domain.model.Movie
import com.seb.sdpr.domain.model.LibrarySyncProgress
import com.seb.sdpr.domain.model.LibrarySyncStage
import com.seb.sdpr.domain.usecase.GetFilteredMoviesUseCase
import com.seb.sdpr.domain.usecase.GetRandomMovieUseCase
import com.seb.sdpr.domain.usecase.SyncLibraryUseCase
import com.seb.sdpr.util.normalizeLanguageCode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

data class FilterUiState(
    val filterState: FilterState = FilterState(),
    val availableGenres: List<String> = emptyList(),
    val availableCollections: List<String> = emptyList(),
    val availableRatings: List<String> = emptyList(),
    val availableLanguages: List<String> = emptyList(),
    val availableDecades: List<String> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Movie> = emptyList(),
    val movieCount: Int = 0,
    val lastSyncTime: Long = 0L,
    val isSyncing: Boolean = false,
    val syncProgress: LibrarySyncProgress? = null,
    val isAiDialogOpen: Boolean = false,
    val isAiLoading: Boolean = false,
    val aiDraft: String = "",
    val aiMessages: List<AiChatMessage> = emptyList(),
    val aiCandidateCount: Int = 0,
    val isGeneratingMovie: Boolean = false,
    val isGeneratingPlaylist: Boolean = false,
    val syncError: String? = null,
    val noResults: Boolean = false,
    val noResultsMessage: String? = null,
    val isConfigured: Boolean = false
)

@HiltViewModel
class FilterViewModel @Inject constructor(
    private val syncUseCase: SyncLibraryUseCase,
    private val filteredMoviesUseCase: GetFilteredMoviesUseCase,
    private val randomMovieUseCase: GetRandomMovieUseCase,
    private val repository: MovieRepository,
    private val claudeMovieAdvisorRepository: ClaudeMovieAdvisorRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val gson = Gson()
    private val collectionCatalogType = object : TypeToken<List<PlexCollectionCatalogItem>>() {}.type
    private val _ui = MutableStateFlow(FilterUiState())
    val uiState: StateFlow<FilterUiState> = _ui.asStateFlow()

    private var collectionCatalog: List<PlexCollectionCatalogItem> = emptyList()
    private var allMoviesCache: List<Movie> = emptyList()
    private var aiCandidateCache: List<Movie> = emptyList()
    private var aiFilterSnapshot: FilterState? = null

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                collectionCatalog = parseCollectionCatalog(prefs[PreferenceKeys.COLLECTION_CATALOG])
                _ui.update {
                    it.copy(
                        isConfigured = !prefs[PreferenceKeys.SERVER_IP].isNullOrBlank() &&
                            !prefs[PreferenceKeys.PLEX_TOKEN].isNullOrBlank(),
                        lastSyncTime = prefs[PreferenceKeys.LAST_SYNC] ?: 0L
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.getAllMovies().collect { movies ->
                allMoviesCache = movies

                val genres = movies.flatMap { it.genres }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                    .sortedBy { it.lowercase() }

                val collections = (movies.flatMap { it.collections } + collectionCatalog.map { it.title })
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                    .sortedBy { it.lowercase() }

                val ratings = movies.mapNotNull { it.contentRating?.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                    .sortedBy { it.lowercase() }

                val languages = movies.flatMap { it.audioLanguages }
                    .map(::normalizeLanguageCode)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sortedBy { it.lowercase() }

                val decades = movies.mapNotNull { it.decade }
                    .distinct()
                    .sortedBy { it.dropLast(1).toIntOrNull() ?: 0 }

                val searchResults = computeSearchResults(_ui.value.searchQuery, movies)

                _ui.update {
                    it.copy(
                        availableGenres = genres,
                        availableCollections = collections,
                        availableRatings = ratings,
                        availableLanguages = languages,
                        availableDecades = decades,
                        searchResults = searchResults,
                        movieCount = movies.size
                    )
                }
            }
        }
    }

    fun sync(forceFull: Boolean = false) {
        if (_ui.value.isSyncing) return
        _ui.update { it.copy(isSyncing = true, syncError = null, syncProgress = LibrarySyncProgress()) }
        viewModelScope.launch {
            val startedAt = System.nanoTime()
            try {
                val prefs = dataStore.data.first()
                val ip = prefs[PreferenceKeys.SERVER_IP].orEmpty()
                val token = prefs[PreferenceKeys.PLEX_TOKEN].orEmpty()
                check(ip.isNotBlank() && token.isNotBlank()) {
                    "Configure d'abord le serveur dans les parametres"
                }
                val report: (LibrarySyncProgress) -> Unit = { progress ->
                    _ui.update { it.copy(syncProgress = progress) }
                }
                syncUseCase(ip, token, forceFull, report).getOrThrow()
                val catalog = repository.fetchCollectionCatalog(ip, token, report)
                report(LibrarySyncProgress(LibrarySyncStage.FINISHING))
                val now = System.currentTimeMillis()
                collectionCatalog = catalog
                dataStore.edit { prefsEdit ->
                    prefsEdit[PreferenceKeys.LAST_SYNC] = now
                    prefsEdit[PreferenceKeys.COLLECTION_CATALOG] = gson.toJson(catalog)
                }
                _ui.update { state ->
                    state.copy(
                        lastSyncTime = now,
                        availableCollections = (state.availableCollections + catalog.map { item -> item.title })
                            .map { value -> value.trim() }
                            .filter { value -> value.isNotBlank() }
                            .distinctBy { value -> value.lowercase() }
                            .sortedBy { value -> value.lowercase() }
                    )
                }
                Log.i("PlexSync", "Refresh terminé (${if (forceFull) "complet" else "rapide"}) en ${(System.nanoTime() - startedAt) / 1_000_000} ms, catalogue compris")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(syncError = error.message ?: "Erreur sync") }
            } finally {
                _ui.update { it.copy(isSyncing = false, syncProgress = null) }
            }
        }
    }

    fun generateMovie(onResult: (String, FilterState) -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(isGeneratingMovie = true, noResults = false, noResultsMessage = null) }
            val filterState = _ui.value.filterState
            val prefs = dataStore.data.first()
            val collectionInfo = filterState.selectedCollection?.let { selected ->
                collectionCatalog.firstOrNull { it.title.equals(selected, ignoreCase = true) }
            }
            val ip = prefs[PreferenceKeys.SERVER_IP] ?: ""
            val token = prefs[PreferenceKeys.PLEX_TOKEN] ?: ""

            val movie = if (collectionInfo != null) {
                if (ip.isBlank() || token.isBlank()) {
                    null
                } else {
                    val collectionMovies = repository.getMoviesFromPlexCollection(
                        serverIp = ip,
                        token = token,
                        collectionRatingKey = collectionInfo.ratingKey,
                        content = collectionInfo.content
                    )
                    randomMovieUseCase(
                        filter = filterState,
                        sourceMovies = collectionMovies,
                        ignoreCollectionFilter = true
                    )
                }
            } else {
                randomMovieUseCase(filterState)
            }

            _ui.update { it.copy(isGeneratingMovie = false) }

            if (movie != null) {
                onResult(movie.ratingKey, filterState)
            } else {
                val diagnostic = if (collectionInfo != null && ip.isNotBlank() && token.isNotBlank()) {
                    repository.buildSmartCollectionDiagnostic(
                        serverIp = ip,
                        token = token,
                        collection = collectionInfo
                    )
                } else {
                    null
                }
                _ui.update {
                    it.copy(
                        noResults = true,
                        noResultsMessage = diagnostic
                    )
                }
            }
        }
    }

    fun generatePlaylist(onResult: (FilterState) -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(isGeneratingPlaylist = true, noResults = false, noResultsMessage = null) }
            val filterState = _ui.value.filterState
            val movies = resolveFilteredMovies(filterState)

            _ui.update { it.copy(isGeneratingPlaylist = false) }

            if (movies.isNotEmpty()) {
                onResult(filterState)
            } else {
                val prefs = dataStore.data.first()
                val collectionInfo = filterState.selectedCollection?.let { selected ->
                    collectionCatalog.firstOrNull { it.title.equals(selected, ignoreCase = true) }
                }
                val ip = prefs[PreferenceKeys.SERVER_IP] ?: ""
                val token = prefs[PreferenceKeys.PLEX_TOKEN] ?: ""
                val diagnostic = if (collectionInfo != null && ip.isNotBlank() && token.isNotBlank()) {
                    repository.buildSmartCollectionDiagnostic(
                        serverIp = ip,
                        token = token,
                        collection = collectionInfo
                    )
                } else {
                    null
                }
                _ui.update {
                    it.copy(
                        noResults = true,
                        noResultsMessage = diagnostic
                    )
                }
            }
        }
    }

    fun openAiDialog() {
        if (_ui.value.filterState == FilterState()) return

        _ui.update {
            it.copy(
                isAiDialogOpen = true,
                isAiLoading = true,
                aiDraft = "",
                aiMessages = emptyList(),
                aiCandidateCount = 0
            )
        }

        viewModelScope.launch {
            if (!claudeMovieAdvisorRepository.hasApiKey()) {
                aiCandidateCache = emptyList()
                aiFilterSnapshot = _ui.value.filterState
                _ui.update {
                    it.copy(
                        isAiLoading = false,
                        aiMessages = listOf(
                            AiChatMessage(
                                role = AiChatRole.NOTICE,
                                text = "Cle Anthropic absente. Ajoute `ANTHROPIC_API_KEY=` dans `local.properties`, puis rebuild l'app."
                            )
                        )
                    )
                }
                return@launch
            }

            val filterState = _ui.value.filterState
            val movies = resolveFilteredMovies(filterState)
            aiCandidateCache = movies
            aiFilterSnapshot = filterState

            val notice = when {
                movies.isEmpty() ->
                    "Aucun film ne correspond aux filtres actuels. Ajuste la preselction avant d'utiliser l'agent AI."
                else ->
                    "J'ai ${movies.size} films dans ta preselction. Dis-moi l'ambiance que tu cherches, et je te proposerai les meilleurs matchs."
            }

            _ui.update {
                it.copy(
                    isAiLoading = false,
                    aiCandidateCount = movies.size,
                    aiMessages = listOf(AiChatMessage(role = AiChatRole.NOTICE, text = notice))
                )
            }
        }
    }

    fun closeAiDialog() {
        aiCandidateCache = emptyList()
        aiFilterSnapshot = null
        _ui.update {
            it.copy(
                isAiDialogOpen = false,
                isAiLoading = false,
                aiDraft = "",
                aiMessages = emptyList(),
                aiCandidateCount = 0
            )
        }
    }

    fun setAiDraft(value: String) = _ui.update { it.copy(aiDraft = value) }

    fun sendAiMessage() {
        val prompt = _ui.value.aiDraft.trim()
        if (prompt.isBlank() || _ui.value.isAiLoading) return

        if (!claudeMovieAdvisorRepository.hasApiKey()) {
            _ui.update {
                it.copy(
                    aiDraft = "",
                    aiMessages = it.aiMessages + AiChatMessage(
                        role = AiChatRole.NOTICE,
                        text = "Cle Anthropic absente. Ajoute `ANTHROPIC_API_KEY=` dans `local.properties`, puis rebuild l'app."
                    )
                )
            }
            return
        }

        if (aiFilterSnapshot != _ui.value.filterState) {
            _ui.update {
                it.copy(
                    aiDraft = "",
                    aiMessages = it.aiMessages + AiChatMessage(
                        role = AiChatRole.NOTICE,
                        text = "Les filtres ont change. Ferme et rouvre la fenetre AI pour recharger la bonne preselction."
                    )
                )
            }
            return
        }

        if (aiCandidateCache.isEmpty()) {
            _ui.update {
                it.copy(
                    aiDraft = "",
                    aiMessages = it.aiMessages + AiChatMessage(
                        role = AiChatRole.NOTICE,
                        text = "Aucun film n'est disponible dans la preselction actuelle."
                    )
                )
            }
            return
        }

        val userMessage = AiChatMessage(role = AiChatRole.USER, text = prompt)
        val conversation = _ui.value.aiMessages + userMessage
        _ui.update {
            it.copy(
                aiDraft = "",
                isAiLoading = true,
                aiMessages = conversation
            )
        }

        viewModelScope.launch {
            claudeMovieAdvisorRepository.chat(
                candidates = aiCandidateCache,
                conversation = conversation
            ).fold(
                onSuccess = { reply ->
                    _ui.update {
                        it.copy(
                            isAiLoading = false,
                            aiMessages = it.aiMessages + AiChatMessage(
                                role = AiChatRole.ASSISTANT,
                                text = reply
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _ui.update {
                        it.copy(
                            isAiLoading = false,
                            aiMessages = it.aiMessages + AiChatMessage(
                                role = AiChatRole.NOTICE,
                                text = error.message ?: "Impossible de joindre Claude pour l'instant."
                            )
                        )
                    }
                }
            )
        }
    }

    fun reset() = _ui.update {
        it.copy(
            filterState = FilterState(),
            searchQuery = "",
            searchResults = emptyList()
        )
    }

    fun setSearchQuery(value: String) = _ui.update {
        it.copy(
            searchQuery = value,
            searchResults = computeSearchResults(value, allMoviesCache)
        )
    }

    fun clearSearch() = _ui.update { it.copy(searchQuery = "", searchResults = emptyList()) }
    fun setGenre(value: String?) = _ui.update { it.copy(filterState = it.filterState.copy(selectedGenre = value)) }
    fun setCollection(value: String?) = _ui.update { it.copy(filterState = it.filterState.copy(selectedCollection = value)) }
    fun toggleRating(value: String) = _ui.update { state ->
        val current = state.filterState.selectedContentRatings
        val updated = if (current.any { it.equals(value, ignoreCase = true) }) {
            current.filterNot { it.equals(value, ignoreCase = true) }
        } else {
            current + value
        }
        state.copy(filterState = state.filterState.copy(selectedContentRatings = updated))
    }
    fun setDecade(value: String?) = _ui.update { it.copy(filterState = it.filterState.copy(selectedDecade = value)) }
    fun setRTScore(min: Int, max: Int) = _ui.update { it.copy(filterState = it.filterState.copy(rtScoreMin = min, rtScoreMax = max)) }
    fun setLanguage(value: String?) = _ui.update { it.copy(filterState = it.filterState.copy(selectedLanguage = value)) }
    fun setDuration(value: Int) = _ui.update { it.copy(filterState = it.filterState.copy(maxDurationMinutes = value)) }
    fun setResolution(value: String?) = _ui.update { it.copy(filterState = it.filterState.copy(selectedResolution = value)) }
    fun setWatched(value: WatchedFilter) = _ui.update { it.copy(filterState = it.filterState.copy(watchedFilter = value)) }
    fun dismissNoResults() = _ui.update { it.copy(noResults = false, noResultsMessage = null) }

    private suspend fun resolveFilteredMovies(filterState: FilterState): List<Movie> {
        val prefs = dataStore.data.first()
        val collectionInfo = filterState.selectedCollection?.let { selected ->
            collectionCatalog.firstOrNull { it.title.equals(selected, ignoreCase = true) }
        }
        val ip = prefs[PreferenceKeys.SERVER_IP] ?: ""
        val token = prefs[PreferenceKeys.PLEX_TOKEN] ?: ""

        return if (collectionInfo != null && ip.isNotBlank() && token.isNotBlank()) {
            val collectionMovies = repository.getMoviesFromPlexCollection(
                serverIp = ip,
                token = token,
                collectionRatingKey = collectionInfo.ratingKey,
                content = collectionInfo.content
            )
            filteredMoviesUseCase(
                filter = filterState,
                sourceMovies = collectionMovies,
                ignoreCollectionFilter = true
            )
        } else {
            filteredMoviesUseCase(filterState)
        }
    }

    private fun parseCollectionCatalog(raw: String?): List<PlexCollectionCatalogItem> =
        if (raw.isNullOrBlank()) emptyList() else gson.fromJson(raw, collectionCatalogType) ?: emptyList()

    private fun computeSearchResults(query: String, movies: List<Movie>): List<Movie> {
        val normalized = query.trim()
        if (normalized.length < 2) return emptyList()
        val needle = normalized.lowercase()
        return movies
            .filter { it.title.lowercase().contains(needle) }
            .sortedWith(
                compareBy<Movie>(
                    { !it.title.lowercase().startsWith(needle) },
                    { it.title.lowercase() },
                    { it.year ?: Int.MAX_VALUE }
                )
            )
            .take(12)
    }
}

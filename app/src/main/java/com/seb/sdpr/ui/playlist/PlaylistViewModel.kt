package com.seb.sdpr.ui.playlist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.seb.sdpr.data.PreferenceKeys
import com.seb.sdpr.data.repository.MovieRepository
import com.seb.sdpr.data.repository.PlexCollectionCatalogItem
import com.seb.sdpr.domain.model.Movie
import com.seb.sdpr.domain.usecase.GetFilteredMoviesUseCase
import com.seb.sdpr.ui.filter.FilterState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistUiState(
    val movies: List<Movie> = emptyList(),
    val serverIp: String = "",
    val plexToken: String = "",
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: MovieRepository,
    private val dataStore: DataStore<Preferences>,
    private val filteredMoviesUseCase: GetFilteredMoviesUseCase
) : ViewModel() {

    private val gson = Gson()
    private val collectionCatalogType = object : TypeToken<List<PlexCollectionCatalogItem>>() {}.type
    private val _ui = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _ui.asStateFlow()

    fun load(filterState: FilterState?) {
        val filter = filterState ?: FilterState()
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            val prefs = dataStore.data.first()
            val serverIp = prefs[PreferenceKeys.SERVER_IP] ?: ""
            val plexToken = prefs[PreferenceKeys.PLEX_TOKEN] ?: ""
            val collectionCatalog = parseCollectionCatalog(prefs[PreferenceKeys.COLLECTION_CATALOG])
            val collectionInfo = filter.selectedCollection?.let { selected ->
                collectionCatalog.firstOrNull { it.title.equals(selected, ignoreCase = true) }
            }

            val movies = if (collectionInfo != null && serverIp.isNotBlank() && plexToken.isNotBlank()) {
                val collectionMovies = repository.getMoviesFromPlexCollection(
                    serverIp = serverIp,
                    token = plexToken,
                    collectionRatingKey = collectionInfo.ratingKey,
                    content = collectionInfo.content
                )
                filteredMoviesUseCase(
                    filter = filter,
                    sourceMovies = collectionMovies,
                    ignoreCollectionFilter = true
                )
            } else {
                filteredMoviesUseCase(filter)
            }

            _ui.update {
                it.copy(
                    movies = movies,
                    serverIp = serverIp,
                    plexToken = plexToken,
                    isLoading = false,
                    error = if (movies.isEmpty()) "Aucun film ne correspond aux filtres actuels." else null
                )
            }
        }
    }

    private fun parseCollectionCatalog(raw: String?): List<PlexCollectionCatalogItem> =
        if (raw.isNullOrBlank()) emptyList() else gson.fromJson(raw, collectionCatalogType) ?: emptyList()
}

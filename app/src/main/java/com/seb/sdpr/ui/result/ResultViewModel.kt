package com.seb.sdpr.ui.result

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seb.sdpr.data.PreferenceKeys
import com.seb.sdpr.data.remote.PlexApi
import com.seb.sdpr.data.remote.PlexClient
import com.seb.sdpr.data.repository.MovieRepository
import com.seb.sdpr.data.repository.PlexCollectionCatalogItem
import com.seb.sdpr.domain.model.Movie
import com.seb.sdpr.domain.usecase.GetRandomMovieUseCase
import com.seb.sdpr.ui.filter.FilterState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultUiState(
    val movie:      Movie?  = null,
    val serverIp:   String  = "",
    val plexToken:  String  = "",
    val machineId:  String  = "",
    val isLoading:  Boolean = true,
    val error:      String? = null,
    val clients:    List<PlexClient> = emptyList(),
    val isLoadingClients: Boolean = false,
    val clientsError: String? = null,
    val isCasting:  Boolean = false,
    val castError:  String? = null
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MovieRepository,
    private val dataStore:  DataStore<Preferences>,
    private val plexApi: PlexApi,
    private val randomMovieUseCase: GetRandomMovieUseCase
) : ViewModel() {

    private val gson = Gson()
    private val collectionCatalogType = object : TypeToken<List<PlexCollectionCatalogItem>>() {}.type
    private val ratingKey: String = checkNotNull(savedStateHandle["ratingKey"])
    private var currentFilterState: FilterState = FilterState()
    private val _ui = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val ip    = prefs[PreferenceKeys.SERVER_IP]  ?: ""
            val token = prefs[PreferenceKeys.PLEX_TOKEN] ?: ""
            var mid   = prefs[PreferenceKeys.MACHINE_ID] ?: ""
            if (mid.isBlank() && ip.isNotBlank() && token.isNotBlank()) {
                mid = runCatching {
                    plexApi.getServerInfo("http://$ip:32400/", token)
                        .mediaContainer
                        ?.machineIdentifier
                        .orEmpty()
                }.getOrDefault("")

                if (mid.isNotBlank()) {
                    dataStore.edit { it[PreferenceKeys.MACHINE_ID] = mid }
                }
            }
            _ui.update { it.copy(
                serverIp  = ip,
                plexToken = token,
                machineId = mid,
                isLoading = true,
                error     = null
            )}
            loadMovie(ratingKey)
        }
    }

    fun setFilterState(filterState: FilterState?) {
        currentFilterState = filterState ?: FilterState()
    }

    fun reroll() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            val prefs = dataStore.data.first()
            val ip = prefs[PreferenceKeys.SERVER_IP] ?: ""
            val token = prefs[PreferenceKeys.PLEX_TOKEN] ?: ""
            val collectionCatalog = parseCollectionCatalog(prefs[PreferenceKeys.COLLECTION_CATALOG])
            val collectionInfo = currentFilterState.selectedCollection?.let { selected ->
                collectionCatalog.firstOrNull { it.title.equals(selected, ignoreCase = true) }
            }

            val movie = if (collectionInfo != null && ip.isNotBlank() && token.isNotBlank()) {
                val collectionMovies = repository.getMoviesFromPlexCollection(
                    serverIp = ip,
                    token = token,
                    collectionRatingKey = collectionInfo.ratingKey,
                    content = collectionInfo.content
                )
                randomMovieUseCase(
                    filter = currentFilterState,
                    sourceMovies = collectionMovies,
                    ignoreCollectionFilter = true
                )
            } else {
                randomMovieUseCase(currentFilterState)
            }
            _ui.update { it.copy(
                movie = movie,
                isLoading = false,
                error = if (movie == null) "Aucun film ne correspond aux filtres actuels" else null
            ) }
        }
    }

    private suspend fun loadMovie(key: String) {
        val movie = repository.getMovieByKey(key)
        _ui.update { it.copy(
            movie = movie,
            isLoading = false,
            error = if (movie == null) "Film introuvable" else null
        ) }
    }

    fun refreshClients() {
        val state = _ui.value
        if (state.isLoadingClients) return
        _ui.update { it.copy(clients = emptyList(), isLoadingClients = true, clientsError = null) }
        viewModelScope.launch {
            try {
                if (state.serverIp.isBlank() || state.plexToken.isBlank()) {
                    _ui.update { it.copy(clientsError = "Configure le serveur Plex et le token dans les paramètres.") }
                    return@launch
                }
                val clientsList = repository.getClients(state.serverIp, state.plexToken)
                    .filter { !it.name.isNullOrBlank() && !it.machineIdentifier.isNullOrBlank() }
                _ui.update { it.copy(clients = clientsList) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _ui.update { it.copy(clientsError = clientDiscoveryErrorMessage(error)) }
            } finally {
                _ui.update { it.copy(isLoadingClients = false) }
            }
        }
    }

    fun castToClient(clientMachineId: String) {
        viewModelScope.launch {
            val state = _ui.value
            val ratingKey = state.movie?.ratingKey ?: return@launch
            if (state.serverIp.isBlank() || state.plexToken.isBlank() || state.machineId.isBlank()) return@launch

            _ui.update { it.copy(isCasting = true, castError = null) }
            val success = repository.castMovie(
                serverIp = state.serverIp,
                token = state.plexToken,
                serverMachineId = state.machineId,
                ratingKey = ratingKey,
                targetClientMachineId = clientMachineId
            )
            _ui.update { it.copy(
                isCasting = false,
                castError = if (success) null else "Erreur lors du Cast. Assurez-vous que le lecteur est actif."
            )}
        }
    }

    fun clearCastError() {
        _ui.update { it.copy(castError = null) }
    }

    private fun parseCollectionCatalog(raw: String?): List<PlexCollectionCatalogItem> =
        if (raw.isNullOrBlank()) emptyList() else gson.fromJson(raw, collectionCatalogType) ?: emptyList()
}

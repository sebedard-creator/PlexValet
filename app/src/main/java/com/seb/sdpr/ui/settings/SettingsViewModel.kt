package com.seb.sdpr.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seb.sdpr.data.PreferenceKeys
import com.seb.sdpr.data.repository.MovieRepository
import com.seb.sdpr.data.remote.PlexApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverIp: String   = "",
    val plexToken: String  = "",
    val movieCount: Int    = 0,
    val lastSyncTime: Long = 0L,
    val isLoading: Boolean = false,
    val error: String?     = null,
    val successMsg: String?= null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val repository: MovieRepository,
    private val plexApi: PlexApi
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.first().let { prefs ->
                _ui.update { it.copy(
                    serverIp  = prefs[PreferenceKeys.SERVER_IP]  ?: "",
                    plexToken = prefs[PreferenceKeys.PLEX_TOKEN] ?: "",
                    lastSyncTime = prefs[PreferenceKeys.LAST_SYNC] ?: 0L
                )}
            }
        }
        viewModelScope.launch {
            repository.getAllMovies().collect { movies ->
                _ui.update { it.copy(movieCount = movies.size) }
            }
        }
    }

    fun onIpChange(v: String)    = _ui.update { it.copy(serverIp = v,  error = null) }
    fun onTokenChange(v: String) = _ui.update { it.copy(plexToken = v, error = null) }

    fun testAndSave() {
        viewModelScope.launch {
            val ip    = _ui.value.serverIp.trim()
            val token = _ui.value.plexToken.trim()
            if (ip.isEmpty() || token.isEmpty()) {
                _ui.update { it.copy(error = "IP et token requis") }
                return@launch
            }
            _ui.update { it.copy(isLoading = true, error = null, successMsg = null) }
            try {
                val info = plexApi.getServerInfo("http://$ip:32400/", token)
                val machineId = info.mediaContainer?.machineIdentifier ?: ""
                dataStore.edit { prefs ->
                    prefs[PreferenceKeys.SERVER_IP]  = ip
                    prefs[PreferenceKeys.PLEX_TOKEN] = token
                    prefs[PreferenceKeys.MACHINE_ID] = machineId
                }
                val name = info.mediaContainer?.friendlyName ?: "Serveur Plex"
                _ui.update { it.copy(isLoading = false, successMsg = "Connecté à $name ✓") }
            } catch (e: Exception) {
                _ui.update { it.copy(isLoading = false, error = "Connexion échouée : ${e.message}") }
            }
        }
    }
}

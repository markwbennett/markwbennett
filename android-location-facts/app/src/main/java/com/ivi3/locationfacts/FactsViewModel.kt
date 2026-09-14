package com.ivi3.locationfacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivi3.locationfacts.ai.ClaudeFactsService
import com.ivi3.locationfacts.ai.FactsException
import com.ivi3.locationfacts.ai.FactsResult
import com.ivi3.locationfacts.ai.PlaceContext
import com.ivi3.locationfacts.data.ApiKeyStore
import com.ivi3.locationfacts.location.LocationSource
import com.ivi3.locationfacts.location.PlaceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Stage(val label: String) {
    LOCATING("Finding where you are…"),
    RESOLVING_PLACE("Working out what this place is called…"),
    ASKING_CLAUDE("Searching the web for what is interesting here…"),
}

sealed interface UiState {
    /** No API key stored yet. */
    data object NeedsApiKey : UiState

    /** Permission has not been granted (or was denied). */
    data class NeedsPermission(val permanentlyDenied: Boolean = false) : UiState

    data class Loading(val stage: Stage) : UiState

    data class Ready(val place: PlaceContext, val result: FactsResult) : UiState

    data class Failed(val message: String, val retryable: Boolean) : UiState
}

class FactsViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyStore = ApiKeyStore(application)
    private val locationSource = LocationSource(application)
    private val placeResolver = PlaceResolver(application)

    private val _state = MutableStateFlow<UiState>(UiState.Loading(Stage.LOCATING))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** True once the user has been shown the system permission dialog at least once. */
    private var permissionAsked = false
    private var inFlight: Job? = null

    fun hasApiKey(): Boolean = apiKeyStore.hasKey()

    fun saveApiKey(key: String) {
        apiKeyStore.save(key)
        refresh()
    }

    fun clearApiKey() {
        apiKeyStore.clear()
        inFlight?.cancel()
        _state.value = UiState.NeedsApiKey
    }

    fun onPermissionResult(granted: Boolean) {
        permissionAsked = true
        if (granted) refresh() else _state.value = UiState.NeedsPermission(permanentlyDenied = true)
    }

    /** Entry point from the UI: called on first composition and by the refresh button. */
    fun refresh() {
        inFlight?.cancel()
        if (!apiKeyStore.hasKey()) {
            _state.value = UiState.NeedsApiKey
            return
        }
        if (!locationSource.hasPermission()) {
            _state.value = UiState.NeedsPermission(permanentlyDenied = permissionAsked)
            return
        }

        inFlight = viewModelScope.launch {
            try {
                _state.value = UiState.Loading(Stage.LOCATING)
                val location = locationSource.currentLocation()

                _state.value = UiState.Loading(Stage.RESOLVING_PLACE)
                val place = placeResolver.resolve(location)

                _state.value = UiState.Loading(Stage.ASKING_CLAUDE)
                val apiKey = apiKeyStore.load()
                if (apiKey.isNullOrBlank()) {
                    _state.value = UiState.NeedsApiKey
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    ClaudeFactsService(apiKey).factsFor(place)
                }

                _state.value = UiState.Ready(place, result)
            } catch (e: LocationSource.Unavailable) {
                _state.value = UiState.Failed(e.message ?: "No location available.", retryable = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // a newer refresh replaced this one; leave the state to it
            } catch (e: FactsException) {
                _state.value = UiState.Failed(e.message ?: "Something went wrong.", e.retryable)
            } catch (e: Exception) {
                _state.value = UiState.Failed(
                    e.message ?: "Something went wrong (${e::class.simpleName}).",
                    retryable = true,
                )
            }
        }
    }
}

package com.nexttry.confesiones.ui.community

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexttry.confesiones.data.Community
import com.nexttry.confesiones.data.ConfesionRepository
import com.nexttry.confesiones.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Estado de la UI para la pantalla de selección
data class CommunityUiState(
    val communities: List<Community> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * ViewModel para la pantalla de selección de comunidad.
 * Usamos AndroidViewModel para poder acceder al Contexto de la aplicación.
 */
class CommunityViewModel(application: Application) : AndroidViewModel(application) {

    private val confesionRepository = ConfesionRepository()
    private val prefsRepository = UserPreferencesRepository(application)

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // Al iniciar, cargamos la lista de comunidades
        loadCommunities()
    }

    private fun loadCommunities() {
        viewModelScope.launch {
            val communities = confesionRepository.getCommunities()
            _uiState.update { it.copy(communities = communities, isLoading = false) }
        }
    }

    /**
     * Lo llama la UI cuando el usuario selecciona una comunidad.
     */
//    fun onCommunitySelected(communityId: String) {
//        viewModelScope.launch {
//            prefsRepository.saveCommunityId(communityId)
//        }
//    }
}
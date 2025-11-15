package com.nexttry.confesiones.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.nexttry.confesiones.data.ConfesionRepository
import com.nexttry.confesiones.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Estado de la UI para la pantalla de perfil
data class ProfileUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val profile: UserProfile? = null // El perfil actual del usuario
)

class ProfileViewModel : ViewModel() {

    private val repository = ConfesionRepository()
    private val userId = Firebase.auth.currentUser?.uid

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    // Carga el perfil del usuario y lo observa en tiempo real
    private fun loadProfile() {
        if (userId == null) {
            _uiState.update { it.copy(isLoading = false, error = "Usuario no autenticado") }
            return
        }

        viewModelScope.launch {
            repository.getUserProfileStream(userId)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = "Error al cargar: ${e.message}") }
                }
                .collect { profileFromDb ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            profile = profileFromDb // Actualiza el perfil (puede ser null)
                        )
                    }
                }
        }
    }

    /**
     * Se llama cuando el usuario guarda los cambios.
     */
    fun onSaveProfile(
        gender: String?,
        age: Int?,
        countryCode: String?,
        allowsMessaging: Boolean
    ) {
        if (userId == null) return

        // Obtenemos el perfil actual para no perder el anonymousName
        val currentProfile = _uiState.value.profile

        // Creamos el objeto actualizado
        val updatedProfile = UserProfile(
            // Mantenemos el nombre anónimo que ya existía
            anonymousName = currentProfile?.anonymousName,
            // Actualizamos los otros campos
            gender = gender,
            age = age,
            countryCode = countryCode,
            allowsMessaging = allowsMessaging // Guardamos la nueva preferencia
        )

        viewModelScope.launch {
            try {
                repository.updateUserProfile(userId, updatedProfile)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al guardar: ${e.message}") }
            }
        }
    }
}
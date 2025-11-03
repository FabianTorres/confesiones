package com.nexttry.confesiones.ui.newconfession

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttry.confesiones.data.ConfesionRepository
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Estado de la UI para esta pantalla
data class NewConfessionUiState(
    val text: String = "",
    val isPublishing: Boolean = false,
    val error: String? = null,
    val publishSuccess: Boolean = false // Para saber cuándo navegar hacia atrás
)

class NewConfessionViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val repository = ConfesionRepository()
    // Obtenemos el communityId pasado a través de la ruta de navegación
    private val communityId: String = savedStateHandle.get<String>("communityId")!!
    private val userId = Firebase.auth.currentUser?.uid
    private val _uiState = MutableStateFlow(NewConfessionUiState())
    val uiState = _uiState.asStateFlow()

    // Límite de caracteres (igual que antes en PublicarConfesionUI)
    val maxChars = 250

    /**
     * Actualiza el texto en el estado de la UI.
     */
    fun onTextChanged(newText: String) {
        if (newText.length <= maxChars) {
            _uiState.update { it.copy(text = newText, error = null) } // Limpia errores al escribir
        }
    }

    /**
     * Llama al repositorio para publicar la confesión.
     */
    fun publishConfession() {
        val currentText = _uiState.value.text.trim()
        if (currentText.isBlank() || _uiState.value.isPublishing || userId == null) {
            return
        }

        _uiState.update { it.copy(isPublishing = true, error = null) }

        viewModelScope.launch {
            try {
                // 1. Obtenemos el perfil del usuario UNA VEZ
                val userProfile = repository.getUserProfileStream(userId!!).firstOrNull()

                // 2. Pasamos los 3 datos al repositorio
                repository.addConfesion(
                    texto = currentText,
                    communityId = communityId,
                    // --- MODIFICACIÓN AQUÍ ---
                    authorGender = userProfile?.gender,
                    authorAge = userProfile?.age,
                    authorCountry = userProfile?.countryCode
                    // --- FIN DE LA MODIFICACIÓN ---
                )

                _uiState.update { it.copy(isPublishing = false, publishSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isPublishing = false, error = "Error al publicar: ${e.message}") }
            }
        }
    }
}
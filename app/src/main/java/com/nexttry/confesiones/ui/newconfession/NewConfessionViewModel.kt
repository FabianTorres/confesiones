package com.nexttry.confesiones.ui.newconfession

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttry.confesiones.data.ConfesionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        if (currentText.isBlank() || _uiState.value.isPublishing) {
            return // Evita publicar vacío o múltiples veces
        }

        _uiState.update { it.copy(isPublishing = true, error = null) } // Marca como publicando

        viewModelScope.launch {
            try {
                repository.addConfesion(currentText, communityId)
                _uiState.update { it.copy(isPublishing = false, publishSuccess = true) } // Marca éxito
            } catch (e: Exception) {
                _uiState.update { it.copy(isPublishing = false, error = "Error al publicar: ${e.message}") }
            }
        }
    }
}
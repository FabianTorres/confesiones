package com.nexttry.confesiones.ui.newcomment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttry.confesiones.data.ConfesionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Estado similar al de NewConfession, adaptado para comentarios
data class NewCommentUiState(
    val text: String = "",
    val isPublishing: Boolean = false,
    val error: String? = null,
    val publishSuccess: Boolean = false
)

class NewCommentViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val repository = ConfesionRepository()
    // Obtenemos el confessionId pasado por navegación
    private val confessionId: String = savedStateHandle.get<String>("confessionId")!!

    private val _uiState = MutableStateFlow(NewCommentUiState())
    val uiState = _uiState.asStateFlow()

    // Límite más largo para comentarios
    val maxChars = 250

    fun onTextChanged(newText: String) {
        if (newText.length <= maxChars) {
            _uiState.update { it.copy(text = newText, error = null) }
        }
    }

    fun publishComment() {
        val currentText = _uiState.value.text.trim()
        if (currentText.isBlank() || _uiState.value.isPublishing) {
            return
        }

        _uiState.update { it.copy(isPublishing = true, error = null) }

        viewModelScope.launch {
            try {
                repository.addComment(confessionId, currentText)
                _uiState.update { it.copy(isPublishing = false, publishSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isPublishing = false, error = "Error al comentar: ${e.message}") }
            }
        }
    }
}
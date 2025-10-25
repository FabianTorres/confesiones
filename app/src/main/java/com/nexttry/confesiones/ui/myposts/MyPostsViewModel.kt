package com.nexttry.confesiones.ui.myposts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.nexttry.confesiones.data.Confesion
import com.nexttry.confesiones.data.ConfesionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Estado de la UI para esta pantalla
data class MyPostsUiState(
    val myConfessions: List<Confesion> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class MyPostsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ConfesionRepository()
    private val _uiState = MutableStateFlow(MyPostsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadMyPosts()
    }

    private fun loadMyPosts() {
        // Obtenemos el ID del usuario actual
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            // Si por alguna razón no hay usuario, mostramos error
            _uiState.update { it.copy(isLoading = false, error = "Usuario no autenticado") }
            return
        }

        // Empezamos a escuchar el stream de confesiones del usuario
        viewModelScope.launch {
            repository.getMyConfessionsStream(userId)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = "Error al cargar: ${e.message}") }
                }
                .collect { confessions ->
                    _uiState.update { it.copy(isLoading = false, myConfessions = confessions) }
                }
        }
    }
}
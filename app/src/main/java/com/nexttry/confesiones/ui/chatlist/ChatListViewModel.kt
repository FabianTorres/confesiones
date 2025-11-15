package com.nexttry.confesiones.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.nexttry.confesiones.data.ChatRoom
import com.nexttry.confesiones.data.ConfesionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListUiState(
    val isLoading: Boolean = true,
    val pendingChats: List<ChatRoom> = emptyList(),
    val activeChats: List<ChatRoom> = emptyList(),
    val error: String? = null,
    val currentUserId: String? = Firebase.auth.currentUser?.uid
)

class ChatListViewModel : ViewModel() {

    private val repository = ConfesionRepository()
    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadChats()
    }

    private fun loadChats() {
        val userId = _uiState.value.currentUserId
        if (userId == null) {
            _uiState.update { it.copy(isLoading = false, error = "Usuario no autenticado") }
            return
        }

        viewModelScope.launch {
            repository.getMyChatRoomsStream(userId)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { allChats ->
                    // Dividimos los chats en dos listas (pendientes y activos)
                    // Filtramos los "rejected" para que no se muestren
                    val (pending, active) = allChats
                        .filter { it.status != "rejected" }
                        .partition { it.status == "pending" }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingChats = pending,
                            activeChats = active
                        )
                    }
                }
        }
    }
}
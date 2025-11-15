package com.nexttry.confesiones.ui.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.nexttry.confesiones.data.ChatRoom
import com.nexttry.confesiones.data.ConfesionRepository
import com.nexttry.confesiones.data.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Estado de la UI para esta pantalla
data class ConversationUiState(
    val isLoading: Boolean = true,
    val chatRoom: ChatRoom? = null,
    val messages: List<Message> = emptyList(),
    val error: String? = null,
    val currentUserId: String? = Firebase.auth.currentUser?.uid,
    val otherUserId: String? = null
)

class ConversationViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val repository = ConfesionRepository()
    private val chatId: String = savedStateHandle.get<String>("chatId")!!
    private val currentUserId: String? = Firebase.auth.currentUser?.uid

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadChatAndMessages()
    }

    private fun loadChatAndMessages() {
        if (chatId.isEmpty() || currentUserId == null) {
            _uiState.update { it.copy(isLoading = false, error = "Error: ID de chat o usuario no válido") }
            return
        }

        viewModelScope.launch {
            // Usamos 'combine' para que la UI se actualice si cambia
            // el ChatRoom (ej. el status) O si llegan nuevos mensajes.
            combine(
                repository.getChatRoomStream(chatId),
                repository.getMessagesStream(chatId)
            ) { chatRoom, messages ->
                val otherId = chatRoom?.members?.find { it != currentUserId }
                ConversationUiState(
                    isLoading = false,
                    chatRoom = chatRoom,
                    messages = messages,
                    currentUserId = currentUserId,
                    otherUserId = otherId
                )
            }.catch { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * Envía un nuevo mensaje de texto.
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || currentUserId == null) return

        viewModelScope.launch {
            try {
                repository.sendChatMessage(chatId, text, currentUserId)
            } catch (e: Exception) {
                // TODO: Manejar error de envío (ej. con un evento)
            }
        }
    }

    /**
     * Acepta la solicitud de chat.
     */
    fun acceptChat() {
        viewModelScope.launch {
            try {
                repository.acceptChat(chatId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al aceptar el chat") }
            }
        }
    }

    /**
     * Rechaza la solicitud de chat.
     */
    fun rejectChat() {
        viewModelScope.launch {
            try {
                repository.rejectChat(chatId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al rechazar el chat") }
            }
        }
    }

    /**
     * Llama al repositorio para bloquear al otro usuario en este chat.
     */
    fun onBlockUserClicked() {
        val userToBlock = _uiState.value.otherUserId
        if (currentUserId == null || userToBlock == null) {
            _uiState.update { it.copy(error = "No se puede bloquear al usuario") }
            return
        }

        viewModelScope.launch {
            try {
                repository.blockUser(currentUserId, userToBlock)
                // TODO: Emitir un evento de "Bloqueado con éxito" y quizá navegar atrás.
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al bloquear usuario: ${e.message}") }
            }
        }
    }

    /**
     * Llama al repositorio para reportar un mensaje específico.
     */
    fun onReportMessageClicked(messageId: String, reason: String) {
        if (currentUserId == null) {
            _uiState.update { it.copy(error = "No se puede reportar sin usuario") }
            return
        }

        viewModelScope.launch {
            try {
                // Reutilizamos la función de reporte existente
                repository.reportItem(messageId, "message", currentUserId, reason)
                // TODO: Emitir evento de "Reporte enviado".
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al reportar mensaje: ${e.message}") }
            }
        }
    }
}
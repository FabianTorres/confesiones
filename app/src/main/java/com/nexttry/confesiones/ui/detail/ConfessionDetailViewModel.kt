package com.nexttry.confesiones.ui.detail

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.nexttry.confesiones.data.Comment
import com.nexttry.confesiones.data.Confesion
import com.nexttry.confesiones.data.ConfesionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

// Estado de la UI para la pantalla de detalle
data class DetailUiState(
    val confession: Confesion? = null,
    val comments: List<Comment> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUserId: String? = Firebase.auth.currentUser?.uid // ID del usuario actual
)

// Eventos para esta pantalla ---
sealed class DetailScreenEvent {
    data class ShowSnackbar(val message: String) : DetailScreenEvent()
}

class ConfessionDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = ConfesionRepository()
    private val confessionId: String = savedStateHandle.get<String>("confessionId")!!

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState = _uiState.asStateFlow()

    // Channel y SharedFlow ---
    private val _eventChannel = Channel<DetailScreenEvent>()
    val events = _eventChannel.receiveAsFlow()

    init {
        loadConfessionAndComments()
    }

    private fun loadConfessionAndComments() {
        viewModelScope.launch {
            try {
                // Combinamos los dos streams (confesión y comentarios)
                // para que la UI se actualice cuando cualquiera de los dos cambie.
                combine(
                    repository.getConfessionStream(confessionId),
                    repository.getCommentsStream(confessionId)
                ) { confessionData, commentsData ->
                    // Creamos el nuevo estado con ambos datos
                    _uiState.update {
                        it.copy(
                            confession = confessionData,
                            comments = commentsData,
                            isLoading = false // Dejamos de cargar cuando tenemos ambos
                        )
                    }
                }.catch { e -> // Manejo de errores
                    _uiState.update { it.copy(error = "Error al cargar datos: ${e.message}", isLoading = false) }
                }.collect() // Empezamos a escuchar
            } catch (e: Exception){
                _uiState.update { it.copy(error = "Error inicial: ${e.message}", isLoading = false) }
            }
        }
    }

    /**
     * Llama al repositorio para añadir un nuevo comentario.
     */
    fun postComment(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                repository.addComment(confessionId, text)
                _eventChannel.send(DetailScreenEvent.ShowSnackbar("Comentario publicado"))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al publicar comentario") }
                _eventChannel.send(DetailScreenEvent.ShowSnackbar("Error al comentar"))
            }
        }
    }

    /**
     * Llama al repositorio para dar/quitar like (reutilizamos la lógica).
     */
    fun onLikeClicked() {
        val userId = _uiState.value.currentUserId ?: return
        // Obtenemos la confesión actual del estado
        val currentConfession = _uiState.value.confession ?: return

        // --- INICIO: LÓGICA OPTIMISTA ---
        // 1. Creamos el mapa de likes optimista
        val optimisticLikes = currentConfession.likes.toMutableMap()
        val currentlyLiked = optimisticLikes.containsKey(userId)
        if (currentlyLiked) {
            optimisticLikes.remove(userId)
        } else {
            optimisticLikes[userId] = true
        }
        // Calculamos el contador optimista
        val optimisticCount = optimisticLikes.size.toLong()

        // 2. Creamos la confesión optimista
        val optimisticConfession = currentConfession.copy(
            likes = optimisticLikes,
            likesCount = optimisticCount
        )

        // 3. Actualizamos la UI INMEDIATAMENTE
        _uiState.update { it.copy(confession = optimisticConfession) }
        // --- FIN: LÓGICA OPTIMISTA ---

        // 4. AHORA, en segundo plano, llamamos al repositorio
        viewModelScope.launch {
            try {
                // Pasamos el ID de la confesión (el estado ya fue actualizado)
                repository.toggleLike(confessionId, userId)
                // Nota: Si la operación falla, la UI se quedará en el estado optimista.
                // Podríamos añadir lógica para revertir si falla, pero por ahora lo dejamos así.
            } catch (e: Exception) {
                Log.e("DetailVM-Like", "Error en onLikeClicked (repo)", e) // Log específico
                // Podríamos intentar revertir la UI aquí si quisiéramos
                _uiState.update { it.copy(error = "Error al procesar like", confession = currentConfession) } // Revertir
                _eventChannel.send(DetailScreenEvent.ShowSnackbar("Error al dar like"))
            }
        }
    }

    /**
     * Se llama al reportar la confesión principal desde la pantalla de detalle.
     */
    fun onReportConfessionClicked(reason: String) {
        Log.d("DetailVM-Report", "Reporte solicitado para confesión ID: $confessionId") // <-- LOG AQUÍ
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            try {
                repository.reportItem(confessionId, "confession", userId, reason)
                _eventChannel.send(DetailScreenEvent.ShowSnackbar("Reporte de confesión enviado"))
            } catch (e: Exception) {
                Log.e("DetailVM-Report", "Error al reportar confesión", e)
                _uiState.update { it.copy(error = "Error al enviar el reporte") }
                _eventChannel.send(DetailScreenEvent.ShowSnackbar("Error al enviar reporte"))
            }
        }
    }

    /**
     * Se llama al reportar un comentario específico.
     */
    fun onReportCommentClicked(commentId: String, reason: String) {
        Log.d("DetailVM-Report", "Reporte solicitado para comentario ID: $commentId")
        val userId = _uiState.value.currentUserId ?: return
        viewModelScope.launch {
            try {
                repository.reportItem(commentId, "comment", userId, reason)
                _eventChannel.send(DetailScreenEvent.ShowSnackbar("Reporte de comentario enviado"))
            } catch (e: Exception) {
                Log.e("DetailVM-Report", "Error al reportar comentario", e)
                _uiState.update { it.copy(error = "Error al enviar el reporte") }
                _eventChannel.send(DetailScreenEvent.ShowSnackbar("Error al enviar reporte"))
            }
        }
    }
}
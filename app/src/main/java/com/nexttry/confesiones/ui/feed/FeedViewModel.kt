package com.nexttry.confesiones.ui.feed

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nexttry.confesiones.data.Confesion
import com.nexttry.confesiones.data.ConfesionRepository
import com.nexttry.confesiones.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.SavedStateHandle
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

// Define los diferentes estados que puede tener nuestra UI
data class FeedUiState(
    val confesiones: List<Confesion> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUserId: String? = null
)

// Clase sellada para los eventos de una sola vez ---
sealed class FeedScreenEvent {
    data class ShowSnackbar(val message: String) : FeedScreenEvent()
    // Podríamos añadir más eventos aquí si fuera necesario (ej: Navigate)
}

class FeedViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val repository = ConfesionRepository()
    private val prefsRepository = UserPreferencesRepository(application)
    // Leemos el communityId que nos pasó la UI
    private val communityId: String = savedStateHandle.get<String>("communityId")!!
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState = _uiState.asStateFlow()

    // Usamos un Channel para enviar eventos desde las coroutines de forma segura.
    private val _eventChannel = Channel<FeedScreenEvent>()
    // Convertimos el Channel en un SharedFlow para que la UI lo observe.
    val events = _eventChannel.receiveAsFlow()

    init {

        iniciarSecuenciaDeCarga()
    }

    private fun iniciarSecuenciaDeCarga() {
        viewModelScope.launch {
            try {

                // Se asegura el login
                repository.asegurarLoginAnonimo()
                //Se obtiene el UID y lo ponemos en el estado
                val userId = Firebase.auth.currentUser?.uid
                _uiState.update { it.copy(currentUserId = userId) }
                // Se empieza a escuchar el feed
                repository.getConfesionesStream(communityId)
                    .collect { confesiones ->
                        _uiState.update { it.copy(confesiones = confesiones, isLoading = false) }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al iniciar: ${e.message}", isLoading = false) }
            }
        }
    }

    fun publicarConfesion(texto: String) {
        if (texto.isBlank()) return
        viewModelScope.launch {
            try {
                repository.addConfesion(texto, communityId)
                _eventChannel.send(FeedScreenEvent.ShowSnackbar("Confesión publicada"))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error al publicar: ${e.message}") }
                _eventChannel.send(FeedScreenEvent.ShowSnackbar("Error al publicar"))
            }
        }
    }

    /**
     * Like optimista
     * Se llama cuando el usuario presiona el botón de 'like'.
     */
    fun onLikeClicked(confesionId: String) {
        val userId = _uiState.value.currentUserId ?: return

        // 1. Hacemos una copia local de la lista actual de confesiones.
        val confesionesActuales = _uiState.value.confesiones

        // 2. Creamos una nueva lista "optimista"
        val confesionesOptmistas = confesionesActuales.map { confesion ->
            if (confesion.id == confesionId) {
                // Si esta es la confesión que se está likeando, la modificamos localmente.
                val likesMutables = confesion.likes.toMutableMap()
                if (likesMutables.containsKey(userId)) {
                    likesMutables.remove(userId) // Optimista: Quitar like
                } else {
                    likesMutables[userId] = true // Optimista: Poner like
                }
                confesion.copy(likes = likesMutables) // Devolvemos la confesión modificada
            } else {
                confesion // Devolvemos las otras confesiones sin cambios
            }
        }

        // 3. Actualizamos la UI con la lista optimista.
        // El usuario ve el cambio al instante.
        _uiState.update { it.copy(confesiones = confesionesOptmistas) }


        // 4. AHORA, en segundo plano, enviamos la petición real al servidor.
        viewModelScope.launch {
            try {
                repository.toggleLike(confesionId, userId)
            } catch (e: Exception) {
                // Si la petición falla (ej: sin internet), lo registramos.
                Log.e("ViewModel-Like", "Error en onLikeClicked", e)
                _uiState.update { it.copy(error = "Error al procesar el like") }
                _eventChannel.send(FeedScreenEvent.ShowSnackbar("Error al dar like"))
            }
        }
    }

    /**
     * Se llama cuando el usuario presiona el botón de reportar en una confesión del feed.
     */
    fun onReportConfessionClicked(confessionId: String, reason: String) {
        Log.d("ViewModel-Report", "Reporte solicitado para confesión ID: $confessionId") // <-- LOG AQUÍ
        val userId = _uiState.value.currentUserId ?: return // Necesitamos el ID del reportante
        viewModelScope.launch {
            try {
                repository.reportItem(confessionId, "confession", userId, reason)
                _eventChannel.send(FeedScreenEvent.ShowSnackbar("Reporte enviado"))
            } catch (e: Exception) {
                Log.e("ViewModel-Report", "Error al reportar confesión", e)
                _uiState.update { it.copy(error = "Error al enviar el reporte") }
                _eventChannel.send(FeedScreenEvent.ShowSnackbar("Error al enviar reporte"))
            }
        }
    }
}
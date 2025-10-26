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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job

/**
 * Define los criterios de ordenamiento disponibles para el feed.
 */
enum class SortOrder {
    RECENT, // Ordenar por más reciente (timestamp)
    POPULAR // Ordenar por más popular (likesCount)
}

// Define los diferentes estados que puede tener nuestra UI
data class FeedUiState(
    val confesiones: List<Confesion> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUserId: String? = null,
    val sortOrder: SortOrder = SortOrder.RECENT
)


// Clase sellada para los eventos de una sola vez ---
sealed class FeedScreenEvent {
    data class ShowSnackbar(val message: String) : FeedScreenEvent()
    // Podríamos añadir más eventos aquí si fuera necesario (ej: Navigate)
}

class FeedViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {

    private val repository = ConfesionRepository()
    private val prefsRepository = UserPreferencesRepository(application)

    // Leemos el communityId que nos pasó la UI
    private val communityId: String = savedStateHandle.get<String>("communityId")!!
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState = _uiState.asStateFlow()

    // Para rastrear el último like optimista y evitar el parpadeo
    private var lastOptimisticLikeId: String? = null
    private var lastOptimisticLikeTime: Long = 0L
    private val debounceFirestoreMillis =
        750L // Tiempo (ms) para ignorar updates de Firestore post-like


    // Usamos un Channel para enviar eventos desde las coroutines de forma segura.
    private val _eventChannel = Channel<FeedScreenEvent>()

    // Convertimos el Channel en un SharedFlow para que la UI lo observe.
    val events = _eventChannel.receiveAsFlow()

    init {

        iniciarSecuenciaDeCarga()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun iniciarSecuenciaDeCarga() {
        viewModelScope.launch {
            try {

                // Se asegura el login
                repository.asegurarLoginAnonimo()
                //Se obtiene el UID y lo ponemos en el estado
                val userId = Firebase.auth.currentUser?.uid
                _uiState.update { it.copy(currentUserId = userId) }
                // Usamos flatMapLatest (o switchMap) para que el stream se
                // reinicie automáticamente cada vez que el estado de _uiState cambie
                // (específicamente, cuando 'sortOrder' cambie).
                _uiState
                    .flatMapLatest { state ->
                        // El stream ahora depende del 'state.sortOrder'
                        repository.getConfesionesStream(communityId, state.sortOrder)
                    }
                    .catch { e ->
                        // Si el nuevo stream falla (ej: por índice de Firestore faltante),
                        // lo capturamos aquí.
                        Log.e("FeedViewModel", "Error al colectar feed", e)
                        _uiState.update {
                            it.copy(
                                error = "Error al cargar feed: ${e.message}",
                                isLoading = false
                            )
                        }
                    }
                    .collect { confesionesFromFirestore ->

                        val now = System.currentTimeMillis()
                        val recentlyLikedId = lastOptimisticLikeId
                        val currentUserId = _uiState.value.currentUserId

                        // ¿Acabamos de hacer un like optimista Y esta actualización llegó muy rápido?
                        if (recentlyLikedId != null && (now - lastOptimisticLikeTime < debounceFirestoreMillis)) {
                            // Buscamos si la confesión likeada está en la lista que llegó de Firestore
                            val confessionInUpdate =
                                confesionesFromFirestore.find { it.id == recentlyLikedId }
                            // Buscamos la versión optimista que tenemos en la UI actual
                            val optimisticConfession =
                                _uiState.value.confesiones.find { it.id == recentlyLikedId }

                            // Si encontramos ambas y la de Firestore NO tiene el like que SÍ tiene la optimista...
                            if (confessionInUpdate != null && optimisticConfession != null) { // Comparamos contadores
                                val userLikedInFirestore =
                                    confessionInUpdate.likes.containsKey(currentUserId)
                                val userLikedOptimistically =
                                    optimisticConfession.likes.containsKey(currentUserId)
                                if (userLikedInFirestore != userLikedOptimistically) {


                                    // Si son diferentes, significa que la versión de Firestore es "vieja"
                                    // Ignoramos esta actualización.
                                    Log.d(
                                        "FeedViewModel",
                                        "Ignorando update de Firestore para $recentlyLikedId para evitar parpadeo (like/dislike)."
                                    )
                                    return@collect
                                } else {
                                    // Si son iguales, Firestore ya está actualizado, reseteamos.
                                    lastOptimisticLikeId = null
                                }
                            } else {
                                // Si la de Firestore ya tiene el like (o es otra confesión), reseteamos el tracker
                                lastOptimisticLikeId = null
                            }
                        } else {
                            // Si no hubo like reciente o ya pasó el tiempo, reseteamos el tracker
                            lastOptimisticLikeId = null
                        }
                        // Colectamos los nuevos datos y actualizamos la UI
                        _uiState.update {
                            it.copy(
                                confesiones = confesionesFromFirestore,
                                isLoading = false
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Error al iniciar: ${e.message}",
                        isLoading = false
                    )
                }
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

        // 1. Actualizamos el estado directamente
        _uiState.update { currentState ->
            // Mapeamos la lista actual para encontrar y modificar la confesión
            val optimisticConfessions = currentState.confesiones.map { confesion ->
                if (confesion.id == confesionId) {
                    // Modificamos esta confesión
                    val optimisticLikes = confesion.likes.toMutableMap()
                    if (optimisticLikes.containsKey(userId)) {
                        optimisticLikes.remove(userId)
                    } else {
                        optimisticLikes[userId] = true
                    }
                    val optimisticCount = optimisticLikes.size.toLong()
                    // Devolvemos la copia modificada
                    confesion.copy(likes = optimisticLikes, likesCount = optimisticCount)
                } else {
                    // Devolvemos las otras sin cambios
                    confesion
                }
            }
            // Devolvemos el nuevo estado con la lista optimista
            currentState.copy(confesiones = optimisticConfessions)
        }

        lastOptimisticLikeId = confesionId
        lastOptimisticLikeTime = System.currentTimeMillis()


        // 2. AHORA, en segundo plano, enviamos la petición real al servidor.
        viewModelScope.launch {
            try {
                repository.toggleLike(confesionId, userId)
                // Si falla, el listener de Firestore eventualmente corregirá la UI
            } catch (e: Exception) {
                Log.e("ViewModel-Like", "Error en onLikeClicked (repo)", e)
                // Podríamos intentar revertir aquí, pero es complejo con la lista.
                // Dejaremos que el listener de Firestore lo corrija eventualmente.
                _uiState.update { it.copy(error = "Error al procesar el like") }
                _eventChannel.send(FeedScreenEvent.ShowSnackbar("Error al dar like"))
            }
        }
    }

    /**
     * Se llama cuando el usuario cambia el criterio de ordenamiento en la UI.
     */
    fun onSortOrderChanged(newSortOrder: SortOrder) {
        // Evitamos recargar si el orden ya es el seleccionado
        if (newSortOrder == _uiState.value.sortOrder) return

        // Actualizamos el estado.
        // Ponemos isLoading = true para mostrar un indicador
        // mientras 'flatMapLatest' recarga el stream.
        _uiState.update {
            it.copy(
                sortOrder = newSortOrder,
                isLoading = true
            )
        }
    }

    /**
     * Se llama cuando el usuario presiona el botón de reportar en una confesión del feed.
     */
    fun onReportConfessionClicked(confessionId: String, reason: String) {
        Log.d(
            "ViewModel-Report",
            "Reporte solicitado para confesión ID: $confessionId"
        ) // <-- LOG AQUÍ
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
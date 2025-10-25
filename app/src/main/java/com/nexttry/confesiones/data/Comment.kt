package com.nexttry.confesiones.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * Modelo de datos para un comentario en una confesión.
 */
data class Comment(
    @DocumentId val id: String = "",
    val texto: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val userId: String = "" // ID del autor anónimo del comentario
)
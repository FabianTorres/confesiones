package com.nexttry.confesiones.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * Modelo de datos para un documento en la subcolección
 * 'chatRooms/{chatId}/messages'.
 * Representa un único mensaje.
 */
data class Message(
    @DocumentId val id: String = "",

    // El UID del remitente
    val senderId: String = "",

    // El contenido del mensaje
    val text: String = "",

    // La fecha de envío
    val timestamp: Timestamp = Timestamp.now(),

    /**
     * Campo especial para identificar mensajes contextuales.
     * Será 'true' si este mensaje es el texto de una confesión
     * que se inyectó automáticamente al iniciar/continuar el chat.
     */
    val isContext: Boolean = false
)
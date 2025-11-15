package com.nexttry.confesiones.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue

/**
 * Modelo de datos para un documento en la colección 'chatRooms'.
 * Representa una conversación entre dos o más usuarios.
 */
data class ChatRoom(
    @DocumentId val id: String = "",

    // Lista de los UIDs de los participantes (ej. ["uid_A", "uid_B"])
    val members: List<String> = emptyList(),

    // Mapa de los nombres anónimos de los participantes
    // ej. {"uid_A": "Usuario 12345", "uid_B": "Usuario 67890"}
    val memberNames: Map<String, String> = emptyMap(),

    // Estado del chat: "pending" (esperando aceptación) o "active"
    val status: String = "pending",

    // --- Campos denormalizados para la lista de chats ---
    // (Se actualizan con una Cloud Function)

    // El texto del último mensaje para mostrar un preview
    val lastMessageText: String? = null,

    // El UID de quien envió el último mensaje (para mostrar "Tú:")
    val lastMessageSenderId: String? = null,

    // La fecha del último mensaje (para ordenar la lista de chats)
    val lastMessageTimestamp: Timestamp? = null,

    // --- Campo para Borrado Automático ---
    // Este campo será usado por Firestore TTL.
    // Se actualizará con la fecha del último mensaje + 7 días.
    val ttlTimestamp: Timestamp = Timestamp.now()
)
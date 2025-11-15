package com.nexttry.confesiones.data

import com.google.firebase.firestore.DocumentId

/**
 * Modelo de datos para el perfil de un usuario.
 * El ID del documento será el mismo UID del usuario de Firebase Auth.
 */
data class UserProfile(

    // Campos de Perfil
    val gender: String? = null,
    val age: Int? = null,
    val countryCode: String? = null,
    val anonymousName: String? = null,
    val allowsMessaging: Boolean = true
)
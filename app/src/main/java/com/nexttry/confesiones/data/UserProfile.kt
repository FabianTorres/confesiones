package com.nexttry.confesiones.data

import com.google.firebase.firestore.DocumentId

/**
 * Modelo de datos para el perfil opcional de un usuario.
 * El ID del documento será el mismo UID del usuario de Firebase Auth.
 */
data class UserProfile(

    // --- Campos de Perfil Opcionales ---
    val gender: String? = null,
    val age: Int? = null,
    val countryCode: String? = null,
)
package com.nexttry.confesiones.data

import com.google.firebase.firestore.DocumentId

/**
 * Modelo de datos que representa un canal o comunidad.
 * @param id El identificador único del documento en Firestore (ej: "universidad_de_chile").
 * @param nombre El nombre visible para el usuario (ej: "Universidad de Chile").
 */
data class Community(
    @DocumentId val id: String = "",
    val nombre: String = ""
)
package com.nexttry.confesiones.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Confesion(
    @DocumentId val id: String = "",
    val texto: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val userId: String = "",
    val likesCount: Long = 0,
    val communityId: String = "",
    val likes: Map<String, Boolean> = emptyMap(),
    val commentsCount: Long = 0,
    val authorGender: String? = null,
    val authorAge: Int? = null,
    val authorCountry: String? = null
)
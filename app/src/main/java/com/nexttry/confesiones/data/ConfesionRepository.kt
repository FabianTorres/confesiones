package com.nexttry.confesiones.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nexttry.confesiones.ui.feed.SortOrder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.nexttry.confesiones.ui.feed.TimeRange
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar
import java.util.Date


class ConfesionRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun asegurarLoginAnonimo() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    // Devuelve un Flow que emite la lista de confesiones cada vez que hay un cambio
    fun getConfesionesStream(
        communityId: String,
        sortOrder: SortOrder,
        timeRange: TimeRange

    ): Flow<List<Confesion>> = callbackFlow {


        var query: Query = db.collection("confesiones")
            .whereEqualTo("communityId", communityId)

        // Aplicar filtro de tiempo SI ES NECESARIO (solo para Populares y no ALL)
        if (sortOrder == SortOrder.POPULAR && timeRange != TimeRange.ALL) {
            val startTime = calculateStartTime(timeRange)
            if (startTime != null) {
                // Añadimos el filtro por fecha ANTES del orderBy
                query = query.whereGreaterThanOrEqualTo("timestamp", startTime)
            }
        }

        // 2. Añadimos el ordenamiento dinámicamente
        query = when (sortOrder) {
            SortOrder.RECENT -> {
                // Orden por defecto: más reciente primero
                query.orderBy("timestamp", Query.Direction.DESCENDING)
            }
            SortOrder.POPULAR -> {
                // Nuevo orden: más populares (más likes) primero
                // Añadimos 'timestamp' como segundo orden para desempates
                query
                    .orderBy("likesCount", Query.Direction.DESCENDING)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
            }
            SortOrder.OLDEST -> {
                // Ordenar por timestamp Ascendente (más viejos primero)
                query.orderBy("timestamp", Query.Direction.ASCENDING)
            }
        }

        // 3. El listener usa la consulta final
        val listener = query
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {

                    close(error); return@addSnapshotListener
                }
                val confesiones = snapshot?.toObjects(Confesion::class.java) ?: emptyList()
                trySend(confesiones)
            }
        awaitClose { listener.remove() }
    }

    // Función suspendida para añadir una nueva confesión
    suspend fun addConfesion(texto: String,
                             communityId: String,
                             authorGender: String?,
                             authorAge: Int?,
                             authorCountry: String?,
                             authorAllowsMessaging: Boolean) {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        val nuevaConfesion = Confesion(
            texto = texto,
            userId = userId,
            communityId = communityId,
            authorGender = authorGender,
            authorAge = authorAge,
            authorCountry = authorCountry,
            authorAllowsMessaging = authorAllowsMessaging
        )
        db.collection("confesiones").add(nuevaConfesion).await()
    }

    /**
     * Obtiene la lista completa de comunidades desde Firestore.
     * @return Una lista de objetos Community.
     */
    suspend fun getCommunities(): List<Community> {
        return db.collection("comunidades")
            .orderBy("nombre") // Las ordenamos alfabéticamente por nombre
            .get()
            .await()
            .toObjects(Community::class.java)
    }


    /**
     * Añade o quita un 'like' de una confesión usando una transacción segura.
     * @param confesionId El ID del documento de la confesión.
     * @param userId El ID del usuario que está dando/quitando el like.
     */
    suspend fun toggleLike(confesionId: String, userId: String) {
        val confesionRef = db.collection("confesiones").document(confesionId)

        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(confesionRef)
                val confesion = snapshot.toObject(Confesion::class.java)
                    ?: throw Exception("La confesión no existe")

                // Copiamos el mapa actual de likes a uno mutable
                val newLikes = confesion.likes.toMutableMap()

                if (newLikes.containsKey(userId)) {
                    // El usuario ya dio like, así que lo quitamos
                    newLikes.remove(userId)
                } else {
                    // El usuario no ha dado like, así que lo añadimos
                    newLikes[userId] = true
                }

                // Obtenemos el nuevo tamaño del contador
                val newCount = newLikes.size.toLong()

                // Actualizamos ambos campos en la transacción: el mapa y el contador
                transaction.update(confesionRef, "likes", newLikes)
                transaction.update(confesionRef, "likesCount", newCount)




            }.await()
        } catch (e: Exception) {
            Log.e("Repo-Like", "Error al procesar el like", e)
            throw e // Relanzamos el error para que el ViewModel lo sepa
        }
    }

    /**
     * Obtiene un stream que emite los datos de una confesión específica cada vez que cambia.
     * @param confessionId El ID del documento de la confesión.
     * @return Un Flow que emite el objeto Confesion o null si no se encuentra.
     */
    fun getConfessionStream(confessionId: String): Flow<Confesion?> = callbackFlow {
        val docRef = db.collection("confesiones").document(confessionId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error); return@addSnapshotListener
            }
            trySend(snapshot?.toObject(Confesion::class.java))
        }
        awaitClose { listener.remove() }
    }

    /**
     * Obtiene un stream que emite la lista de comentarios para una confesión específica.
     * @param confessionId El ID del documento de la confesión.
     * @return Un Flow que emite la lista de objetos Comment.
     */
    fun getCommentsStream(confessionId: String): Flow<List<Comment>> = callbackFlow {
        val commentsRef = db.collection("confesiones").document(confessionId).collection("comments")
        val listener = commentsRef.orderBy("timestamp", Query.Direction.ASCENDING) // Comentarios más antiguos primero
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error); return@addSnapshotListener
                }
                val comments = snapshot?.toObjects(Comment::class.java) ?: emptyList()
                trySend(comments)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Añade un nuevo comentario a una confesión.
     * @param confessionId El ID de la confesión a comentar.
     * @param texto El contenido del comentario.
     */
    suspend fun addComment(confessionId: String, texto: String) {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        val newComment = Comment(
            texto = texto,
            userId = userId
        )

        // Referencia al documento de la confesión principal
        val confessionRef = db.collection("confesiones").document(confessionId)
        // Referencia a la subcolección de comentarios
        val newCommentRef = confessionRef.collection("comments").document() // Creamos la referencia antes

        // Usamos una transacción para asegurar que ambas escrituras (comentario y contador) ocurran
        db.runTransaction { transaction ->
            // 1. Incrementamos el contador en el documento principal
            // Obtenemos el valor actual del contador
            val snapshot = transaction.get(confessionRef)
            val currentCount = snapshot.getLong("commentsCount") ?: 0
            // Actualizamos el contador sumándole 1
            transaction.update(confessionRef, "commentsCount", currentCount + 1)

            // 2. Añadimos el nuevo comentario a la subcolección
            transaction.set(newCommentRef, newComment)

            // La transacción se completará automáticamente si todo va bien
            null // La lambda de la transacción debe devolver algo (null está bien)
        }.await() // Esperamos a que la transacción termine
    }

    /**
     * Guarda un reporte sobre una confesión o comentario en Firestore.
     * @param reportedItemId El ID del elemento reportado.
     * @param itemType "confession" o "comment".
     * @param reporterUserId El UID del usuario que reporta.
     */
    suspend fun reportItem(reportedItemId: String,
                           itemType: String,
                           reporterUserId: String,
                           reason: String) {
        val reportData = hashMapOf(
            "reportedItemId" to reportedItemId,
            "itemType" to itemType,
            "reporterUserId" to reporterUserId,
            "timestamp" to Timestamp.now(),
            "reason" to reason.takeIf { it.isNotBlank() }

        )
        // Añadimos el reporte a la colección 'reports'
        db.collection("reports").add(reportData).await()
    }

    /**
     * Obtiene un stream de las confesiones publicadas por el usuario actual.
     * @param userId El UID del usuario actual.
     * @return Un Flow que emite la lista de confesiones del usuario.
     */
    fun getMyConfessionsStream(userId: String): Flow<List<Confesion>> = callbackFlow {
        // Creamos la consulta filtrando por el campo userId
        val query = db.collection("confesiones")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING) // Las más recientes primero
            .limit(50) // Limitamos por si tiene muchas

        // Creamos el listener
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("Repo-MyPosts", "Error escuchando mis confesiones", error)
                close(error); return@addSnapshotListener
            }
            val myConfessions = snapshot?.toObjects(Confesion::class.java) ?: emptyList()
            Log.d("Repo-MyPosts", "Mis confesiones cargadas: ${myConfessions.size}")
            trySend(myConfessions) // Emitimos la lista
        }
        // Cerramos el listener cuando el Flow se cancela
        awaitClose { listener.remove() }
    }

    /**
     * Obtiene los datos de una comunidad específica por su ID.
     * @param communityId El ID del documento de la comunidad.
     * @return El objeto Community o null si no se encuentra.
     */
    suspend fun getCommunityById(communityId: String): Community? {
        return try {
            db.collection("comunidades").document(communityId)
                .get()
                .await()
                .toObject(Community::class.java)
        } catch (e: Exception) {
            Log.e("ConfesionRepo", "Error al obtener comunidad por ID: $communityId", e)
            null // Devuelve null si hay un error
        }
    }


    /**
     * Calcula el Timestamp de inicio basado en el TimeRange seleccionado.
     */
    private fun calculateStartTime(timeRange: TimeRange): Timestamp? {
        val calendar = Calendar.getInstance()
        return when (timeRange) {
            TimeRange.DAY -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                Timestamp(calendar.time)
            }
            TimeRange.WEEK -> {
                calendar.add(Calendar.WEEK_OF_YEAR, -1)
                Timestamp(calendar.time)
            }
            TimeRange.MONTH -> {
                calendar.add(Calendar.MONTH, -1)
                Timestamp(calendar.time)
            }
            TimeRange.ALL -> null // No necesita filtro de tiempo
        }
    }

    /**
     * Obtiene los datos del perfil de un usuario como un Flow.
     * Emite null si el perfil no existe.
     * @param userId El UID del usuario.
     */
    fun getUserProfileStream(userId: String): Flow<UserProfile?> = callbackFlow {
        val docRef = db.collection("userProfiles").document(userId)

        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error); return@addSnapshotListener
            }
            // Emite el perfil, o null si no existe
            trySend(snapshot?.toObject(UserProfile::class.java))
        }
        awaitClose { listener.remove() }
    }

    /**
     * Crea o sobrescribe el perfil de un usuario.
     * @param userId El UID del usuario.
     * @param profile El objeto UserProfile a guardar.
     */
    suspend fun updateUserProfile(userId: String, profile: UserProfile) {
        // Usamos .set() en lugar de .update() para que cree el documento si no existe.
        db.collection("userProfiles").document(userId).set(profile).await()
    }


    /**
     * Busca un chat 1-a-1 existente entre dos usuarios.
     * Si no existe, crea uno nuevo en estado "pending" e inyecta la confesión
     * como el primer mensaje contextual.
     * Si ya existe, simplemente inyecta la confesión como un nuevo mensaje contextual.
     *
     * @param currentUserId El UID del usuario que inicia el chat.
     * @param authorId El UID del autor de la confesión.
     * @param confesion La confesión que sirve de contexto.
     * @return El ID del chat (ya sea existente o nuevo).
     */
    suspend fun findOrCreateChat(
        currentUserId: String,
        authorId: String,
        confesion: Confesion
    ): String {
        // 1. Para evitar duplicados, la lista de miembros SIEMPRE se guarda ordenada alfabéticamente.
        val sortedMembers = listOf(currentUserId, authorId).sorted()

        // 2. Busca un chat que contenga exactamente a estos dos miembros
        val chatQuery = db.collection("chatRooms")
            .whereEqualTo("members", sortedMembers)
            .limit(1)
            .get()
            .await()

        val chatId: String

        if (chatQuery.isEmpty) {
            // --- 3A. NO EXISTE CHAT: Creamos uno nuevo ---
            Log.d("ChatRepo", "No existe chat, creando uno nuevo.")

            // 3.1. Obtenemos los perfiles para los nombres anónimos
            val currentUserProfile = getUserProfileStream(currentUserId).firstOrNull()
            val authorProfile = getUserProfileStream(authorId).firstOrNull()

            val newChatRoom = ChatRoom(
                members = sortedMembers,
                memberNames = mapOf(
                    currentUserId to (currentUserProfile?.anonymousName ?: "Usuario"),
                    authorId to (authorProfile?.anonymousName ?: "Usuario")
                ),
                status = "pending" // El chat empieza como una solicitud
                // ttlTimestamp se pone por defecto
            )

            // 3.2. Creamos el documento del ChatRoom
            val chatDocRef = db.collection("chatRooms").add(newChatRoom).await()
            chatId = chatDocRef.id

        } else {
            // --- 3B. EL CHAT YA EXISTE: Obtenemos su ID ---
            chatId = chatQuery.documents.first().id
            Log.d("ChatRepo", "Chat existente encontrado: $chatId")
        }

        // --- 4. AÑADIMOS EL MENSAJE DE CONTEXTO ---
        // (Tanto si el chat es nuevo como si es existente)
        val contextMessage = Message(
            senderId = currentUserId, // El que inicia la acción
            text = confesion.texto, // El texto de la confesión
            isContext = true,
            timestamp = Timestamp.now()
        )

        // 4.1. Añadimos el mensaje a la subcolección
        db.collection("chatRooms").document(chatId)
            .collection("messages")
            .add(contextMessage)
            .await()

        // La Cloud Function que creamos en el Paso 4 (plan) se encargará
        // de actualizar el lastMessageText, timestamp y status del ChatRoom padre.

        return chatId
    }


    /**
     * Obtiene un stream que emite los datos de un ChatRoom específico.
     */
    fun getChatRoomStream(chatId: String): Flow<ChatRoom?> = callbackFlow {
        val docRef = db.collection("chatRooms").document(chatId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error); return@addSnapshotListener
            }
            trySend(snapshot?.toObject(ChatRoom::class.java))
        }
        awaitClose { listener.remove() }
    }

    /**
     * Obtiene un stream que emite la lista de mensajes de un chat, ordenados por fecha.
     */
    fun getMessagesStream(chatId: String): Flow<List<Message>> = callbackFlow {
        val messagesRef = db.collection("chatRooms").document(chatId).collection("messages")
        val listener = messagesRef.orderBy("timestamp", Query.Direction.ASCENDING) // Los más nuevos al final
            .limitToLast(100) // Traemos solo los últimos 100 mensajes
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error); return@addSnapshotListener
                }
                val messages = snapshot?.toObjects(Message::class.java) ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Envía un nuevo mensaje (no contextual) a un chat.
     */
    suspend fun sendChatMessage(chatId: String, text: String, senderId: String) {
        val message = Message(
            senderId = senderId,
            text = text,
            isContext = false,
            timestamp = Timestamp.now()
        )
        db.collection("chatRooms").document(chatId)
            .collection("messages")
            .add(message)
            .await()
        // La Cloud Function que planificamos se encargará de actualizar el ChatRoom padre.
    }

    /**
     * Actualiza el estado de un chat a "active" (aceptado).
     */
    suspend fun acceptChat(chatId: String) {
        db.collection("chatRooms").document(chatId)
            .update("status", "active")
            .await()
    }

    /**
     * Actualiza el estado de un chat a "rejected" (rechazado).
     * Nota: No borramos el documento para evitar mensajes huérfanos.
     * La lista de chats principal filtrará los "rejected".
     */
    suspend fun rejectChat(chatId: String) {
        db.collection("chatRooms").document(chatId)
            .update("status", "rejected")
            .await()
    }

    /**
     * Obtiene un stream de todos los ChatRooms en los que el usuario actual
     * es miembro, ordenados por el último mensaje.
     */
    fun getMyChatRoomsStream(userId: String): Flow<List<ChatRoom>> = callbackFlow {
        val query = db.collection("chatRooms")
            // Busca todos los chats donde el array 'members' contenga nuestro ID
            .whereArrayContains("members", userId)
            // Ordena por el último mensaje (el más nuevo primero)
            // La Cloud Function que planificamos es VITAL para que este campo esté actualizado.
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ChatRepo", "Error al escuchar mis chats", error)
                close(error); return@addSnapshotListener
            }
            val chatRooms = snapshot?.toObjects(ChatRoom::class.java) ?: emptyList()
            trySend(chatRooms)
        }
        awaitClose { listener.remove() }
    }

    /**
     * Añade un UID a la lista de bloqueo del usuario actual.
     */
    suspend fun blockUser(currentUserId: String, userIdToBlock: String) {
        val userProfileRef = db.collection("userProfiles").document(currentUserId)

        // Usamos FieldValue.arrayUnion para añadir el ID a la lista
        // de forma segura, evitando duplicados.
        userProfileRef.update("blockedUsers", com.google.firebase.firestore.FieldValue.arrayUnion(userIdToBlock))
            .await()
    }
}
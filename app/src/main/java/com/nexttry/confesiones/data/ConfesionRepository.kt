package com.nexttry.confesiones.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nexttry.confesiones.data.Comment
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ConfesionRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun asegurarLoginAnonimo() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    // Devuelve un Flow que emite la lista de confesiones cada vez que hay un cambio
    fun getConfesionesStream(communityId: String): Flow<List<Confesion>> = callbackFlow {
        val listener = db.collection("confesiones")
            .whereEqualTo("communityId", communityId) // El filtro mágico
            .orderBy("timestamp", Query.Direction.DESCENDING)
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
    suspend fun addConfesion(texto: String, communityId: String) {
        val userId = auth.currentUser?.uid ?: throw Exception("Usuario no autenticado")
        val nuevaConfesion = Confesion(
            texto = texto,
            userId = userId,
            communityId = communityId
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

                // Actualizamos el documento en la transacción con el nuevo mapa
                transaction.update(confesionRef, "likes", newLikes)

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
}
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

/**
 * Cloud Function que se dispara al crear un nuevo mensaje.
 * Actualiza el documento 'chatRoom' padre con la información
 * del último mensaje (para previews y ordenación).
 */
export const onNewMessage = onDocumentCreated(
  "chatRooms/{chatId}/messages/{messageId}",
  async (event) => {
    const snapshot = event.data;
    const { chatId } = event.params;

    if (!snapshot) {
      logger.warn("No se encontró data del mensaje creado.");
      return;
    }

    // 1. Obtiene los datos del mensaje
    const messageData = snapshot.data();
    if (!messageData) {
      logger.warn("Mensaje vacío o inválido.");
      return;
    }

    const { text, senderId, timestamp, isContext } = messageData;
    const chatRoomRef = db.collection("chatRooms").doc(chatId);

    // 2. Calcula la nueva fecha de borrado automático (TTL)
    const newTtlTimestamp = new Date(
      timestamp.toMillis() + 7 * 24 * 60 * 60 * 1000
    );

    try {
      if (isContext) {
        // Caso: mensaje contextual (una confesión)
        await chatRoomRef.update({
          lastMessageText: "Se compartió una confesión...",
          lastMessageSenderId: null,
          lastMessageTimestamp: timestamp,
          ttlTimestamp: admin.firestore.Timestamp.fromDate(newTtlTimestamp),
          status: "pending",
        });
      } else {
        // Caso: mensaje normal
        await chatRoomRef.update({
          lastMessageText: text,
          lastMessageSenderId: senderId,
          lastMessageTimestamp: timestamp,
          ttlTimestamp: admin.firestore.Timestamp.fromDate(newTtlTimestamp),
          status: "active",
        });
      }

      logger.info(`ChatRoom ${chatId} actualizado correctamente.`);
    } catch (error) {
      logger.error("Error al actualizar ChatRoom:", error);
    }
  }
);

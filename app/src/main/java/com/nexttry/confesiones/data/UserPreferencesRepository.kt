package com.nexttry.confesiones.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

//private val communityIdKey = stringPreferencesKey("selected_community_id")
/**
 * Gestiona el guardado y la lectura de las preferencias del usuario de forma local.
 */
class UserPreferencesRepository(private val context: Context) {

    private val communityIdKey = stringPreferencesKey("selected_community_id")

    /**
     * Un Flow que emite el ID de la comunidad guardada cada vez que cambia.
     */
    val selectedCommunityId: Flow<String> = context.dataStore.data
        // Se añadió un bloque .catch para manejar errores ---
        .catch { exception ->
            // Si hay un error de lectura (ej: corrupción de datos), emitimos un valor por defecto.
            if (exception is IOException) {
                Log.e("UserPrefsRepo", "Error al leer preferencias.", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            // Se cambió el valor devuelto ---
            // Si la preferencia no existe, devolvemos una cadena vacía "" en lugar de null.
            // Esto asegura que el Flow siempre emita un valor inicial.
            preferences[communityIdKey] ?: ""
        }


    suspend fun saveCommunityId(communityId: String) {
        context.dataStore.edit { preferences ->
            preferences[communityIdKey] = communityId
        }
    }

    /**
     * Limpia el ID de la comunidad guardada, efectivamente "cerrando sesión"
     * del canal actual y forzando una nueva selección.
     */
    suspend fun clearCommunityId() {
        context.dataStore.edit { preferences ->
            preferences.remove(communityIdKey)
        }
    }
}
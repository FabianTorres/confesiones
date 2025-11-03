package com.nexttry.confesiones.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Transgender
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexttry.confesiones.data.Confesion // Importamos el modelo

/**
 * Un Composable "helper" que renderiza TODA la información
 * de perfil disponible de una confesión.
 */
@Composable
fun AuthorInfoRow(confesion: Confesion, modifier: Modifier = Modifier) {
    // Usamos un Row para poner las 3 insignias una al lado de la otra
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp) // Espacio entre insignias
    ) {

        // --- Insignia 1: Género ---
        confesion.authorGender?.let { gender ->
            val (icon, color) = when (gender) {
                "male" -> Icons.Default.Male to Color(0xFF89CFF0) // Azul claro
                "female" -> Icons.Default.Female to Color(0xFFF4C2C2) // Rosa claro
                else -> Icons.Default.Transgender to Color(0xFF98FF98) // Verde claro (para "Otro")
            }
            Icon(
                imageVector = icon,
                contentDescription = gender, // TODO: Usar stringResource
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }

        // --- Insignia 2: Edad ---
        confesion.authorAge?.let { age ->
            Text(
                text = "$age años",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary // Color que resalta
            )
        }

        // --- Insignia 3: País ---
        confesion.authorCountry?.let { countryCode ->
            Text(
                text = countryCode, // ej. "CL"
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
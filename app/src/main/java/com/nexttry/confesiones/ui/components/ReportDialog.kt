package com.nexttry.confesiones.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Diálogo para confirmar el reporte de un elemento.
 * @param onDismissRequest Se llama cuando el usuario cierra el diálogo (clic fuera o botón Cancelar).
 * @param onConfirm Se llama cuando el usuario confirma el reporte, pasando el motivo.
 */
@Composable
fun ReportDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (reason: String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Reportar Contenido") },
        text = {
            Column {
                Text("¿Estás seguro de que quieres reportar este contenido como inapropiado?")
                Spacer(Modifier.height(16.dp))
                // Campo opcional para el motivo
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(reason.trim()) // Enviamos el motivo (o vacío si no escribió nada)
                }
            ) {
                Text("Reportar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancelar")
            }
        }
    )
}
package com.nexttry.confesiones.ui.newconfession

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConfessionScreen(
    communityId: String, // Recibimos el ID
    onNavigateBack: () -> Unit,
    vm: NewConfessionViewModel = viewModel() // El ViewModel se asocia automáticamente
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    // Efecto para navegar hacia atrás cuando publishSuccess sea true
    LaunchedEffect(uiState.publishSuccess) {
        if (uiState.publishSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva Confesión") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Botón de publicar en la barra superior
                    IconButton(
                        onClick = { vm.publishConfession() },
                        enabled = uiState.text.isNotBlank() && !uiState.isPublishing // Habilitado si hay texto y no está publicando
                    ) {
                        // Muestra indicador de carga si está publicando
                        if (uiState.isPublishing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Send, contentDescription = "Publicar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            //verticalArrangement = Arrangement.SpaceBetween // Empuja el contador hacia abajo
        ) {
            // Campo de texto principal
            OutlinedTextField(
                value = uiState.text,
                onValueChange = { vm.onTextChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    //.weight(1f),
                    .heightIn(min = 250.dp, max = 450.dp), //para modificar el textArea
                placeholder = { Text("Escribe tu confesión anónima...") },
                isError = uiState.error != null, // Muestra error si existe
                colors = OutlinedTextFieldDefaults.colors( // Usando la función correcta
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorBorderColor = MaterialTheme.colorScheme.error, // Color de borde en error
                    errorLabelColor = MaterialTheme.colorScheme.error   // Color de label en error
                )
            )

            Spacer(Modifier.height(8.dp))

            // Mensaje de error
            val currentError = uiState.error
            if (currentError != null) {
                Text(
                    // 2. Usa la variable local segura
                    text = currentError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    //modifier = Modifier.padding(start = 16.dp)
                )
                Spacer(Modifier.height(4.dp))
            }


            // Contador de caracteres
            Text(
                text = "${uiState.text.length} / ${vm.maxChars}",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
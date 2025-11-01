package com.nexttry.confesiones.ui.newcomment

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.nexttry.confesiones.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCommentScreen(
    confessionId: String, // Recibimos el ID
    onNavigateBack: () -> Unit,
    vm: NewCommentViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    // Calcular color con elevación
    val appBarContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)

    LaunchedEffect(uiState.publishSuccess) {
        if (uiState.publishSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo Comentario") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.accessibility_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBarContainerColor,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(
                        onClick = { vm.publishComment() },
                        enabled = uiState.text.isNotBlank() && !uiState.isPublishing
                    ) {
                        if (uiState.isPublishing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Send,
                                contentDescription = stringResource(id = R.string.accessibility_comment))
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
            //verticalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedTextField(
                value = uiState.text,
                onValueChange = { vm.onTextChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 250.dp, max = 450.dp),
                placeholder = { Text("Escribe tu comentario...") },
                isError = uiState.error != null,
                colors = OutlinedTextFieldDefaults.colors( // Usando la función correcta
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorBorderColor = MaterialTheme.colorScheme.error,
                    errorLabelColor = MaterialTheme.colorScheme.error
                )
            )

            Spacer(Modifier.height(8.dp))

            // 1. Captura el valor localmente
            val currentError = uiState.error
            if (currentError != null) {
                Text(
                    // 2. Usa la variable local
                    text = currentError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    //modifier = Modifier.padding(start = 16.dp)
                )
                Spacer(Modifier.height(4.dp))
            }

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
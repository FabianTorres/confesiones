package com.nexttry.confesiones.ui.myposts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexttry.confesiones.ui.feed.TarjetaConfesion
import androidx.compose.material.icons.outlined.EditOff
import com.nexttry.confesiones.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPostsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToConfession: (String) -> Unit,
    vm: MyPostsViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    // --- Calcular color con elevación ---
    val appBarContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Publicaciones") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBarContainerColor,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                // Estado de carga
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                // Estado de error
                uiState.error != null -> {
                    Text(
                        text = "Error: ${uiState.error}",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // Estado vacío (no ha publicado nada)
                uiState.myConfessions.isEmpty() -> {
                    // Reemplazamos el Text simple por el nuevo Composable
                    Box(modifier = Modifier.align(Alignment.Center)) { // Centramos el componente
                        EmptyState(
                            icon = Icons.Outlined.EditOff,
                            title = "Sin publicaciones",
                            subtitle = "Aún no has publicado ninguna confesión. ¡Anímate a compartir algo!"
                        )
                    }
                }
                // Estado con datos: Mostramos la lista
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.myConfessions, key = { it.id }) { confesion ->
                            // Reutilizamos TarjetaConfesion, pero sin algunas acciones
                            TarjetaConfesion(
                                confesion = confesion,
                                // No mostramos estado de like aquí (podría añadirse si quieres)
                                isLikedByCurrentUser = false,
                                // El like no hace nada en esta pantalla (o podría navegar al detalle)
                                onLikeClicked = { /* No-op */ },
                                // Al hacer clic en la tarjeta, vamos al detalle
                                onCardClicked = { onNavigateToConfession(confesion.id) },
                                // El reporte no hace nada aquí (o podría añadirse)
                                onReportClicked = { /* No-op */ }
                            )
                        }
                    }
                }
            }
        }
    }
}
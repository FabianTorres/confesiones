package com.nexttry.confesiones.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexttry.confesiones.data.Comment
import com.nexttry.confesiones.data.Confesion
import com.nexttry.confesiones.ui.components.ReportDialog
import com.nexttry.confesiones.ui.feed.formatRelativeTime
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfessionDetailScreen(
    confessionId: String,
    onNavigateBack: () -> Unit,
    vm: ConfessionDetailViewModel = viewModel() // El ViewModel se crea automáticamente
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    // Estados para diálogos de reporte (sin cambios)
    var showReportConfessionDialog by remember { mutableStateOf(false) }
    var showReportCommentDialog by remember { mutableStateOf(false) }
    var commentToReport by remember { mutableStateOf<String?>(null) }

    // Estado para manejar los Snackbars ---
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    //LaunchedEffect para escuchar eventos ---
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is DetailScreenEvent.ShowSnackbar -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = event.message,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Confesión") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.confession == null) {
                // Mostramos un mensaje si la confesión no se pudo cargar
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No se pudo cargar la confesión.")
                }
            }
            else {
                // Contenido principal: confesión y comentarios
                LazyColumn(
                    modifier = Modifier.weight(1f), // Ocupa el espacio disponible
                    contentPadding = PaddingValues(16.dp), // Padding general
                    verticalArrangement = Arrangement.spacedBy(16.dp) // Espacio mayor entre elementos
                ) {
                    // 1. Confesión Principal (ahora con padding propio)
                    item {
                        ConfessionDetailCard(
                            confesion = uiState.confession!!,
                            isLiked = uiState.currentUserId in uiState.confession!!.likes,
                            onLikeClicked = { vm.onLikeClicked() },
                            onReportClicked = { showReportConfessionDialog = true }
                        )
                    }

                    // 2. Sección de Comentarios (Título + Lista o Mensaje de vacío)
                    item {
                        Text(
                            "Comentarios (${uiState.comments.size})", // Muestra el número de comentarios
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp) // Espacio extra arriba
                        )
                    }

                    if (uiState.comments.isEmpty()) {
                        // --- EDITADO: Estado Vacío para comentarios ---
                        item {
                            Text(
                                "Nadie ha comentado aún. ¡Sé el primero!",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        // Lista de comentarios
                        items(uiState.comments, key = { it.id }) { comment ->
                            CommentCard(
                                comment = comment,
                                onReportClicked = {
                                    commentToReport = comment.id
                                    showReportCommentDialog = true
                                }
                            )
                        }
                    }
                }
                // 5. Campo para añadir un nuevo comentario (fijo abajo)
                CommentInput(onCommentSent = { vm.postComment(it) })
            }
        }
    }

    // Diálogo para reportar la confesión
    if (showReportConfessionDialog) {
        ReportDialog(
            onDismissRequest = { showReportConfessionDialog = false },
            onConfirm = { reason ->
                vm.onReportConfessionClicked(reason)
                showReportConfessionDialog = false
                // TODO: Snackbar "Reporte enviado"
            }
        )
    }

    // Diálogo para reportar un comentario
    if (showReportCommentDialog && commentToReport != null) {
        ReportDialog(
            onDismissRequest = {
                showReportCommentDialog = false
                commentToReport = null
            },
            onConfirm = { reason ->
                vm.onReportCommentClicked(commentToReport!!, reason)
                showReportCommentDialog = false
                commentToReport = null
                // TODO: Snackbar "Reporte enviado"
            }
        )
    }
}

// --- Componentes de UI específicos para esta pantalla ---

@Composable
fun ConfessionDetailCard(
    confesion: Confesion,
    isLiked: Boolean,
    onLikeClicked: () -> Unit,
    onReportClicked: () -> Unit
) {
    // Estado local para el feedback visual del botón de reporte
    var reportClicked by remember { mutableStateOf(false) }
    val maxChars = 1000

    Card(
        shape = RoundedCornerShape(16.dp), // Bordes redondeados
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // Añade sombra
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) { // Padding interno
            // Texto principal de la confesión
            Text(
                text = confesion.texto,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                // --- EDITADO: Limitar a 1000 caracteres (aunque es mucho) ---
                // Usamos take() para cortar la string y evitar problemas de rendimiento con textos gigantes
                // text = confesion.texto.take(maxChars),
                // maxLines = 50, // Un número grande para permitir scroll si es necesario
                // overflow = TextOverflow.Ellipsis // Mostrar "..." si se corta
                // NOTA: Con 1000 caracteres, es probable que no necesites maxLines/overflow,
                // pero take(maxChars) es una buena medida de seguridad. Decide si quieres el límite visual o solo de datos.
                // Por ahora, mostraremos el texto completo ya que 1000 es bastante.
            )
            Spacer(modifier = Modifier.height(16.dp)) // Un poco más de espacio

            // Fila inferior con acciones y metadatos
            Row(
                modifier = Modifier.fillMaxWidth(), // Ocupa todo el ancho
                horizontalArrangement = Arrangement.SpaceBetween, // Separa elementos
                verticalAlignment = Alignment.CenterVertically // Centra verticalmente
            ) {
                // Hijo 1: Grupo de Likes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Botón de Like
                    IconButton(onClick = onLikeClicked, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    // Contador de Likes
                    Text(
                        text = confesion.likes.size.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Hijo 2: Grupo de Reporte y Fecha
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botón de Reportar
                    IconButton(
                        onClick = {
                            onReportClicked()
                            reportClicked = true // Activa feedback
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Reportar Confesión",
                            tint = if (reportClicked) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    // Texto de la Fecha
                    Text(
                        text = formatRelativeTime(confesion.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun CommentCard(comment: Comment, onReportClicked: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        // --- EDITADO: Box es ahora el único hijo directo de la Card ---
        // Le damos una altura mínima para que no colapse si el comentario es muy corto
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp) // Altura mínima para la tarjeta
                .padding(horizontal = 16.dp, vertical = 10.dp) // Padding general
        ) {
            // Hijo 1: Texto principal del comentario.
            // Lo alineamos arriba a la izquierda y le damos padding para que no choque con los otros elementos.
            Text(
                text = comment.texto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart) // Fijo arriba a la izquierda
                    .padding(end = 24.dp, bottom = 20.dp) // Espacio para el ícono de reporte y la fecha
            )

            // Hijo 2: Botón de reporte.
            // Alinearlo en la esquina superior derecha del Box.
            IconButton(
                onClick = onReportClicked,
                modifier = Modifier
                    .align(Alignment.TopEnd) // Fijo arriba a la derecha
                    .size(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = "Reportar Comentario",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }

            // Hijo 3: Fecha del comentario.
            // Alinearlo en la esquina inferior derecha del Box.
            Text(
                text = formatRelativeTime(comment.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.BottomEnd) // Fijo abajo a la derecha
            )
        } // Fin Box
    } // Fin Card
}

@Composable
fun CommentInput(onCommentSent: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val maxChars = 1000
    Card(
        modifier = Modifier.padding(8.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                // - Alineación al inicio para que el botón no se estire ---
                verticalAlignment = Alignment.Top // O Alignment.Center si prefieres el botón centrado verticalmente
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= maxChars) text = it },
                    label = { Text("Escribe un comentario...") },
                    modifier = Modifier.weight(1f), // Ocupa el espacio horizontal restante
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    ),
                    // --- EDITADO: Se añadieron minLines y maxLines ---
                    minLines = 1, // Altura mínima de 1 línea
                    maxLines = 4  // Altura máxima de 4 líneas (antes de hacer scroll interno)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // --- EDITADO: Padding superior para alinear mejor con el texto ---
                IconButton(
                    onClick = {
                        onCommentSent(text)
                        text = ""
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.padding(top = 8.dp) // Añade un poco de espacio arriba
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Enviar Comentario")
                }
            }
            Text(
                text = "${text.length} / $maxChars",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
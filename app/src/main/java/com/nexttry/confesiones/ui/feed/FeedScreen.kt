package com.nexttry.confesiones.ui.feed

import android.app.Application
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Flag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexttry.confesiones.data.Confesion
import com.nexttry.confesiones.ui.components.ReportDialog
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

private const val TAG = "FeedScreen"
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(communityId: String,
               onChangeCommunity: () -> Unit,
               onNavigateToConfession: (String) -> Unit,
               onNavigateToMyPosts: () -> Unit) {
    Log.d(TAG, "FeedScreen se está componiendo. CommunityId: $communityId")
    //  Obtenemos el contexto y la aplicación
    val context = LocalContext.current
    val application = context.applicationContext as Application

    Log.d(TAG, "Creando la fábrica del ViewModel...")
    val vm: FeedViewModel = viewModel(
        key = communityId,
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                Log.d(TAG, "Factory: Creando SavedStateHandle...")
                // Usamos la 'application' que obtuvimos fuera de la fábrica.
                val savedStateHandle = SavedStateHandle(mapOf("communityId" to communityId))
                Log.d(TAG, "Factory: Creando instancia de FeedViewModel...")
                return FeedViewModel(application, savedStateHandle) as T
            }
        }
    )

    val uiState by vm.uiState.collectAsStateWithLifecycle()

    // Estado para manejar los Snackbars ---
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is FeedScreenEvent.ShowSnackbar -> {
                    // Lanzamos una coroutine para mostrar el Snackbar
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = event.message,
                            duration = SnackbarDuration.Short // Duración corta
                        )
                    }
                }
                // Manejar otros eventos si los hubiera
            }
        }
    }


    var showReportDialog by remember { mutableStateOf(false) }
    var itemToReport by remember { mutableStateOf<String?>(null) }
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Confesiones Anónimas") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    //  Se añadió el botón para "Mis Publicaciones" ---
                    IconButton(onClick = onNavigateToMyPosts) {
                        Icon(
                            imageVector = Icons.Default.Person, // Ícono de persona
                            contentDescription = "Mis Publicaciones"
                        )
                    }
                    // Botón para cambiar de comunidad
                    IconButton(onClick = onChangeCommunity) {
                        Icon(

                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Cambiar de Comunidad"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.confesiones, key = { it.id }) { confesion ->
                        TarjetaConfesion(
                            confesion = confesion,
                            isLikedByCurrentUser = uiState.currentUserId in confesion.likes,
                            onLikeClicked = { vm.onLikeClicked(confesion.id) },
                            onCardClicked = { onNavigateToConfession(confesion.id) },
                            onReportClicked = { itemToReport = confesion.id
                                                showReportDialog = true }
                        )
                    }
                }
                PublicarConfesionUI(
                    onPublish = { texto -> vm.publicarConfesion(texto) }
                )
            }
        }
    }

    if (showReportDialog && itemToReport != null) {
        ReportDialog(
            onDismissRequest = {
                showReportDialog = false // Cierra el diálogo
                itemToReport = null    // Limpia el ID guardado
            },
            onConfirm = { reason ->
                // Cuando el usuario confirma, llamamos al ViewModel con el ID y el motivo
                vm.onReportConfessionClicked(itemToReport!!, reason)
                showReportDialog = false // Cierra el diálogo
                itemToReport = null    // Limpia el ID guardado
                // Aquí podrías mostrar un Snackbar de confirmación
            }
        )
    }
}

@Composable
fun TarjetaConfesion(
    confesion: Confesion,
    isLikedByCurrentUser: Boolean,
    onLikeClicked: () -> Unit,
    onCardClicked: () -> Unit,
    onReportClicked: () -> Unit
) {
    // Estado local para el feedback visual del botón de reporte
    var reportClicked by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.clickable(onClick = onCardClicked),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            // Mantenemos el fondo semi-transparente
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // Aumentamos la sombra
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)) // Borde muy sutil
    )
     {
        Column(modifier = Modifier.padding(16.dp)) { // Padding interno de la tarjeta
            // Texto principal de la confesión
            Text(
                text = confesion.texto,
                style = MaterialTheme.typography.bodyLarge, // Estilo de texto principal
                color = MaterialTheme.colorScheme.onSurface, // Color de texto según el tema
                maxLines = 4, // Aproximadamente 120 caracteres, ajusta si es necesario
                overflow = TextOverflow.Ellipsis // Muestra "..." si el texto es más largo
            )
            Spacer(modifier = Modifier.height(12.dp)) // Espacio vertical

            // Fila inferior con acciones y metadatos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hijo 1: Grupo Likes y Comentarios
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp) // Mayor espacio entre Likes y Comentarios
                ) {
                    // Likes
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onLikeClicked, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = if (isLikedByCurrentUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isLikedByCurrentUser) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = confesion.likes.size.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    // --- EDITADO: Se añadió la sección de Comentarios ---
                    // Comentarios
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon( // Ícono de comentario
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Comentarios",
                            modifier = Modifier.size(20.dp), // Tamaño similar al corazón
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text( // Contador de comentarios
                            text = confesion.commentsCount.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                } // Fin Grupo Likes y Comentarios

                // Hijo 2: Grupo Reporte y Fecha
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ){
                    // Botón de Reportar (Bandera)
                    IconButton(
                        onClick = {
                            onReportClicked()
                            reportClicked = true // Activa el feedback visual (color rojo temporal)
                        },
                        modifier = Modifier.size(20.dp) // Tamaño más pequeño para la bandera
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Reportar",
                            // Cambia el color a rojo si se acaba de hacer clic, si no, grisáceo
                            tint = if (reportClicked) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    // Texto de la Fecha
                    Text(
                        text = formatRelativeTime(confesion.timestamp), // Muestra la fecha formateada
                        style = MaterialTheme.typography.bodySmall, // Estilo pequeño
                        fontStyle = FontStyle.Italic, // Cursiva
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) // Color grisáceo
                    )
                }
            }
        }
    }
}

fun formatRelativeTime(timestamp: com.google.firebase.Timestamp): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp.toDate().time

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        seconds < 60 -> "Hace un momento"
        minutes < 60 -> "Hace $minutes min"
        hours < 24 -> "Hace $hours h"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(timestamp.toDate())
    }
}

@Composable
fun PublicarConfesionUI(onPublish: (String) -> Unit) {
    var texto by remember { mutableStateOf("") }

    val maxChars = 120
    //El área de publicación ahora está en una Card para destacarla
    Card(
        modifier = Modifier.padding(8.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { if (it.length <= maxChars) texto = it },
                    label = { Text("Escribe algo...") },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Usamos un IconButton para un look más limpio ---
                IconButton(
                    onClick = {
                        onPublish(texto)
                        texto = ""
                    },
                    enabled = texto.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Publicar Confesión"
                    )
                }
            }

            // --- NUEVO: Contador de caracteres visible ---
            Text(
                text = "${texto.length} / $maxChars",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
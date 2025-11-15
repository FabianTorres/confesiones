package com.nexttry.confesiones.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexttry.confesiones.R
import com.nexttry.confesiones.data.Message
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import com.nexttry.confesiones.ui.feed.formatRelativeTime
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.input.pointer.pointerInput
import com.nexttry.confesiones.ui.components.ReportDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    chatId: String,
    navController: NavController,
    vm: ConversationViewModel = viewModel(key = chatId)
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val appBarContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Para hacer scroll automático al final cuando llega un nuevo mensaje
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    // Lógica para encontrar el nombre del "otro" usuario
    val otherUserName = remember(uiState.chatRoom) {
        uiState.chatRoom?.memberNames
            ?.filterKeys { it != uiState.currentUserId }
            ?.values?.firstOrNull() ?: "Usuario"
    }

    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var messageToReport by remember { mutableStateOf<Message?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(otherUserName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.accessibility_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBarContainerColor,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    // Botón (⋮) para "Más opciones"
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Más opciones" // TODO: Añadir a strings.xml
                        )
                    }
                    // Menú desplegable para "Bloquear"
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Bloquear a $otherUserName") },
                            onClick = {
                                showMenu = false
                                vm.onBlockUserClicked()
                                // Opcional: Mostrar un Snackbar y/o salir de la pantalla
                                navController.popBackStack()
                            }
                        )
                        // Aquí podríamos añadir "Reportar Usuario" en el futuro
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- Banner de Aceptar/Rechazar ---
            if (uiState.chatRoom?.status == "pending") {
                PendingChatBanner(
                    onAccept = { vm.acceptChat() },
                    onReject = {
                        vm.rejectChat()
                        navController.popBackStack() // Salir de la pantalla al rechazar
                    }
                )
            }

            // --- Lista de Mensajes ---
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isSentByCurrentUser = message.senderId == uiState.currentUserId,
                        onLongPress = {
                            // No podemos reportar mensajes de contexto o nuestros propios mensajes
                            if (!message.isContext && message.senderId != uiState.currentUserId) {
                                messageToReport = message
                                showReportDialog = true
                            }
                        }
                    )
                }
            }

            // --- Caja de Texto para Enviar ---
            ChatInput(
                onMessageSent = { vm.sendMessage(it) },
                // Solo se puede chatear si el estado es "active"
                isEnabled = uiState.chatRoom?.status == "active"
            )
        }
    }

    if (showReportDialog && messageToReport != null) {
        ReportDialog(
            onDismissRequest = {
                showReportDialog = false
                messageToReport = null
            },
            onConfirm = { reason ->
                vm.onReportMessageClicked(messageToReport!!.id, reason)
                showReportDialog = false
                messageToReport = null
                // TODO: Mostrar un Snackbar de "Reporte enviado"
            }
        )
    }
}

/**
 * Muestra una burbuja de chat.
 */
@Composable
fun MessageBubble(message: Message, isSentByCurrentUser: Boolean, onLongPress: () -> Unit) {


    // Contenedor principal que detecta la pulsación larga
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) { // Usamos message.id como clave
                detectTapGestures(
                    onLongPress = { onLongPress() }
                )
            },
    ) {
        // Si es un mensaje de contexto (confesión), usa un estilo especial
        if (message.isContext) {
            ContextMessage(message.text)
            return
        }
        // Si es un mensaje normal, lo muestra en una burbuja alineada
        else {

            // Determina colores y alineación
            val bubbleColor = if (isSentByCurrentUser) {
                MaterialTheme.colorScheme.primary // Mensaje enviado (azul)
            } else {
                MaterialTheme.colorScheme.surfaceVariant // Mensaje recibido (gris)
            }

            val textColor = if (isSentByCurrentUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            val alignment = if (isSentByCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
            val shape = if (isSentByCurrentUser) {
                RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
            } else {
                RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (isSentByCurrentUser) 64.dp else 0.dp,
                        end = if (isSentByCurrentUser) 0.dp else 64.dp
                    ),
                contentAlignment = alignment
            ) {
                Column(
                    modifier = Modifier
                        .clip(shape)
                        .background(bubbleColor)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = if (isSentByCurrentUser) Alignment.End else Alignment.Start
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                    Text(
                        text = formatRelativeTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Muestra el mensaje de contexto (la confesión).
 */
@Composable
fun ContextMessage(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Respuesta a:\n\"$text\"",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Muestra la caja de texto para enviar un mensaje.
 */
@Composable
fun ChatInput(onMessageSent: (String) -> Unit, isEnabled: Boolean) {
    var text by remember { mutableStateOf("") }

    Surface(tonalElevation = 3.dp) { // Una leve sombra para separar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (isEnabled) "Escribe un mensaje..." else "Debes esperar a que acepten el chat") },
                enabled = isEnabled,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onMessageSent(text)
                        text = "" // Limpia el campo
                    }
                },
                enabled = text.isNotBlank() && isEnabled
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(id = R.string.accessibility_send),
                    tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

/**
 * Muestra el banner para aceptar o rechazar un chat pendiente.
 */
@Composable
fun PendingChatBanner(onAccept: () -> Unit, onReject: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Un usuario quiere enviarte un mensaje.",
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onAccept, colors = ButtonDefaults.buttonColors()) {
                    Text("Aceptar")
                }
                OutlinedButton(onClick = onReject) {
                    Text("Rechazar")
                }
            }
        }
    }
}
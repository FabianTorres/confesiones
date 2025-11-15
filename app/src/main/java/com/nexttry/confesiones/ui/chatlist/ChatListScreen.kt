package com.nexttry.confesiones.ui.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nexttry.confesiones.R
import com.nexttry.confesiones.data.ChatRoom
import com.nexttry.confesiones.ui.components.EmptyState
import com.nexttry.confesiones.ui.feed.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    navController: NavController,
    vm: ChatListViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val appBarContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Mensajes") },
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
                )
            )
        }
    ) { padding ->

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.pendingChats.isEmpty() && uiState.activeChats.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.Forum,
                        title = "Bandeja de entrada vacía",
                        subtitle = "Cuando inicies una conversación, aparecerá aquí."
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // --- SECCIÓN 1: SOLICITUDES DE MENSAJES ---
                    if (uiState.pendingChats.isNotEmpty()) {
                        item {
                            Text(
                                "Solicitudes de Mensajes",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(uiState.pendingChats, key = { it.id }) { chatRoom ->
                            ChatRow(
                                chatRoom = chatRoom,
                                currentUserId = uiState.currentUserId,
                                isPending = true,
                                onClick = {
                                    navController.navigate("conversation/${chatRoom.id}")
                                }
                            )
                        }
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }

                    // --- SECCIÓN 2: CHATS ACTIVOS ---
                    if (uiState.activeChats.isNotEmpty()) {
                        items(uiState.activeChats, key = { it.id }) { chatRoom ->
                            ChatRow(
                                chatRoom = chatRoom,
                                currentUserId = uiState.currentUserId,
                                isPending = false,
                                onClick = {
                                    navController.navigate("conversation/${chatRoom.id}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Muestra una fila en la lista de chats (estilo Instagram/Twitter).
 */
@Composable
private fun ChatRow(
    chatRoom: ChatRoom,
    currentUserId: String?,
    isPending: Boolean,
    onClick: () -> Unit
) {
    // Lógica para encontrar el nombre del "otro" usuario
    val otherUserName = remember(chatRoom) {
        chatRoom.memberNames
            .filterKeys { it != currentUserId }
            .values.firstOrNull() ?: "Usuario"
    }

    // Lógica para el preview del último mensaje
    val lastMessagePreview = when {
        isPending -> "Solicitud de mensaje..."
        chatRoom.lastMessageSenderId == currentUserId -> "Tú: ${chatRoom.lastMessageText}"
        else -> chatRoom.lastMessageText
    } ?: "..."

    val date = chatRoom.lastMessageTimestamp?.let { formatRelativeTime(it) } ?: ""

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(otherUserName, fontWeight = FontWeight.Bold)
        },
        supportingContent = {
            Text(
                lastMessagePreview,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Text(date, style = MaterialTheme.typography.bodySmall)
        }
    )
}
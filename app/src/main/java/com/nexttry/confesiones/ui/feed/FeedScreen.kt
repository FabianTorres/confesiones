package com.nexttry.confesiones.ui.feed

import android.app.Application
import android.util.Log
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.navigation.NavHostController
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material.icons.filled.Add // Icono "+"
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet // Para la hoja deslizable
import androidx.compose.material3.rememberModalBottomSheetState // Estado de la hoja
import androidx.compose.material3.ListItem // Para las opciones dentro de la hoja
import androidx.compose.material3.Icon // Para iconos en las opciones
import androidx.compose.material.icons.filled.Edit // Icono para "Nueva confesión"
import androidx.compose.material.icons.outlined.FilterList // Icono de Filtro
import androidx.compose.material3.RadioButton // Para seleccionar el orden
import androidx.compose.foundation.selection.selectableGroup // Para agrupar RadioButtons
import androidx.compose.foundation.selection.selectable // Para hacer clickables los ListItems
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.nexttry.confesiones.R

private const val TAG = "FeedScreen"
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(navController: NavHostController,
               communityId: String,
               onChangeCommunity: () -> Unit,
               onNavigateToConfession: (String) -> Unit,
               onNavigateToMyPosts: () -> Unit) {
    Log.d(TAG, "FeedScreen se está componiendo. CommunityId: $communityId")
    //  Obtenemos el contexto y la aplicación
    val context = LocalContext.current
    val application = context.applicationContext as Application

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

    // Crear y recordar el estado del LazyColumn
    val lazyListState = rememberLazyListState()
    // Bandera para evitar el scroll en la carga inicial
    //var isInitialLoad by remember { mutableStateOf(true) }

    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    // --- ESTADO PARA BOTTOM SHEET DE ACCIONES (el del FAB) ---
    //val addActionSheetState = rememberModalBottomSheetState()
    var showAddActionSheet by remember { mutableStateOf(false) }

    // --- ESTADO PARA BOTTOM SHEET DE FILTROS ---
    val filterSheetState = rememberModalBottomSheetState()
    var showFilterSheet by remember { mutableStateOf(false) }

    // Calcular color con elevación
    val appBarContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)

    // --- PASO 2: Restaurar el LaunchedEffect, pero usando scrollToItem ---
    LaunchedEffect(uiState.sortOrder) {
        // Espera hasta que isLoading se vuelva 'false'
        snapshotFlow { uiState.isLoading }
            .filter { !it }
            .first() // Suspende hasta que isLoading sea false

        // Una vez que isLoading es false...
        if (uiState.confesiones.isNotEmpty()) {
            // Usamos scrollToItem (SIN animación) para un reseteo instantáneo
            lazyListState.scrollToItem(index = 0)
            Log.d(TAG, "Scroll INSTANTÁNEO al inicio ejecutado tras cambio de orden.")
        }
    }



    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                            Text(
                                text = uiState.communityName,
                                // Limita el texto a una sola línea
                                maxLines = 1,
                                // Añade puntos suspensivos si el texto no cabe
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBarContainerColor,
                    scrolledContainerColor = appBarContainerColor,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                scrollBehavior = scrollBehavior,
                actions = {
                    // icono de filtro
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = stringResource(id = R.string.accessibility_filter_sort)
                        )
                    }


                    //  Se añadió el botón para "Mis Publicaciones" ---
                    IconButton(onClick = onNavigateToMyPosts) {
                        Icon(
                            imageVector = Icons.Outlined.AccountCircle,
                            contentDescription = stringResource(id = R.string.accessibility_my_posts)
                        )
                    }
                    // Botón para cambiar de comunidad
                    IconButton(onClick = onChangeCommunity) {
                        Icon(

                            imageVector = Icons.Outlined.SwapHoriz,
                            contentDescription = stringResource(id = R.string.accessibility_change_community)
                        )
                    }
                }

            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        // Se añade el FLOATING ACTION BUTTON
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddActionSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(id = R.string.accessibility_add_confession))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Le damos un padding vertical para separarlo
//            SortOrderSelector(
//                modifier = Modifier.padding(vertical = 8.dp),
//                currentSortOrder = uiState.sortOrder,
//                onSortChanged = { vm.onSortOrderChanged(it) }
//            )
            // GESTIONAMOS EL ESTADO DE CARGA Y LA LISTA
            // Mantenemos el Box con CircularProgressIndicator para el estado de carga
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Usamos AnimatedVisibility para la lista cuando NO está cargando
            AnimatedVisibility(
                visible = !uiState.isLoading, // Visible cuando isLoading es false
                modifier = Modifier.weight(1f),
                enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000)), // Duración del fade-in
                exit = fadeOut() // Puedes añadir un fade-out si quieres, aunque no es estrictamente necesario aquí
            ) {

                Column(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(), // Ocupa el espacio de la Column padre
                        state = lazyListState,
                        contentPadding = PaddingValues(vertical = 8.dp),
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
                }
            }
        }
    }

    // BOTTOM SHEET DE ACCIONES
    if (showAddActionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddActionSheet = false },
            sheetState = sheetState,
            contentWindowInsets = { WindowInsets(0.dp) }
        ) {
            // Contenido de la hoja
            Column(modifier = Modifier.padding(bottom = 32.dp)) { // Espacio extra abajo
                // Opción 1: Nueva Confesión
                ListItem(
                    headlineContent = { Text("Nueva confesión") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable {
                        showAddActionSheet = false // Cierra la hoja
                        // Navegamos a la nueva ruta pasando el communityId
                        navController.navigate("new_confession/$communityId")
                    }
                )
                // Aquí podrías añadir más ListItems para futuras opciones
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = filterSheetState,
            contentWindowInsets = { WindowInsets(0.dp) }
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                // Título de la sección
                Text(
                    "Ordenar por",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Grupo de RadioButtons para asegurar selección única
                Column(Modifier.selectableGroup()) {
                    // Opción: Recientes
                    ListItem(
                        headlineContent = { Text("Más Recientes") },
                        leadingContent = {
                            RadioButton(
                                selected = (uiState.sortOrder == SortOrder.RECENT),
                                onClick = null // El clic se maneja en el Modifier del ListItem
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (uiState.sortOrder == SortOrder.RECENT),
                                onClick = {
                                    vm.onSortOrderChanged(SortOrder.RECENT)
                                    showFilterSheet = false // Cierra el sheet al seleccionar
                                }
                            )
                    )
                    // Opción: Populares
                    ListItem(
                        headlineContent = { Text("Más Populares") },
                        leadingContent = {
                            RadioButton(
                                selected = (uiState.sortOrder == SortOrder.POPULAR),
                                onClick = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (uiState.sortOrder == SortOrder.POPULAR),
                                onClick = { vm.onSortOrderChanged(SortOrder.POPULAR) } // Solo cambia el estado
                            )
                    )
                    // Opción: Antiguos
                    ListItem(
                        headlineContent = { Text("Más Antiguos") },
                        leadingContent = {
                            RadioButton(
                                selected = (uiState.sortOrder == SortOrder.OLDEST),
                                onClick = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (uiState.sortOrder == SortOrder.OLDEST),
                                onClick = {
                                    vm.onSortOrderChanged(SortOrder.OLDEST)
                                    showFilterSheet = false
                                }
                            )
                    )
                }

                // --- AÑADIR SECCIÓN RANGO DE TIEMPO (Solo si Populares está activo) ---
                AnimatedVisibility(visible = uiState.sortOrder == SortOrder.POPULAR) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) // Separador visual
                        Text(
                            "Popularidad en",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        // Botones segmentados para el rango de tiempo
                        TimeRangeSelector(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            selectedTimeRange = uiState.selectedTimeRange,
                            onTimeRangeSelected = { timeRange ->
                                vm.onTimeRangeChanged(timeRange)
                                // Cerramos el sheet DESPUÉS de seleccionar el rango
                                showFilterSheet = false
                            }
                        )
                    }
                }

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
            containerColor = MaterialTheme.colorScheme.surfaceContainer
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
            Spacer(modifier = Modifier.height(16.dp)) // Espacio vertical

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
                        // 1. Define el estado animado para la escala
                        val scale by animateFloatAsState(
                            targetValue = if (isLikedByCurrentUser) 1.1f else 1.0f,
                            animationSpec = spring( // Asegúrate de que 'spring' tenga ()
                                // Usa Spring. (con S mayúscula) para las constantes
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "LikeScaleAnimation"
                        )
                        IconButton(onClick = onLikeClicked, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = if (isLikedByCurrentUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (isLikedByCurrentUser) R.string.accessibility_unlike
                                    else R.string.accessibility_like
                                ),
                                tint = if (isLikedByCurrentUser) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale
                                )
                            )
                        }
                        Text(
                            //text = confesion.likes.size.toString(),
                            text = confesion.likesCount.toString(),
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
                            contentDescription = stringResource(id = R.string.accessibility_comments_count),
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
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = stringResource(id = R.string.accessibility_report_confession),
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

/**
 * Muestra los botones segmentados para elegir el rango de tiempo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRangeSelector(
    modifier: Modifier = Modifier,
    selectedTimeRange: TimeRange,
    onTimeRangeSelected: (TimeRange) -> Unit
) {
    val options = listOf(TimeRange.DAY, TimeRange.WEEK, TimeRange.MONTH, TimeRange.ALL)
    val optionsText = mapOf(
        TimeRange.DAY to "Hoy",
        TimeRange.WEEK to "Semana",
        TimeRange.MONTH to "Mes",
        TimeRange.ALL to "Siempre"
    )

    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, timeRange ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onTimeRangeSelected(timeRange) },
                selected = (timeRange == selectedTimeRange)
            ) {
                Text(optionsText[timeRange] ?: "")
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

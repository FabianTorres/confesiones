package com.nexttry.confesiones

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexttry.confesiones.data.UserPreferencesRepository
import com.nexttry.confesiones.ui.community.CommunityScreen
import com.nexttry.confesiones.ui.feed.FeedScreen
import com.nexttry.confesiones.ui.theme.ConfesionesTheme
import com.nexttry.confesiones.ui.detail.ConfessionDetailScreen
import com.nexttry.confesiones.ui.myposts.MyPostsScreen
import com.nexttry.confesiones.ui.conversation.ConversationScreen
import com.nexttry.confesiones.ui.chatlist.ChatListScreen
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.nexttry.confesiones.ui.newconfession.NewConfessionScreen
import com.nexttry.confesiones.ui.newcomment.NewCommentScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import androidx.compose.ui.res.stringResource
import com.nexttry.confesiones.R
import com.nexttry.confesiones.ui.profile.ProfileScreen
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.nexttry.confesiones.BuildConfig
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.nexttry.confesiones.data.ConfesionRepository
import com.nexttry.confesiones.data.UserProfile
import kotlinx.coroutines.flow.firstOrNull


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Firebase (necesario para App Check)
        FirebaseApp.initializeApp(this)

        // Obtener la instancia de FirebaseAppCheck
        val firebaseAppCheck = FirebaseAppCheck.getInstance()

        // Instalar el proveedor de Play Integrity en modo debug o produccion
        if (BuildConfig.DEBUG) {
            // Estamos en un build de depuración (Emulador o Teléfono de desarrollo)
            Log.d("AppCheck", "Instalando AppCheck Debug Provider")
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            // Estamos en un build de Producción (Release para la Play Store)
            Log.d("AppCheck", "Instalando AppCheck Play Integrity Provider")
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }


        val prefsRepository = UserPreferencesRepository(this)
        setContent {
            ConfesionesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigator(prefsRepository)
                }
            }
        }
    }
}

@Composable
fun AppNavigator(prefsRepository: UserPreferencesRepository) {
    val navController = rememberNavController()
    val communityId by prefsRepository.selectedCommunityId.collectAsState(initial = null)
    val currentCommunityId = communityId

    // 1. Obtenemos el ID de usuario actual
    val userId = Firebase.auth.currentUser?.uid
    // 2. Creamos una instancia del repositorio (solo la usaremos aquí)
    val repository = remember { ConfesionRepository() }

    // 3. Este efecto se ejecuta cada vez que el userId cambia (de null a un valor)
    LaunchedEffect(userId) {
        // Si no hay usuario, no hagas nada
        if (userId == null) return@LaunchedEffect

        // Comprobamos si el usuario ya tiene un perfil en Firestore
        val userProfile = repository.getUserProfileStream(userId).firstOrNull()

        if (userProfile == null) {
            // CASO 1:
            // El perfil no existe en absoluto. Creamos uno.
            try {
                val newAnonymousName = "Usuario${(10000..99999).random()}"
                val newProfile = UserProfile(
                    anonymousName = newAnonymousName,
                    allowsMessaging = true // Por defecto, acepta mensajes
                )
                repository.updateUserProfile(userId, newProfile)
                Log.d("AppNavigator", "Nuevo perfil CREADO con nombre: $newAnonymousName")
            } catch (e: Exception) {
                Log.e("AppNavigator", "Error al CREAR perfil de usuario", e)
            }
        } else if (userProfile.anonymousName == null) {
            // CASO 2: Usuario ANTIGUO
            // El perfil SÍ existe, pero le falta el nombre anónimo.
            try {
                val newAnonymousName = "Usuario${(10000..99999).random()}"
                // Copiamos el perfil existente y SOLO añadimos el nombre
                val updatedProfile = userProfile.copy(anonymousName = newAnonymousName)

                repository.updateUserProfile(userId, updatedProfile)
                Log.d("AppNavigator", "Perfil ANTIGUO ACTUALIZADO con nombre: $newAnonymousName")
            } catch (e: Exception) {
                Log.e("AppNavigator", "Error al ACTUALIZAR perfil de usuario", e)
            }
        } else {
            //CASO 3: Usuario que regresa
            // El perfil existe y ya tiene nombre. No hacemos nada.
            Log.d("AppNavigator", "El perfil del usuario ya existe y está completo.")
        }
    }
    // --- FIN DE LÓGICA DE PERFIL ---

    if (currentCommunityId == null) {
        // 1. Estado de carga: Reemplazamos el Box simple por nuestro
        //    nuevo Composable de pantalla de carga.
        AppSplashScreen()
    } else {
        // 2. Estado cargado: 'currentCommunityId' es ahora un String no-nulo (gracias al smart-cast)
        val startDestination = if (currentCommunityId.isEmpty()) {
            "community_select" // Usuario nuevo
        } else {
            "feed/$currentCommunityId" // Usuario que regresa
        }

        // 3. Creamos el NavHost con la ruta de inicio decidida
        AppNavHost(
            navController = navController,
            startDestination = startDestination
        )
    }
}

/**
 * El NavHost que contiene todas las pantallas de la app.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNavHost(navController: NavHostController, startDestination: String) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300)) }
    ) {
        // RUTA 1: La pantalla del Feed
        composable(route = "feed/{communityId}") { backStackEntry ->

            val communityId = backStackEntry.arguments?.getString("communityId")
            if (communityId != null) {
                FeedScreen(
                    navController = navController,
                    communityId = communityId,
                    onChangeCommunity = {
                        navController.navigate("community_select") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToConfession = { confessionId ->
                        navController.navigate("confession/$confessionId")
                    }

                )
            }
        }

        // --- RUTA 2: La pantalla de Selección de Comunidad ---
        composable(route = "community_select") {
            CommunityScreen(
                onCommunitySelected = { selectedId ->
                    navController.navigate("feed/$selectedId") {
                        popUpTo("community_select") { inclusive = true }
                    }
                }
            )
        }


        //  Se añadió la RUTA 3: Pantalla de Detalle ---
        composable(route = "confession/{confessionId}") { backStackEntry ->
            // Extraemos el ID de la confesión de la ruta
            val confessionId = backStackEntry.arguments?.getString("confessionId")
            if (confessionId != null) {
                // Llamamos a la nueva pantalla (que crearemos a continuación)
                ConfessionDetailScreen(
                    navController = navController,
                    confessionId = confessionId,
                    // Añadimos una acción para poder volver atrás
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(route = "my_posts") {

            MyPostsScreen(
                onNavigateBack = { navController.popBackStack() },
                // Pasamos la acción para navegar al detalle si hace clic en una de sus confesiones
                onNavigateToConfession = { confessionId ->
                    navController.navigate("confession/$confessionId")
                }
            )
        }

        // RUTA 5: Nueva Confesión
        composable(route = "new_confession/{communityId}") { backStackEntry ->
            val communityId = backStackEntry.arguments?.getString("communityId")
            if (communityId != null) {
                NewConfessionScreen(
                    communityId = communityId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            // Podrías añadir un else aquí para manejar el caso de communityId nulo si es necesario
        }

        // RUTA 6: Nuevo Comentario
        composable(route = "new_comment/{confessionId}") { backStackEntry ->
            val confessionId = backStackEntry.arguments?.getString("confessionId")
            if (confessionId != null) {
                NewCommentScreen(
                    confessionId = confessionId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            // Podrías añadir un else aquí para manejar el caso de confessionId nulo
        }

        //RUTA 7: Perfil de Usuario
        composable(route = "profile") {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // RUTA 8: PANTALLA DE CONVERSACIÓN
        composable(route = "conversation/{chatId}") { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            if (chatId != null) {
                ConversationScreen(
                    chatId = chatId,
                    navController = navController
                )

            }
        }

        // RUTA 9: LISTA DE CHATS
        composable(route = "chat_list") {
            ChatListScreen(navController = navController)
        }
    }
}

/**
 * Pantalla de carga estilizada que se muestra al iniciar la app
 * mientras se cargan las preferencias iniciales del DataStore.
 */
@Composable
fun AppSplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // Usamos el color de fondo principal de la app
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Un ícono representativo de la app
            Icon(
                // Usamos un ícono de Material que sugiere "preguntas y respuestas" o "chat"
                imageVector = Icons.Filled.QuestionAnswer,
                contentDescription = stringResource(id = R.string.accessibility_app_logo),
                modifier = Modifier.size(120.dp),
                // Usamos el color primario del tema para el ícono
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Texto de bienvenida o carga
            Text(
                text = "Cargando confesiones...",
                style = MaterialTheme.typography.titleMedium,
                // Usamos el color de texto principal
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Mantenemos el indicador de progreso
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
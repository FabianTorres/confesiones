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
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.nexttry.confesiones.ui.newconfession.NewConfessionScreen
import com.nexttry.confesiones.ui.newcomment.NewCommentScreen


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        // --- RUTA 1: La pantalla del Feed ---
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
                        navController.navigate("confession/$confessionId") // Navega a la nueva ruta
                    },
                    onNavigateToMyPosts = {
                        navController.navigate("my_posts") // Navega a la nueva ruta
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
                contentDescription = "Logo de Confesiones",
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
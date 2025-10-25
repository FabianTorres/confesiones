package com.nexttry.confesiones

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.util.Log
import androidx.activity.compose.setContent
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
        // 1. Estado de carga: Aún estamos leyendo desde DataStore
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
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
@Composable
fun AppNavHost(navController: NavHostController, startDestination: String) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // --- RUTA 1: La pantalla del Feed ---
        composable(route = "feed/{communityId}") { backStackEntry ->

            val communityId = backStackEntry.arguments?.getString("communityId")
            if (communityId != null) {
                FeedScreen(
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
    }
}
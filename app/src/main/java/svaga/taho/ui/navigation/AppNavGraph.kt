package svaga.taho.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import svaga.taho.data.local.TokenManager
import svaga.taho.ui.auth.AuthViewModel
import svaga.taho.ui.auth.LoginScreen
import svaga.taho.ui.auth.RegisterScreen
import svaga.taho.ui.auth.RoleSelectionScreen
import svaga.taho.ui.client.ClientHomeScreen
import svaga.taho.ui.driver.DriverHomeScreen
import svaga.taho.ui.driver.StatisticsScreen
import svaga.taho.ui.components.DocumentScreen

import javax.inject.Inject

@Composable
fun AppNavGraph(navController: NavHostController) {
    // Получаем TokenManager через Hilt прямо внутри Composable — это разрешено и рекомендуется
    val tokenManager: TokenManager = hiltViewModel<AuthViewModel>().tokenManager
    // Если AuthViewModel ещё не создан — Hilt создаст его автоматически

    val token by tokenManager.tokenFlow.collectAsState(initial = null)
    val role by tokenManager.roleFlow.collectAsState(initial = null)
    val lastRole by tokenManager.lastRoleFlow.collectAsState(initial = null)

    val startDestination = when {
        token.isNullOrEmpty() -> when (lastRole) {
            "DRIVER" -> Screen.DriverHome.route  // после logout водитель → DriverHome (но без токена экран сам редиректнет на логин)
            else -> Screen.Login.route
        }
        role == "DRIVER" -> Screen.DriverHome.route
        role == "CLIENT" -> Screen.ClientHome.route
        else -> Screen.Login.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) { LoginScreen(navController) }
        composable(Screen.Register.route) { RegisterScreen(navController) }
        composable(Screen.RoleSelection.route) { RoleSelectionScreen(navController) }
        composable(Screen.ClientHome.route) { ClientHomeScreen(navController) }
        composable(Screen.DriverHome.route) { DriverHomeScreen(navController) }
        composable(Screen.Statistics.route) { StatisticsScreen(navController = navController) }
        composable("document/{type}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "privacy_policy"
            DocumentScreen(navController = navController, documentType = type)
        }
    }

}
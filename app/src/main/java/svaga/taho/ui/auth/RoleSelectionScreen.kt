package svaga.taho.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import svaga.taho.data.local.TokenManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Composable
fun RoleSelectionScreen(
    navController: NavController,
) {val tokenManager: TokenManager = hiltViewModel<AuthViewModel>().tokenManager
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Вы — водитель", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Text("Кем хотите войти сегодня?")

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                runBlocking { tokenManager.setLastModeDriver(false) }
                navController.navigate("client_home") { popUpTo(0) }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Пассажиром")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                runBlocking { tokenManager.setLastModeDriver(true) }
                navController.navigate("driver_home") { popUpTo(0) }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Водителем")
        }
    }
}
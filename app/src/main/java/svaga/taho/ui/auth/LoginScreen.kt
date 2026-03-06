package svaga.taho.ui.auth

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import svaga.taho.util.ui.PhoneVisualTransformation

private fun validateLoginPhone(digits: String): String? = when {
    digits.isEmpty()   -> "Введите номер телефона"
    digits.length < 10 -> "Номер должен содержать 10 цифр"
    else -> null
}

private fun validateLoginPassword(password: String): String? = when {
    password.isEmpty() -> "Введите пароль"
    password.length < 4 -> "Минимум 4 символа"
    else -> null
}

// ── Экран ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var phoneDigits by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var loading     by remember { mutableStateOf(false) }
    var error       by remember { mutableStateOf<String?>(null) }
    var submitted   by remember { mutableStateOf(false) }

    val phoneError    = if (submitted) validateLoginPhone(phoneDigits) else null
    val passwordError = if (submitted) validateLoginPassword(password) else null

    val isFormValid = validateLoginPhone(phoneDigits) == null &&
            validateLoginPassword(password) == null

    LaunchedEffect(Unit) {
        viewModel.event.collectLatest { event ->
            when (event) {
                is AuthViewModel.AuthEvent.Loading -> loading = true
                is AuthViewModel.AuthEvent.Error -> {
                    loading = false
                    error = event.message
                }
                is AuthViewModel.AuthEvent.ToClientHome -> {
                    loading = false
                    navController.navigate("client_home") { popUpTo(0) }
                }
                is AuthViewModel.AuthEvent.ToRoleSelection -> {
                    loading = false
                    navController.navigate("role_selection") { popUpTo(0) }
                }
                is AuthViewModel.AuthEvent.ToRegister -> navController.navigate("register")
                else -> loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Вход", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = phoneDigits,
            onValueChange = { phoneDigits = it },
            label = { Text("Телефон") },
            modifier = Modifier.fillMaxWidth()
        )

        // ── Телефон ──────────────────────────────────────────────
     /*   OutlinedTextField(
            value = phoneDigits,
            onValueChange = { input ->
                phoneDigits = input.filter { it.isDigit() }.take(10)
            },
            label = { Text("Телефон") },
            placeholder = { Text("+7 (___) ___-__-__") },
            //9954visualTransformation = PhoneVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = phoneError != null,
            supportingText = phoneError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        ) */

        Spacer(modifier = Modifier.height(16.dp))

        // ── Пароль ───────────────────────────────────────────────
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(50) },
            label = { Text("Пароль") },
           visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = passwordError != null,
            supportingText = passwordError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Ошибка от сервера
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                submitted = true
                error = null
                Log.d("LOGIN", "Отправляем номер: $phoneDigits")
               // if (isFormValid) {
                    viewModel.login( phoneDigits, password)   //TODO Здесь потом заменить "+7$phoneDigits"
              //  }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = LocalContentColor.current
                )
            } else {
                Text("Войти")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { navController.navigate("register") }) {
            Text("Нет аккаунта? Зарегистрироваться")
        }
    }
}
package svaga.taho.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import svaga.taho.util.ui.PhoneVisualTransformation

// ====================== ВАЛИДАЦИЯ ======================
private fun validatePhone(digits: String): String? = when {
    digits.isEmpty() -> "Введите номер телефона"
    digits.length < 10 -> "Номер должен содержать 10 цифр"
    else -> null
}

private fun validateFullName(name: String): String? {
    val trimmed = name.trim()
    if (trimmed.isBlank()) return "Введите ФИО"

    val parts = trimmed.split("\\s+".toRegex())
    return when {
        parts.size < 2 -> "Введите фамилию и имя"
        parts.any { it.any { c -> !c.isLetter() && c != '-' } } -> "ФИО должно содержать только буквы и дефис"
        else -> null
    }
}

private fun validatePassword(password: String): String? = when {
    password.isEmpty() -> "Введите пароль"
    password.length < 4 -> "Минимум 4 символа"
    password.length > 50 -> "Слишком длинный пароль"
    else -> null
}

// ====================== ЭКРАН ======================
@Composable
fun RegisterScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    // Состояния из ViewModel
    val showCodeDialog by viewModel.showCodeDialog.collectAsState()
    val verificationCode by viewModel.verificationCode.collectAsState()
    val isCodeVerified by viewModel.isCodeVerified.collectAsState()

    // Локальные состояния
    var phoneDigits by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var generalError by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var codeError by remember { mutableStateOf<String?>(null) }

    val phoneFull = "+7$phoneDigits"

    // Вычисляем ошибки и возможность отправки кода
    val phoneError = if (submitted) validatePhone(phoneDigits) else null
    val fullNameError = if (submitted) validateFullName(fullName) else null
    val passwordError = if (submitted) validatePassword(password) else null

    val isPhoneValid = phoneDigits.length == 10 && phoneError == null
    val isNameValid = fullName.trim().isNotBlank() && fullNameError == null
    val isPasswordValid = password.length >= 4 && passwordError == null

    val canSendCode = isPhoneValid && isNameValid && isPasswordValid

    // Слушаем события из ViewModel
    LaunchedEffect(Unit) {
        viewModel.event.collectLatest { event ->
            when (event) {
                is AuthViewModel.AuthEvent.Loading -> loading = true
                is AuthViewModel.AuthEvent.Error -> {
                    loading = false
                    generalError = event.message
                }
                is AuthViewModel.AuthEvent.ToClientHome -> {
                    loading = false
                    navController.navigate("client_home") { popUpTo(0) }
                }
                is AuthViewModel.AuthEvent.ToRoleSelection -> {
                    loading = false
                    navController.navigate("role_selection") { popUpTo(0) }
                }
                else -> loading = false
            }
        }
    }

    // ==================== ДИАЛОГ ВВОДА КОДА ====================
    if (showCodeDialog) {
        val codeFocusRequester = remember { FocusRequester() }

        AlertDialog(
            onDismissRequest = { /* нельзя закрывать */ },
            title = { Text("Подтверждение номера") },
            text = {
                Column {
                    Text("Мы отправили 6-значный код в Telegram на номер:")
                    Text(phoneFull, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = { newCode ->
                            val filtered = newCode.filter { it.isDigit() }.take(6)
                            viewModel.verifyTelegramCode(filtered)   // обновляем в VM
                            codeError = null                        // сбрасываем ошибку при вводе
                        },
                        label = { Text("Код из Telegram") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = codeError != null,
                        supportingText = codeError?.let {
                            { Text(it, color = MaterialTheme.colorScheme.error) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(codeFocusRequester)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (verificationCode.length == 6) {
                            viewModel.verifyTelegramCode(verificationCode)
                        } else {
                            codeError = "Введите 6-значный код"
                        }
                    },
                    enabled = verificationCode.length == 6 && !loading
                ) {
                    Text("Подтвердить")
                }
            }
        )

        // Автофокус
        LaunchedEffect(Unit) {
            delay(150)
            codeFocusRequester.requestFocus()
        }
    }

    // ==================== ОСНОВНОЙ ЭКРАН ====================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Регистрация", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        // Поле телефона
        OutlinedTextField(
            value = phoneDigits,
            onValueChange = { phoneDigits = it.filter { char -> char.isDigit() }.take(10) },
            label = { Text("Телефон") },
            placeholder = { Text("+7 (___) ___-__-__") },
            visualTransformation = PhoneVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = phoneError != null,
            supportingText = phoneError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Поле ФИО
        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it.filter { it.isLetter() || it == ' ' || it == '-' } },
            label = { Text("ФИО") },
            placeholder = { Text("Иванов Иван") },
            isError = fullNameError != null,
            supportingText = fullNameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Поле пароля
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(50) },
            label = { Text("Пароль") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = passwordError != null,
            supportingText = passwordError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (generalError != null) {
            Text(
                text = generalError!!,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Кнопка "Подтвердить номер"
        Button(
            onClick = {
                submitted = true
                generalError = null
                if (canSendCode) {
                    viewModel.sendTelegramCode(phoneFull)
                }
            },
            enabled = !loading && canSendCode,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Подтвердить номер")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Кнопка "Зарегистрироваться"
        Button(
            onClick = {
                if (isCodeVerified && verificationCode.length == 6) {
                    viewModel.register(
                        phone = phoneFull,
                        name = fullName.trim(),
                        password = password,
                        code = verificationCode
                    )
                }
            },
            enabled = !loading && isCodeVerified && verificationCode.length == 6,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Зарегистрироваться")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { navController.popBackStack() }) {
            Text("Уже есть аккаунт? Войти")
        }
    }
}
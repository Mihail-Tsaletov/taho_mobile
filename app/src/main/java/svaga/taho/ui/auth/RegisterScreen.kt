package svaga.taho.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import svaga.taho.ui.navigation.Screen
import svaga.taho.util.ui.PhoneVisualTransformation


// ── Валидация ──────────────────────────────────────────────────────────────────

private fun validatePhone(digits: String): String? = when {
    digits.isEmpty()  -> "Введите номер телефона"
    digits.length < 10 -> "Номер должен содержать 10 цифр"
    else -> null
}

private fun validateFullName(name: String): String? {
    val parts = name.trim().split("\\s+".toRegex())
    return when {
        name.isBlank()    -> "Введите ФИО"
        parts.size < 2    -> "Введите фамилию и имя"
        parts.any { it.any { c -> !c.isLetter() && c != '-' } } -> "ФИО должно содержать только буквы"
        else -> null
    }
}

private fun validatePassword(password: String): String? = when {
    password.isEmpty()  -> "Введите пароль"
    password.length < 4 -> "Минимум 4 символа"
    password.length > 50 -> "Слишком длинный пароль"
    else -> null
}

// ── Экран ──────────────────────────────────────────────────────────────────────

@Composable
fun RegisterScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    // Храним только цифры номера (без +7), маска применяется через VisualTransformation
    var phoneDigits by remember { mutableStateOf("") }
    var fullName    by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var loading     by remember { mutableStateOf(false) }
    var error       by remember { mutableStateOf<String?>(null) }
    var smsCode by remember { mutableStateOf("") }
    val phoneVerification by viewModel.phoneVerification.collectAsState()
    val isPhoneVerified = phoneVerification is AuthViewModel.PhoneVerificationState.Verified

    // Показываем ошибки полей только после первой попытки отправки
    var submitted by remember { mutableStateOf(false) }

    val phoneError    = if (submitted) validatePhone(phoneDigits) else null
    val fullNameError = if (submitted) validateFullName(fullName) else null
    val passwordError = if (submitted) validatePassword(password) else null
    var agreedToTerms by remember { mutableStateOf(false) }
    val showCodeInput = phoneVerification is AuthViewModel.PhoneVerificationState.CodeSent ||
            phoneVerification is AuthViewModel.PhoneVerificationState.Error


    val isFormValid = isPhoneVerified &&
            validatePhone(phoneDigits) == null &&
            validateFullName(fullName) == null &&
            validatePassword(password) == null &&
            agreedToTerms

    // ── Антиспам: cooldown после превышения лимита попыток ──────
    val registerCooldownSeconds by viewModel.registerCooldownSeconds.collectAsState()
    val isRegisterBlocked = registerCooldownSeconds > 0

    // ──────────────────────────────────────────────────────────

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
                is AuthViewModel.AuthEvent.ToDriverHome -> {
                    loading = false
                    navController.navigate("driver_home") { popUpTo(0) }
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
        Text("Регистрация", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(32.dp))

        // ── Телефон ──────────────────────────────────────────────
        OutlinedTextField(
            value = phoneDigits,
            onValueChange = { input ->
                phoneDigits = input.filter { it.isDigit() }.take(10)
                if (isPhoneVerified) viewModel.resetPhoneVerification()
            },
            label = { Text("Телефон") },
            placeholder = { Text("+7 (___) ___-__-__") },
            visualTransformation = PhoneVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = phoneError != null,
            supportingText = phoneError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── ФИО ──────────────────────────────────────────────────
        OutlinedTextField(
            value = fullName,
            onValueChange = { input ->
                // Разрешаем буквы, пробелы и дефис
                fullName = input.filter { it.isLetter() || it == ' ' || it == '-' }
            },
            label = { Text("ФИО") },
            placeholder = { Text("Иванов Иван") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            isError = fullNameError != null,
            supportingText = fullNameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

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

        // ── Чекбокс согласия ─────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = agreedToTerms,
                onCheckedChange = { agreedToTerms = it }
            )

            val annotated = buildAnnotatedString {
                append("Я прочитал и согласен с ")

                pushStringAnnotation(tag = "privacy", annotation = "privacy_policy")
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append("Политикой конфиденциальности")
                }
                pop()

                append(" и ")

                pushStringAnnotation(tag = "terms", annotation = "terms_of_use")
                withStyle(SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )) {
                    append("Условиями пользования")
                }
                pop()
            }

            ClickableText(
                text = annotated,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                onClick = { offset ->
                    annotated.getStringAnnotations("privacy", offset, offset)
                        .firstOrNull()?.let {
                            navController.navigate(Screen.Document.route("privacy_policy"))
                        }
                    annotated.getStringAnnotations("terms", offset, offset)
                        .firstOrNull()?.let {
                            navController.navigate(Screen.Document.route("terms_of_use"))
                        }
                }
            )
        }

        if (!isPhoneVerified) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.sendCode("+7$phoneDigits") },
                enabled = validatePhone(phoneDigits) == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (phoneVerification is AuthViewModel.PhoneVerificationState.CodeSent ||
                        phoneVerification is AuthViewModel.PhoneVerificationState.Error
                    ) "Отправить код повторно" else "Получить код"
                )
            }

            if (showCodeInput) {
                OutlinedTextField(
                    value = smsCode,
                    onValueChange = { smsCode = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Код из СМС") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = phoneVerification is AuthViewModel.PhoneVerificationState.Error,
                    supportingText = {
                        if (phoneVerification is AuthViewModel.PhoneVerificationState.Error) {
                            Text((phoneVerification as AuthViewModel.PhoneVerificationState.Error).message)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.verifyCode("+7$phoneDigits", smsCode) },
                    enabled = smsCode.length >= 4,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Подтвердить")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Text("✓ Номер подтверждён", color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (submitted && !agreedToTerms) {
            Text(
                "Необходимо принять условия",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }


        // Общая ошибка от сервера
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                submitted = true
                error = null
                if (isFormValid) {
                    // Передаём на бэкенд номер в формате +7XXXXXXXXXX
                    viewModel.register("+7$phoneDigits", fullName.trim(), password)
                }
            },
            enabled = !loading && !isRegisterBlocked && isFormValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = LocalContentColor.current
                )
                isRegisterBlocked -> Text("Повторите через $registerCooldownSeconds сек")
                else -> Text("Зарегистрироваться")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { navController.popBackStack() }) {
            Text("Уже есть аккаунт? Войти")
        }
    }
}
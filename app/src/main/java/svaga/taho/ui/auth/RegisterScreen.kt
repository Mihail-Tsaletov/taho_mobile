package svaga.taho.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// ── Цвета (в стиле LoginScreen) ─────────────────────────────────────────────
private val BgTop = Color(0xFFF7EEFC)
private val BgBottom = Color(0xFFEFE0FA)
private val WaveColor1 = Color(0xFFE9D9F7)
private val WaveColor2 = Color(0xFFE1CDF5)
private val AccentPurple = Color(0xFF6E3ADB)
private val AccentPurpleDark = Color(0xFF5B2FC2)
private val FieldBg = Color(0xFFFFFFFF)
private val PlaceholderGray = Color(0xFFB2A8BE)
private val TextMuted = Color(0xFF8E8398)
private val TitleDark = Color(0xFF1A1626)
private val SuccessGreen = Color(0xFF2E9E5B)

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        // ── Декоративные "волны" внизу экрана ───────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(160.dp)
                .background(WaveColor1, shape = WaveShape(amplitude = 26.dp, phase = 0f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(110.dp)
                .background(WaveColor2, shape = WaveShape(amplitude = 18.dp, phase = 0.35f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // ── Иконка-логотип ────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .shadow(elevation = 14.dp, shape = RoundedCornerShape(24.dp), spotColor = AccentPurple.copy(alpha = 0.25f))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = AccentPurple,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Регистрация",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TitleDark
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Создайте аккаунт, чтобы начать\nпользоваться приложением",
                fontSize = 14.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(28.dp))

            // ── Телефон ──────────────────────────────────────────────
            RoundedField(
                value = phoneDigits,
                onValueChange = { input ->
                    phoneDigits = input.filter { it.isDigit() }.take(10)
                    if (isPhoneVerified) viewModel.resetPhoneVerification()
                },
                placeholder = "+7 (___) ___-__-__",
                leadingIcon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone,
                visualTransformation = PhoneVisualTransformation(),
                isError = phoneError != null,
                errorText = phoneError
            )

            Spacer(Modifier.height(14.dp))

            // ── ФИО ──────────────────────────────────────────────────
            RoundedField(
                value = fullName,
                onValueChange = { input ->
                    fullName = input.filter { it.isLetter() || it == ' ' || it == '-' }
                },
                placeholder = "Иванов Иван",
                leadingIcon = Icons.Default.Person,
                keyboardType = KeyboardType.Text,
                isError = fullNameError != null,
                errorText = fullNameError
            )

            Spacer(Modifier.height(14.dp))

            // ── Пароль ───────────────────────────────────────────────
            var passwordVisible by remember { mutableStateOf(false) }
            RoundedField(
                value = password,
                onValueChange = { password = it.take(50) },
                placeholder = "Пароль",
                leadingIcon = Icons.Default.Lock,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = passwordError != null,
                errorText = passwordError,
                trailing = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(text = if (passwordVisible) "🙈" else "👁", fontSize = 18.sp)
                    }
                }
            )

            Spacer(Modifier.height(20.dp))

            // ── Чекбокс согласия ─────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = agreedToTerms,
                    onCheckedChange = { agreedToTerms = it },
                    colors = CheckboxDefaults.colors(checkedColor = AccentPurple)
                )

                val annotated = buildAnnotatedString {
                    append("Я прочитал и согласен с ")

                    pushStringAnnotation(tag = "privacy", annotation = "privacy_policy")
                    withStyle(
                        SpanStyle(
                            color = AccentPurple,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append("Политикой конфиденциальности")
                    }
                    pop()

                    append(" и ")

                    pushStringAnnotation(tag = "terms", annotation = "terms_of_use")
                    withStyle(SpanStyle(
                        color = AccentPurple,
                        textDecoration = TextDecoration.Underline
                    )) {
                        append("Условиями пользования")
                    }
                    pop()
                }

                ClickableText(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium.copy(color = TitleDark),
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
                Spacer(Modifier.height(12.dp))

                OutlinedGradientButton(
                    text = if (phoneVerification is AuthViewModel.PhoneVerificationState.CodeSent ||
                        phoneVerification is AuthViewModel.PhoneVerificationState.Error
                    ) "Отправить код повторно" else "Получить код",
                    enabled = validatePhone(phoneDigits) == null,
                    onClick = { viewModel.sendCode("+7$phoneDigits") }
                )

                if (showCodeInput) {
                    Spacer(Modifier.height(12.dp))
                    RoundedField(
                        value = smsCode,
                        onValueChange = { smsCode = it.filter { c -> c.isDigit() }.take(6) },
                        placeholder = "Код из СМС",
                        leadingIcon = null,
                        keyboardType = KeyboardType.Number,
                        isError = phoneVerification is AuthViewModel.PhoneVerificationState.Error,
                        errorText = (phoneVerification as? AuthViewModel.PhoneVerificationState.Error)?.message
                    )
                    Spacer(Modifier.height(12.dp))
                    GradientButton(
                        text = "Подтвердить",
                        enabled = smsCode.length >= 4,
                        onClick = { viewModel.verifyCode("+7$phoneDigits", smsCode) }
                    )
                }

                Spacer(Modifier.height(16.dp))
            } else {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Номер подтверждён", color = SuccessGreen, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(16.dp))
            }

            if (submitted && !agreedToTerms) {
                Text(
                    "Необходимо принять условия",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }

            // Общая ошибка от сервера
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
            }

            GradientButton(
                text = when {
                    loading -> null
                    isRegisterBlocked -> "Повторите через $registerCooldownSeconds сек"
                    else -> "Зарегистрироваться"
                },
                loading = loading,
                enabled = !loading && !isRegisterBlocked && isFormValid,
                onClick = {
                    submitted = true
                    error = null
                    if (isFormValid) {
                        // Передаём на бэкенд номер в формате +7XXXXXXXXXX
                        viewModel.register("+7$phoneDigits", fullName.trim(), password)
                    }
                }
            )

            Spacer(Modifier.height(20.dp))

            val backInteractionSource = remember { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = backInteractionSource,
                    indication = LocalIndication.current
                ) { navController.popBackStack() }
            ) {
                Text("Уже есть аккаунт?", color = TextMuted, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
                Text("Войти", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ── Переиспользуемое скруглённое поле ввода ─────────────────────────────────
@Composable
private fun RoundedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    errorText: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = PlaceholderGray) },
            leadingIcon = leadingIcon?.let {
                { Icon(it, contentDescription = null, tint = AccentPurple) }
            },
            trailingIcon = trailing,
            singleLine = true,
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FieldBg,
                unfocusedContainerColor = FieldBg,
                errorContainerColor = FieldBg,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                errorBorderColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0x1A6E3ADB))
        )
        if (errorText != null) {
            Text(
                text = errorText,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

// ── Основная градиентная кнопка ─────────────────────────────────────────────
@Composable
private fun GradientButton(
    text: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(20.dp), spotColor = AccentPurple.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(AccentPurple, AccentPurpleDark))
                else Brush.horizontalGradient(listOf(Color(0xFFC9BEDD), Color(0xFFBDAFD6)))
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
        } else {
            Text(text ?: "", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Вторичная кнопка с фиолетовой обводкой (для "Получить код") ────────────
@Composable
private fun OutlinedGradientButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(
                width = 1.5.dp,
                color = if (enabled) AccentPurple else Color(0xFFD9D0E6),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) AccentPurple else Color(0xFFB2A8BE),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}


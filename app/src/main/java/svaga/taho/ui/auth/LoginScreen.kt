package svaga.taho.ui.auth

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import svaga.taho.util.ui.PhoneVisualTransformation

// ── Цвета для нового дизайна ────────────────────────────────────────
private val BgTop = Color(0xFFF7EEFC)
private val BgBottom = Color(0xFFEFE0FA)
private val WaveColor1 = Color(0xFFE9D9F7)
private val WaveColor2 = Color(0xFFE1CDF5)
private val AccentPurple = Color(0xFF6E3ADB)
private val AccentPurpleDark = Color(0xFF5B2FC2)
private val FieldBg = Color(0xFFFFFFFF)
private val PlaceholderGray = Color(0xFFB2A8BE)



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var phoneDigits by remember { mutableStateOf("") }


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
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            // ── Иконка-логотип ────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .shadow(elevation = 18.dp, shape = RoundedCornerShape(28.dp), spotColor = AccentPurple.copy(alpha = 0.25f))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = AccentPurple,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Вход",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1A1626)
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Введите данные для входа\nв ваш аккаунт",
                fontSize = 15.sp,
                color = Color(0xFF8E8398),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(36.dp))

            // ── Поле "Номер телефона" ──────────────────────────────
            OutlinedTextField(
                value = phoneDigits,
                onValueChange = { input -> phoneDigits = input.filter { it.isDigit() }.take(10) },
                placeholder = { Text("+7 (___) ___-__-__", color = PlaceholderGray) },
                leadingIcon = {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = AccentPurple)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                visualTransformation = PhoneVisualTransformation(),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = FieldBg,
                    unfocusedContainerColor = FieldBg,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0x1A6E3ADB))
            )

            Spacer(Modifier.height(16.dp))

            // ── Поле "Пароль" ──────────────────────────────────────
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Пароль", color = PlaceholderGray) },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = AccentPurple)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = AccentPurple
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = FieldBg,
                    unfocusedContainerColor = FieldBg,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0x1A6E3ADB))
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(24.dp))

            // ── Кнопка "Войти" ──────────────────────────────────────
            val buttonInteractionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(20.dp), spotColor = AccentPurple.copy(alpha = 0.4f))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(AccentPurple, AccentPurpleDark)))
                    .clickable(
                        enabled = !loading,
                        interactionSource = buttonInteractionSource,
                        indication = LocalIndication.current
                    ) { viewModel.login("+7$phoneDigits", password) },
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Войти", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(28.dp))

            Text("Нет аккаунта?", color = Color(0xFF8E8398), fontSize = 14.sp)

            Spacer(Modifier.height(4.dp))

            val linkInteractionSource = remember { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = linkInteractionSource,
                    indication = LocalIndication.current
                ) { navController.navigate("register") }
            ) {
                Text(
                    "Зарегистрироваться",
                    color = AccentPurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.width(4.dp))
                Text("›", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ── Простая волнообразная форма для декоративного фона ─────────────
public class WaveShape(
    private val amplitude: androidx.compose.ui.unit.Dp,
    private val phase: Float
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val amp = with(density) { amplitude.toPx() }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, amp * 2)
            val steps = 40
            for (i in 0..steps) {
                val x = size.width * (i / steps.toFloat())
                val angle = (i / steps.toFloat() + phase) * 2 * Math.PI.toFloat()
                val y = amp * 2 + amp * kotlin.math.sin(angle)
                lineTo(x, y)
            }
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}
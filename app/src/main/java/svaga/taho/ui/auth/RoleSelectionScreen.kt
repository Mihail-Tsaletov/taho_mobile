package svaga.taho.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

// ── Цвета (совпадают с LoginScreen) ─────────────────────────────────
private val BgTop = Color(0xFFF7EEFC)
private val BgBottom = Color(0xFFEFE0FA)
private val WaveColor1 = Color(0xFFE9D9F7)
private val WaveColor2 = Color(0xFFE1CDF5)
private val AccentPurple = Color(0xFF6E3ADB)
private val AccentPurpleDark = Color(0xFF5B2FC2)
private val CardBg = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF1A1626)
private val TextSecondary = Color(0xFF8E8398)

@Composable
fun RoleSelectionScreen(
    navController: NavController,
) {
    val tokenManager = hiltViewModel<AuthViewModel>().tokenManager
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        // ── Декоративные волны внизу ────────────────────────────────
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
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = AccentPurple.copy(alpha = 0.25f)
                    )
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
                text = "Вы — водитель",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Кем хотите войти сегодня?",
                fontSize = 15.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(40.dp))

            // ── Карточка «Пассажиром» ─────────────────────────────
            RoleCard(
                title = "Пассажиром",
                subtitle = "Заказать поездку",
                icon = Icons.Default.Person,
                accent = AccentPurple,
                onClick = {
                    scope.launch {
                        tokenManager.setLastModeDriver(false)
                        navController.navigate("client_home") { popUpTo(0) }
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            // ── Карточка «Водителем» ──────────────────────────────
            RoleCard(
                title = "Водителем",
                subtitle = "Принимать заказы",
                icon = Icons.Default.DirectionsCar,
                accent = AccentPurpleDark,
                onClick = {
                    scope.launch {
                        tokenManager.setLastModeDriver(true)
                        navController.navigate("driver_home") { popUpTo(0) }
                    }
                }
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = AccentPurple.copy(alpha = 0.18f)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Иконка в цветном квадрате
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            // Стрелка
            Text(
                text = "›",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = accent.copy(alpha = 0.7f)
            )
        }
    }
}
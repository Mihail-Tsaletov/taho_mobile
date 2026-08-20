package svaga.taho.ui.menu

import android.content.Intent
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathIterator
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import svaga.taho.ui.auth.AuthViewModel
import androidx.core.net.toUri
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import svaga.taho.R
import svaga.taho.data.local.TokenManager
import svaga.taho.di.AppModule
import svaga.taho.ui.navigation.Screen
import svaga.taho.util.RepairState

private val AccentColor = Color(0xFF6C5CE7)
private val AccentBg = Color(0xFFF0EDFB)

// ── Базовый пункт меню с произвольным контентом ────────────────
@Composable
private fun DrawerMenuItem(
    @DrawableRes icon: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPressed) AccentBg else Color.Transparent)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null, // убираем стандартный ripple, у нас свой эффект
                onClick = onClick
            )
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isPressed) AccentColor else Color.Transparent)
        )

        Spacer(Modifier.width(12.dp))

        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = if (isPressed) AccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(Modifier.width(16.dp))

        content()
    }
}

// ── Пункт меню с текстовой меткой ───────────────────────────────
@Composable
public fun DrawerMenuItem(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    DrawerMenuItem(icon = icon, onClick = onClick) {
        Text(
            text = label,
            fontSize = 17.sp,
            fontWeight = if (isPressed) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isPressed) AccentColor else MaterialTheme.colorScheme.onSurface
        )
    }
}
@Composable
fun AppDrawerContentForDriver(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel(),
    name: String,
    phone: String,
    onCloseDrawer: () -> Unit,
    onRepairStarted: () -> Unit = {}
) {
    val context = LocalContext.current

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showRoleChangeConfirm by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val repairTimerManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        ).repairTimerManager()
    }
    val repairState by repairTimerManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val tokenManager: TokenManager = hiltViewModel<AuthViewModel>().tokenManager
    val token by tokenManager.tokenFlow.collectAsState(initial = "")

    val apiService = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppModule.ApiProvider::class.java
        ).apiService()
    }

    var showRepairConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Кнопка закрытия
                IconButton(
                    onClick = onCloseDrawer,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Закрыть меню",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 15.dp))

        DrawerMenuItem(
            icon = R.drawable.outline_bar_chart_4_bars_24,
            label = "Статистика",
            onClick = {
                onCloseDrawer()
                navController.navigate(Screen.Statistics.route)
            }
        )

        Spacer(Modifier.height(4.dp))

        DrawerMenuItem(
            icon = R.drawable.outline_contact_support_24,
            label = "Связаться с оператором",
            onClick = {
                onCloseDrawer()
                val intent = Intent(Intent.ACTION_DIAL, "tel:+71234567890".toUri())
                context.startActivity(intent)
            }
        )

        Spacer(Modifier.height(4.dp))

        DrawerMenuItem(
            icon = R.drawable.outline_person_24,
            label = "Смена роли",
            onClick = { showRoleChangeConfirm = true }
        )

        Spacer(Modifier.height(4.dp))

        DrawerMenuItem(
            icon = R.drawable.outline_logout_24,
            label = "Выйти из аккаунта",
            onClick = { showLogoutConfirm = true }
        )

        Spacer(Modifier.height(4.dp))

        HorizontalDivider(modifier = Modifier.padding(vertical = 15.dp))


        DrawerMenuItem(
            icon = R.drawable.outline_parking_sign_24,
            enabled = repairState is RepairState.Idle,
            onClick = {
                if (repairState is RepairState.Idle) showRepairConfirm = true
            }
        ) {
            when (val rs = repairState) {
                is RepairState.Idle -> Text(
                    text = "Уйти на отдых",
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                is RepairState.OnRepair -> {
                    val hours = rs.secondsLeft / 3600
                    val mins = (rs.secondsLeft % 3600) / 60
                    val secs = rs.secondsLeft % 60
                    Text(
                        text = "Отдых: %02d:%02d:%02d".format(hours, mins, secs),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFF9800)
                    )
                }
                is RepairState.Expired -> Text(
                    text = "Отдых завершён",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Red
                )
            }
        }

        DrawerMenuItem(
            icon = R.drawable.outline_info_24,
            label = "О приложении",
            onClick = {
                showAboutDialog = true}
        )
    }

    // Диалоговые окна подтверждения
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Вы уверены?") },
            text = { Text("Вы хотите выйти из аккаунта?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    authViewModel.logout()
                    onCloseDrawer()
                }) { Text("Да") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Нет") }
            }
        )
    }

    if (showRoleChangeConfirm) {
        AlertDialog(
            onDismissRequest = { showRoleChangeConfirm = false },
            title = { Text("Вы уверены?") },
            text = { Text("Вы хотите сменить роль?") },
            confirmButton = {
                TextButton(onClick = {
                    showRoleChangeConfirm = false
                    navController.navigate("role_selection") { popUpTo(0) }
                    onCloseDrawer()
                }) { Text("Да") }
            },
            dismissButton = {
                TextButton(onClick = { showRoleChangeConfirm = false }) { Text("Нет") }
            }
        )
    }
    if (showRepairConfirm) {
        AlertDialog(
            onDismissRequest = { showRepairConfirm = false },
            title = { Text("Уйти на отдых?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Вы уйдёте на отдых на 2 часа. За 5 минут до окончания придёт уведомление. Если не выйдете на линию — статус изменится на офлайн.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRepairConfirm = false
                        scope.launch {
                            try {
                                apiService.standRepair("Bearer $token")
                                repairTimerManager.startRepair(
                                    scope = scope,
                                    context = context,
                                    onExpired = {
                                        scope.launch {
                                            // Уводим в офлайн
                                            apiService.toggleOnlineStatus("Bearer $token")
                                        }
                                    }
                                )
                                onRepairStarted()  // ← обновляем профиль сразу при уходе
                                onCloseDrawer()
                            } catch (e: Exception) {
                                Log.e("RepairTimer", "Ошибка ухода на отдых", e)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Text("Уйти на отдых", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRepairConfirm = false }) {
                    Text("Отмена", color = Color.Gray)
                }
            }
        )
    }
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("О приложении") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Политика конфиденциальности") },
                        modifier = Modifier.clickable {
                            showAboutDialog = false
                            onCloseDrawer()
                            navController.navigate(Screen.Document.route("privacy_policy"))
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Условия пользования") },
                        modifier = Modifier.clickable {
                            showAboutDialog = false
                            onCloseDrawer()
                            navController.navigate(Screen.Document.route("terms_of_use"))
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Закрыть") }
            }
        )
    }
}
package svaga.taho.ui.menu

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import svaga.taho.ui.auth.AuthViewModel
import androidx.core.net.toUri
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import svaga.taho.data.local.TokenManager
import svaga.taho.di.AppModule
import svaga.taho.ui.navigation.Screen
import svaga.taho.util.RepairState

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
            .width(280.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        // Верхняя панель с кнопкой закрытия (справа)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text("TahoTaxi", style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.weight(1f)) // толкает кнопку вправо

            // Кнопка закрытия — круглая со стрелкой назад
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

        // Header with name and phone
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
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // История заказов
        ListItem(
            headlineContent = { Text("Статистика") },
            modifier = Modifier.clickable {
                onCloseDrawer()
                navController.navigate(Screen.Statistics.route)
            }
        )

        // Связаться с оператором
        ListItem(
            headlineContent = { Text("Связаться с оператором") },
            modifier = Modifier.clickable {
                onCloseDrawer()
                val intent = Intent(Intent.ACTION_DIAL, "tel:+79495895834".toUri()) // Замените на реальный номер
                context.startActivity(intent)
            }
        )

        // Смена роли
        ListItem(
            headlineContent = { Text("Смена роли") },
            modifier = Modifier.clickable {
                showRoleChangeConfirm = true
            }
        )

        // Выйти из аккаунта
        ListItem(
            headlineContent = { Text("Выйти из аккаунта") },
            modifier = Modifier.clickable {
                showLogoutConfirm = true
            }
        )

        // О приложении
        ListItem(
            headlineContent = { Text("О приложении") },
            modifier = Modifier.clickable {
                // Пока ничего, можно добавить диалог или тост
                onCloseDrawer()
            }
        )

        ListItem(
            headlineContent = {
                when (val rs = repairState) {
                    is RepairState.Idle -> Text("Уйти на отдых")
                    is RepairState.OnRepair -> {
                        val hours = rs.secondsLeft / 3600
                        val mins = (rs.secondsLeft % 3600) / 60
                        val secs = rs.secondsLeft % 60
                        Text("Отдых: %02d:%02d:%02d".format(hours, mins, secs),
                            color = Color(0xFFFF9800))
                    }
                    is RepairState.Expired -> Text("Отдых завершён", color = Color.Red)
                }
            },
            modifier = Modifier.clickable {
                if (repairState is RepairState.Idle) showRepairConfirm = true
            }
        )

        ListItem(
            headlineContent = { Text("О приложении") },
            modifier = Modifier.clickable {
                showAboutDialog = true
            }
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
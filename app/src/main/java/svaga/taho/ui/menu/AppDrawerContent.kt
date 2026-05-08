package svaga.taho.ui.menu

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import svaga.taho.ui.auth.AuthViewModel
import androidx.core.net.toUri

@Composable
fun AppDrawerContent(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel(),
    name: String,
    phone: String,
    role: String = "",
    onCloseDrawer: () -> Unit
) {
    val context = LocalContext.current

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showRoleChangeConfirm by remember { mutableStateOf(false) }

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

        // Связаться с оператором
        ListItem(
            headlineContent = { Text("Связаться с оператором") },
            modifier = Modifier.clickable {
                onCloseDrawer()
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+71234567890"))
                context.startActivity(intent)
            }
        )

        // Смена роли
        if (role == "DRIVER") {
            ListItem(
                headlineContent = { Text("Смена роли") },
                modifier = Modifier.clickable {
                    showRoleChangeConfirm = true
                }
            )
        }

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
                onCloseDrawer()
                // Здесь можно показать диалог или тост
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
}
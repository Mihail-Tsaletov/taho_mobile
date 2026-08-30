package svaga.taho.ui.menu

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import svaga.taho.ui.auth.AuthViewModel
import androidx.core.net.toUri
import svaga.taho.R
import svaga.taho.ui.client.ClientViewModel
import svaga.taho.ui.menu.DrawerMenuItem
import svaga.taho.ui.navigation.Screen


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
    var showAboutDialog by remember { mutableStateOf(false) }
    val clientViewModel: ClientViewModel = hiltViewModel()


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


        Spacer(Modifier.height(4.dp))

        DrawerMenuItem(
            icon = R.drawable.outline_contact_support_24,
            label = "Связаться с оператором",
            onClick = {
                onCloseDrawer()
                val intent = Intent(Intent.ACTION_DIAL, "tel:+79495895834".toUri())
                context.startActivity(intent)
            }
        )

        Spacer(Modifier.height(4.dp))
        if (role == "DRIVER") {
            DrawerMenuItem(
                icon = R.drawable.outline_person_24,
                label = "Смена роли",
                onClick = { showRoleChangeConfirm = true }
            )
        }


        Spacer(Modifier.height(4.dp))

        DrawerMenuItem(
            icon = R.drawable.outline_logout_24,
            label = "Выйти из аккаунта",
            onClick = { showLogoutConfirm = true }
        )

        Spacer(Modifier.height(4.dp))

        HorizontalDivider(modifier = Modifier.padding(vertical = 15.dp))

        DrawerMenuItem(
            icon = R.drawable.outline_info_24,
            label = "О приложении",
            onClick = {
                showAboutDialog = true}
        )

        // Диалоговые окна подтверждения
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Вы уверены?") },
            text = { Text("Вы хотите выйти из аккаунта?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    clientViewModel.resetOrderState()
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
}
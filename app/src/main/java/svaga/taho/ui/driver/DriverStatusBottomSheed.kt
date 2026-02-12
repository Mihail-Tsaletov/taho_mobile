package svaga.taho.ui.driver

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverStatusBottomSheet(
    driverName: String,
    driverStatus: String,
    onToggleStatus: suspend () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = driverName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Статус: ${if (driverStatus == "OFFLINE") "Отдых" else "На линии"}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (driverStatus == "OFFLINE") Color.Gray else Color.Green
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Баланс: 0 ₽", // пока статический
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        onToggleStatus()
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (driverStatus == "OFFLINE") Color(0xFF37CC12) else Color(
                        0xFFAF4C4C
                    )
                )
            ) {
                Text(
                    if (driverStatus == "OFFLINE") "Выйти на линию" else "Уйти c линии",
                    color = Color.White
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}


//TODO сделать прооверку на остальные статусы водилы
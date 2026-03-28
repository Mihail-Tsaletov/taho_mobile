package svaga.taho.ui.driver

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverStatusBottomSheet(
    driverName: String,
    driverStatus: String,
    driverBalance: BigDecimal,
    onToggleStatus: suspend (parkId: Int?) -> Unit,  // ← parkId: 1=Черема, 2=Город, null=уход с линии
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Показываем выбор парковки только при выходе на линию
    var showParkingSelection by remember { mutableStateOf(false) }

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
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = if (driverStatus == "OFFLINE") Color.Gray else Color(0xFF10B826)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Баланс: $driverBalance ₽",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(24.dp))

            if (!showParkingSelection) {
                // ── Основная кнопка ──────────────────────────────────
                Button(
                    onClick = {
                        if (driverStatus == "OFFLINE") {
                            // Выход на линию — показываем выбор парковки
                            showParkingSelection = true
                        } else {
                            // Уход с линии — без выбора
                            scope.launch {
                                onToggleStatus(null)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (driverStatus == "OFFLINE") Color(0xFF37CC12) else Color(0xFFAF4C4C)
                    )
                ) {
                    Text(
                        text = if (driverStatus == "OFFLINE") "Выйти на линию" else "Уйти с линии",
                        color = Color.White
                    )
                }
            } else {
                // ── Выбор парковки ───────────────────────────────────
                Text(
                    text = "Выберите парковку",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                onToggleStatus(1) // Черема
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                    ) {
                        Text("Черема", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                onToggleStatus(2) // Город
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047))
                    ) {
                        Text("Город", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = { showParkingSelection = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Назад", color = Color.Gray)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
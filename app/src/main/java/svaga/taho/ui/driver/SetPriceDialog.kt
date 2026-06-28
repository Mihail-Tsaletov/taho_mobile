package svaga.taho.ui.driver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetPriceDialog(
    orderId: String,
    onConfirm: (price: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var priceInput by remember { mutableStateOf("") }
    var showConfirmation by remember { mutableStateOf(false) }
    val price = priceInput.toIntOrNull()

    if (!showConfirmation) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    "Новый маршрут",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Цена для данного направления ещё не установлена. " +
                                "Укажите стоимость — она сохранится и будет использоваться автоматически для всех последующих поездок по этому маршруту.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Цена, ₽") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showConfirmation = true },
                    enabled = price != null && price > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                ) {
                    Text("Далее", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Отмена", color = Color.Gray)
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = {
                Text("Подтвердите тариф", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Тариф для данного направления:",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$price ₽",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E88E5)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠️ Цена сохранится на сервере для всех последующих поездок по этому маршруту.",
                        color = Color(0xFFFF9800),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onConfirm(price!!) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Сохранить тариф", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text("Изменить", color = Color.Gray)
                }
            }
        )
    }
}
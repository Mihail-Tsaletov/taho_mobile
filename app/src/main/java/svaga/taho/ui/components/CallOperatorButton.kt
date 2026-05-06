package svaga.taho.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private const val OPERATOR_PHONE = "+999"

@Composable
fun CallOperatorButton(
    modifier: Modifier = Modifier,
    operatorPhone: String = OPERATOR_PHONE
) {
    val context = LocalContext.current
    val showConfirmDialog = remember { mutableStateOf(false) }

    if (showConfirmDialog.value) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog.value = false },
            title = {
                Text(
                    text = "Вызов оператору",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Вы хотите позвонить оператору?\n$operatorPhone",
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog.value = false
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$operatorPhone"))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Да", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog.value = false }) {
                    Text("Нет", color = Color.Gray)
                }
            }
        )
    }

    FloatingActionButton(
        onClick = { showConfirmDialog.value = true },
        modifier = modifier,
        containerColor = Color(0xFF4CAF50),
        contentColor = Color.White,
        shape = CircleShape
    ) {
        Icon(
            imageVector = Icons.Default.Phone,
            contentDescription = "Позвонить оператору"
        )
    }
}
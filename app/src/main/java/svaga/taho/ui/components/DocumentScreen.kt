package svaga.taho.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    navController: NavController,
    documentType: String // "privacy_policy" или "terms_of_use"
) {
    val context = LocalContext.current

    val (title, content) = remember(documentType) {
        val resId = when (documentType) {
            "privacy_policy" -> context.resources.getIdentifier("privacy_policy", "raw", context.packageName)
            "terms_of_use"   -> context.resources.getIdentifier("terms_of_use", "raw", context.packageName)
            else -> 0
        }
        val text = if (resId != 0) {
            context.resources.openRawResource(resId).bufferedReader().readText()
        } else {
            "Документ не найден"
        }
        val titleStr = when (documentType) {
            "privacy_policy" -> "Политика конфиденциальности"
            "terms_of_use"   -> "Условия пользования"
            else -> "Документ"
        }
        Pair(titleStr, text)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
            )
        }
    ) { padding ->
        Text(
            text = content,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
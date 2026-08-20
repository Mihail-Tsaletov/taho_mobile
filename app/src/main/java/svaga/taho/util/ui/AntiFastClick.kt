package svaga.taho.util.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun rememberDebouncedClick(intervalMs: Long = 600L, action: () -> Unit): () -> Unit {
    var lastClickTime by remember { mutableStateOf(0L) }
    return {
        val now = System.currentTimeMillis()
        if (now - lastClickTime > intervalMs) {
            lastClickTime = now
            action()
        }
    }
}
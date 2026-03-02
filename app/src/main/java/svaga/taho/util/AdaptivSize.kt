// util/AdaptiveSize.kt
package svaga.taho.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Базовый экран — 360dp (стандартный Android телефон)
private const val BASE_WIDTH = 360f

@Composable
fun Int.adaptiveSp(): TextUnit {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val scale = (screenWidth / BASE_WIDTH).coerceIn(0.85f, 1.25f)
    return (this * scale).sp
}

@Composable
fun Int.adaptiveDp(): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val scale = (screenWidth / BASE_WIDTH).coerceIn(0.85f, 1.25f)
    return (this * scale).dp
}
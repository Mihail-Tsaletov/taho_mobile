package svaga.taho.util.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

fun formatPhone(digits: String): String {
    val d = digits.take(10)
    return buildString {
        append("+7 ")
        if (d.isEmpty()) return@buildString
        append("(")
        append(d.take(3))
        if (d.length > 3) {
            append(") ")
            append(d.substring(3, minOf(6, d.length)))
        }
        if (d.length > 6) {
            append("-")
            append(d.substring(6, minOf(8, d.length)))
        }
        if (d.length > 8) {
            append("-")
            append(d.substring(8))
        }
    }
}

class PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = formatPhone(text.text)
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, text.text.length)
                return when {
                    o == 0  -> 3
                    o <= 3  -> o + 4
                    o <= 6  -> o + 6
                    o <= 8  -> o + 7
                    o <= 10 -> o + 8
                    else    -> formatted.length
                }
            }
            override fun transformedToOriginal(offset: Int): Int =
                text.text.length.coerceAtMost(offset)
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
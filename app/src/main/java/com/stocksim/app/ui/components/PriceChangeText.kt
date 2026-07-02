package com.stocksim.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.stocksim.app.ui.theme.pnlColor
import com.stocksim.app.util.formatPrice
import com.stocksim.app.util.formatSignedPercent
import kotlin.math.abs

/**
 * 前日比などの騰落表示。「▲123.5 (+1.23%)」の形式で、
 * 色（赤=上昇/緑=下落）と記号・符号の両方で方向を伝える。
 */
@Composable
fun PriceChangeText(
    change: Double?,
    changePercent: Double?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    if (change == null) {
        Text(text = "—", style = style, color = pnlColor(null), modifier = modifier)
        return
    }
    val arrow = when {
        change > 0.0 -> "▲"
        change < 0.0 -> "▼"
        else -> "±"
    }
    val percentText = changePercent?.let { " (${formatSignedPercent(it)})" } ?: ""
    Text(
        text = arrow + formatPrice(abs(change)) + percentText,
        style = style,
        color = pnlColor(change),
        modifier = modifier,
    )
}

package com.stocksim.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocksim.app.model.ChartSeries
import com.stocksim.app.ui.theme.AppBackground
import com.stocksim.app.ui.theme.AppOnBackground
import com.stocksim.app.ui.theme.AppPrimary
import com.stocksim.app.ui.theme.AppSurfaceVariant
import com.stocksim.app.ui.theme.DownGreen
import com.stocksim.app.ui.theme.TextSecondary
import com.stocksim.app.ui.theme.UpRed
import com.stocksim.app.util.formatDateTime
import com.stocksim.app.util.formatPrice
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 折れ線チャート（株価・資産推移共用）。
 * - 基準値（前日終値や開始資金など）との比較で赤/緑を決める
 * - [baseline] は破線＋ラベルで表示
 * - [costBasis] は自分の取得単価ライン（保有銘柄のみ、レンジ内の時だけ描画）
 * - 横ドラッグでクロスヘア＋日時・価格のツールチップ
 */
@Composable
fun PriceLineChart(
    series: ChartSeries,
    baseline: Double?,
    modifier: Modifier = Modifier,
    baselineLabel: String? = null,
    costBasis: Double? = null,
    costBasisLabel: String? = null,
) {
    val closes = series.closes
    if (closes.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "チャートデータがありません",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        return
    }

    val reference = baseline ?: closes.first()
    val lineColor = if (closes.last() >= reference) UpRed else DownGreen

    var touchIndex by remember(series) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = TextSecondary, fontSize = 10.sp)
    val tooltipStyle = TextStyle(color = AppOnBackground, fontSize = 11.sp)

    Canvas(
        modifier = modifier.pointerInput(series) {
            detectHorizontalDragGestures(
                onDragEnd = { touchIndex = null },
                onDragCancel = { touchIndex = null },
            ) { change, _ ->
                change.consume()
                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                touchIndex = (fraction * (closes.size - 1)).roundToInt()
                    .coerceIn(0, closes.size - 1)
            }
        }
    ) {
        val w = size.width
        val h = size.height

        var minV = closes.min()
        var maxV = closes.max()
        baseline?.let {
            minV = minOf(minV, it)
            maxV = maxOf(maxV, it)
        }
        val rawSpan = maxV - minV
        val span = if (rawSpan > 0.0) rawSpan else maxOf(abs(maxV) * 0.01, 1.0)
        val lo = minV - span * 0.08
        val hi = maxV + span * 0.08

        fun xAt(index: Int): Float = index.toFloat() / (closes.size - 1) * w
        fun yAt(value: Double): Float = (h * (1.0 - (value - lo) / (hi - lo))).toFloat()

        // 面（グラデーション塗り）
        val fillPath = Path().apply {
            moveTo(0f, yAt(closes[0]))
            for (i in 1 until closes.size) lineTo(xAt(i), yAt(closes[i]))
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
                startY = 0f,
                endY = h,
            ),
        )

        // 折れ線
        val linePath = Path().apply {
            moveTo(0f, yAt(closes[0]))
            for (i in 1 until closes.size) lineTo(xAt(i), yAt(closes[i]))
        }
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // 基準線（前日終値・開始資金など、破線）
        baseline?.let { value ->
            val y = yAt(value)
            drawLine(
                color = TextSecondary.copy(alpha = 0.6f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
            baselineLabel?.let { label ->
                val text = textMeasurer.measure(AnnotatedString(label), labelStyle)
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset(
                        w - text.size.width - 4.dp.toPx(),
                        (y - text.size.height - 2.dp.toPx()).coerceIn(0f, h - text.size.height),
                    ),
                )
            }
        }

        // 取得単価ライン（レンジ内にある時のみ描画）
        costBasis?.takeIf { it in lo..hi }?.let { value ->
            val y = yAt(value)
            drawLine(
                color = AppPrimary.copy(alpha = 0.8f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
            )
            costBasisLabel?.let { label ->
                val text = textMeasurer.measure(
                    AnnotatedString(label),
                    labelStyle.copy(color = AppPrimary),
                )
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset(
                        w - text.size.width - 4.dp.toPx(),
                        (y + 2.dp.toPx()).coerceIn(0f, h - text.size.height),
                    ),
                )
            }
        }

        // 高値・安値ラベル
        val maxLabel = textMeasurer.measure(AnnotatedString(formatPrice(maxV)), labelStyle)
        drawText(
            textLayoutResult = maxLabel,
            topLeft = Offset(
                4.dp.toPx(),
                (yAt(maxV) + 3.dp.toPx()).coerceIn(0f, h - maxLabel.size.height),
            ),
        )
        val minLabel = textMeasurer.measure(AnnotatedString(formatPrice(minV)), labelStyle)
        drawText(
            textLayoutResult = minLabel,
            topLeft = Offset(
                4.dp.toPx(),
                (yAt(minV) - minLabel.size.height - 3.dp.toPx()).coerceIn(0f, h - minLabel.size.height),
            ),
        )

        // クロスヘア＋ツールチップ
        touchIndex?.let { index ->
            val x = xAt(index)
            val value = closes[index]
            drawLine(
                color = TextSecondary,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1.dp.toPx(),
            )
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, yAt(value)))
            drawCircle(color = AppBackground, radius = 2.dp.toPx(), center = Offset(x, yAt(value)))

            val timeText = series.timestamps.getOrNull(index)
                ?.let { formatDateTime(it * 1000) }
                .orEmpty()
            val tooltip = textMeasurer.measure(
                AnnotatedString("$timeText  ${formatPrice(value)}"),
                tooltipStyle,
            )
            val padX = 6.dp.toPx()
            val padY = 3.dp.toPx()
            val tipX = (x - tooltip.size.width / 2f)
                .coerceIn(padX, (w - tooltip.size.width - padX).coerceAtLeast(padX))
            drawRoundRect(
                color = AppSurfaceVariant,
                topLeft = Offset(tipX - padX, 0f),
                size = Size(tooltip.size.width + padX * 2, tooltip.size.height + padY * 2),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )
            drawText(textLayoutResult = tooltip, topLeft = Offset(tipX, padY))
        }
    }
}

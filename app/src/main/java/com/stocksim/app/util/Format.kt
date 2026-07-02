package com.stocksim.app.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val yenFormat = DecimalFormat("#,##0")
private val priceFormat = DecimalFormat("#,##0.##")
private val smallPriceFormat = DecimalFormat("0.0000")
private val percentFormat = DecimalFormat("0.00")

/** 金額（円）。小数は四捨五入して表示する。 */
fun formatYen(value: Double): String = "¥" + yenFormat.format(value.roundToLong())

/** 符号付き金額。 */
fun formatSignedYen(value: Double): String {
    val sign = if (value >= 0) "+" else "-"
    return sign + "¥" + yenFormat.format(abs(value).roundToLong())
}

/** 株価・レート。10未満（EURUSD等のFXレートなど）は小数第4位まで表示する。 */
fun formatPrice(value: Double): String =
    if (abs(value) < 10.0) smallPriceFormat.format(value) else priceFormat.format(value)

/** 数量の単位。FX（=X）は「通貨」、それ以外は「株」。 */
fun unitLabelFor(symbol: String): String = if (symbol.endsWith("=X")) "通貨" else "株"

/** 符号付きパーセント。 */
fun formatSignedPercent(value: Double): String {
    val sign = if (value >= 0) "+" else "-"
    return sign + percentFormat.format(abs(value)) + "%"
}

fun formatQuantity(value: Long, unit: String = "株"): String = yenFormat.format(value) + unit

fun formatVolume(value: Long): String = yenFormat.format(value)

fun formatDateTime(epochMillis: Long): String =
    SimpleDateFormat("M/d HH:mm", Locale.JAPAN).format(Date(epochMillis))

fun formatFullDateTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy/M/d HH:mm", Locale.JAPAN).format(Date(epochMillis))

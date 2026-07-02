package com.stocksim.app.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val yenFormat = DecimalFormat("#,##0")
private val priceFormat = DecimalFormat("#,##0.##")
private val percentFormat = DecimalFormat("0.00")

/** 金額（円）。小数は四捨五入して表示する。 */
fun formatYen(value: Double): String = "¥" + yenFormat.format(value.roundToLong())

/** 符号付き金額。 */
fun formatSignedYen(value: Double): String {
    val sign = if (value >= 0) "+" else "-"
    return sign + "¥" + yenFormat.format(abs(value).roundToLong())
}

/** 株価。呼値が小数の銘柄（ETF等）に備え小数第2位まで表示する。 */
fun formatPrice(value: Double): String = priceFormat.format(value)

/** 符号付きパーセント。 */
fun formatSignedPercent(value: Double): String {
    val sign = if (value >= 0) "+" else "-"
    return sign + percentFormat.format(abs(value)) + "%"
}

fun formatQuantity(value: Long): String = yenFormat.format(value) + "株"

fun formatVolume(value: Long): String = yenFormat.format(value)

fun formatDateTime(epochMillis: Long): String =
    SimpleDateFormat("M/d HH:mm", Locale.JAPAN).format(Date(epochMillis))

fun formatFullDateTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy/M/d HH:mm", Locale.JAPAN).format(Date(epochMillis))

package com.stocksim.app.util

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 遅延データ基準の取引可能時間の判定。
 *
 * Yahoo のデータは取引所ごとに遅延がある（API の exchangeDataDelayedBy が分単位で返す。
 * 東証は20分・米国市場は15分が基準）。「いま画面に見えている価格 = delay分前の市場」
 * なので、(現在時刻 - delay) が取引所の立会時間内かどうかで判定する。
 * 結果として取引可能時間帯は実際の立会時間を delay 分だけ後ろにずらしたものになる。
 * ※祝日は未対応（土日のみ休場扱い）。
 */
object TradingHours {

    enum class MarketType { TSE, US, FX }

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val newYork = ZoneId.of("America/New_York")

    private val tseAmOpen: LocalTime = LocalTime.of(9, 0)
    private val tseAmClose: LocalTime = LocalTime.of(11, 30)
    private val tsePmOpen: LocalTime = LocalTime.of(12, 30)
    private val tsePmClose: LocalTime = LocalTime.of(15, 30)
    private val usOpen: LocalTime = LocalTime.of(9, 30)
    private val usClose: LocalTime = LocalTime.of(16, 0)
    private val fxBoundary: LocalTime = LocalTime.of(7, 0)

    fun marketTypeOf(symbol: String): MarketType = when {
        symbol.endsWith("=X") -> MarketType.FX
        symbol.endsWith(".T") -> MarketType.TSE
        else -> MarketType.US
    }

    /** API が遅延情報を返さなかった時のフォールバック（分） */
    fun defaultDelayFor(symbol: String): Int = when (marketTypeOf(symbol)) {
        MarketType.TSE -> 20
        MarketType.US -> 15
        MarketType.FX -> 0
    }

    fun isOpen(
        symbol: String,
        delayMinutes: Int,
        now: ZonedDateTime = ZonedDateTime.now(tokyo),
    ): Boolean {
        val effective = now.minusMinutes(delayMinutes.toLong())
        return when (marketTypeOf(symbol)) {
            MarketType.TSE -> {
                val t = effective.withZoneSameInstant(tokyo)
                val time = t.toLocalTime()
                isWeekday(t) && (
                    inRange(time, tseAmOpen, tseAmClose) || inRange(time, tsePmOpen, tsePmClose)
                    )
            }

            MarketType.US -> {
                val t = effective.withZoneSameInstant(newYork)
                isWeekday(t) && inRange(t.toLocalTime(), usOpen, usClose)
            }

            MarketType.FX -> {
                // 月曜7:00〜土曜7:00（日本時間）をオープン扱いにする
                val t = effective.withZoneSameInstant(tokyo)
                when (t.dayOfWeek) {
                    DayOfWeek.SUNDAY -> false
                    DayOfWeek.SATURDAY -> t.toLocalTime().isBefore(fxBoundary)
                    DayOfWeek.MONDAY -> !t.toLocalTime().isBefore(fxBoundary)
                    else -> true
                }
            }
        }
    }

    /** 取引可能時間帯の表示用文字列（日本時間・遅延ずらし込み） */
    fun hoursLabel(
        symbol: String,
        delayMinutes: Int,
        now: ZonedDateTime = ZonedDateTime.now(tokyo),
    ): String {
        val delay = delayMinutes.toLong()
        return when (marketTypeOf(symbol)) {
            MarketType.TSE -> {
                val s1 = tseAmOpen.plusMinutes(delay)
                val e1 = tseAmClose.plusMinutes(delay)
                val s2 = tsePmOpen.plusMinutes(delay)
                val e2 = tsePmClose.plusMinutes(delay)
                "${fmt(s1)}〜${fmt(e1)} / ${fmt(s2)}〜${fmt(e2)}"
            }

            MarketType.US -> {
                // 当日のNY立会時間を日本時間へ変換（夏/冬時間を自動反映）
                val nyDate = now.withZoneSameInstant(newYork).toLocalDate()
                val open = nyDate.atTime(usOpen).atZone(newYork)
                    .plusMinutes(delay).withZoneSameInstant(tokyo)
                val close = nyDate.atTime(usClose).atZone(newYork)
                    .plusMinutes(delay).withZoneSameInstant(tokyo)
                "${fmt(open.toLocalTime())}〜${fmt(close.toLocalTime())}"
            }

            MarketType.FX -> "月曜7:00〜土曜7:00"
        }
    }

    private fun isWeekday(t: ZonedDateTime): Boolean =
        t.dayOfWeek != DayOfWeek.SATURDAY && t.dayOfWeek != DayOfWeek.SUNDAY

    private fun inRange(t: LocalTime, start: LocalTime, endInclusive: LocalTime): Boolean =
        !t.isBefore(start) && !t.isAfter(endInclusive)

    private fun fmt(t: LocalTime): String = "%d:%02d".format(t.hour, t.minute)
}

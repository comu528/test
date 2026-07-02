package com.stocksim.app.model

/** 現在の気配値（Yahoo は15〜20分遅延） */
data class Quote(
    val symbol: String,
    val name: String,
    val currency: String,
    /** 取引通貨建ての現在値 */
    val price: Double,
    /** 円換算した現在値（JPY銘柄は price と同値） */
    val priceJpy: Double = price,
    val previousClose: Double?,
    val dayHigh: Double?,
    val dayLow: Double?,
    val volume: Long?,
    val marketTime: Long?,
) {
    val change: Double? get() = previousClose?.let { price - it }
    val changePercent: Double?
        get() = previousClose?.takeIf { it != 0.0 }?.let { (price - it) / it * 100.0 }
}

/** チャート用時系列 */
data class ChartSeries(
    val timestamps: List<Long>,
    val closes: List<Double>,
    val previousClose: Double?,
)

/** 銘柄検索結果 */
data class StockSearchResult(
    val symbol: String,
    val name: String,
    val exchange: String,
)

/** 保有銘柄 + 現在値の表示用ビュー（金額はすべて円建て） */
data class HoldingView(
    val symbol: String,
    val name: String,
    val quantity: Long,
    val averageCost: Double,
    val currency: String,
    val currentPrice: Double?,
    val previousClose: Double?,
) {
    val costBasis: Double get() = averageCost * quantity

    /** 評価額。現在値が未取得の間は取得原価で代用する。 */
    val marketValue: Double get() = (currentPrice ?: averageCost) * quantity

    val unrealizedPnl: Double? get() = currentPrice?.let { (it - averageCost) * quantity }
    val unrealizedPnlPercent: Double?
        get() = currentPrice?.takeIf { averageCost != 0.0 }
            ?.let { (it - averageCost) / averageCost * 100.0 }
}

/** 売買の結果 */
sealed interface TradeOutcome {
    data class Success(val message: String) : TradeOutcome
    data class Failure(val message: String) : TradeOutcome
}

/** チャートの表示期間 */
enum class ChartRange(val label: String, val range: String, val interval: String) {
    DAY1("1日", "1d", "5m"),
    WEEK1("1週", "5d", "15m"),
    MONTH1("1ヶ月", "1mo", "1d"),
    MONTH6("6ヶ月", "6mo", "1d"),
    YEAR1("1年", "1y", "1wk"),
}

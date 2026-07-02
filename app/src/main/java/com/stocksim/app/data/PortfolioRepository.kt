package com.stocksim.app.data

import androidx.room.withTransaction
import com.stocksim.app.data.local.AppDatabase
import com.stocksim.app.data.local.AssetSnapshotEntity
import com.stocksim.app.data.local.HoldingEntity
import com.stocksim.app.data.local.PortfolioEntity
import com.stocksim.app.data.local.TradeEntity
import com.stocksim.app.data.remote.ChartResult
import com.stocksim.app.data.remote.YahooFinanceClient
import com.stocksim.app.model.ChartRange
import com.stocksim.app.model.ChartSeries
import com.stocksim.app.model.Quote
import com.stocksim.app.model.StockSearchResult
import com.stocksim.app.model.TradeOutcome
import com.stocksim.app.util.formatYen
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import kotlin.math.floor

class PortfolioRepository(
    private val db: AppDatabase,
    private val yahoo: YahooFinanceClient,
) {

    val portfolio: Flow<PortfolioEntity?> = db.portfolioDao().observe()
    val holdings: Flow<List<HoldingEntity>> = db.holdingDao().observeAll()
    val trades: Flow<List<TradeEntity>> = db.tradeDao().observeAll()
    val assetSnapshots: Flow<List<AssetSnapshotEntity>> = db.snapshotDao().observeAll()

    fun observeHolding(symbol: String): Flow<HoldingEntity?> = db.holdingDao().observe(symbol)

    /** Yahoo への同時リクエスト数を絞る（銘柄一覧などまとめて取る時の429対策） */
    private val fetchSemaphore = Semaphore(8)

    /** USD/JPY レートのキャッシュ */
    private val fxMutex = Mutex()
    private var fxCache: Pair<Long, Double>? = null

    // ---- ゲームライフサイクル ----

    /** 初期資金を設定して新しいゲームを開始する。 */
    suspend fun startGame(initialCapital: Double) {
        db.withTransaction {
            db.holdingDao().clear()
            db.tradeDao().clear()
            db.snapshotDao().clear()
            db.portfolioDao().clear()
            db.portfolioDao().upsert(
                PortfolioEntity(
                    initialCapital = initialCapital,
                    cash = initialCapital,
                    createdAt = System.currentTimeMillis(),
                )
            )
            db.snapshotDao().insert(
                AssetSnapshotEntity(
                    timestamp = System.currentTimeMillis(),
                    totalAssets = initialCapital,
                )
            )
        }
    }

    /** 全データを消去し初期設定画面に戻す。 */
    suspend fun resetGame() {
        db.withTransaction {
            db.holdingDao().clear()
            db.tradeDao().clear()
            db.snapshotDao().clear()
            db.portfolioDao().clear()
        }
    }

    /** 資産推移チャート用に総資産を記録する（一定間隔で間引き）。 */
    suspend fun recordSnapshot(totalAssets: Double) {
        val last = db.snapshotDao().latest()
        val now = System.currentTimeMillis()
        if (last == null || now - last.timestamp >= SNAPSHOT_INTERVAL_MS) {
            db.snapshotDao().insert(AssetSnapshotEntity(timestamp = now, totalAssets = totalAssets))
            db.snapshotDao().prune()
        }
    }

    /** 総資産が尽きた時に呼ぶ。 */
    suspend fun markGameOver() {
        db.portfolioDao().setGameOver()
    }

    // ---- 売買 ----

    /** 約定代金に対する手数料（0.1%、円未満切り捨て） */
    fun feeFor(amount: Double): Double = floor(amount * FEE_RATE)

    /** [priceJpy] は円換算済みの約定単価（JPY銘柄はそのままの株価）。 */
    suspend fun buy(
        symbol: String,
        name: String,
        currency: String,
        quantity: Long,
        priceJpy: Double,
    ): TradeOutcome {
        if (quantity <= 0) return TradeOutcome.Failure("数量は1株以上を指定してください")
        if (priceJpy <= 0.0) return TradeOutcome.Failure("株価を取得できていないため注文できません")
        return db.withTransaction {
            val portfolio = db.portfolioDao().get()
                ?: return@withTransaction TradeOutcome.Failure("ポートフォリオが初期化されていません")
            val cost = priceJpy * quantity
            val fee = feeFor(cost)
            val total = cost + fee
            if (total > portfolio.cash) {
                return@withTransaction TradeOutcome.Failure(
                    "買付余力が不足しています（必要額 ${formatYen(total)} / 現金 ${formatYen(portfolio.cash)}）"
                )
            }
            val existing = db.holdingDao().get(symbol)
            val newQuantity = (existing?.quantity ?: 0L) + quantity
            val newAverageCost = if (existing == null) {
                priceJpy
            } else {
                (existing.averageCost * existing.quantity + cost) / newQuantity
            }
            db.holdingDao().upsert(
                HoldingEntity(
                    symbol = symbol,
                    name = name,
                    quantity = newQuantity,
                    averageCost = newAverageCost,
                    currency = currency,
                )
            )
            db.portfolioDao().upsert(portfolio.copy(cash = portfolio.cash - total))
            db.tradeDao().insert(
                TradeEntity(
                    symbol = symbol,
                    name = name,
                    side = TradeEntity.SIDE_BUY,
                    quantity = quantity,
                    price = priceJpy,
                    fee = fee,
                    realizedPnl = null,
                    timestamp = System.currentTimeMillis(),
                )
            )
            TradeOutcome.Success("${name} を ${quantity}株 買付しました")
        }
    }

    /** [priceJpy] は円換算済みの約定単価。 */
    suspend fun sell(symbol: String, name: String, quantity: Long, priceJpy: Double): TradeOutcome {
        if (quantity <= 0) return TradeOutcome.Failure("数量は1株以上を指定してください")
        if (priceJpy <= 0.0) return TradeOutcome.Failure("株価を取得できていないため注文できません")
        return db.withTransaction {
            val portfolio = db.portfolioDao().get()
                ?: return@withTransaction TradeOutcome.Failure("ポートフォリオが初期化されていません")
            val existing = db.holdingDao().get(symbol)
                ?: return@withTransaction TradeOutcome.Failure("この銘柄を保有していません")
            if (quantity > existing.quantity) {
                return@withTransaction TradeOutcome.Failure(
                    "保有数を超えています（保有 ${existing.quantity}株）"
                )
            }
            val proceeds = priceJpy * quantity
            val fee = feeFor(proceeds)
            val realizedPnl = (priceJpy - existing.averageCost) * quantity - fee
            val remaining = existing.quantity - quantity
            if (remaining == 0L) {
                db.holdingDao().delete(symbol)
            } else {
                db.holdingDao().upsert(existing.copy(quantity = remaining))
            }
            db.portfolioDao().upsert(portfolio.copy(cash = portfolio.cash + proceeds - fee))
            db.tradeDao().insert(
                TradeEntity(
                    symbol = symbol,
                    name = name,
                    side = TradeEntity.SIDE_SELL,
                    quantity = quantity,
                    price = priceJpy,
                    fee = fee,
                    realizedPnl = realizedPnl,
                    timestamp = System.currentTimeMillis(),
                )
            )
            TradeOutcome.Success("${name} を ${quantity}株 売却しました")
        }
    }

    // ---- 株価データ ----

    /**
     * USD/JPY レートを取得する（5分キャッシュ）。
     * 取得失敗時は古いキャッシュがあればそれで代用する。
     */
    suspend fun usdJpyRate(): Double = fxMutex.withLock {
        val cached = fxCache
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.first < FX_CACHE_MS) return cached.second
        val rate = runCatching {
            yahoo.fetchChart("USDJPY=X", "1d", "5m").meta.regularMarketPrice
        }.getOrNull() ?: cached?.second ?: throw IOException("為替レートを取得できませんでした")
        fxCache = now to rate
        return rate
    }

    /** 1単位あたりの円換算レート。 */
    private suspend fun jpyRateFor(currency: String): Double = when (currency) {
        "JPY" -> 1.0
        "USD" -> usdJpyRate()
        else -> throw IOException("未対応の通貨です（$currency）")
    }

    suspend fun fetchQuote(symbol: String, fallbackName: String? = null): Quote =
        fetchSemaphore.withPermit {
            val quote = yahoo.fetchChart(symbol, ChartRange.DAY1.range, ChartRange.DAY1.interval)
                .toQuote(fallbackName)
            quote.copy(priceJpy = quote.price * jpyRateFor(quote.currency))
        }

    /** 複数銘柄の現在値をまとめて取得。失敗した銘柄は結果から除く。 */
    suspend fun fetchQuotes(symbols: List<String>): Map<String, Quote> = coroutineScope {
        symbols.map { symbol ->
            async {
                runCatching { fetchQuote(symbol) }.getOrNull()?.let { symbol to it }
            }
        }.awaitAll().filterNotNull().toMap()
    }

    suspend fun fetchChartSeries(symbol: String, range: ChartRange): ChartSeries {
        val result = yahoo.fetchChart(symbol, range.range, range.interval)
        val timestamps = result.timestamp.orEmpty()
        val closes = result.indicators?.quote?.firstOrNull()?.close.orEmpty()
        val points = timestamps.zip(closes)
            .mapNotNull { (time, close) -> close?.let { time to it } }
        return ChartSeries(
            timestamps = points.map { it.first },
            closes = points.map { it.second },
            previousClose = result.meta.previousClose ?: result.meta.chartPreviousClose,
        )
    }

    /**
     * 銘柄検索。東証（.T）と米国市場（サフィックスなし）の株式・ETFを返す。
     * それ以外の取引所は通貨換算が未対応のため除外する。
     */
    suspend fun search(query: String): List<StockSearchResult> =
        yahoo.search(query).mapNotNull { quote ->
            val symbol = quote.symbol ?: return@mapNotNull null
            if (quote.quoteType != "EQUITY" && quote.quoteType != "ETF") return@mapNotNull null
            val isTokyo = symbol.endsWith(".T")
            val isUs = !symbol.contains(".")
            if (!isTokyo && !isUs) return@mapNotNull null
            StockSearchResult(
                symbol = symbol,
                name = quote.longname ?: quote.shortname ?: symbol,
                exchange = quote.exchDisp ?: if (isTokyo) "東証" else "米国",
            )
        }

    private fun ChartResult.toQuote(fallbackName: String?): Quote {
        // 現在値が欠けたレスポンスを0円として扱うと誤ったゲームオーバー判定や
        // 損益計算につながるため、取得失敗として弾く
        val price = meta.regularMarketPrice
        if (price == null || price <= 0.0) {
            throw IOException("現在値を取得できませんでした")
        }
        return Quote(
            symbol = meta.symbol.orEmpty(),
            name = meta.longName ?: meta.shortName ?: fallbackName ?: meta.symbol.orEmpty(),
            currency = meta.currency ?: "JPY",
            price = price,
            previousClose = meta.previousClose ?: meta.chartPreviousClose,
            dayHigh = meta.regularMarketDayHigh,
            dayLow = meta.regularMarketDayLow,
            volume = meta.regularMarketVolume,
            marketTime = meta.regularMarketTime,
        )
    }

    companion object {
        /** 約定代金に対する手数料率 */
        const val FEE_RATE = 0.001

        /**
         * ゲームオーバー閾値。現物取引のみでは総資産が厳密に0円を下回ることは
         * ないため、「これ以上まともな取引ができない＝資金が底をついた」ラインとして
         * 総資産がこの額を下回ったら終了とする。
         */
        const val GAME_OVER_THRESHOLD = 1_000.0

        /** 資産スナップショットの最小記録間隔 */
        private const val SNAPSHOT_INTERVAL_MS = 5 * 60_000L

        /** 為替レートのキャッシュ有効期間 */
        private const val FX_CACHE_MS = 5 * 60_000L
    }
}

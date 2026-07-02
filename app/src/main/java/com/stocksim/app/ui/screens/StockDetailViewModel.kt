package com.stocksim.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksim.app.data.PortfolioRepository
import com.stocksim.app.data.local.HoldingEntity
import com.stocksim.app.model.ChartRange
import com.stocksim.app.model.ChartSeries
import com.stocksim.app.model.Quote
import com.stocksim.app.model.TradeOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class StockDetailUiState(
    val symbol: String,
    val name: String,
    val quote: Quote? = null,
    val series: ChartSeries? = null,
    val selectedRange: ChartRange = ChartRange.DAY1,
    val isLoadingQuote: Boolean = true,
    val isLoadingChart: Boolean = true,
    val error: String? = null,
    val holding: HoldingEntity? = null,
    val cash: Double = 0.0,
    /** 注文執行中（「取引中…」オーバーレイ表示） */
    val isTrading: Boolean = false,
)

class StockDetailViewModel(
    private val repo: PortfolioRepository,
    private val symbol: String,
    initialName: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StockDetailUiState(symbol = symbol, name = initialName.ifBlank { symbol })
    )
    val uiState: StateFlow<StockDetailUiState> = _uiState.asStateFlow()

    /** 約定結果などのスナックバー通知 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var chartJob: Job? = null

    init {
        viewModelScope.launch {
            repo.observeHolding(symbol).collect { holding ->
                _uiState.update { it.copy(holding = holding) }
            }
        }
        viewModelScope.launch {
            repo.portfolio.collect { portfolio ->
                _uiState.update { it.copy(cash = portfolio?.cash ?: 0.0) }
            }
        }
        refresh()
    }

    fun refresh() {
        loadQuote()
        loadChart(_uiState.value.selectedRange)
    }

    /** 自動更新用。チャートは触らず現在値だけ更新する（クロスヘア操作を邪魔しない） */
    fun refreshQuote() {
        loadQuote()
    }

    fun selectRange(range: ChartRange) {
        if (range == _uiState.value.selectedRange) return
        _uiState.update { it.copy(selectedRange = range) }
        loadChart(range)
    }

    fun buy(quantity: Long) = trade(isBuy = true, quantity = quantity)

    fun sell(quantity: Long) = trade(isBuy = false, quantity = quantity)

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * 実際の取引所のタイムラグを模して、注文からランダムに10〜20秒待ってから約定させる。
     * 約定価格は待ち時間の後に取り直した最新の株価（取得失敗時は注文時点の株価）。
     */
    private fun trade(isBuy: Boolean, quantity: Long) {
        val state = _uiState.value
        if (state.isTrading) return
        if (state.quote == null) {
            _message.value = "株価を取得できていないため注文できません"
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isTrading = true) }
            try {
                delay(Random.nextLong(TRADE_LAG_MIN_MS, TRADE_LAG_MAX_MS + 1))
                val quote = runCatching { repo.fetchQuote(symbol, _uiState.value.name) }
                    .getOrNull() ?: _uiState.value.quote
                if (quote == null) {
                    _message.value = "株価を取得できず注文が失敗しました"
                    return@launch
                }
                _uiState.update { it.copy(quote = quote, name = quote.name) }
                val outcome = if (isBuy) {
                    repo.buy(symbol, quote.name, quote.currency, quantity, quote.priceJpy)
                } else {
                    repo.sell(symbol, quote.name, quantity, quote.priceJpy)
                }
                when (outcome) {
                    is TradeOutcome.Success -> _message.value = outcome.message
                    is TradeOutcome.Failure -> _message.value = outcome.message
                }
            } finally {
                _uiState.update { it.copy(isTrading = false) }
            }
        }
    }

    private fun loadQuote() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingQuote = true, error = null) }
            try {
                val quote = repo.fetchQuote(symbol, fallbackName = _uiState.value.name)
                _uiState.update {
                    it.copy(quote = quote, name = quote.name, isLoadingQuote = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingQuote = false, error = e.message ?: "株価の取得に失敗しました")
                }
            }
        }
    }

    companion object {
        /** 約定までのタイムラグ（リアリティ演出） */
        private const val TRADE_LAG_MIN_MS = 10_000L
        private const val TRADE_LAG_MAX_MS = 20_000L
    }

    private fun loadChart(range: ChartRange) {
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChart = true) }
            try {
                val series = repo.fetchChartSeries(symbol, range)
                _uiState.update { it.copy(series = series, isLoadingChart = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(series = null, isLoadingChart = false) }
            }
        }
    }
}

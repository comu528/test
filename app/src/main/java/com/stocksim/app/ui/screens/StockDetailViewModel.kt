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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    fun buy(quantity: Long) = trade { price -> repo.buy(symbol, _uiState.value.name, quantity, price) }

    fun sell(quantity: Long) = trade { price -> repo.sell(symbol, _uiState.value.name, quantity, price) }

    fun consumeMessage() {
        _message.value = null
    }

    private fun trade(execute: suspend (price: Double) -> TradeOutcome) {
        val price = _uiState.value.quote?.price ?: 0.0
        viewModelScope.launch {
            when (val outcome = execute(price)) {
                is TradeOutcome.Success -> _message.value = outcome.message
                is TradeOutcome.Failure -> _message.value = outcome.message
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

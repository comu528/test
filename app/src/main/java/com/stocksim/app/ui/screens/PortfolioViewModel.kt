package com.stocksim.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksim.app.data.PortfolioRepository
import com.stocksim.app.model.HoldingView
import com.stocksim.app.model.Quote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PortfolioUiState(
    val initialCapital: Double = 0.0,
    val cash: Double = 0.0,
    val holdings: List<HoldingView> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastUpdated: Long? = null,
) {
    val marketValue: Double get() = holdings.sumOf { it.marketValue }
    val totalAssets: Double get() = cash + marketValue
    val totalPnl: Double get() = totalAssets - initialCapital
    val totalPnlPercent: Double?
        get() = initialCapital.takeIf { it != 0.0 }?.let { totalPnl / it * 100.0 }
}

class PortfolioViewModel(private val repo: PortfolioRepository) : ViewModel() {

    private val quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    private val refreshing = MutableStateFlow(false)
    private val lastUpdated = MutableStateFlow<Long?>(null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val uiState: StateFlow<PortfolioUiState> = combine(
        repo.portfolio, repo.holdings, quotes, refreshing, lastUpdated,
    ) { portfolio, holdings, quoteMap, isRefreshing, updated ->
        PortfolioUiState(
            initialCapital = portfolio?.initialCapital ?: 0.0,
            cash = portfolio?.cash ?: 0.0,
            holdings = holdings.map { h ->
                val quote = quoteMap[h.symbol]
                HoldingView(
                    symbol = h.symbol,
                    name = h.name,
                    quantity = h.quantity,
                    averageCost = h.averageCost,
                    currentPrice = quote?.price,
                    previousClose = quote?.previousClose,
                )
            },
            isRefreshing = isRefreshing,
            lastUpdated = updated,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())

    init {
        // 60秒ごとに自動更新（Yahoo のデータ自体が15〜20分遅延なので十分な頻度）
        viewModelScope.launch {
            while (isActive) {
                refreshInternal()
                delay(60_000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshInternal() }
    }

    fun consumeError() {
        _error.value = null
    }

    private suspend fun refreshInternal() {
        val symbols = repo.holdings.first().map { it.symbol }
        if (symbols.isEmpty()) {
            lastUpdated.value = System.currentTimeMillis()
            return
        }
        refreshing.value = true
        val fetched = repo.fetchQuotes(symbols)
        refreshing.value = false
        if (fetched.isEmpty()) {
            _error.value = "株価を取得できませんでした。通信環境を確認してください。"
            return
        }
        quotes.update { it + fetched }
        lastUpdated.value = System.currentTimeMillis()
        checkGameOver()
    }

    /** 全保有銘柄の現在値が揃っている時だけ総資産を判定する */
    private suspend fun checkGameOver() {
        val portfolio = repo.portfolio.first() ?: return
        val holdings = repo.holdings.first()
        val quoteMap = quotes.value
        if (holdings.any { quoteMap[it.symbol] == null }) return
        val total = portfolio.cash + holdings.sumOf { (quoteMap[it.symbol]?.price ?: 0.0) * it.quantity }
        if (total <= 0.0) {
            repo.markGameOver()
        }
    }
}

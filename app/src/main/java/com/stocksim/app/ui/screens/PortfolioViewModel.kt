package com.stocksim.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksim.app.data.PortfolioRepository
import com.stocksim.app.data.local.AssetSnapshotEntity
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
                    currency = h.currency,
                    currentPrice = quote?.priceJpy,
                    previousClose = quote?.previousClose,
                )
            },
            isRefreshing = isRefreshing,
            lastUpdated = updated,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortfolioUiState())

    /** 資産推移チャート用の履歴 */
    val assetHistory: StateFlow<List<AssetSnapshotEntity>> = repo.assetSnapshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 画面側のライフサイクル対応ループから呼ばれる自動更新（エラーは静かに握りつぶす） */
    fun autoRefresh() {
        viewModelScope.launch { refreshInternal(manual = false) }
    }

    /** 更新ボタンからの手動更新（失敗はスナックバーで通知する） */
    fun refresh() {
        viewModelScope.launch { refreshInternal(manual = true) }
    }

    fun consumeError() {
        _error.value = null
    }

    private suspend fun refreshInternal(manual: Boolean) {
        if (refreshing.value) return // 自動更新と手動更新の重複実行を防ぐ
        refreshing.value = true
        try {
            val symbols = repo.holdings.first().map { it.symbol }
            if (symbols.isNotEmpty()) {
                val fetched = repo.fetchQuotes(symbols)
                if (fetched.isEmpty()) {
                    if (manual) {
                        _error.value = "株価を取得できませんでした。通信環境を確認してください。"
                    }
                    return
                }
                quotes.update { it + fetched }
                if (manual && fetched.size < symbols.size) {
                    _error.value = "一部の銘柄の株価を取得できませんでした"
                }
            }
            lastUpdated.value = System.currentTimeMillis()
        } finally {
            refreshing.value = false
        }
        recordAndCheckGameOver()
    }

    /**
     * 総資産を計算してスナップショットに記録し、ゲームオーバー閾値との比較も行う。
     * 現在値を取得できていない銘柄は表示と同じく平均取得単価で評価する。
     */
    private suspend fun recordAndCheckGameOver() {
        val portfolio = repo.portfolio.first() ?: return
        val holdings = repo.holdings.first()
        val quoteMap = quotes.value
        val marketValue = holdings.sumOf { holding ->
            val price = quoteMap[holding.symbol]?.priceJpy?.takeIf { it > 0.0 }
                ?: holding.averageCost
            price * holding.quantity
        }
        val total = portfolio.cash + marketValue
        repo.recordSnapshot(total)
        if (!portfolio.gameOver && total < PortfolioRepository.GAME_OVER_THRESHOLD) {
            repo.markGameOver()
        }
    }
}

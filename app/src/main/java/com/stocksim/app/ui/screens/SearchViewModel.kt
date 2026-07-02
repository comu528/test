package com.stocksim.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksim.app.data.MarketCatalog
import com.stocksim.app.data.PortfolioRepository
import com.stocksim.app.model.Quote
import com.stocksim.app.model.StockSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Market(val label: String) { JAPAN("日本株"), US("米国株") }

enum class MarketSort(val label: String) { GAINERS("値上がり"), LOSERS("値下がり"), CODE("コード順") }

/** 一覧の1行。quote が null の間は価格未取得。 */
data class MarketRow(
    val symbol: String,
    val name: String,
    val quote: Quote?,
)

data class SearchUiState(
    val query: String = "",
    val results: List<StockSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
    // ---- 銘柄一覧（検索語が空の時に表示） ----
    val market: Market = Market.JAPAN,
    val sort: MarketSort = MarketSort.GAINERS,
    val marketRows: List<MarketRow> = emptyList(),
    val isLoadingMarket: Boolean = false,
    val marketError: String? = null,
)

class SearchViewModel(private val repo: PortfolioRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var marketJob: Job? = null

    /** 市場ごとの株価キャッシュ（取得時刻つき） */
    private val marketCache = mutableMapOf<Market, Pair<Long, Map<String, Quote>>>()

    init {
        loadMarket(Market.JAPAN)
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(results = emptyList(), isSearching = false, hasSearched = false, error = null)
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400) // 入力が落ち着くまで待つ
            _uiState.update { it.copy(isSearching = true, error = null) }
            try {
                val results = repo.search(query.trim())
                _uiState.update { it.copy(results = results, isSearching = false, hasSearched = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        results = emptyList(),
                        isSearching = false,
                        hasSearched = true,
                        error = "検索に失敗しました。通信環境を確認してください。",
                    )
                }
            }
        }
    }

    fun selectMarket(market: Market) {
        if (market == _uiState.value.market) return
        _uiState.update { it.copy(market = market) }
        loadMarket(market)
    }

    fun selectSort(sort: MarketSort) {
        _uiState.update { it.copy(sort = sort) }
        rebuildRows()
    }

    fun refreshMarket() {
        loadMarket(_uiState.value.market, force = true)
    }

    private fun loadMarket(market: Market, force: Boolean = false) {
        marketJob?.cancel()
        val cached = marketCache[market]
        val now = System.currentTimeMillis()
        if (!force && cached != null && now - cached.first < MARKET_CACHE_MS) {
            rebuildRows()
            return
        }
        marketJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMarket = true, marketError = null) }
            rebuildRows() // キャッシュがあれば先に表示しておく
            val symbols = catalogFor(market).map { it.symbol }
            val fetched = repo.fetchQuotes(symbols)
            if (fetched.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoadingMarket = false,
                        marketError = "株価を取得できませんでした。通信環境を確認してください。",
                    )
                }
                return@launch
            }
            val merged = (marketCache[market]?.second ?: emptyMap()) + fetched
            marketCache[market] = System.currentTimeMillis() to merged
            _uiState.update { it.copy(isLoadingMarket = false) }
            rebuildRows()
        }
    }

    private fun rebuildRows() {
        val state = _uiState.value
        val quotes = marketCache[state.market]?.second ?: emptyMap()
        val rows = catalogFor(state.market).map { stock ->
            MarketRow(
                symbol = stock.symbol,
                name = quotes[stock.symbol]?.name?.takeIf { it.isNotBlank() } ?: stock.name,
                quote = quotes[stock.symbol],
            )
        }
        val sorted = when (state.sort) {
            MarketSort.GAINERS -> rows.sortedByDescending { it.quote?.changePercent ?: Double.NEGATIVE_INFINITY }
            MarketSort.LOSERS -> rows.sortedBy { it.quote?.changePercent ?: Double.POSITIVE_INFINITY }
            MarketSort.CODE -> rows.sortedBy { it.symbol }
        }
        _uiState.update { it.copy(marketRows = sorted) }
    }

    private fun catalogFor(market: Market) = when (market) {
        Market.JAPAN -> MarketCatalog.japan
        Market.US -> MarketCatalog.us
    }

    companion object {
        private const val MARKET_CACHE_MS = 60_000L
    }
}

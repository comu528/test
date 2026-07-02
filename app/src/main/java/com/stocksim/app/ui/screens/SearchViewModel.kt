package com.stocksim.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksim.app.data.PortfolioRepository
import com.stocksim.app.model.StockSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<StockSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(private val repo: PortfolioRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

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
}

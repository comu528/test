package com.stocksim.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stocksim.app.data.PortfolioRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppPhase {
    data object Loading : AppPhase
    data object Setup : AppPhase
    data object Main : AppPhase
    data class GameOver(val initialCapital: Double) : AppPhase
}

/** アプリ全体のフェーズ（初期設定 / メイン / ゲームオーバー）を司る */
class AppViewModel(private val repo: PortfolioRepository) : ViewModel() {

    val phase: StateFlow<AppPhase> = repo.portfolio
        .map { portfolio ->
            when {
                portfolio == null -> AppPhase.Setup
                portfolio.gameOver -> AppPhase.GameOver(portfolio.initialCapital)
                else -> AppPhase.Main
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPhase.Loading)

    fun startGame(initialCapital: Double) {
        viewModelScope.launch { repo.startGame(initialCapital) }
    }

    fun resetGame() {
        viewModelScope.launch { repo.resetGame() }
    }
}

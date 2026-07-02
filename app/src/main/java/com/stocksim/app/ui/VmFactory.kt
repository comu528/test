package com.stocksim.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.stocksim.app.StockSimApp
import com.stocksim.app.data.AppContainer

/** コンストラクタ引数付き ViewModel 用の汎用ファクトリ */
class VmFactory(private val creator: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as StockSimApp).container

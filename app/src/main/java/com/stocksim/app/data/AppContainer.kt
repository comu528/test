package com.stocksim.app.data

import android.content.Context
import com.stocksim.app.data.local.AppDatabase
import com.stocksim.app.data.remote.YahooFinanceClient

/** 手動DIコンテナ。Application から参照する。 */
class AppContainer(context: Context) {

    private val database = AppDatabase.build(context.applicationContext)
    private val yahoo = YahooFinanceClient()

    val repository = PortfolioRepository(database, yahoo)
}

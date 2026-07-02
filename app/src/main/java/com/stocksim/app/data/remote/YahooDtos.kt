package com.stocksim.app.data.remote

import kotlinx.serialization.Serializable

// ---- v8/finance/chart ----

@Serializable
data class ChartResponse(val chart: ChartOuter = ChartOuter())

@Serializable
data class ChartOuter(
    val result: List<ChartResult>? = null,
    val error: ChartError? = null,
)

@Serializable
data class ChartError(
    val code: String? = null,
    val description: String? = null,
)

@Serializable
data class ChartResult(
    val meta: ChartMeta = ChartMeta(),
    val timestamp: List<Long>? = null,
    val indicators: Indicators? = null,
)

@Serializable
data class ChartMeta(
    val currency: String? = null,
    val symbol: String? = null,
    val exchangeName: String? = null,
    val shortName: String? = null,
    val longName: String? = null,
    val regularMarketPrice: Double? = null,
    val previousClose: Double? = null,
    val chartPreviousClose: Double? = null,
    val regularMarketDayHigh: Double? = null,
    val regularMarketDayLow: Double? = null,
    val regularMarketVolume: Long? = null,
    val regularMarketTime: Long? = null,
)

@Serializable
data class Indicators(val quote: List<QuoteIndicator>? = null)

@Serializable
data class QuoteIndicator(val close: List<Double?>? = null)

// ---- v1/finance/search ----

@Serializable
data class SearchResponse(val quotes: List<SearchQuote> = emptyList())

@Serializable
data class SearchQuote(
    val symbol: String? = null,
    val shortname: String? = null,
    val longname: String? = null,
    val quoteType: String? = null,
    val exchDisp: String? = null,
)

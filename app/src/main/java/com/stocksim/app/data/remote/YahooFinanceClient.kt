package com.stocksim.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Yahoo Finance の非公式パブリックエンドポイントを叩くクライアント。
 * 株価は取引所により15〜20分程度遅延したデータが返る。
 */
class YahooFinanceClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** 時系列＋現在値。range/interval は Yahoo の書式（例: 1d/5m, 1mo/1d）。 */
    suspend fun fetchChart(symbol: String, range: String, interval: String): ChartResult =
        withContext(Dispatchers.IO) {
            val url = "$CHART_BASE/$symbol".toHttpUrl().newBuilder()
                .addQueryParameter("range", range)
                .addQueryParameter("interval", interval)
                .addQueryParameter("includePrePost", "false")
                .addQueryParameter("lang", "ja-JP")
                .addQueryParameter("region", "JP")
                .build()
            val body = get(url.toString())
            val response = json.decodeFromString(ChartResponse.serializer(), body)
            response.chart.error?.let {
                throw IOException(it.description ?: it.code ?: "チャートの取得に失敗しました")
            }
            response.chart.result?.firstOrNull()
                ?: throw IOException("銘柄 $symbol のデータが見つかりません")
        }

    /** 銘柄検索（銘柄名・コードどちらでも可）。 */
    suspend fun search(query: String): List<SearchQuote> = withContext(Dispatchers.IO) {
        val url = SEARCH_BASE.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("quotesCount", "20")
            .addQueryParameter("newsCount", "0")
            .addQueryParameter("listsCount", "0")
            .addQueryParameter("lang", "ja-JP")
            .addQueryParameter("region", "JP")
            .build()
        val body = get(url.toString())
        json.decodeFromString(SearchResponse.serializer(), body).quotes
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("通信エラー (HTTP ${response.code})")
            return response.body?.string() ?: throw IOException("空のレスポンスを受信しました")
        }
    }

    companion object {
        private const val CHART_BASE = "https://query1.finance.yahoo.com/v8/finance/chart"
        private const val SEARCH_BASE = "https://query1.finance.yahoo.com/v1/finance/search"

        // ボット判定による 429 を避けるため一般的なブラウザの UA を名乗る
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}

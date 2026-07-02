package com.stocksim.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.stocksim.app.data.PortfolioRepository
import com.stocksim.app.model.ChartRange
import com.stocksim.app.ui.components.PriceChangeText
import com.stocksim.app.ui.components.PriceLineChart
import com.stocksim.app.ui.theme.DownGreen
import com.stocksim.app.ui.theme.TextSecondary
import com.stocksim.app.ui.theme.UpRed
import com.stocksim.app.ui.theme.pnlColor
import com.stocksim.app.util.formatFullDateTime
import com.stocksim.app.util.formatPrice
import com.stocksim.app.util.formatQuantity
import com.stocksim.app.util.formatSignedPercent
import com.stocksim.app.util.formatSignedYen
import com.stocksim.app.util.formatVolume
import com.stocksim.app.util.formatYen
import com.stocksim.app.util.unitLabelFor
import kotlinx.coroutines.delay
import kotlin.math.floor

private enum class TradeSide { BUY, SELL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    viewModel: StockDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tradeSide by remember { mutableStateOf<TradeSide?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 表示中は60秒ごとに現在値を更新し、古い株価のまま約定できないようにする
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(60_000)
                viewModel.refreshQuote()
            }
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "更新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                if (state.enforceTradingHours && !state.marketOpen && state.quote != null) {
                    Text(
                        text = "取引時間外です（取引可能: ${state.marketHoursLabel ?: "-"}）",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { tradeSide = TradeSide.SELL },
                        enabled = (state.holding?.quantity ?: 0L) > 0 && state.quote != null &&
                            !state.isTrading && state.canTradeNow,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("売る", color = DownGreen)
                    }
                    Button(
                        onClick = { tradeSide = TradeSide.BUY },
                        enabled = state.quote != null && !state.isTrading && state.canTradeNow,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = UpRed),
                    ) {
                        Text("買う")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // 価格ヘッダー
            when {
                state.isLoadingQuote && state.quote == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }

                state.quote == null -> {
                    Text(
                        text = state.error ?: "株価を取得できませんでした",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }

                else -> {
                    val quote = state.quote!!
                    val isForeign = quote.currency != "JPY"
                    val isIndex = state.symbol.startsWith("^")
                    Text(
                        text = state.symbol,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = when {
                                isIndex -> formatPrice(quote.price) // 指数はポイント表記
                                isForeign -> "$" + formatPrice(quote.price)
                                else -> "${formatPrice(quote.price)}円"
                            },
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(12.dp))
                        PriceChangeText(
                            change = quote.change,
                            changePercent = quote.changePercent,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        text = buildString {
                            if (isForeign) append("約 ${formatYen(quote.priceJpy)}・")
                            if (quote.delayMinutes > 0) {
                                append("${quote.delayMinutes}分遅延データ")
                            } else {
                                append("ほぼリアルタイム")
                            }
                            quote.marketTime?.let { append("・${formatFullDateTime(it * 1000)} 時点") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 期間切り替え
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.selectedRange == range,
                        onClick = { viewModel.selectRange(range) },
                        label = { Text(range.label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // チャート
            if (state.isLoadingChart) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                val series = state.series
                if (series != null) {
                    // 取得単価（円建て）をチャートの通貨に換算して重ねる
                    val fxRate = state.quote?.takeIf { it.price > 0.0 }
                        ?.let { it.priceJpy / it.price } ?: 1.0
                    val costBasisNative = state.holding?.averageCost?.div(fxRate)
                    PriceLineChart(
                        series = series,
                        baseline = if (state.selectedRange == ChartRange.DAY1) series.previousClose else null,
                        baselineLabel = if (state.selectedRange == ChartRange.DAY1) "前日終値" else null,
                        costBasis = costBasisNative,
                        costBasisLabel = "取得単価",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "チャートを取得できませんでした",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 当日の指標
            state.quote?.let { quote ->
                val money: (Double) -> String = { value ->
                    when {
                        state.symbol.startsWith("^") -> formatPrice(value)
                        quote.currency != "JPY" -> "$" + formatPrice(value)
                        else -> formatPrice(value) + "円"
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StatRow("前日終値", quote.previousClose?.let(money) ?: "—")
                        StatRow("高値", quote.dayHigh?.let(money) ?: "—")
                        StatRow("安値", quote.dayLow?.let(money) ?: "—")
                        StatRow("出来高", quote.volume?.let { formatVolume(it) } ?: "—", last = true)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 保有状況（金額は円建て）
            state.holding?.let { holding ->
                val quotePrice = state.quote?.priceJpy
                val pnl = quotePrice?.let { (it - holding.averageCost) * holding.quantity }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("保有状況", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        StatRow("保有数量", formatQuantity(holding.quantity, unitLabelFor(state.symbol)))
                        StatRow("平均取得単価", "${formatPrice(holding.averageCost)}円")
                        StatRow(
                            label = "評価損益",
                            value = pnl?.let {
                                formatSignedYen(it) + (
                                    if (holding.averageCost != 0.0 && quotePrice != null) {
                                        " (${formatSignedPercent((quotePrice - holding.averageCost) / holding.averageCost * 100.0)})"
                                    } else ""
                                    )
                            } ?: "—",
                            valueColor = pnlColor(pnl),
                            last = true,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("買付余力", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(formatYen(state.cash), style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    val side = tradeSide
    val quote = state.quote
    if (side != null && quote != null) {
        TradeDialog(
            isBuy = side == TradeSide.BUY,
            name = state.name,
            symbol = state.symbol,
            price = quote.price,
            priceJpy = quote.priceJpy,
            currency = quote.currency,
            cash = state.cash,
            heldQuantity = state.holding?.quantity ?: 0L,
            onConfirm = { quantity ->
                if (side == TradeSide.BUY) viewModel.buy(quantity) else viewModel.sell(quantity)
                tradeSide = null
            },
            onDismiss = { tradeSide = null },
        )
    }

    // 約定待ちオーバーレイ（表示中は操作をブロック。閉じた瞬間の最新価格で約定する）
    if (state.isTrading) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("取引中…", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "市場で約定を待っています",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Text(
                        text = "画面を閉じると注文はキャンセルされます",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color? = null,
    last: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
    if (!last) HorizontalDivider()
}

@Composable
private fun TradeDialog(
    isBuy: Boolean,
    name: String,
    symbol: String,
    price: Double,
    priceJpy: Double,
    currency: String,
    cash: Double,
    heldQuantity: Long,
    onConfirm: (quantity: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val isFx = symbol.endsWith("=X")
    val isIndex = symbol.startsWith("^")
    val unit = unitLabelFor(symbol)
    val step = when {
        isFx -> 1_000L
        isIndex -> 1L
        else -> 100L
    }
    var quantityText by remember {
        mutableStateOf(
            when {
                isFx -> "1000"
                isIndex -> "1"
                else -> "100"
            }
        )
    }
    val quantity = quantityText.toLongOrNull() ?: 0L
    val isForeign = currency != "JPY"

    // 約定・手数料はすべて円建てで計算する
    val amount = priceJpy * quantity
    val fee = floor(amount * PortfolioRepository.FEE_RATE)
    val total = if (isBuy) amount + fee else amount - fee

    val validationError = when {
        quantity <= 0 -> "数量を入力してください"
        isBuy && total > cash -> "買付余力が不足しています"
        !isBuy && quantity > heldQuantity -> "保有数（${formatQuantity(heldQuantity, unit)}）を超えています"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isBuy) "$name を買う" else "$name を売る") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("現在値", color = TextSecondary)
                    Text(
                        text = if (isForeign) {
                            "$" + formatPrice(price) + "（約 ${formatYen(priceJpy)}）"
                        } else {
                            "${formatPrice(price)}円"
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { input ->
                            quantityText = input.filter { it.isDigit() }.take(9)
                        },
                        label = { Text("数量（$unit）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        TextButton(
                            onClick = {
                                quantityText = (quantity + step).coerceAtMost(999_999_999L).toString()
                            },
                        ) {
                            Text("+$step")
                        }
                        TextButton(
                            onClick = { quantityText = maxOf(quantity - step, 1L).toString() },
                        ) {
                            Text("-$step")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("約定代金", color = TextSecondary)
                    Text(formatYen(amount))
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("手数料（0.1%）", color = TextSecondary)
                    Text(formatYen(fee))
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (isBuy) "支払額合計" else "受取額",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(formatYen(total), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (isBuy) "取引後の買付余力" else "保有数",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Text(
                        text = if (isBuy) formatYen(cash - total) else formatQuantity(heldQuantity, unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                validationError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(quantity) },
                enabled = validationError == null,
            ) {
                Text(if (isBuy) "買い注文を出す" else "売り注文を出す")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

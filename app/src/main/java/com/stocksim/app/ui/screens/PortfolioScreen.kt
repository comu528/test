package com.stocksim.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.stocksim.app.data.local.AssetSnapshotEntity
import com.stocksim.app.model.ChartSeries
import com.stocksim.app.model.HoldingView
import com.stocksim.app.ui.components.PriceLineChart
import com.stocksim.app.ui.theme.AppPrimary
import com.stocksim.app.ui.theme.TextSecondary
import com.stocksim.app.ui.theme.pnlColor
import com.stocksim.app.util.formatDateTime
import com.stocksim.app.util.formatPrice
import com.stocksim.app.util.formatQuantity
import com.stocksim.app.util.formatSignedPercent
import com.stocksim.app.util.formatSignedYen
import com.stocksim.app.util.formatYen
import com.stocksim.app.util.unitLabelFor
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel,
    onOpenStock: (symbol: String, name: String) -> Unit,
    onReset: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val assetHistory by viewModel.assetHistory.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showResetDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 画面が表示されている間だけ60秒ごとに株価を自動更新する
    // （Yahoo のデータ自体が15〜20分遅延なので十分な頻度）
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.autoRefresh()
                delay(60_000)
            }
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ポートフォリオ") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "株価を更新")
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "設定")
                    }
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = "最初からやり直す")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryCard(state) }

            if (assetHistory.size >= 2) {
                item { AssetHistoryCard(history = assetHistory, initialCapital = state.initialCapital) }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("保有銘柄", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(14.dp).height(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        state.lastUpdated?.let {
                            Text(
                                text = "${formatDateTime(it)} 更新",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }

            if (state.holdings.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "まだ保有銘柄がありません",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "「検索」タブから銘柄を探して最初の取引を始めましょう",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            } else {
                items(state.holdings, key = { it.symbol }) { holding ->
                    HoldingRow(holding = holding, onClick = { onOpenStock(holding.symbol, holding.name) })
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("設定") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("取引時間の制限", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "実際の市場の取引時間（データ遅延分だけ後ろにずらした時間帯）内のみ売買できるようにします。OFFにするといつでも売買できます。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.enforceTradingHours,
                        onCheckedChange = { viewModel.setEnforceTradingHours(it) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("閉じる") }
            },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("最初からやり直しますか？") },
            text = { Text("保有銘柄・取引履歴・資金がすべて消去され、初期資金の設定からやり直します。") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onReset()
                }) { Text("やり直す") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun SummaryCard(state: PortfolioUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "総資産",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .background(AppPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Lv.${state.level}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppPrimary,
                    )
                }
                if (state.maxLevel > state.level) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "最高 Lv.${state.maxLevel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
            Text(
                text = formatYen(state.totalAssets),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "損益 ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Text(
                    text = formatSignedYen(state.totalPnl) +
                        (state.totalPnlPercent?.let { " (${formatSignedPercent(it)})" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = pnlColor(state.totalPnl),
                )
            }
            Spacer(Modifier.height(12.dp))
            // 次のレベルへの進捗
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "次のLv.${state.level + 1}まで",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text(
                    text = "あと ${formatYen((state.assetsForNextLevel - state.totalAssets).coerceAtLeast(0.0))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { state.levelProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("現金（買付余力）", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text(formatYen(state.cash), style = MaterialTheme.typography.titleMedium)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("株式評価額", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text(formatYen(state.marketValue), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** 総資産の推移チャート。開始資金を基準線として表示する。 */
@Composable
private fun AssetHistoryCard(history: List<AssetSnapshotEntity>, initialCapital: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("資産推移", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            PriceLineChart(
                series = ChartSeries(
                    timestamps = history.map { it.timestamp / 1000 },
                    closes = history.map { it.totalAssets },
                    previousClose = initialCapital,
                ),
                baseline = initialCapital,
                baselineLabel = "開始資金",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
        }
    }
}

@Composable
private fun HoldingRow(holding: HoldingView, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = holding.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${holding.symbol}・${formatQuantity(holding.quantity, unitLabelFor(holding.symbol))}・平均 ${formatPrice(holding.averageCost)}円",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = holding.currentPrice?.let { "${formatPrice(it)}円" } ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                val pnl = holding.unrealizedPnl
                if (pnl != null) {
                    Text(
                        text = formatSignedYen(pnl) +
                            (holding.unrealizedPnlPercent?.let { " (${formatSignedPercent(it)})" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = pnlColor(pnl),
                    )
                } else {
                    Text(
                        text = "取得中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

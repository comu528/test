package com.stocksim.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stocksim.app.ui.components.PriceChangeText
import com.stocksim.app.ui.theme.TextSecondary
import com.stocksim.app.util.formatPrice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenStock: (symbol: String, name: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("銘柄") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("銘柄名またはコード（例：トヨタ、7203、AAPL）") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "クリア")
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))

            if (state.query.isBlank()) {
                MarketListSection(state = state, viewModel = viewModel, onOpenStock = onOpenStock)
            } else {
                SearchResultSection(state = state, onOpenStock = onOpenStock)
            }
        }
    }
}

/** 検索語が空の時に表示する主要銘柄一覧（日本株/米国株） */
@Composable
private fun MarketListSection(
    state: SearchUiState,
    viewModel: SearchViewModel,
    onOpenStock: (symbol: String, name: String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Market.entries.forEach { market ->
                FilterChip(
                    selected = state.market == market,
                    onClick = { viewModel.selectMarket(market) },
                    label = { Text(market.label) },
                )
            }
        }
        IconButton(onClick = { viewModel.refreshMarket() }) {
            Icon(Icons.Filled.Refresh, contentDescription = "株価を更新")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MarketSort.entries.forEach { sort ->
            FilterChip(
                selected = state.sort == sort,
                onClick = { viewModel.selectSort(sort) },
                label = { Text(sort.label) },
            )
        }
    }
    Spacer(Modifier.height(4.dp))

    if (state.isLoadingMarket) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
    }

    val marketError = state.marketError
    if (marketError != null && state.marketRows.all { it.quote == null }) {
        Text(
            text = marketError,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.marketRows, key = { it.symbol }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenStock(row.symbol, row.name) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = row.symbol,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    val quote = row.quote
                    Text(
                        text = when {
                            quote == null -> "—"
                            // 指数はポイント表記（通貨記号なし）
                            row.symbol.startsWith("^") -> formatPrice(quote.price)
                            quote.currency == "USD" -> "$" + formatPrice(quote.price)
                            else -> formatPrice(quote.price) + "円"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(2.dp))
                    PriceChangeText(
                        change = quote?.change,
                        changePercent = quote?.changePercent,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

/** 検索語入力時の検索結果 */
@Composable
private fun SearchResultSection(
    state: SearchUiState,
    onOpenStock: (symbol: String, name: String) -> Unit,
) {
    if (state.isSearching) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }

    when {
        state.error != null -> {
            Text(
                text = state.error.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }

        state.hasSearched && state.results.isEmpty() -> {
            Text(
                text = "見つかりませんでした。\n（検索できるのは東証・米国市場の株式/ETFです）",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }

        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.results, key = { it.symbol }) { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenStock(result.symbol, result.name) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(result.name, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${result.symbol}・${result.exchange}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextSecondary,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

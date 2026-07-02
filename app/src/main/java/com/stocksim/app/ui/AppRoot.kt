package com.stocksim.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stocksim.app.ui.screens.HistoryScreen
import com.stocksim.app.ui.screens.HistoryViewModel
import com.stocksim.app.ui.screens.GameOverScreen
import com.stocksim.app.ui.screens.PortfolioScreen
import com.stocksim.app.ui.screens.PortfolioViewModel
import com.stocksim.app.ui.screens.SearchScreen
import com.stocksim.app.ui.screens.SearchViewModel
import com.stocksim.app.ui.screens.SetupScreen
import com.stocksim.app.ui.screens.StockDetailScreen
import com.stocksim.app.ui.screens.StockDetailViewModel

@Composable
fun AppRoot() {
    val container = appContainer()
    val appViewModel: AppViewModel = viewModel(
        factory = VmFactory { AppViewModel(container.repository) },
    )
    val phase by appViewModel.phase.collectAsStateWithLifecycle()

    when (val current = phase) {
        AppPhase.Loading -> Box(Modifier.fillMaxSize())
        AppPhase.Setup -> SetupScreen(onStart = appViewModel::startGame)
        is AppPhase.GameOver -> GameOverScreen(
            initialCapital = current.initialCapital,
            onRestart = appViewModel::resetGame,
        )
        AppPhase.Main -> MainScaffold(onReset = appViewModel::resetGame)
    }
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun MainScaffold(onReset: () -> Unit) {
    val container = appContainer()
    val navController = rememberNavController()
    val tabs = listOf(
        TabItem("portfolio", "資産", Icons.Filled.PieChart),
        TabItem("search", "検索", Icons.Filled.Search),
        TabItem("history", "履歴", Icons.Filled.ReceiptLong),
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || tabs.any { it.route == currentRoute }

    val openStock: (String, String) -> Unit = { symbol, name ->
        navController.navigate("detail/$symbol?name=${Uri.encode(name)}")
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "portfolio",
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable("portfolio") {
                val vm: PortfolioViewModel = viewModel(
                    factory = VmFactory { PortfolioViewModel(container.repository) },
                )
                PortfolioScreen(viewModel = vm, onOpenStock = openStock, onReset = onReset)
            }
            composable("search") {
                val vm: SearchViewModel = viewModel(
                    factory = VmFactory { SearchViewModel(container.repository) },
                )
                SearchScreen(viewModel = vm, onOpenStock = openStock)
            }
            composable("history") {
                val vm: HistoryViewModel = viewModel(
                    factory = VmFactory { HistoryViewModel(container.repository) },
                )
                HistoryScreen(viewModel = vm)
            }
            composable(
                route = "detail/{symbol}?name={name}",
                arguments = listOf(
                    navArgument("symbol") { type = NavType.StringType },
                    navArgument("name") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val symbol = entry.arguments?.getString("symbol").orEmpty()
                val name = entry.arguments?.getString("name").orEmpty()
                val vm: StockDetailViewModel = viewModel(
                    key = "detail-$symbol",
                    factory = VmFactory { StockDetailViewModel(container.repository, symbol, name) },
                )
                StockDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
        }
    }
}

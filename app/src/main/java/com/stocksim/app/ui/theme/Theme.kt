package com.stocksim.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 騰落カラー（日本の証券アプリの慣習：上昇=赤 / 下落=緑）
// dataviz バリデータ検証済み: CVD分離・ダーク面コントラストともにパス。
// 色だけに頼らず ▲▼ と符号を必ず併記する。
val UpRed = Color(0xFFF23645)
val DownGreen = Color(0xFF089981)

val AppBackground = Color(0xFF0E1116)
val AppSurface = Color(0xFF161B22)
val AppSurfaceVariant = Color(0xFF1E2530)
val AppPrimary = Color(0xFF5B9DFF)
val AppOnBackground = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8B949E)
val AppOutline = Color(0xFF30363D)

/** 損益の符号に応じた表示色。null・ゼロは中立色。 */
fun pnlColor(value: Double?): Color = when {
    value == null -> TextSecondary
    value > 0.0 -> UpRed
    value < 0.0 -> DownGreen
    else -> TextSecondary
}

private val DarkColors = darkColorScheme(
    primary = AppPrimary,
    onPrimary = Color(0xFF0B1220),
    secondary = TextSecondary,
    onSecondary = AppBackground,
    background = AppBackground,
    onBackground = AppOnBackground,
    surface = AppSurface,
    onSurface = AppOnBackground,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = AppSurface,
    surfaceContainerHigh = AppSurfaceVariant,
    surfaceContainerHighest = AppSurfaceVariant,
    surfaceContainerLow = AppSurface,
    outline = AppOutline,
    outlineVariant = AppOutline,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A0505),
)

@Composable
fun StockSimTheme(content: @Composable () -> Unit) {
    // トレーディングアプリらしさを優先し常時ダークテーマ
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}

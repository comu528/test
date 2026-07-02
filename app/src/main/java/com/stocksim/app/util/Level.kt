package com.stocksim.app.util

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 資産倍率ベースのレベル。
 *
 * Lv = round(1 + K * ln(総資産 / 初期資金)) を1以上にクランプする。
 * K=20.5 で 1.05倍→Lv2、1.10倍→Lv3、1.28倍→Lv6、2倍→Lv15、
 * 10倍→Lv48、100倍→Lv95 になる（資産が減ればレベルも下がる）。
 */
object Level {

    private const val K = 20.5

    fun levelFor(totalAssets: Double, initialCapital: Double): Int {
        if (initialCapital <= 0.0 || totalAssets <= 0.0) return 1
        val raw = 1.0 + K * ln(totalAssets / initialCapital)
        return max(1, raw.roundToInt())
    }

    /** このレベルに到達する最低倍率（丸めの下限しきい値）。Lv1は下限なし扱い。 */
    fun thresholdMultiplier(level: Int): Double =
        if (level <= 1) 0.0 else exp((level - 1.5) / K)

    /** 次のレベルに必要な総資産 */
    fun assetsForNextLevel(currentLevel: Int, initialCapital: Double): Double =
        initialCapital * thresholdMultiplier(currentLevel + 1)

    /** 現レベル内の進捗（0f〜1f）。プログレスバー表示用。 */
    fun progressToNext(totalAssets: Double, initialCapital: Double): Float {
        if (initialCapital <= 0.0 || totalAssets <= 0.0) return 0f
        val level = levelFor(totalAssets, initialCapital)
        val floor = initialCapital * thresholdMultiplier(level)
        val ceiling = assetsForNextLevel(level, initialCapital)
        if (ceiling <= floor) return 0f
        return ((totalAssets - floor) / (ceiling - floor)).toFloat().coerceIn(0f, 1f)
    }
}

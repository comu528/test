package com.stocksim.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** ゲーム全体の状態（1行のみ、id=1固定） */
@Entity(tableName = "portfolio")
data class PortfolioEntity(
    @PrimaryKey val id: Int = 1,
    val initialCapital: Double,
    val cash: Double,
    val gameOver: Boolean = false,
    val createdAt: Long,
)

/** 保有銘柄 */
@Entity(tableName = "holdings")
data class HoldingEntity(
    @PrimaryKey val symbol: String,
    val name: String,
    val quantity: Long,
    /** 平均取得単価（常に円建て。外貨銘柄は約定時レートで円換算して保存） */
    val averageCost: Double,
    /** 銘柄の取引通貨（"JPY" / "USD"） */
    val currency: String = "JPY",
)

/** 総資産の推移スナップショット（資産推移チャート用） */
@Entity(tableName = "asset_snapshots")
data class AssetSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val totalAssets: Double,
)

/** 取引履歴 */
@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val name: String,
    /** "BUY" or "SELL" */
    val side: String,
    val quantity: Long,
    val price: Double,
    val fee: Double,
    /** 売却時の実現損益（買いは null） */
    val realizedPnl: Double?,
    val timestamp: Long,
) {
    companion object {
        const val SIDE_BUY = "BUY"
        const val SIDE_SELL = "SELL"
    }
}

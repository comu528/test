package com.stocksim.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolio WHERE id = 1")
    fun observe(): Flow<PortfolioEntity?>

    @Query("SELECT * FROM portfolio WHERE id = 1")
    suspend fun get(): PortfolioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(portfolio: PortfolioEntity)

    /** 他カラムを触らずゲームオーバーだけ立てる（売買トランザクションと競合しても安全） */
    @Query("UPDATE portfolio SET gameOver = 1 WHERE id = 1")
    suspend fun setGameOver()

    @Query("DELETE FROM portfolio")
    suspend fun clear()
}

@Dao
interface HoldingDao {
    @Query("SELECT * FROM holdings ORDER BY symbol")
    fun observeAll(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings WHERE symbol = :symbol")
    fun observe(symbol: String): Flow<HoldingEntity?>

    @Query("SELECT * FROM holdings WHERE symbol = :symbol")
    suspend fun get(symbol: String): HoldingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(holding: HoldingEntity)

    @Query("DELETE FROM holdings WHERE symbol = :symbol")
    suspend fun delete(symbol: String)

    @Query("DELETE FROM holdings")
    suspend fun clear()
}

@Dao
interface AssetSnapshotDao {
    @Query("SELECT * FROM asset_snapshots ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<AssetSnapshotEntity>>

    @Query("SELECT * FROM asset_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): AssetSnapshotEntity?

    @Insert
    suspend fun insert(snapshot: AssetSnapshotEntity)

    /** 古いスナップショットを間引いて肥大化を防ぐ */
    @Query(
        "DELETE FROM asset_snapshots WHERE id NOT IN " +
            "(SELECT id FROM asset_snapshots ORDER BY timestamp DESC LIMIT 5000)"
    )
    suspend fun prune()

    @Query("DELETE FROM asset_snapshots")
    suspend fun clear()
}

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TradeEntity>>

    @Insert
    suspend fun insert(trade: TradeEntity)

    @Query("DELETE FROM trades")
    suspend fun clear()
}

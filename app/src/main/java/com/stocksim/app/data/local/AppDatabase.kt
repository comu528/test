package com.stocksim.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PortfolioEntity::class,
        HoldingEntity::class,
        TradeEntity::class,
        AssetSnapshotEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun portfolioDao(): PortfolioDao
    abstract fun holdingDao(): HoldingDao
    abstract fun tradeDao(): TradeDao
    abstract fun snapshotDao(): AssetSnapshotDao

    companion object {
        /** v2: 米国株対応（保有銘柄の通貨カラム）と資産推移スナップショット */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE holdings ADD COLUMN currency TEXT NOT NULL DEFAULT 'JPY'"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS asset_snapshots (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "timestamp INTEGER NOT NULL, " +
                        "totalAssets REAL NOT NULL)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "stocksim.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

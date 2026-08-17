package com.pirlruc.finsilo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AssetEntity::class,
        TransactionEntity::class,
        DailyMarketDataEntity::class,
        CurrencyRateEntity::class,
        TargetAllocationEntity::class,
        NavHistoryEntity::class,
        NavRebuildStateEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(FinsiloTypeConverters::class)
abstract class FinsiloDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao

    companion object {
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // v1 and v2 share the same tables; v2 was a Room version bump.
                }
            }

        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE assets ADD COLUMN isin TEXT")
                    db.execSQL("ALTER TABLE assets ADD COLUMN quote_symbol TEXT")
                }
            }

        val MIGRATION_3_4: Migration =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS nav_history (date TEXT NOT NULL, value_eur TEXT NOT NULL, PRIMARY KEY(date))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS nav_rebuild_state (" +
                            "id INTEGER NOT NULL, fingerprint TEXT NOT NULL, as_of TEXT NOT NULL, " +
                            "rebuilt_at_ms INTEGER NOT NULL, PRIMARY KEY(id))",
                    )
                }
            }
    }
}

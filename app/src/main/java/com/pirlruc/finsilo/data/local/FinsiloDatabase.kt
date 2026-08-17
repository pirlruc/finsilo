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
    ],
    version = 3,
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
    }
}

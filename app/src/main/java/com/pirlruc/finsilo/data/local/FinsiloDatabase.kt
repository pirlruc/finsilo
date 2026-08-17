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
    exportSchema = false,
)
@TypeConverters(FinsiloTypeConverters::class)
abstract class FinsiloDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao

    companion object {
        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE assets ADD COLUMN isin TEXT")
                    db.execSQL("ALTER TABLE assets ADD COLUMN quote_symbol TEXT")
                }
            }
    }
}

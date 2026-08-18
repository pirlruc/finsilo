package com.pirlruc.finsilo.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Encrypted Room database (SQLCipher). Schema JSON is exported under `app/schemas`.
 *
 * Additive changes from v2 onward are Room [AutoMigration]s so the generated
 * `ALTER`/`CREATE` SQL lives in kapt output (`build/`), not in scanned source.
 * v1 had no exported schema; [MIGRATION_1_2] is an empty version bump.
 */
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
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
@TypeConverters(FinsiloTypeConverters::class)
abstract class FinsiloDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao

    companion object {
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // v1 and v2 share the same tables; v2 was a Room version bump.
                    // No execSQL: there is nothing to rewrite, and v1 was never exported.
                }
            }
    }
}

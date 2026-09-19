package com.pirlruc.finsilo.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Encrypted Room database (SQLCipher). Schema JSON is exported under `app/schemas`.
 *
 * Additive changes from v2 onward are Room [AutoMigration]s so the generated
 * `ALTER`/`CREATE` SQL lives in KSP output (`build/`), not in scanned source.
 * v8 drops unread `nav_rebuild_state` columns via [DropUnreadNavRebuildColumns].
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
        PriceAlertThresholdEntity::class,
        LedgerTemplateEntity::class,
        WatchlistItemEntity::class,
        WatchlistQuoteEntity::class,
        RatingAlertEntity::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = FinsiloDatabase.DropUnreadNavRebuildColumns::class),
    ],
)
@TypeConverters(FinsiloTypeConverters::class)
abstract class FinsiloDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao

    @DeleteColumn.Entries(
        DeleteColumn(tableName = "nav_rebuild_state", columnName = "as_of"),
        DeleteColumn(tableName = "nav_rebuild_state", columnName = "rebuilt_at_ms"),
    )
    class DropUnreadNavRebuildColumns : AutoMigrationSpec

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

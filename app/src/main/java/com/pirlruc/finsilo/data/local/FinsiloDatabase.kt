package com.pirlruc.finsilo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AssetEntity::class,
        TransactionEntity::class,
        DailyMarketDataEntity::class,
        CurrencyRateEntity::class,
        TargetAllocationEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(FinsiloTypeConverters::class)
abstract class FinsiloDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao
}

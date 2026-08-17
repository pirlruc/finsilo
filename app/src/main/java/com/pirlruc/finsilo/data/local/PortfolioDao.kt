package com.pirlruc.finsilo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM assets")
    suspend fun getAssets(): List<AssetEntity>

    @Query("SELECT * FROM transactions")
    suspend fun getTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM daily_market_data")
    suspend fun getMarketData(): List<DailyMarketDataEntity>

    @Query("SELECT * FROM currency_history")
    suspend fun getFxRates(): List<CurrencyRateEntity>

    @Query("SELECT * FROM target_allocation")
    suspend fun getTargets(): List<TargetAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(items: List<AssetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(items: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarketData(items: List<DailyMarketDataEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFxRates(items: List<CurrencyRateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargets(items: List<TargetAllocationEntity>)

    @Query("DELETE FROM transactions")
    suspend fun deleteTransactions()

    @Query("DELETE FROM daily_market_data")
    suspend fun deleteMarketData()

    @Query("DELETE FROM currency_history")
    suspend fun deleteFxRates()

    @Query("DELETE FROM target_allocation")
    suspend fun deleteTargets()

    @Query("DELETE FROM assets")
    suspend fun deleteAssets()

    @Transaction
    suspend fun replaceTargets(items: List<TargetAllocationEntity>) {
        deleteTargets()
        if (items.isNotEmpty()) insertTargets(items)
    }

    @Transaction
    suspend fun replaceAll(
        assets: List<AssetEntity>,
        transactions: List<TransactionEntity>,
        market: List<DailyMarketDataEntity>,
        fx: List<CurrencyRateEntity>,
        targets: List<TargetAllocationEntity>,
    ) {
        deleteTransactions()
        deleteMarketData()
        deleteFxRates()
        deleteTargets()
        deleteAssets()
        insertAssets(assets)
        insertTransactions(transactions)
        insertMarketData(market)
        insertFxRates(fx)
        insertTargets(targets)
    }
}

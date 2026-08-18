package com.pirlruc.finsilo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import java.time.LocalDate

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM assets")
    suspend fun getAssets(): List<AssetEntity>

    @Query("SELECT * FROM transactions ORDER BY date ASC, ledger_sequence ASC, transaction_id ASC")
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

    @Query("SELECT COUNT(*) FROM currency_history WHERE date = :date")
    suspend fun countFxOn(date: LocalDate): Int

    @Query("SELECT * FROM nav_history ORDER BY date ASC")
    suspend fun getNavHistory(): List<NavHistoryEntity>

    @Query("SELECT * FROM nav_rebuild_state WHERE id = 1")
    suspend fun getNavRebuildState(): NavRebuildStateEntity?

    @Query("DELETE FROM nav_history")
    suspend fun deleteNavHistory()

    @Query("DELETE FROM nav_rebuild_state")
    suspend fun deleteNavRebuildState()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNavHistory(items: List<NavHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNavRebuildState(state: NavRebuildStateEntity)

    @Transaction
    suspend fun replaceNavHistory(items: List<NavHistoryEntity>, state: NavRebuildStateEntity) {
        deleteNavHistory()
        deleteNavRebuildState()
        if (items.isNotEmpty()) insertNavHistory(items)
        insertNavRebuildState(state)
    }

    @Transaction
    suspend fun upsertQuotes(market: List<DailyMarketDataEntity>, fx: List<CurrencyRateEntity>) {
        if (market.isNotEmpty()) insertMarketData(market)
        if (fx.isNotEmpty()) insertFxRates(fx)
    }

    @Transaction
    suspend fun insertLedgerEntry(asset: AssetEntity?, transaction: TransactionEntity, fx: CurrencyRateEntity?) {
        if (asset != null) insertAssets(listOf(asset))
        insertTransactions(listOf(transaction))
        if (fx != null && countFxOn(fx.date) == 0) {
            insertFxRates(listOf(fx))
        }
    }

    @Transaction
    suspend fun insertImported(assets: List<AssetEntity>, transactions: List<TransactionEntity>, fx: List<CurrencyRateEntity>) {
        if (assets.isNotEmpty()) insertAssets(assets)
        if (transactions.isNotEmpty()) insertTransactions(transactions)
        fx.forEach { rate ->
            if (countFxOn(rate.date) == 0) {
                insertFxRates(listOf(rate))
            }
        }
    }

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
        deleteNavHistory()
        deleteNavRebuildState()
        insertAssets(assets)
        insertTransactions(transactions)
        insertMarketData(market)
        insertFxRates(fx)
        insertTargets(targets)
    }
}

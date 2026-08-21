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

    @Query("SELECT * FROM price_alert_threshold")
    suspend fun getThresholds(): List<PriceAlertThresholdEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThresholds(items: List<PriceAlertThresholdEntity>)

    @Query("DELETE FROM price_alert_threshold WHERE asset_id = :assetId")
    suspend fun deleteThreshold(assetId: String)

    @Query("SELECT * FROM ledger_template")
    suspend fun getTemplates(): List<LedgerTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(items: List<LedgerTemplateEntity>)

    @Query("DELETE FROM ledger_template WHERE template_id = :id")
    suspend fun deleteTemplate(id: String)

    @Query("SELECT * FROM watchlist_item")
    suspend fun getWatchlistItems(): List<WatchlistItemEntity>

    @Query("SELECT * FROM watchlist_quote")
    suspend fun getWatchlistQuotes(): List<WatchlistQuoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlistItems(items: List<WatchlistItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlistQuotes(items: List<WatchlistQuoteEntity>)

    @Query("DELETE FROM watchlist_item WHERE item_id = :id")
    suspend fun deleteWatchlistItem(id: String)

    @Query("DELETE FROM watchlist_quote WHERE item_id = :id")
    suspend fun deleteWatchlistQuotes(id: String)

    @Query("DELETE FROM watchlist_quote")
    suspend fun deleteAllWatchlistQuotes()

    @Query("DELETE FROM watchlist_item")
    suspend fun deleteAllWatchlistItems()

    @Query("DELETE FROM ledger_template")
    suspend fun deleteAllTemplates()

    @Query("DELETE FROM price_alert_threshold")
    suspend fun deleteAllThresholds()

    @Query("SELECT * FROM rating_alert")
    suspend fun getRatingAlerts(): List<RatingAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRatingAlerts(items: List<RatingAlertEntity>)

    @Query("DELETE FROM rating_alert WHERE target_id = :targetId AND scope = :scope")
    suspend fun deleteRatingAlert(targetId: String, scope: String)

    @Query("DELETE FROM rating_alert")
    suspend fun deleteAllRatingAlerts()

    @Query("DELETE FROM rating_alert WHERE scope = :scope")
    suspend fun deleteRatingAlertsByScope(scope: String)

    @Query("DELETE FROM daily_market_data WHERE asset_id = :assetId")
    suspend fun deleteMarketDataForAsset(assetId: String)

    @Transaction
    suspend fun clearWatchlist() {
        deleteAllWatchlistQuotes()
        deleteAllWatchlistItems()
    }

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
}

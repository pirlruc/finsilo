package com.pirlruc.finsilo.data

import com.pirlruc.finsilo.data.local.LedgerTemplateEntity
import com.pirlruc.finsilo.data.local.PortfolioDao
import com.pirlruc.finsilo.data.local.PriceAlertThresholdEntity
import com.pirlruc.finsilo.data.local.RatingAlertEntity
import com.pirlruc.finsilo.data.local.WatchlistItemEntity
import com.pirlruc.finsilo.data.local.WatchlistQuoteEntity
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.RatingAlertScope
import com.pirlruc.finsilo.domain.model.WatchlistItem
import com.pirlruc.finsilo.domain.model.WatchlistSnapshot

internal object RoomPortfolioExtras {
    suspend fun loadThresholds(dao: PortfolioDao): List<PriceAlertThreshold> = dao.getThresholds().map { it.toDomain() }

    suspend fun saveThreshold(dao: PortfolioDao, threshold: PriceAlertThreshold) {
        if (threshold.isEmpty) {
            dao.deleteThreshold(threshold.assetId)
        } else {
            dao.insertThresholds(listOf(PriceAlertThresholdEntity.from(threshold)))
        }
    }

    suspend fun loadTemplates(dao: PortfolioDao): List<LedgerTemplate> = dao.getTemplates().map { it.toDomain() }

    suspend fun saveTemplate(dao: PortfolioDao, template: LedgerTemplate) {
        dao.insertTemplates(listOf(LedgerTemplateEntity.from(template)))
    }

    suspend fun loadWatchlist(dao: PortfolioDao): WatchlistSnapshot = WatchlistSnapshot(
        items = dao.getWatchlistItems().map { it.toDomain() },
        quotes = dao.getWatchlistQuotes().map { it.toDomain() },
    )

    suspend fun saveWatchlistItem(dao: PortfolioDao, item: WatchlistItem) {
        dao.insertWatchlistItems(listOf(WatchlistItemEntity.from(item)))
    }

    suspend fun deleteWatchlistItem(dao: PortfolioDao, id: String) {
        dao.deleteWatchlistQuotes(id)
        dao.deleteWatchlistItem(id)
        dao.deleteRatingAlert(id, RatingAlertScope.WATCHLIST.name)
    }

    suspend fun replaceWatchlistQuotes(dao: PortfolioDao, quotes: List<DailyMarketData>) {
        quotes.groupBy { it.assetId }.forEach { (id, rows) ->
            dao.deleteWatchlistQuotes(id)
            dao.insertWatchlistQuotes(rows.map(WatchlistQuoteEntity::from))
        }
    }

    suspend fun loadRatingAlerts(dao: PortfolioDao): List<RatingAlertPref> = dao.getRatingAlerts().map { it.toDomain() }

    suspend fun saveRatingAlert(dao: PortfolioDao, pref: RatingAlertPref) {
        dao.insertRatingAlerts(listOf(RatingAlertEntity.from(pref)))
    }
}

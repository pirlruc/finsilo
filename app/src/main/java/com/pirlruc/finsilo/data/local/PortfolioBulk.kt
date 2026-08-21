package com.pirlruc.finsilo.data.local

import androidx.room.withTransaction

internal suspend fun FinsiloDatabase.replaceAll(
    assets: List<AssetEntity>,
    transactions: List<TransactionEntity>,
    market: List<DailyMarketDataEntity>,
    fx: List<CurrencyRateEntity>,
    targets: List<TargetAllocationEntity>,
) {
    withTransaction {
        val dao = portfolioDao()
        dao.deleteTransactions()
        dao.deleteMarketData()
        dao.deleteFxRates()
        dao.deleteTargets()
        dao.deleteAssets()
        dao.deleteNavHistory()
        dao.deleteNavRebuildState()
        dao.insertAssets(assets)
        dao.insertTransactions(transactions)
        dao.insertMarketData(market)
        dao.insertFxRates(fx)
        dao.insertTargets(targets)
    }
}

internal suspend fun FinsiloDatabase.replaceExtras(
    templates: List<LedgerTemplateEntity>,
    watchlistItems: List<WatchlistItemEntity>,
    watchlistQuotes: List<WatchlistQuoteEntity>,
    thresholds: List<PriceAlertThresholdEntity>,
    ratingAlerts: List<RatingAlertEntity> = emptyList(),
) {
    withTransaction {
        val dao = portfolioDao()
        dao.deleteAllTemplates()
        dao.deleteAllWatchlistQuotes()
        dao.deleteAllWatchlistItems()
        dao.deleteAllThresholds()
        dao.deleteAllRatingAlerts()
        if (templates.isNotEmpty()) dao.insertTemplates(templates)
        if (watchlistItems.isNotEmpty()) dao.insertWatchlistItems(watchlistItems)
        if (watchlistQuotes.isNotEmpty()) dao.insertWatchlistQuotes(watchlistQuotes)
        if (thresholds.isNotEmpty()) dao.insertThresholds(thresholds)
        if (ratingAlerts.isNotEmpty()) dao.insertRatingAlerts(ratingAlerts)
    }
}

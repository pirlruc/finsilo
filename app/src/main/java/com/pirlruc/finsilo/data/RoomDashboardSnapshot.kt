package com.pirlruc.finsilo.data

import com.pirlruc.finsilo.data.local.PortfolioDao
import com.pirlruc.finsilo.domain.market.QuoteMerge
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.startDate
import java.time.LocalDate

/** Range-scoped quote load for the dashboard chip, plus the latest bar per asset. */
internal object RoomDashboardSnapshot {
    suspend fun load(dao: PortfolioDao, range: HistoryRange, asOf: LocalDate): PortfolioSnapshot {
        val transactions = dao.getTransactions().map { it.toDomain() }
        val firstTx = transactions.minOfOrNull { it.date } ?: asOf
        val from = range.startDate(asOf, firstTx)
        val ranged = dao.getMarketDataFrom(from).map { it.toDomain() }
        val latest = dao.getLatestMarketData().map { it.toDomain() }
        return PortfolioSnapshot(
            assets = dao.getAssets().map { it.toDomain() },
            transactions = transactions,
            marketData = QuoteMerge.plusLatest(ranged, latest),
            fxRates = dao.getFxRates().map { it.toDomain() },
            targets = dao.getTargets().map { it.toDomain() },
        )
    }
}

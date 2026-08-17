package com.pirlruc.finsilo.data

import com.pirlruc.finsilo.data.local.AssetEntity
import com.pirlruc.finsilo.data.local.CurrencyRateEntity
import com.pirlruc.finsilo.data.local.DailyMarketDataEntity
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.local.TargetAllocationEntity
import com.pirlruc.finsilo.data.local.TransactionEntity
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.repository.PortfolioReadRepository
import com.pirlruc.finsilo.domain.repository.SamplePortfolioWriter

class RoomPortfolioRepository(
    private val database: FinsiloDatabase,
) : PortfolioReadRepository, SamplePortfolioWriter {
    private val dao get() = database.portfolioDao()

    override suspend fun load(): PortfolioSnapshot =
        PortfolioSnapshot(
            assets = dao.getAssets().map { it.toDomain() },
            transactions = dao.getTransactions().map { it.toDomain() },
            marketData = dao.getMarketData().map { it.toDomain() },
            fxRates = dao.getFxRates().map { it.toDomain() },
            targets = dao.getTargets().map { it.toDomain() },
        )

    override suspend fun write(snapshot: PortfolioSnapshot) {
        dao.replaceAll(
            assets = snapshot.assets.map(AssetEntity::from),
            transactions = snapshot.transactions.map(TransactionEntity::from),
            market = snapshot.marketData.map(DailyMarketDataEntity::from),
            fx = snapshot.fxRates.map(CurrencyRateEntity::from),
            targets = snapshot.targets.map(TargetAllocationEntity::from),
        )
    }

    override suspend fun clear() {
        dao.replaceAll(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }

    suspend fun upsertQuotes(
        market: List<com.pirlruc.finsilo.domain.model.DailyMarketData>,
        fx: List<com.pirlruc.finsilo.domain.model.CurrencyRate>,
    ) {
        if (market.isNotEmpty()) {
            dao.insertMarketData(market.map(DailyMarketDataEntity::from))
        }
        if (fx.isNotEmpty()) {
            dao.insertFxRates(fx.map(CurrencyRateEntity::from))
        }
    }
}

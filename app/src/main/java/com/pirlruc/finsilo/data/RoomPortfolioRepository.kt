package com.pirlruc.finsilo.data

import com.pirlruc.finsilo.data.local.AssetEntity
import com.pirlruc.finsilo.data.local.CurrencyRateEntity
import com.pirlruc.finsilo.data.local.DailyMarketDataEntity
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.local.TargetAllocationEntity
import com.pirlruc.finsilo.data.local.TransactionEntity
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.repository.LedgerWriteRepository
import com.pirlruc.finsilo.domain.repository.PortfolioReadRepository
import com.pirlruc.finsilo.domain.repository.SamplePortfolioWriter

class RoomPortfolioRepository(private val database: FinsiloDatabase) :
    PortfolioReadRepository,
    SamplePortfolioWriter,
    LedgerWriteRepository {
    private val dao get() = database.portfolioDao()

    override suspend fun load(): PortfolioSnapshot = PortfolioSnapshot(
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

    override suspend fun upsertAsset(asset: Asset) {
        dao.insertAssets(listOf(AssetEntity.from(asset)))
    }

    override suspend fun insertTransaction(transaction: Transaction) {
        dao.insertTransactions(listOf(TransactionEntity.from(transaction)))
    }

    override suspend fun replaceTargets(targets: List<TargetAllocation>) {
        dao.replaceTargets(targets.map(TargetAllocationEntity::from))
    }

    override suspend fun upsertFxRate(rate: CurrencyRate) {
        dao.insertFxRates(listOf(CurrencyRateEntity.from(rate)))
    }

    override suspend fun saveLedgerEntry(asset: Asset?, transaction: Transaction, fxRate: CurrencyRate?) {
        dao.insertLedgerEntry(
            asset = asset?.let(AssetEntity::from),
            transaction = TransactionEntity.from(transaction),
            fx = fxRate?.let(CurrencyRateEntity::from),
        )
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

package com.pirlruc.finsilo.data

import com.pirlruc.finsilo.data.local.AssetEntity
import com.pirlruc.finsilo.data.local.CurrencyRateEntity
import com.pirlruc.finsilo.data.local.DailyMarketDataEntity
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.local.NavHistoryEntity
import com.pirlruc.finsilo.data.local.NavRebuildStateEntity
import com.pirlruc.finsilo.data.local.TargetAllocationEntity
import com.pirlruc.finsilo.data.local.TransactionEntity
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.repository.LedgerWriteRepository
import com.pirlruc.finsilo.domain.repository.PortfolioReadRepository
import com.pirlruc.finsilo.domain.repository.SamplePortfolioWriter
import com.pirlruc.finsilo.domain.usecase.RebuildNavHistoryUseCase
import java.time.LocalDate

class RoomPortfolioRepository(private val database: FinsiloDatabase) :
    PortfolioReadRepository,
    SamplePortfolioWriter,
    LedgerWriteRepository {
    private val dao get() = database.portfolioDao()
    private val rebuildNav = RebuildNavHistoryUseCase()

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
        rebuildNavHistoryIfNeeded(snapshot)
    }

    override suspend fun clear() {
        dao.replaceAll(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }

    override suspend fun upsertAsset(asset: Asset) {
        dao.insertAssets(listOf(AssetEntity.from(asset)))
        rebuildNavHistoryIfNeeded(load())
    }

    override suspend fun insertTransaction(transaction: Transaction) {
        dao.insertTransactions(listOf(TransactionEntity.from(transaction)))
        rebuildNavHistoryIfNeeded(load(), changedFrom = transaction.date)
    }

    override suspend fun replaceTargets(targets: List<TargetAllocation>) {
        dao.replaceTargets(targets.map(TargetAllocationEntity::from))
    }

    override suspend fun upsertFxRate(rate: CurrencyRate) {
        dao.insertFxRates(listOf(CurrencyRateEntity.from(rate)))
        rebuildNavHistoryIfNeeded(load(), changedFrom = rate.date)
    }

    override suspend fun saveLedgerEntry(asset: Asset?, transaction: Transaction, fxRate: CurrencyRate?) {
        dao.insertLedgerEntry(
            asset = asset?.let(AssetEntity::from),
            transaction = TransactionEntity.from(transaction),
            fx = fxRate?.let(CurrencyRateEntity::from),
        )
        rebuildNavHistoryIfNeeded(load(), changedFrom = transaction.date)
    }

    suspend fun upsertQuotes(market: List<DailyMarketData>, fx: List<CurrencyRate>) {
        dao.upsertQuotes(market.map(DailyMarketDataEntity::from), fx.map(CurrencyRateEntity::from))
        val changedFrom = (market.map { it.date } + fx.map { it.date }).minOrNull()
        rebuildNavHistoryIfNeeded(load(), changedFrom = changedFrom)
    }

    suspend fun loadNavHistory(): List<NavPoint> = dao.getNavHistory().map { it.toDomain() }

    suspend fun rebuildNavHistoryIfNeeded(snapshot: PortfolioSnapshot, asOf: LocalDate = LocalDate.now(), changedFrom: LocalDate? = null) {
        if (snapshot.isEmpty) return
        val stored = dao.getNavHistory().map { it.toDomain() }
        val decision = rebuildNav(snapshot, asOf, dao.getNavRebuildState()?.fingerprint, stored, changedFrom)
        if (decision.skip) return
        dao.replaceNavHistory(
            items = decision.points.map(NavHistoryEntity::from),
            state = NavRebuildStateEntity(
                fingerprint = decision.fingerprint,
                asOf = asOf,
                rebuiltAtMs = System.currentTimeMillis(),
            ),
        )
    }
}

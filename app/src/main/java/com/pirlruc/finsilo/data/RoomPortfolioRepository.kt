package com.pirlruc.finsilo.data

import com.pirlruc.finsilo.data.local.AssetEntity
import com.pirlruc.finsilo.data.local.CurrencyRateEntity
import com.pirlruc.finsilo.data.local.DailyMarketDataEntity
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.local.LedgerTemplateEntity
import com.pirlruc.finsilo.data.local.NavHistoryEntity
import com.pirlruc.finsilo.data.local.NavRebuildStateEntity
import com.pirlruc.finsilo.data.local.PriceAlertThresholdEntity
import com.pirlruc.finsilo.data.local.TargetAllocationEntity
import com.pirlruc.finsilo.data.local.TransactionEntity
import com.pirlruc.finsilo.data.local.WatchlistItemEntity
import com.pirlruc.finsilo.data.local.WatchlistQuoteEntity
import com.pirlruc.finsilo.data.sync.WidgetNavCache
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.WatchlistItem
import com.pirlruc.finsilo.domain.model.WatchlistSnapshot
import com.pirlruc.finsilo.domain.repository.LedgerWriteRepository
import com.pirlruc.finsilo.domain.repository.PortfolioReadRepository
import com.pirlruc.finsilo.domain.repository.SamplePortfolioWriter
import com.pirlruc.finsilo.domain.usecase.BackfillLedgerSequenceUseCase
import com.pirlruc.finsilo.domain.usecase.RebuildNavHistoryUseCase
import java.time.LocalDate

class RoomPortfolioRepository(private val database: FinsiloDatabase, private val widgetNav: WidgetNavCache? = null) :
    PortfolioReadRepository,
    SamplePortfolioWriter,
    LedgerWriteRepository {
    private val dao get() = database.portfolioDao()
    private val rebuildNav = RebuildNavHistoryUseCase()
    private val backfillSequence = BackfillLedgerSequenceUseCase()

    override suspend fun load(): PortfolioSnapshot {
        val snapshot = rawLoad()
        val filled = backfillSequence(snapshot.transactions)
        if (sameSequences(snapshot.transactions, filled)) return snapshot
        dao.insertTransactions(filled.map(TransactionEntity::from))
        return snapshot.copy(transactions = filled)
    }

    private suspend fun rawLoad(): PortfolioSnapshot = PortfolioSnapshot(
        assets = dao.getAssets().map { it.toDomain() },
        transactions = dao.getTransactions().map { it.toDomain() },
        marketData = dao.getMarketData().map { it.toDomain() },
        fxRates = dao.getFxRates().map { it.toDomain() },
        targets = dao.getTargets().map { it.toDomain() },
    )

    private fun sameSequences(original: List<Transaction>, filled: List<Transaction>): Boolean {
        if (original.size != filled.size) return false
        val byId = original.associate { it.id to it.sequence }
        return filled.all { byId[it.id] == it.sequence }
    }

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

    /**
     * Persist CSV import rows without wiping quotes that a concurrent sync may have written.
     * [write] is a full snapshot replace and is reserved for sample load/clear.
     */
    suspend fun persistImport(before: PortfolioSnapshot, after: PortfolioSnapshot) {
        val newAssets = after.assets.filter { incoming -> before.assets.none { it.id == incoming.id } }
        val newTx = after.transactions.filter { incoming -> before.transactions.none { it.id == incoming.id } }
        val newFx = after.fxRates.filter { incoming -> before.fxRates.none { it.date == incoming.date } }
        if (newAssets.isEmpty() && newTx.isEmpty() && newFx.isEmpty()) return
        dao.insertImported(
            newAssets.map(AssetEntity::from),
            newTx.map(TransactionEntity::from),
            newFx.map(CurrencyRateEntity::from),
        )
        val changedFrom = newTx.minOfOrNull { it.date } ?: newFx.minOfOrNull { it.date }
        rebuildNavHistoryIfNeeded(load(), changedFrom = changedFrom)
    }

    override suspend fun clear() {
        dao.replaceAll(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        widgetNav?.write(null)
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
        if (decision.skip) {
            widgetNav?.write(decision.points.maxByOrNull { it.date })
            return
        }
        dao.replaceNavHistory(
            items = decision.points.map(NavHistoryEntity::from),
            state = NavRebuildStateEntity(
                fingerprint = decision.fingerprint,
                asOf = asOf,
                rebuiltAtMs = System.currentTimeMillis(),
            ),
        )
        widgetNav?.write(decision.points.maxByOrNull { it.date })
    }

    suspend fun loadThresholds(): List<PriceAlertThreshold> = dao.getThresholds().map { it.toDomain() }

    suspend fun saveThreshold(threshold: PriceAlertThreshold) {
        if (threshold.isEmpty) {
            dao.deleteThreshold(threshold.assetId)
        } else {
            dao.insertThresholds(listOf(PriceAlertThresholdEntity.from(threshold)))
        }
    }

    suspend fun loadTemplates(): List<LedgerTemplate> = dao.getTemplates().map { it.toDomain() }

    suspend fun saveTemplate(template: LedgerTemplate) {
        dao.insertTemplates(listOf(LedgerTemplateEntity.from(template)))
    }

    suspend fun deleteTemplate(id: String) {
        dao.deleteTemplate(id)
    }

    suspend fun loadWatchlist(): WatchlistSnapshot = WatchlistSnapshot(
        items = dao.getWatchlistItems().map { it.toDomain() },
        quotes = dao.getWatchlistQuotes().map { it.toDomain() },
    )

    suspend fun saveWatchlistItem(item: WatchlistItem) {
        dao.insertWatchlistItems(listOf(WatchlistItemEntity.from(item)))
    }

    suspend fun deleteWatchlistItem(id: String) {
        dao.deleteWatchlistQuotes(id)
        dao.deleteWatchlistItem(id)
    }

    suspend fun replaceWatchlistQuotes(quotes: List<DailyMarketData>) {
        quotes.groupBy { it.assetId }.forEach { (id, rows) ->
            dao.deleteWatchlistQuotes(id)
            dao.insertWatchlistQuotes(rows.map(WatchlistQuoteEntity::from))
        }
    }

    suspend fun lastNavPoint(): NavPoint? = dao.getNavHistory().maxByOrNull { it.date }?.toDomain()
}

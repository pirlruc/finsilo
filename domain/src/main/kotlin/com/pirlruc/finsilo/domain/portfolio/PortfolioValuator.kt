package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.market.QuoteCurrency
import com.pirlruc.finsilo.domain.model.AllocationReport
import com.pirlruc.finsilo.domain.model.AllocationSlice
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.ZERO
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.minus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.percentOf
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.times
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.toEur
import java.math.BigDecimal
import java.time.LocalDate

/** Marks holdings to market and builds allocation slices. */
class PortfolioValuator(private val ledger: PositionLedger = PositionLedger()) {
    private var boundSnapshot: PortfolioSnapshot? = null
    private val navCache = HashMap<LocalDate, BigDecimal>()

    fun valueHoldings(snapshot: PortfolioSnapshot, asOf: LocalDate): List<HoldingValuation> {
        val txsByAsset = ledger.transactionsOnOrBefore(snapshot.transactions, asOf).groupBy { it.assetId }
        val marketByAsset = ledger.indexMarket(snapshot.marketData)
        val eurPerUsd = ledger.eurPerUsdOn(asOf, snapshot.fxRates)

        return snapshot.assets.mapNotNull { asset ->
            val txs = txsByAsset[asset.id].orEmpty()
            if (txs.isEmpty()) return@mapNotNull null
            if (asset.locallyValued) {
                val value = ledger.locallyValuedEur(txs)
                if (value.signum() == 0) return@mapNotNull null
                val cost = costOfLocalInstrument(txs)
                HoldingValuation(
                    asset = asset,
                    quantity = BigDecimal.ONE,
                    priceEur = value,
                    valueEur = value,
                    costEur = cost,
                    unrealizedPnlEur = minus(value, cost),
                )
            } else {
                marketableHolding(asset, txs, asOf, marketByAsset, eurPerUsd)
            }
        }
    }

    fun missingUsdFx(snapshot: PortfolioSnapshot): Boolean =
        snapshot.fxRates.isEmpty() && snapshot.assets.any { QuoteCurrency.needsUsdFx(it) }

    /** User-visible reasons holdings were left out of NAV. */
    fun valuationWarnings(snapshot: PortfolioSnapshot, asOf: LocalDate): List<String> {
        val warnings = ArrayList<String>(2)
        if (missingUsdFx(snapshot)) {
            warnings += "USD market quotes need an FX rate (EUR per 1 USD) before they can be valued."
        }
        val omitted = unpricedSymbols(snapshot, asOf)
        if (omitted.isNotEmpty()) {
            warnings += "No market quote for ${omitted.joinToString(", ")}; NAV uses last trade price or omits the holding."
        }
        return warnings
    }

    /** Marketable holdings with quantity but no daily bar on/before [asOf]. */
    fun unpricedSymbols(snapshot: PortfolioSnapshot, asOf: LocalDate): List<String> {
        val marketByAsset = ledger.indexMarket(snapshot.marketData)
        val txsByAsset = ledger.transactionsOnOrBefore(snapshot.transactions, asOf).groupBy { it.assetId }
        val skipUsd = missingUsdFx(snapshot)
        return snapshot.assets.mapNotNull { asset ->
            if (asset.locallyValued || asset.assetType == AssetType.CASH) return@mapNotNull null
            if (skipUsd && QuoteCurrency.needsUsdFx(asset)) return@mapNotNull null
            val qty = ledger.position(txsByAsset[asset.id].orEmpty()).quantity
            if (qty.signum() == 0) return@mapNotNull null
            if (ledger.marketOnOrBefore(asset.id, asOf, marketByAsset) != null) null else asset.symbol
        }
    }

    private fun marketableHolding(
        asset: Asset,
        txs: List<Transaction>,
        asOf: LocalDate,
        marketByAsset: Map<String, List<DailyMarketData>>,
        eurPerUsd: BigDecimal?,
    ): HoldingValuation? {
        val lot = ledger.position(txs)
        if (lot.quantity.signum() == 0) return null
        val priceEur = pricedInEur(asset, asOf, marketByAsset, txs, eurPerUsd) ?: return null
        val value = times(lot.quantity, priceEur)
        return HoldingValuation(
            asset = asset,
            quantity = lot.quantity,
            priceEur = priceEur,
            valueEur = value,
            costEur = lot.remainingCostEur,
            unrealizedPnlEur = minus(value, lot.remainingCostEur),
        )
    }

    /**
     * Live USD feeds (crypto, commodities, US listings) convert with EUR-per-USD
     * even when the instrument is booked in EUR. Last-trade fallback stays in the
     * booking currency.
     */
    private fun pricedInEur(
        asset: Asset,
        asOf: LocalDate,
        marketByAsset: Map<String, List<DailyMarketData>>,
        txs: List<Transaction>,
        eurPerUsd: BigDecimal?,
    ): BigDecimal? {
        val market = ledger.marketOnOrBefore(asset.id, asOf, marketByAsset)
        val native = ledger.nativePrice(asset.id, asOf, marketByAsset, txs) ?: return null
        val currency = if (market != null) QuoteCurrency.of(asset) else asset.baseCurrency
        val rate = if (currency == Currency.EUR) BigDecimal.ONE else eurPerUsd
        return rate?.let { toEur(native, currency, it) }
    }

    fun cashEur(snapshot: PortfolioSnapshot, asOf: LocalDate): BigDecimal {
        val assetsById = snapshot.assets.associateBy { it.id }
        val txs = ledger.transactionsOnOrBefore(snapshot.transactions, asOf)
        return ledger.cashEur(txs, assetsById)
    }

    fun totalNavEur(snapshot: PortfolioSnapshot, asOf: LocalDate): BigDecimal {
        if (boundSnapshot !== snapshot) {
            navCache.clear()
            boundSnapshot = snapshot
        }
        return navCache.getOrPut(asOf) {
            val holdings = valueHoldings(snapshot, asOf)
            val cash = cashEur(snapshot, asOf)
            holdings.fold(cash) { acc, holding -> plus(acc, holding.valueEur) }
        }
    }

    fun allocation(snapshot: PortfolioSnapshot, asOf: LocalDate): AllocationReport {
        val holdings = valueHoldings(snapshot, asOf)
        val cash = cashEur(snapshot, asOf)
        val holdingTotal = holdings.fold(ZERO) { acc, h -> plus(acc, h.valueEur) }
        val total = plus(holdingTotal, cash)
        val targets = snapshot.targets.associate { it.assetType to it.weightPercent }

        val byType = LinkedHashMap<AssetType, BigDecimal>()
        for (holding in holdings) {
            val type = holding.asset.assetType
            byType[type] = plus(byType[type] ?: ZERO, holding.valueEur)
        }
        if (cash.signum() > 0) {
            byType[AssetType.CASH] = plus(byType[AssetType.CASH] ?: ZERO, cash)
        }

        val slices =
            byType.entries
                .sortedByDescending { it.value }
                .map { (type, value) ->
                    val weight = percentOf(value, total)
                    val target = targets[type]
                    AllocationSlice(
                        assetType = type,
                        valueEur = value,
                        weightPercent = weight,
                        targetPercent = target,
                        driftPercent = target?.let { minus(weight, it) },
                    )
                }

        val unrealized = holdings.fold(ZERO) { acc, h -> plus(acc, h.unrealizedPnlEur) }
        return AllocationReport(
            asOf = asOf,
            totalValueEur = total,
            unrealizedPnlEur = unrealized,
            cashEur = cash,
            slices = slices,
            holdings = holdings.sortedByDescending { it.valueEur },
        )
    }

    private fun costOfLocalInstrument(transactions: List<Transaction>): BigDecimal {
        var cost = ZERO
        for (tx in transactions) {
            cost =
                when (tx.type) {
                    TransactionType.BUY ->
                        plus(cost, plus(tx.notionalEur, tx.feesEur))
                    TransactionType.SELL -> {
                        // Withdrawals reduce remaining principal cost, not below zero.
                        val reduced = minus(cost, tx.notionalEur)
                        if (reduced.signum() < 0) ZERO else reduced
                    }
                    else -> cost
                }
        }
        return cost
    }

    fun syntheticCashAsset(): Asset = Asset(
        id = CASH_ASSET_ID,
        symbol = "EUR",
        name = "Cash",
        assetType = AssetType.CASH,
        baseCurrency = Currency.EUR,
    )

    companion object {
        const val CASH_ASSET_ID: String = "cash"
        val DRIFT_BAND_PERCENT: BigDecimal = BigDecimal("5")
    }
}

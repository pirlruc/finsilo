package com.pirlruc.finsilo.domain.portfolio

import com.pirlruc.finsilo.domain.market.QuoteCurrency
import com.pirlruc.finsilo.domain.model.AllocationReport
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.HoldingValuation
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.plus
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
            holdingFor(asset, txs, asOf, marketByAsset, eurPerUsd)
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
        return AllocationComposer.compose(snapshot, asOf, holdings, cash)
    }

    fun syntheticCashAsset(): Asset = Asset(
        id = CASH_ASSET_ID,
        symbol = "EUR",
        name = "Cash",
        assetType = AssetType.CASH,
        baseCurrency = Currency.EUR,
    )

    private fun holdingFor(
        asset: Asset,
        txs: List<Transaction>,
        asOf: LocalDate,
        marketByAsset: Map<String, List<DailyMarketData>>,
        eurPerUsd: BigDecimal?,
    ): HoldingValuation? = if (asset.locallyValued) {
        LocalInstrumentValuator.holding(ledger, asset, txs, asOf, marketByAsset, eurPerUsd)
    } else {
        MarketInstrumentValuator.holding(ledger, asset, txs, asOf, marketByAsset, eurPerUsd)
    }

    companion object {
        const val CASH_ASSET_ID: String = "cash"
        val DRIFT_BAND_PERCENT: BigDecimal = BigDecimal("5")
    }
}

package com.pirlruc.finsilo.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class Asset(
    val id: String,
    val symbol: String,
    val name: String,
    val assetType: AssetType,
    val baseCurrency: Currency,
    /** ISO 6166 ISIN when the user has one (typical for PPR / EU funds). */
    val isin: String? = null,
    /**
     * Listed ticker used for GET-only quotes. Display [symbol] may be an ISIN
     * or a local name; sync uses this when present.
     */
    val quoteSymbol: String? = null,
) {
    val feedSymbol: String
        get() = quoteSymbol?.takeIf { it.isNotBlank() } ?: symbol

    /**
     * Deposits, CTs, and unlisted PPR have no market feed. NAV is principal
     * plus interest. A PPR with a listed [quoteSymbol] (or an exchange suffix
     * on [feedSymbol]) is mark-to-market like an ETF.
     */
    val locallyValued: Boolean
        get() = assetType.isLocallyValued || isUnlistedPpr

    val isUnlistedPpr: Boolean
        get() =
            assetType == AssetType.PPR &&
                quoteSymbol.isNullOrBlank() &&
                '.' !in feedSymbol
}

data class Transaction(
    val id: String,
    val assetId: String,
    val date: LocalDate,
    val type: TransactionType,
    val quantity: BigDecimal,
    val unitPriceNative: BigDecimal,
    /**
     * EUR per 1 USD at execution (e.g. 0.92 means 1 USD = 0.92 EUR).
     * EUR cash = native USD * this rate. Always 1 for EUR-denominated rows.
     */
    val exchangeRateAtExecution: BigDecimal,
    val unitPriceEur: BigDecimal,
    val feesEur: BigDecimal,
) {
    val notionalEur: BigDecimal get() = quantity.multiply(unitPriceEur)
}

data class DailyMarketData(
    val assetId: String,
    val date: LocalDate,
    val closingPriceNative: BigDecimal,
    val analystRating: AnalystRating = AnalystRating.NONE,
    val sma50: BigDecimal? = null,
    val sma200: BigDecimal? = null,
)

data class CurrencyRate(
    val date: LocalDate,
    val eurPerUsd: BigDecimal,
)

data class TargetAllocation(
    val assetType: AssetType,
    val weightPercent: BigDecimal,
)

data class PortfolioSnapshot(
    val assets: List<Asset>,
    val transactions: List<Transaction>,
    val marketData: List<DailyMarketData>,
    val fxRates: List<CurrencyRate>,
    val targets: List<TargetAllocation>,
) {
    val isEmpty: Boolean get() = assets.isEmpty() && transactions.isEmpty()
}

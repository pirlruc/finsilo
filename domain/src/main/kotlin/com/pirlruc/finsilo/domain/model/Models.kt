package com.pirlruc.finsilo.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class Asset(
    val id: String,
    val symbol: String,
    val name: String,
    val assetType: AssetType,
    val baseCurrency: Currency,
)

data class Transaction(
    val id: String,
    val assetId: String,
    val date: LocalDate,
    val type: TransactionType,
    val quantity: BigDecimal,
    val unitPriceNative: BigDecimal,
    /**
     * USD per 1 EUR at execution (e.g. 1.10 means 1 EUR = 1.10 USD).
     * EUR cash = native USD / this rate. Always 1 for EUR-denominated rows.
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
    val usdPerEur: BigDecimal,
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

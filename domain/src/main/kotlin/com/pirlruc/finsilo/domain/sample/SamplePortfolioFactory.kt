package com.pirlruc.finsilo.domain.sample

import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.toEur
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.random.Random

/**
 * Deterministic demo portfolio so the dashboard is reviewable without live APIs (Phase 3).
 * Prices are synthetic; they are not market data.
 */
object SamplePortfolioFactory {
    const val APPLE_ID: String = "asset-aapl"
    const val VWCE_ID: String = "asset-vwce"
    const val BTC_ID: String = "asset-btc"
    const val PPR_ID: String = "asset-ppr"
    const val CT_ID: String = "asset-ct"
    const val DEPOSIT_ID: String = "asset-deposit"
    const val GOLD_ID: String = "asset-gold"
    const val CASH_ID: String = "asset-cash"

    fun create(asOf: LocalDate = LocalDate.of(2026, 8, 16)): PortfolioSnapshot {
        val start = asOf.minusDays(180)
        val assets = assets()
        val fx = fxHistory(start, asOf)
        val transactions = transactions(start, fx)
        val market = marketHistory(start, asOf)
        return PortfolioSnapshot(
            assets = assets,
            transactions = transactions,
            marketData = market,
            fxRates = fx,
            targets = targets(),
        )
    }

    fun assets(): List<Asset> =
        listOf(
            Asset(APPLE_ID, "AAPL", "Apple Inc.", AssetType.STOCK, Currency.USD),
            Asset(VWCE_ID, "VWCE.DE", "Vanguard FTSE All-World", AssetType.ETF, Currency.EUR),
            Asset(BTC_ID, "BTC", "Bitcoin", AssetType.CRYPTO, Currency.USD),
            Asset(
                PPR_ID,
                "PTYAAAA00001",
                "PPR Moderado",
                AssetType.PPR,
                Currency.EUR,
                isin = "PTYAAAA00001",
                quoteSymbol = null,
            ),
            Asset(CT_ID, "CT-POUPANCA", "Certificados de Tesouro Poupança", AssetType.CT, Currency.EUR),
            Asset(DEPOSIT_ID, "DEP-CGD", "Depósito a prazo", AssetType.DEPOSIT, Currency.EUR),
            Asset(GOLD_ID, "XAU", "Gold (spot)", AssetType.COMMODITY, Currency.USD),
            Asset(CASH_ID, "EUR-CASH", "Euro cash", AssetType.CASH, Currency.EUR),
        )

    fun targets(): List<TargetAllocation> =
        listOf(
            TargetAllocation(AssetType.ETF, bd("40")),
            TargetAllocation(AssetType.STOCK, bd("20")),
            TargetAllocation(AssetType.CRYPTO, bd("10")),
            TargetAllocation(AssetType.PPR, bd("15")),
            TargetAllocation(AssetType.CT, bd("6")),
            TargetAllocation(AssetType.DEPOSIT, bd("4")),
            TargetAllocation(AssetType.COMMODITY, bd("3")),
            TargetAllocation(AssetType.CASH, bd("2")),
        )

    private fun eurPerUsdOn(date: LocalDate, fx: List<CurrencyRate>): BigDecimal =
        fx.filter { !it.date.isAfter(date) }.maxByOrNull { it.date }?.eurPerUsd ?: bd("0.92")

    private fun fxHistory(start: LocalDate, asOf: LocalDate): List<CurrencyRate> {
        val random = Random(7)
        val rates = ArrayList<CurrencyRate>()
        var rate = bd("0.922")
        var date = start
        while (!date.isAfter(asOf)) {
            val tick = (random.nextDouble() - 0.48) * 0.003
            rate = rate.add(BigDecimal.valueOf(tick), MoneyMath.CONTEXT).max(bd("0.88")).min(bd("0.98"))
            rates += CurrencyRate(date, rate)
            date = date.plusDays(1)
        }
        return rates
    }

    private fun transactions(start: LocalDate, fx: List<CurrencyRate>): List<Transaction> {
        val fundingDate = start.plusDays(1)
        val fundingRate = eurPerUsdOn(fundingDate, fx)
        return listOf(
            cashDeposit("tx-cash-1", fundingDate, bd("25000"), fundingRate),
            buy("tx-aapl-1", APPLE_ID, start.plusDays(3), bd("15"), bd("185.40"), Currency.USD, fx),
            buy("tx-vwce-1", VWCE_ID, start.plusDays(4), bd("80"), bd("118.25"), Currency.EUR, fx),
            buy("tx-btc-1", BTC_ID, start.plusDays(10), bd("0.08"), bd("61200"), Currency.USD, fx),
            buy("tx-ppr-1", PPR_ID, start.plusDays(12), bd("40"), bd("12.50"), Currency.EUR, fx),
            buy("tx-ct-1", CT_ID, start.plusDays(15), bd("3000"), bd("1"), Currency.EUR, fx),
            buy("tx-dep-1", DEPOSIT_ID, start.plusDays(16), bd("2000"), bd("1"), Currency.EUR, fx),
            buy("tx-gold-1", GOLD_ID, start.plusDays(20), bd("1.5"), bd("2300"), Currency.USD, fx),
            Transaction(
                id = "tx-ppr-int-1",
                assetId = PPR_ID,
                date = start.plusDays(80),
                type = TransactionType.INTEREST,
                quantity = bd("1"),
                unitPriceNative = bd("15"),
                exchangeRateAtExecution = BigDecimal.ONE,
                unitPriceEur = bd("15"),
                feesEur = BigDecimal.ZERO,
            ),
            Transaction(
                id = "tx-ct-int-1",
                assetId = CT_ID,
                date = start.plusDays(100),
                type = TransactionType.INTEREST,
                quantity = bd("1"),
                unitPriceNative = bd("28.50"),
                exchangeRateAtExecution = BigDecimal.ONE,
                unitPriceEur = bd("28.50"),
                feesEur = BigDecimal.ZERO,
            ),
            appleDividend("tx-aapl-div-1", start.plusDays(20), fx),
            appleDividend("tx-aapl-div-2", start.plusDays(90), fx),
            appleDividend("tx-aapl-div-3", start.plusDays(160), fx),
        )
    }

    private fun appleDividend(id: String, date: LocalDate, fx: List<CurrencyRate>): Transaction {
        val rate = eurPerUsdOn(date, fx)
        return Transaction(
            id = id,
            assetId = APPLE_ID,
            date = date,
            type = TransactionType.DIVIDEND,
            quantity = bd("15"),
            unitPriceNative = bd("0.25"),
            exchangeRateAtExecution = rate,
            unitPriceEur = toEur(bd("0.25"), Currency.USD, rate),
            feesEur = BigDecimal.ZERO,
        )
    }

    private fun cashDeposit(id: String, date: LocalDate, amountEur: BigDecimal, eurPerUsd: BigDecimal): Transaction =
        Transaction(
            id = id,
            assetId = CASH_ID,
            date = date,
            type = TransactionType.DEPOSIT_CASH,
            quantity = amountEur,
            unitPriceNative = BigDecimal.ONE,
            exchangeRateAtExecution = eurPerUsd,
            unitPriceEur = BigDecimal.ONE,
            feesEur = BigDecimal.ZERO,
        )

    private fun buy(
        id: String,
        assetId: String,
        date: LocalDate,
        quantity: BigDecimal,
        nativePrice: BigDecimal,
        currency: Currency,
        fx: List<CurrencyRate>,
    ): Transaction {
        val rate = if (currency == Currency.EUR) BigDecimal.ONE else eurPerUsdOn(date, fx)
        val eur = toEur(nativePrice, currency, rate)
        return Transaction(
            id = id,
            assetId = assetId,
            date = date,
            type = TransactionType.BUY,
            quantity = quantity,
            unitPriceNative = nativePrice,
            exchangeRateAtExecution = rate,
            unitPriceEur = eur,
            feesEur = bd("1.50"),
        )
    }

    private fun marketHistory(start: LocalDate, asOf: LocalDate): List<DailyMarketData> {
        val apple = walk(start, asOf, bd("186"), 0.012, 11, ratingsAround(asOf, AnalystRating.BUY, AnalystRating.HOLD))
        val vwce = walk(start, asOf, bd("118.50"), 0.006, 22, ratingsAround(asOf, AnalystRating.HOLD, AnalystRating.HOLD))
        val btc = walk(start, asOf, bd("62000"), 0.025, 33, ratingsAround(asOf, AnalystRating.NONE, AnalystRating.NONE))
        val gold = walk(start, asOf, bd("2320"), 0.008, 55, ratingsAround(asOf, AnalystRating.NONE, AnalystRating.NONE))

        return buildList {
            addAll(withSma(APPLE_ID, apple, goldenCrossNearEnd = true))
            addAll(withSma(VWCE_ID, vwce, goldenCrossNearEnd = false))
            addAll(withSma(BTC_ID, btc, goldenCrossNearEnd = false))
            addAll(withSma(GOLD_ID, gold, goldenCrossNearEnd = false))
        }
    }

    private data class PriceBar(val date: LocalDate, val close: BigDecimal, val rating: AnalystRating)

    private fun walk(
        start: LocalDate,
        asOf: LocalDate,
        startPrice: BigDecimal,
        volatility: Double,
        seed: Int,
        ratingForDate: (LocalDate) -> AnalystRating,
    ): List<PriceBar> {
        val random = Random(seed)
        val bars = ArrayList<PriceBar>()
        var price = startPrice
        var date = start
        while (!date.isAfter(asOf)) {
            val tick = (random.nextDouble() - 0.47) * volatility
            val factor = BigDecimal.ONE.add(BigDecimal.valueOf(tick), MoneyMath.CONTEXT)
            price = MoneyMath.max(price.multiply(factor, MoneyMath.CONTEXT), bd("0.01"))
            bars += PriceBar(date, price, ratingForDate(date))
            date = date.plusDays(1)
        }
        return bars
    }

    private fun ratingsAround(
        asOf: LocalDate,
        earlier: AnalystRating,
        later: AnalystRating,
    ): (LocalDate) -> AnalystRating {
        val changeOn = asOf.minusDays(2)
        return { date -> if (date.isBefore(changeOn)) earlier else later }
    }

    private fun withSma(
        assetId: String,
        bars: List<PriceBar>,
        goldenCrossNearEnd: Boolean,
    ): List<DailyMarketData> {
        val closes = bars.map { it.close }
        return bars.mapIndexed { index, bar ->
            val sma50 = sma(closes, index, 50)
            val sma200Raw = sma(closes, index, 200) ?: sma(closes, index, 80)
            val sma200 =
                if (goldenCrossNearEnd && index >= bars.lastIndex - 1 && sma50 != null && sma200Raw != null) {
                    // Force a golden cross on the last bar for dashboard demo of Phase 5 signals.
                    if (index == bars.lastIndex) MoneyMath.minus(sma50, bd("0.50")) else MoneyMath.plus(sma50, bd("0.50"))
                } else {
                    sma200Raw
                }
            DailyMarketData(
                assetId = assetId,
                date = bar.date,
                closingPriceNative = bar.close,
                analystRating = bar.rating,
                sma50 = sma50,
                sma200 = sma200,
            )
        }
    }

    private fun sma(closes: List<BigDecimal>, index: Int, window: Int): BigDecimal? {
        if (index + 1 < window) return null
        val slice = closes.subList(index + 1 - window, index + 1)
        val sum = slice.fold(BigDecimal.ZERO) { acc, value -> acc.add(value, MoneyMath.CONTEXT) }
        return MoneyMath.div(sum, bd(window))
    }
}

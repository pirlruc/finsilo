package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.market.AlphaVantageParser
import com.pirlruc.finsilo.domain.market.CoinGeckoParser
import com.pirlruc.finsilo.domain.market.FrankfurterParser
import com.pirlruc.finsilo.domain.market.ListedQuoteRouting
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.StooqParser
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoverageBranchesTest {
    private val asOf = LocalDate.of(2026, 8, 16)
    private val cash = Asset("cash", "EUR", "Cash", AssetType.CASH, Currency.EUR)
    private val etf = Asset("vwce", "VWCE.DE", "ETF", AssetType.ETF, Currency.EUR)

    @Test
    fun historyAndRebuildEmptyAndInvertedWindows() {
        val empty = PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val history = GetPortfolioHistoryUseCase()(empty, HistoryRange.ALL, asOf)
        assertTrue(history.points.isEmpty())
        val rebuilt = RebuildNavHistoryUseCase()(empty, asOf, null, emptyList())
        assertTrue(rebuilt.points.isEmpty())
        val futureBuy =
            PortfolioSnapshot(
                listOf(etf, cash),
                listOf(cashTx("c", asOf.plusDays(5), bd("10")), buy("b", etf.id, asOf.plusDays(5), bd("1"), bd("1"))),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        val inverted = GetPortfolioHistoryUseCase()(futureBuy, HistoryRange.YTD, asOf)
        assertTrue(inverted.points.isEmpty())
        val twr = GetTimeWeightedReturnUseCase()(empty, asOf)
        assertEquals(0, BigDecimal.ZERO.compareTo(twr.twrPercent))
    }

    @Test
    fun yocIgnoresOldDividendsAndBucketsFrequency() {
        assertEquals(4, GetYocUseCase.inferPaymentsPerYear(3))
        assertEquals(4, GetYocUseCase.inferPaymentsPerYear(5))
        assertEquals(12, GetYocUseCase.inferPaymentsPerYear(6))
        val snapshot =
            PortfolioSnapshot(
                listOf(etf, cash),
                listOf(
                    cashTx("c", LocalDate.of(2024, 1, 1), bd("5000")),
                    buy("b", etf.id, LocalDate.of(2024, 1, 2), bd("10"), bd("100")),
                    Transaction(
                        id = "d",
                        assetId = etf.id,
                        date = LocalDate.of(2024, 6, 1),
                        type = TransactionType.DIVIDEND,
                        quantity = BigDecimal.ONE,
                        unitPriceNative = bd("5"),
                        exchangeRateAtExecution = BigDecimal.ONE,
                        unitPriceEur = bd("5"),
                        feesEur = BigDecimal.ZERO,
                    ),
                ),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        val yoc = GetYocUseCase()(snapshot, asOf).single()
        assertNull(yoc.ttmPercent)
        assertNull(yoc.paymentsPerYear)
    }

    @Test
    fun parsersCoverRatingBucketsAndAlternatePayloads() {
        fun rating(strong: String, buy: String, hold: String, sell: String, strongSell: String) = AlphaVantageParser.analystRating(
            """{"AnalystRatingStrongBuy":"$strong","AnalystRatingBuy":"$buy","AnalystRatingHold":"$hold","AnalystRatingSell":"$sell","AnalystRatingStrongSell":"$strongSell"}""",
        )
        assertEquals(AnalystRating.STRONG_BUY, rating("10", "0", "0", "0", "0"))
        assertEquals(AnalystRating.BUY, rating("0", "10", "0", "0", "0"))
        assertEquals(AnalystRating.SELL, rating("0", "0", "0", "10", "0"))
        assertEquals(AnalystRating.STRONG_SELL, rating("0", "0", "0", "0", "10"))
        assertEquals(AnalystRating.NONE, rating("0", "0", "0", "0", "0"))
        val items =
            AlphaVantageParser.commoditySeries(
                """{"data":[{"date":"2026-08-14","value":"77.10"},{"date":"bad","value":"1"}]}""",
            )
        assertEquals(1, items.size)
        assertEquals(1, CoinGeckoParser.dailyCloses("""{"prices":[[1755302400000,101.5],[1755302400000,102.0]]}""").size)
        assertTrue(FrankfurterParser.eurPerUsdSeries("""{"2026-08-14":{"EUR":0.92},"2026-08-15":{"EUR":0.93}}""").size == 2)
        assertTrue(StooqParser.dailyCloses("Date,Open,High,Low,Close\n2026-08-14,1,1,1,10.5\n").isNotEmpty())
        assertTrue(ListedQuoteRouting.looksEuropean("NOKIA.HE"))
        assertEquals("nokia.he", ListedQuoteRouting.stooqTicker("NOKIA.HE"))
        assertEquals("eqnr.ol", ListedQuoteRouting.stooqTicker("EQNR.OL"))
        assertEquals("vow.vi", ListedQuoteRouting.stooqTicker("VOW.AT"))
        assertEquals("aapl.foo", ListedQuoteRouting.stooqTicker("AAPL.FOO"))
        assertEquals("aapl..us", ListedQuoteRouting.stooqTicker("AAPL."))
    }

    @Test
    fun signalsSkipCashAndZeroQuantityAndEqualSma() {
        val sold =
            PortfolioSnapshot(
                listOf(etf, cash),
                listOf(
                    cashTx("c", asOf.minusDays(2), bd("2000")),
                    buy("b", etf.id, asOf.minusDays(1), bd("10"), bd("100")),
                    Transaction(
                        id = "s",
                        assetId = etf.id,
                        date = asOf,
                        type = TransactionType.SELL,
                        quantity = bd("10"),
                        unitPriceNative = bd("100"),
                        exchangeRateAtExecution = BigDecimal.ONE,
                        unitPriceEur = bd("100"),
                        feesEur = BigDecimal.ZERO,
                    ),
                ),
                listOf(DailyMarketData(etf.id, asOf, bd("100"), sma50 = bd("100"), sma200 = bd("100"))),
                emptyList(),
                emptyList(),
            )
        assertTrue(GetMarketSignalsUseCase()(sold, asOf).isEmpty())
        val equal =
            GetMarketSignalsUseCase()(
                PortfolioSnapshot(
                    listOf(etf, cash),
                    listOf(cashTx("c", asOf.minusDays(1), bd("2000")), buy("b", etf.id, asOf, bd("1"), bd("100"))),
                    listOf(DailyMarketData(etf.id, asOf, bd("100"), sma50 = bd("100"), sma200 = bd("100"))),
                    emptyList(),
                    emptyList(),
                ),
                asOf,
            ).single()
        assertNull(equal.vsSma50)
        assertNull(equal.cross)
        val below =
            GetMarketSignalsUseCase()(
                PortfolioSnapshot(
                    listOf(etf, cash),
                    listOf(cashTx("c", asOf.minusDays(1), bd("2000")), buy("b", etf.id, asOf, bd("1"), bd("90"))),
                    listOf(DailyMarketData(etf.id, asOf, bd("90"), sma50 = bd("100"), sma200 = bd("110"))),
                    emptyList(),
                    emptyList(),
                ),
                asOf,
            ).single()
        assertEquals(com.pirlruc.finsilo.domain.model.RelativeToAverage.BELOW, below.vsSma50)
    }

    @Test
    fun localSellBelowCostAndUsdTradeUsesStoredFx() {
        val ct = Asset("ct", "CT", "CT", AssetType.CT, Currency.EUR)
        val snapshot =
            PortfolioSnapshot(
                listOf(ct, cash),
                listOf(
                    cashTx("c", LocalDate.of(2026, 1, 1), bd("100")),
                    buy("b", ct.id, LocalDate.of(2026, 1, 2), bd("10"), bd("1")),
                    Transaction(
                        id = "s",
                        assetId = ct.id,
                        date = asOf,
                        type = TransactionType.SELL,
                        quantity = bd("10"),
                        unitPriceNative = bd("20"),
                        exchangeRateAtExecution = BigDecimal.ONE,
                        unitPriceEur = bd("20"),
                        feesEur = BigDecimal.ZERO,
                    ),
                ),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        val holding = PortfolioValuator().valueHoldings(snapshot, asOf).single { it.asset.id == ct.id }
        assertEquals(0, BigDecimal.ZERO.compareTo(holding.costEur))
        assertTrue(holding.valueEur.signum() < 0)
        val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
        val usdBuy =
            RecordLedgerEntryUseCase(newId = { "n" })(
                PortfolioSnapshot(
                    listOf(cash, apple),
                    listOf(cashTx("c", LocalDate.of(2026, 1, 1), bd("5000"))),
                    emptyList(),
                    listOf(CurrencyRate(asOf, bd("0.80"))),
                    emptyList(),
                ),
                LedgerEntryRequest(TransactionType.BUY, asOf, bd("1"), bd("100"), BigDecimal.ZERO, existingAssetId = apple.id),
            ) as LedgerEntryResult.Accepted
        assertEquals(0, bd("80").compareTo(usdBuy.transaction.unitPriceEur))
        assertNull(usdBuy.fxRate)
    }

    @Test
    fun syncRecordsFailuresAndSkipsFutureBars() {
        val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
        val failing =
            object : MarketFeed {
                override suspend fun eurPerUsd(): BigDecimal = error("no fx")

                override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate): List<CurrencyRate> = error("no hist")

                override suspend fun dailyHistory(asset: Asset, asOf: LocalDate): List<PriceBar> = listOf(PriceBar(asOf.plusDays(3), bd("1")))

                override suspend fun analystRating(asset: Asset) = AnalystRating.NONE
            }
        val result =
            runBlocking {
                SyncMarketDataUseCase(failing)(
                    PortfolioSnapshot(listOf(apple), emptyList(), emptyList(), emptyList(), emptyList()),
                    asOf,
                )
            }
        assertTrue(result.marketData.isEmpty())
        assertTrue(result.failures.any { it.startsWith("FX") })
        val throwingHistory =
            object : MarketFeed {
                override suspend fun eurPerUsd(): BigDecimal = bd("0.92")

                override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = listOf(CurrencyRate(to, bd("0.92")))

                override suspend fun dailyHistory(asset: Asset, asOf: LocalDate): List<PriceBar> = error("down")

                override suspend fun analystRating(asset: Asset) = AnalystRating.NONE
            }
        val failed =
            runBlocking {
                SyncMarketDataUseCase(throwingHistory)(
                    PortfolioSnapshot(listOf(apple), emptyList(), emptyList(), emptyList(), emptyList()),
                    asOf,
                )
            }
        assertTrue(failed.failures.any { it.contains("down") })
    }

    @Test
    fun ledgerRejectsBlankNewInstrumentAndUnknownExisting() {
        val useCase = RecordLedgerEntryUseCase(newId = { "n" })
        val funded =
            PortfolioSnapshot(
                listOf(cash),
                listOf(cashTx("c", LocalDate.of(2026, 1, 1), bd("5000"))),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        val blank =
            useCase(
                funded,
                LedgerEntryRequest(
                    TransactionType.BUY,
                    asOf,
                    bd("1"),
                    bd("1"),
                    BigDecimal.ZERO,
                    newAsset = NewAssetDraft("  ", "X", AssetType.STOCK, Currency.EUR),
                ),
            )
        assertTrue(blank is LedgerEntryResult.Rejected)
        val cashType =
            useCase(
                funded,
                LedgerEntryRequest(
                    TransactionType.BUY,
                    asOf,
                    bd("1"),
                    bd("1"),
                    BigDecimal.ZERO,
                    newAsset = NewAssetDraft("CASH", "Cash", AssetType.CASH, Currency.EUR),
                ),
            )
        assertTrue(cashType is LedgerEntryResult.Rejected)
        val missingId =
            useCase(
                funded,
                LedgerEntryRequest(TransactionType.BUY, asOf, bd("1"), bd("1"), BigDecimal.ZERO, existingAssetId = "nope"),
            )
        assertTrue(missingId is LedgerEntryResult.Rejected)
        val dividendOk =
            useCase(
                funded.copy(
                    assets = listOf(cash, etf),
                    transactions = listOf(cashTx("c", LocalDate.of(2026, 1, 1), bd("5000")), buy("b", etf.id, LocalDate.of(2026, 1, 2), bd("1"), bd("10"))),
                ),
                LedgerEntryRequest(TransactionType.DIVIDEND, asOf, BigDecimal.ONE, bd("1"), BigDecimal.ZERO, existingAssetId = etf.id),
            )
        assertTrue(dividendOk is LedgerEntryResult.Accepted)
        val synthetic = PortfolioValuator().syntheticCashAsset()
        assertEquals(AssetType.CASH, synthetic.assetType)
        assertEquals("VWCE.DE", etf.feedSymbol)
    }

    private fun cashTx(id: String, date: LocalDate, amount: BigDecimal) = Transaction(
        id,
        cash.id,
        date,
        TransactionType.DEPOSIT_CASH,
        amount,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ZERO,
    )

    private fun buy(id: String, assetId: String, date: LocalDate, qty: BigDecimal, price: BigDecimal) = Transaction(id, assetId, date, TransactionType.BUY, qty, price, BigDecimal.ONE, price, BigDecimal.ZERO)
}

package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.market.AlphaVantageParser
import com.pirlruc.finsilo.domain.market.CoinGeckoParser
import com.pirlruc.finsilo.domain.market.FrankfurterParser
import com.pirlruc.finsilo.domain.market.StooqParser
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.model.TwrSplit
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwrYocAndParserTest {
    private val asOf = LocalDate.of(2026, 8, 16)
    private val etf = Asset("vwce", "VWCE.DE", "ETF", AssetType.ETF, Currency.EUR)
    private val stock = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
    private val cash = Asset("cash", "EUR", "Cash", AssetType.CASH, Currency.EUR)

    @Test
    fun depositThenInternalBuyDoesNotSplitTwr() {
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(etf, cash),
                transactions =
                listOf(
                    cashTx("c", LocalDate.of(2026, 1, 1), bd("10000")),
                    buy("b", etf.id, LocalDate.of(2026, 1, 2), bd("10"), bd("100")),
                ),
                marketData = listOf(DailyMarketData(etf.id, asOf, bd("110"))),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        val report = GetTimeWeightedReturnUseCase()(snapshot, asOf)
        assertTrue(report.subPeriods.none { it.split == TwrSplit.EXTERNAL_BUY })
        // 9000 cash + 1100 holdings = 10100 vs 10000 start → 1%
        assertEquals(0, bd("1").compareTo(report.twrPercent.setScale(0, java.math.RoundingMode.HALF_EVEN)))
    }

    @Test
    fun buyExceedingCashIsExternalAndWithdrawalSplits() {
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(etf, cash),
                transactions =
                listOf(
                    buy("b", etf.id, LocalDate.of(2026, 1, 1), bd("10"), bd("100")),
                    Transaction(
                        id = "w",
                        assetId = cash.id,
                        date = LocalDate.of(2026, 6, 1),
                        type = TransactionType.WITHDRAWAL,
                        quantity = bd("50"),
                        unitPriceNative = BigDecimal.ONE,
                        exchangeRateAtExecution = BigDecimal.ONE,
                        unitPriceEur = BigDecimal.ONE,
                        feesEur = BigDecimal.ZERO,
                    ),
                ),
                marketData =
                listOf(
                    DailyMarketData(etf.id, LocalDate.of(2026, 1, 1), bd("100")),
                    DailyMarketData(etf.id, asOf, bd("100")),
                ),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        val report = GetTimeWeightedReturnUseCase()(snapshot, asOf)
        assertTrue(report.subPeriods.any { it.split == TwrSplit.EXTERNAL_BUY })
        assertTrue(report.subPeriods.any { it.split == TwrSplit.WITHDRAWAL })
    }

    @Test
    fun yocReportsTtmAndLastTimesInferredFrequency() {
        val txs =
            listOf(
                buy("b", stock.id, LocalDate.of(2025, 8, 20), bd("10"), bd("100")),
                dividend("d1", LocalDate.of(2025, 9, 1)),
                dividend("d2", LocalDate.of(2025, 12, 1)),
                dividend("d3", LocalDate.of(2026, 3, 1)),
                dividend("d4", LocalDate.of(2026, 6, 1)),
            )
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(stock),
                transactions = txs,
                marketData = listOf(DailyMarketData(stock.id, asOf, bd("100"))),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        val yoc = GetYocUseCase()(snapshot, asOf).single()
        assertEquals(4, yoc.paymentsPerYear)
        // TTM 4 × 10 EUR / 1000 cost = 4%
        assertEquals(0, bd("4").compareTo(yoc.ttmPercent))
        // last payment 10 EUR × 4 / 1000 = 4%
        assertEquals(0, bd("4").compareTo(yoc.lastTimesFrequencyPercent))
    }

    @Test
    fun parsersReadFreeApiPayloads() {
        val avFx = """{"Realtime Currency Exchange Rate":{"5. Exchange Rate":"0.92000000"}}"""
        assertEquals(0, bd("0.92").compareTo(AlphaVantageParser.exchangeRate(avFx)))

        val avDaily =
            """
            {"Time Series (Daily)":{
              "2026-08-15":{"1. open":"1","4. close":"123.45"},
              "2026-08-14":{"1. open":"1","4. close":"122.00"}
            }}
            """.trimIndent()
        val closes = AlphaVantageParser.dailyCloses(avDaily)
        assertEquals(2, closes.size)
        assertEquals(bd("123.45"), closes.last().closeNative)

        val overview =
            """
            {"AnalystRatingStrongBuy":"8","AnalystRatingBuy":"2","AnalystRatingHold":"0","AnalystRatingSell":"0","AnalystRatingStrongSell":"0"}
            """.trimIndent()
        assertEquals(AnalystRating.STRONG_BUY, AlphaVantageParser.analystRating(overview))

        val stooq = "Date,Open,High,Low,Close,Volume\n2026-08-14,1,1,1,50.5,10\n2026-08-15,1,1,1,51,10\n"
        assertEquals(bd("51"), StooqParser.dailyCloses(stooq).last().closeNative)

        assertEquals(0, bd("0.91").compareTo(FrankfurterParser.eurPerUsd("""{"rates":{"EUR":0.91}}""")))
        val fxSeries =
            FrankfurterParser.eurPerUsdSeries(
                """{"rates":{"2026-08-14":{"EUR":0.91},"2026-08-15":{"EUR":0.92}}}""",
            )
        assertEquals(2, fxSeries.size)
        assertEquals(0, bd("0.92").compareTo(fxSeries.last().eurPerUsd))

        val quota = runCatching { AlphaVantageParser.exchangeRate("""{"Note":"Thank you for using Alpha Vantage"}""") }
        assertTrue(quota.isFailure)

        val gecko = """{"prices":[[1755302400000,64000.5],[1755388800000,64100]]}"""
        val geckoCloses = CoinGeckoParser.dailyCloses(gecko)
        assertEquals(2, geckoCloses.size)
        assertEquals(0, bd("64100").compareTo(geckoCloses.last().closeNative))

        val wti =
            """
            {"name":"WTI","interval":"daily","data":[{"date":"2026-08-14","value":"77.10"},{"date":"2026-08-15","value":"78.50"}]}
            """.trimIndent()
        val wtiBars = AlphaVantageParser.commoditySeries(wti)
        assertEquals(2, wtiBars.size)
        assertEquals(0, bd("78.50").compareTo(wtiBars.last().closeNative))
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

    private fun dividend(id: String, date: LocalDate) = Transaction(id, stock.id, date, TransactionType.DIVIDEND, bd("10"), bd("1"), BigDecimal.ONE, bd("1"), BigDecimal.ZERO)
}

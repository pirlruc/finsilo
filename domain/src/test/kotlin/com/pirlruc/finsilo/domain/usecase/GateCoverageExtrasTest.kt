package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.market.AlphaVantageParser
import com.pirlruc.finsilo.domain.market.FrankfurterParser
import com.pirlruc.finsilo.domain.market.MovingAverages
import com.pirlruc.finsilo.domain.market.StooqParser
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.TechnicalCross
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GateCoverageExtrasTest {
    private val asOf = LocalDate.of(2026, 8, 16)
    private val etf = Asset("vwce", "VWCE.DE", "ETF", AssetType.ETF, Currency.EUR)
    private val cash = Asset("cash", "EUR", "Cash", AssetType.CASH, Currency.EUR)

    @Test
    fun alertsCoverRatingCrossAndDrift() {
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(etf, cash),
                transactions =
                listOf(
                    cashTx("c", LocalDate.of(2026, 1, 1), bd("10000")),
                    buy("b", etf.id, LocalDate.of(2026, 1, 2), bd("10"), bd("100")),
                ),
                marketData =
                listOf(
                    DailyMarketData(
                        etf.id,
                        asOf.minusDays(1),
                        bd("100"),
                        AnalystRating.HOLD,
                        sma50 = bd("90"),
                        sma200 = bd("95"),
                    ),
                    DailyMarketData(
                        etf.id,
                        asOf,
                        bd("110"),
                        AnalystRating.BUY,
                        sma50 = bd("100"),
                        sma200 = bd("95"),
                    ),
                ),
                fxRates = emptyList(),
                targets = listOf(TargetAllocation(AssetType.ETF, bd("80")), TargetAllocation(AssetType.CASH, bd("20"))),
            )
        val alerts = GetPortfolioAlertsUseCase()(snapshot, asOf)
        assertTrue(alerts.any { it.channel == AlertChannel.RATING })
        assertTrue(alerts.any { it.channel == AlertChannel.CROSS && it.title.contains("golden") })
        assertTrue(alerts.any { it.channel == AlertChannel.DRIFT })
    }

    @Test
    fun deathCrossIsDetected() {
        val cross =
            GetMarketSignalsUseCase.detectCross(
                yesterdaySma50 = bd("100"),
                yesterdaySma200 = bd("90"),
                todaySma50 = bd("80"),
                todaySma200 = bd("90"),
            )
        assertEquals(TechnicalCross.DEATH, cross)
        assertNull(GetMarketSignalsUseCase.detectCross(null, bd("1"), bd("1"), bd("1")))
    }

    @Test
    fun yocFrequencyBuckets() {
        assertNull(GetYocUseCase.inferPaymentsPerYear(0))
        assertEquals(1, GetYocUseCase.inferPaymentsPerYear(1))
        assertEquals(2, GetYocUseCase.inferPaymentsPerYear(2))
        assertEquals(4, GetYocUseCase.inferPaymentsPerYear(4))
        assertEquals(12, GetYocUseCase.inferPaymentsPerYear(12))
    }

    @Test
    fun moneyMathEdges() {
        assertEquals(0, bd("5").compareTo(MoneyMath.max(bd("2"), bd("5"))))
        assertEquals(0, bd("2").compareTo(MoneyMath.min(bd("2"), bd("5"))))
        assertEquals(0, MoneyMath.ZERO.compareTo(MoneyMath.percentOf(bd("1"), bd("0"))))
        assertThrows<IllegalArgumentException> { MoneyMath.div(bd("1"), bd("0")) }
        assertEquals(AnalystRating.NONE, AnalystRating.fromCode(99))
        assertNull(parseDate("not-a-date"))
        assertEquals(asOf, parseDate("2026-08-16"))
        AnalystRating.entries.forEach { rating ->
            assertTrue(rating.displayName.isNotBlank())
            assertEquals(rating, AnalystRating.fromCode(rating.code))
        }
        AssetType.entries.forEach { type ->
            type.isLocallyValued
            type.allowsInterest
            type.allowsDividend
        }
        HistoryRange.entries.forEach { range ->
            GetPortfolioHistoryUseCase()(
                PortfolioSnapshot(listOf(cash), listOf(cashTx("c", asOf, bd("1"))), emptyList(), emptyList(), emptyList()),
                range,
                asOf,
            )
        }
    }

    @Test
    fun parserAndSmaEdges() {
        assertNull(MovingAverages.sma(listOf(bd("1"), bd("2")), 50))
        assertNull(MovingAverages.sma(listOf(bd("1")), 0))
        assertTrue(runCatching { AlphaVantageParser.ensureUsable("""{"Error Message":"bad"}""") }.isFailure)
        assertTrue(runCatching { AlphaVantageParser.ensureUsable("""{"Information":"throttle"}""") }.isFailure)
        val mapped =
            AlphaVantageParser.commoditySeries(
                """{"data":{"2026-08-14":"77.10","2026-08-15":"78.50"}}""",
            )
        assertEquals(2, mapped.size)
        val hold =
            AlphaVantageParser.analystRating(
                """{"AnalystRatingStrongBuy":"0","AnalystRatingBuy":"0","AnalystRatingHold":"10","AnalystRatingSell":"0","AnalystRatingStrongSell":"0"}""",
            )
        assertEquals(AnalystRating.HOLD, hold)
        assertTrue(StooqParser.dailyCloses("Date,Open,High,Low,Close\nbad\n").isEmpty())
        assertNull(FrankfurterParser.eurPerUsd("""{"rates":{"USD":1}}"""))
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

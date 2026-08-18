package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.importcsv.BrokerAssetType
import com.pirlruc.finsilo.domain.importcsv.BrokerCsv
import com.pirlruc.finsilo.domain.importcsv.CsvReader
import com.pirlruc.finsilo.domain.importcsv.ImportBrokerCsvUseCase
import com.pirlruc.finsilo.domain.market.AlphaVantageParser
import com.pirlruc.finsilo.domain.market.ListedQuoteRouting
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.QuoteCurrency
import com.pirlruc.finsilo.domain.model.AllocationSlice
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import com.pirlruc.finsilo.domain.portfolio.NavInputsFingerprint
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoverageCloseoutTest {
    private val asOf = LocalDate.of(2026, 8, 16)
    private val start = LocalDate.of(2026, 8, 1)
    private val cash = Asset("cash", "EUR", "Cash", AssetType.CASH, Currency.EUR)
    private val etf = Asset("vwce", "VWCE.DE", "ETF", AssetType.ETF, Currency.EUR)
    private val btc = Asset("btc", "BTC", "Bitcoin", AssetType.CRYPTO, Currency.EUR)

    @Test
    fun transactionRanksCoverEveryType() {
        val ranks = TransactionType.entries.map { it.ledgerRank to it.csvImportRank }
        assertEquals(TransactionType.entries.size, ranks.distinct().size)
        assertEquals(0, TransactionType.DEPOSIT_CASH.ledgerRank)
        assertEquals(1, TransactionType.SELL.csvImportRank)
        assertEquals(4, TransactionType.INTEREST.csvImportRank)
    }

    @Test
    fun storedNavCoversWhenEndpointsMatchAndRejectsOneSidedMismatch() {
        val snapshot = snap()
        val rebuilt = RebuildNavHistoryUseCase()(snapshot, asOf, null, emptyList())
        val mid = start.plusDays(2)
        val withHole =
            rebuilt.points.map { point ->
                if (point.date == mid) point.copy(valueEur = bd("99999")) else point
            }
        val covered = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ALL, asOf, withHole)
        assertEquals(0, bd("99999").compareTo(covered.points.single { it.date == mid }.valueEur))
        val wrongTo =
            rebuilt.points.map { point ->
                if (point.date == asOf) point.copy(valueEur = point.valueEur.add(bd("1"))) else point
            }
        val walkedTo = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ALL, asOf, wrongTo)
        assertEquals(0, rebuilt.points.last().valueEur.compareTo(walkedTo.points.last().valueEur))
        val wrongFrom =
            rebuilt.points.map { point ->
                if (point.date == start) point.copy(valueEur = point.valueEur.add(bd("1"))) else point
            }
        val walkedFrom = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ALL, asOf, wrongFrom)
        assertEquals(0, rebuilt.points.first().valueEur.compareTo(walkedFrom.points.first().valueEur))
        val emptyStored = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ALL, asOf, emptyList())
        assertEquals(rebuilt.points.size, emptyStored.points.size)
    }

    @Test
    fun sequenceChangesFingerprintAndSameDayPrecedingUsesSequence() {
        val base = snap()
        val shifted = base.copy(transactions = base.transactions.map { it.copy(sequence = it.sequence + 9) })
        assertNotEquals(NavInputsFingerprint.of(base), NavInputsFingerprint.of(shifted))
        val first = buy(etf.id).copy(id = "zzz", date = asOf, sequence = 1)
        val second = first.copy(id = "aaa", sequence = 2, unitPriceEur = bd("200"), unitPriceNative = bd("200"))
        val candidate = first.copy(id = "later", sequence = 3, unitPriceEur = bd("50"), unitPriceNative = bd("50"))
        val prior = PositionLedger().preceding(listOf(second, first), candidate)
        assertEquals(listOf("zzz", "aaa"), prior.map { it.id })
        val tied = PositionLedger().preceding(listOf(second.copy(sequence = 1), first), candidate.copy(sequence = 1, id = "zzz-later"))
        assertEquals(listOf("aaa", "zzz"), tied.map { it.id })
    }

    @Test
    fun alertsSurfaceRatingChangeWithDeathCrossAndInBandDrift() {
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(etf, cash),
                transactions = listOf(cashTx(), buy(etf.id)),
                marketData =
                listOf(
                    DailyMarketData(etf.id, asOf.minusDays(1), bd("100"), AnalystRating.HOLD, bd("100"), bd("90")),
                    DailyMarketData(etf.id, asOf, bd("80"), AnalystRating.SELL, bd("80"), bd("90")),
                ),
                fxRates = emptyList(),
                targets = listOf(TargetAllocation(AssetType.ETF, bd("50")), TargetAllocation(AssetType.CASH, bd("50"))),
            )
        val alerts = GetPortfolioAlertsUseCase()(snapshot, asOf)
        assertTrue(alerts.any { it.channel == AlertChannel.RATING })
        assertTrue(alerts.any { it.title.contains("death") })
        val inBand = AllocationSlice(AssetType.ETF, bd("50"), bd("52"), bd("50"), bd("2"))
        val unknown = AllocationSlice(AssetType.ETF, bd("50"), bd("50"), null, null)
        val drifted = AllocationSlice(AssetType.ETF, bd("50"), bd("60"), bd("50"), bd("10"))
        assertFalse(inBand.exceedsDriftBand)
        assertFalse(unknown.exceedsDriftBand)
        assertTrue(drifted.exceedsDriftBand)
    }

    @Test
    fun importUsdBuyWithoutFxIsSkippedAndEmptyParseFails() {
        val importer = ImportBrokerCsvUseCase()
        val empty = PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Exchange rate,Total,Currency (Total),ID
            Market buy,2024-01-08 09:00:00,US0378331005,AAPL,Apple,1,150,USD,,150,USD,A0
            """.trimIndent()
        val imported = importer(empty, listOf(csv))
        assertTrue(imported.skipped.isNotEmpty() || imported.accepted == 0)
        val none = BrokerCsv.parseAll(emptyList())
        assertEquals("Choose at least one CSV file.", none.error)
        val garbage = importer(empty, listOf("not-a-csv"))
        assertTrue(garbage.error != null)
        assertEquals(AssetType.CRYPTO, BrokerAssetType.infer("BTC", "Bitcoin", null))
        assertEquals(AssetType.COMMODITY, BrokerAssetType.infer("XAU", "Gold", null))
        assertEquals(2, CsvReader.records("h\n\"a\"\"b\",c")[1].size)
        assertEquals("shel.uk", ListedQuoteRouting.stooqTicker("SHEL.LON"))
        assertEquals("nesn.sw", ListedQuoteRouting.stooqTicker("NESN.VX"))
        assertEquals("foo.ir", ListedQuoteRouting.stooqTicker("FOO.IR"))
        assertEquals("bar.co", ListedQuoteRouting.stooqTicker("BAR.CO"))
        assertEquals(Currency.USD, QuoteCurrency.of(btc))
    }

    @Test
    fun syncKeepsStoredRatingOnOlderBarsAndDashboardWiresWarnings() {
        val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
        val feed =
            object : MarketFeed {
                override suspend fun eurPerUsd(): BigDecimal = bd("0.92")

                override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = listOf(CurrencyRate(to, bd("0.92")))

                override suspend fun dailyHistory(asset: Asset, asOf: LocalDate) = listOf(PriceBar(asOf.minusDays(10), bd("190")), PriceBar(asOf, bd("200")))

                override suspend fun analystRating(asset: Asset) = AnalystRating.BUY
            }
        val stored =
            PortfolioSnapshot(
                listOf(apple),
                emptyList(),
                listOf(DailyMarketData(apple.id, asOf.minusDays(10), bd("190"), AnalystRating.HOLD)),
                emptyList(),
                emptyList(),
            )
        val synced = runBlocking { SyncMarketDataUseCase(feed)(stored, asOf) }
        assertEquals(AnalystRating.HOLD, synced.marketData.single { it.date == asOf.minusDays(10) }.analystRating)
        assertEquals(AnalystRating.BUY, synced.marketData.single { it.date == asOf }.analystRating)
        assertTrue(runCatching { AlphaVantageParser.ensureUsable("""{"Note":"call frequency"}""") }.isFailure)
        assertTrue(AlphaVantageParser.dailyCloses("""{"2026-08-14":{"4. close":"12.5"}}""").isNotEmpty())
        val dashboard = GetDashboardUseCase()(snap(), HistoryRange.ALL, asOf)
        assertTrue(dashboard.allocation.totalValueEur.signum() > 0)
        val idle = GetMarketSignalsUseCase()(PortfolioSnapshot(listOf(etf), emptyList(), emptyList(), emptyList(), emptyList()), asOf)
        assertTrue(idle.isEmpty())
        val rejected =
            RecordLedgerEntryUseCase()(
                snap(),
                LedgerEntryRequest(TransactionType.INTEREST, asOf, bd("1"), bd("1"), BigDecimal.ZERO, existingAssetId = etf.id),
            )
        assertTrue(rejected is LedgerEntryResult.Rejected)
        val twr = GetTimeWeightedReturnUseCase()(snap(), asOf)
        assertTrue(twr.subPeriods.isNotEmpty())
        val warnings = PortfolioValuator().valuationWarnings(snap(), asOf)
        assertTrue(warnings.any { it.contains("VWCE.DE") })
    }

    private fun snap() = PortfolioSnapshot(
        assets = listOf(etf, cash),
        transactions = listOf(cashTx(), buy(etf.id)),
        marketData = emptyList(),
        fxRates = emptyList(),
        targets = emptyList(),
    )

    private fun cashTx() = Transaction(
        "c0",
        cash.id,
        start,
        TransactionType.DEPOSIT_CASH,
        bd("10000"),
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ZERO,
        sequence = 1,
    )

    private fun buy(assetId: String) = Transaction(
        "b-$assetId",
        assetId,
        start.plusDays(1),
        TransactionType.BUY,
        bd("10"),
        bd("100"),
        BigDecimal.ONE,
        bd("100"),
        BigDecimal.ZERO,
        sequence = 2,
    )
}

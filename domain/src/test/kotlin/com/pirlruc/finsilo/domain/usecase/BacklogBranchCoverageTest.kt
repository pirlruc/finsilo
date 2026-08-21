package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.backup.LedgerBackupCodec
import com.pirlruc.finsilo.domain.backup.LedgerBackupExtras
import com.pirlruc.finsilo.domain.backup.LedgerBackupResult
import com.pirlruc.finsilo.domain.backup.LedgerBackupText
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.model.RealizedGainsReport
import com.pirlruc.finsilo.domain.model.RealizedKind
import com.pirlruc.finsilo.domain.model.RealizedLotLine
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.model.WatchlistItem
import com.pirlruc.finsilo.domain.model.WatchlistSnapshot
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BacklogBranchCoverageTest {
    private val asOf = LocalDate.of(2026, 8, 16)
    private val cash = Asset("cash", "EUR-CASH", "Cash", AssetType.CASH, Currency.EUR)
    private val ct = Asset("ct", "CT", "Certificados", AssetType.CT, Currency.EUR)
    private val etf = Asset("vwce", "VWCE.DE", "ETF", AssetType.ETF, Currency.EUR)
    private val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)

    @Test
    fun backupTextRoundTripEscapesAndRefusesBadPayloads() {
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(Asset("id", "SYM;x", "Name\twith tab", AssetType.ETF, Currency.EUR, "IE00", "VWCE.DE")),
                transactions = listOf(deposit(), buy("b", "id", asOf, bd("1"), bd("10"), 2)),
                marketData =
                listOf(
                    DailyMarketData("id", asOf, bd("10"), AnalystRating.BUY, bd("9"), null),
                    DailyMarketData("id", asOf.minusDays(1), bd("9"), AnalystRating.NONE, null, bd("8")),
                ),
                fxRates = listOf(CurrencyRate(asOf, bd("0.92"))),
                targets = listOf(TargetAllocation(AssetType.ETF, bd("100"))),
            )
        val restored = LedgerBackupText.decode(LedgerBackupText.encode(snapshot)) as LedgerBackupResult.Restored
        assertEquals("Name\twith tab", restored.snapshot.assets.single().name)
        assertEquals("IE00", restored.snapshot.assets.single().isin)
        assertTrue(LedgerBackupText.decode("NOPE") is LedgerBackupResult.Refused)
        assertTrue(LedgerBackupText.decode("FSILO-LEDGER-1\nZ\tbad") is LedgerBackupResult.Refused)
        assertTrue(LedgerBackupText.decode("FSILO-LEDGER-1\nA\tshort") is LedgerBackupResult.Refused)
        val encrypted = LedgerBackupCodec.encrypt(snapshot, "ABCD1234EFGH5678")
        encrypted[0] = 0x00
        assertTrue(LedgerBackupCodec.decrypt(encrypted, "ABCD1234EFGH5678") is LedgerBackupResult.Refused)
        val good = LedgerBackupCodec.encrypt(snapshot, "ABCD1234EFGH5678")
        assertTrue(LedgerBackupCodec.decrypt(good, "WRONGCODE0000000") is LedgerBackupResult.Refused)
    }

    @Test
    fun priceAlertsCoverMissingDataPercentAndLevelEdges() {
        val useCase = GetPriceThresholdAlertsUseCase()
        assertTrue(useCase(emptyBook(), emptyList(), asOf).isEmpty())
        val threshold = PriceAlertThreshold("missing", bd("1"), bd("1"))
        assertTrue(threshold.isEmpty.not())
        assertTrue(PriceAlertThreshold("x").isEmpty)
        assertTrue(useCase(emptyBook(), listOf(threshold), asOf).isEmpty())
        val oneBar =
            usdBook(
                listOf(DailyMarketData(apple.id, asOf, bd("100"))),
                fx = listOf(CurrencyRate(asOf, bd("1"))),
            )
        assertTrue(useCase(oneBar, listOf(PriceAlertThreshold(apple.id, bd("90"), bd("1"))), asOf).isEmpty())
        val noFx =
            usdBook(
                listOf(
                    DailyMarketData(apple.id, asOf.minusDays(1), bd("100")),
                    DailyMarketData(apple.id, asOf, bd("110")),
                ),
                fx = emptyList(),
            )
        assertTrue(useCase(noFx, listOf(PriceAlertThreshold(apple.id, percentMove = bd("1"))), asOf).isEmpty())
        val flat =
            usdBook(
                listOf(
                    DailyMarketData(apple.id, asOf.minusDays(1), bd("100")),
                    DailyMarketData(apple.id, asOf, bd("100")),
                ),
                fx = listOf(CurrencyRate(asOf, bd("1"))),
            )
        assertTrue(useCase(flat, listOf(PriceAlertThreshold(apple.id, bd("100"), bd("5"))), asOf).isEmpty())
        val zeroPrev =
            usdBook(
                listOf(
                    DailyMarketData(apple.id, asOf.minusDays(1), bd("0")),
                    DailyMarketData(apple.id, asOf, bd("10")),
                ),
                fx = listOf(CurrencyRate(asOf, bd("1"))),
            )
        assertTrue(useCase(zeroPrev, listOf(PriceAlertThreshold(apple.id, percentMove = bd("1"))), asOf).isEmpty())
        val touch =
            usdBook(
                listOf(
                    DailyMarketData(apple.id, asOf.minusDays(1), bd("105")),
                    DailyMarketData(apple.id, asOf, bd("110")),
                ),
                fx = listOf(CurrencyRate(asOf, bd("1"))),
            )
        assertEquals(1, useCase(touch, listOf(PriceAlertThreshold(apple.id, eurLevel = bd("105"))), asOf).size)
        val onlyPct =
            usdBook(
                listOf(
                    DailyMarketData(apple.id, asOf.minusDays(1), bd("100")),
                    DailyMarketData(apple.id, asOf, bd("102")),
                ),
                fx = listOf(CurrencyRate(asOf, bd("1"))),
            )
        assertTrue(useCase(onlyPct, listOf(PriceAlertThreshold(apple.id, percentMove = bd("5"))), asOf).isEmpty())
        val firePct =
            usdBook(
                listOf(
                    DailyMarketData(apple.id, asOf.minusDays(1), bd("100")),
                    DailyMarketData(apple.id, asOf, bd("110")),
                ),
                fx = listOf(CurrencyRate(asOf, bd("1"))),
            )
        assertEquals(1, useCase(firePct, listOf(PriceAlertThreshold(apple.id, percentMove = bd("5"))), asOf).size)
        assertTrue(LedgerBackupCodec.decrypt(ByteArray(8), "x") is LedgerBackupResult.Refused)
    }

    @Test
    fun csvQuotesSpecialCharactersAndWatchlistFeedSymbol() {
        val line =
            RealizedLotLine(
                asset = Asset("id", "A;\"B", "n", AssetType.STOCK, Currency.EUR, "US1"),
                sellDate = asOf,
                acquiredDate = asOf.minusDays(1),
                quantity = bd("1"),
                costEur = bd("1"),
                proceedsEur = bd("2"),
                gainEur = bd("1"),
                kind = RealizedKind.DISPOSAL,
            )
        val csv = RealizedGainsCsv.write(RealizedGainsReport(2026, listOf(line), bd("1")))
        assertTrue(csv.contains("Alienacao"))
        assertTrue(csv.contains("\"A;\"\"B\""))
        val blankQuote = WatchlistItem("w", "MSFT", "Microsoft", AssetType.STOCK, Currency.USD, "  ")
        assertEquals("MSFT", blankQuote.feedSymbol)
        val quoted = WatchlistItem("w", "MSFT", "Microsoft", AssetType.STOCK, Currency.USD, "MSFT")
        assertEquals("MSFT", quoted.feedSymbol)
        assertEquals("MSFT", quoted.asFeedAsset().symbol)
        val bare = WatchlistItem("w", "IBM", "IBM", AssetType.STOCK, Currency.USD)
        assertEquals("IBM", bare.feedSymbol)
        val withNewline =
            RealizedLotLine(
                asset = Asset("nl", "A\nB", "n", AssetType.STOCK, Currency.EUR),
                sellDate = asOf,
                acquiredDate = asOf.minusDays(1),
                quantity = bd("1"),
                costEur = bd("1"),
                proceedsEur = bd("2"),
                gainEur = bd("1"),
                kind = RealizedKind.DISPOSAL,
            )
        assertTrue(RealizedGainsCsv.write(RealizedGainsReport(2026, listOf(withNewline), bd("1"))).contains("\"A\nB\""))
    }

    @Test
    fun manualQuoteRejectsUnknownAndNonPositiveAndUsesCashFlowWithoutBar() {
        val unknown = RecordManualQuoteUseCase()(emptyBook(), "nope", asOf, bd("1"))
        assertTrue(unknown is ManualQuoteResult.Rejected)
        val zero = RecordManualQuoteUseCase()(ctBook(), ct.id, asOf, bd("0"))
        assertTrue(zero is ManualQuoteResult.Rejected)
        val holding = PortfolioValuator().allocation(ctBook(), asOf).holdings.single { it.asset.id == ct.id }
        assertEquals(0, bd("3000").compareTo(holding.valueEur))
        assertEquals(Currency.EUR, holding.quoteCurrency)
        val soldOut =
            ctBook().copy(
                transactions = ctBook().transactions +
                    Transaction("s", ct.id, asOf, TransactionType.SELL, bd("3000"), bd("1"), BigDecimal.ONE, bd("1"), BigDecimal.ZERO, 3),
            )
        val closed = RecordManualQuoteUseCase()(soldOut, ct.id, asOf, bd("1.2")) as ManualQuoteResult.Accepted
        val valued = PortfolioValuator().allocation(soldOut.copy(marketData = listOf(closed.row)), asOf)
        assertTrue(valued.holdings.none { it.asset.id == ct.id })
    }

    @Test
    fun backfillEmptyAndWatchlistSyncFailures() {
        assertTrue(BackfillLedgerSequenceUseCase()(emptyList()).isEmpty())
        val unique = listOf(deposit().copy(sequence = 1), buy("b", etf.id, asOf, bd("1"), bd("1"), 2))
        assertEquals(unique, BackfillLedgerSequenceUseCase()(unique))
        val duplicateSequence = listOf(deposit().copy(sequence = 1), buy("b", etf.id, asOf, bd("1"), bd("1"), 1))
        assertEquals(listOf(1L, 2L), BackfillLedgerSequenceUseCase()(duplicateSequence).map { it.sequence })
        val failed =
            runBlocking {
                SyncWatchlistUseCase(
                    object : MarketFeed {
                        override suspend fun eurPerUsd() = bd("1")
                        override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = emptyList<CurrencyRate>()
                        override suspend fun dailyHistory(asset: Asset, asOf: LocalDate): List<PriceBar> = error("boom")
                        override suspend fun analystRating(asset: Asset) = AnalystRating.NONE
                    },
                )(WatchlistSnapshot(listOf(WatchlistItem("w", "X", "X", AssetType.STOCK, Currency.EUR))), asOf)
            }
        assertTrue(failed.quotes.isEmpty())
        assertTrue(failed.failures.single().contains("boom"))
        val emptyHist =
            runBlocking {
                SyncWatchlistUseCase(
                    object : MarketFeed {
                        override suspend fun eurPerUsd() = bd("1")
                        override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = emptyList<CurrencyRate>()
                        override suspend fun dailyHistory(asset: Asset, asOf: LocalDate) = emptyList<PriceBar>()
                        override suspend fun analystRating(asset: Asset) = AnalystRating.NONE
                    },
                )(WatchlistSnapshot(listOf(WatchlistItem("w", "X", "X", AssetType.STOCK, Currency.EUR))), asOf)
            }
        assertTrue(emptyHist.quotes.isEmpty())
        assertFalse(DashboardCopy.csvFundedBuys(emptyBook()))
        assertTrue(DashboardCopy.extras(emptyBook()).isEmpty())
    }

    @Test
    fun remainingBackupValuatorAndEurThresholdBranches() {
        val ppr = Asset("ppr", "PPR", "PPR", AssetType.PPR, Currency.USD)
        val usdLocal =
            PortfolioSnapshot(
                assets = listOf(cash, ppr),
                transactions = listOf(deposit(), buy("b", ppr.id, asOf.minusDays(2), bd("1"), bd("100"), 2)),
                marketData = listOf(DailyMarketData(ppr.id, asOf, bd("110"))),
                fxRates = listOf(CurrencyRate(asOf, bd("0.90"))),
                targets = emptyList(),
            )
        val holding = PortfolioValuator().allocation(usdLocal, asOf).holdings.single { it.asset.id == ppr.id }
        assertEquals(0, bd("99").compareTo(holding.valueEur))
        val noFx = usdLocal.copy(fxRates = emptyList())
        assertTrue(PortfolioValuator().allocation(noFx, asOf).holdings.none { it.asset.id == ppr.id })

        val etfBook =
            PortfolioSnapshot(
                assets = listOf(cash, etf),
                transactions = listOf(deposit(), buy("e", etf.id, asOf.minusDays(2), bd("1"), bd("100"), 2)),
                marketData =
                listOf(
                    DailyMarketData(etf.id, asOf.minusDays(1), bd("100")),
                    DailyMarketData(etf.id, asOf, bd("110")),
                ),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        val alerts =
            GetPriceThresholdAlertsUseCase()(
                etfBook,
                listOf(PriceAlertThreshold(etf.id, eurLevel = bd("110"), percentMove = bd("5"))),
                asOf,
            )
        assertEquals(2, alerts.size)

        val blank = Asset("x", "X", "X", AssetType.ETF, Currency.EUR, null, null)
        val encoded =
            LedgerBackupText.encode(
                PortfolioSnapshot(listOf(blank), emptyList(), emptyList(), emptyList(), emptyList()),
            )
        val withBlanks = encoded.replace("END", "\n\nEND\n")
        val restored = LedgerBackupText.decode(withBlanks) as LedgerBackupResult.Restored
        assertEquals(null, restored.snapshot.assets.single().isin)
        assertEquals(null, restored.snapshot.assets.single().quoteSymbol)
        assertTrue(LedgerBackupText.decode("FSILO-LEDGER-1\nW\tw1") is LedgerBackupResult.Refused)
        assertTrue(LedgerBackupText.decode("FSILO-LEDGER-2\nZ\tbad") is LedgerBackupResult.Refused)
        assertTrue(LedgerBackupText.decode("FSILO-LEDGER-2\nW\t1") is LedgerBackupResult.Refused)
        assertTrue(LedgerBackupText.decode("FSILO-LEDGER-2\nL\t1") is LedgerBackupResult.Refused)
        assertTrue(LedgerBackupText.decode("FSILO-LEDGER-2\nH\t1") is LedgerBackupResult.Refused)
        assertTrue(LedgerBackupText.decode("FSILO-LEDGER-2\nQ\t1") is LedgerBackupResult.Refused)
        assertTrue(LedgerBackupText.decode("FSILO-LEDGER-3\nZ\tbad") is LedgerBackupResult.Refused)
        val extrasText =
            LedgerBackupText.encode(
                PortfolioSnapshot(listOf(blank), emptyList(), emptyList(), emptyList(), emptyList()),
                LedgerBackupExtras(
                    watchlist = WatchlistSnapshot(
                        items = listOf(WatchlistItem("w1", "MSFT", "Microsoft", AssetType.STOCK, Currency.USD, "MSFT.US")),
                        quotes = listOf(DailyMarketData("w1", asOf, bd("1"), AnalystRating.NONE, bd("2"), bd("3"))),
                    ),
                    templates = listOf(LedgerTemplate("t1", "Cash", TransactionType.DEPOSIT_CASH, "cash", "10", "1", "0")),
                    thresholds = listOf(PriceAlertThreshold("x", bd("9"), bd("5"))),
                ),
            )
        val extras = LedgerBackupText.decode(extrasText) as LedgerBackupResult.Restored
        assertEquals("MSFT", extras.extras.watchlist.items.single().symbol)
        assertEquals("MSFT.US", extras.extras.watchlist.items.single().quoteSymbol)
        assertEquals("w1", extras.extras.watchlist.quotes.single().assetId)
        assertEquals("cash", extras.extras.templates.single().assetId)
        assertEquals(0, bd("9").compareTo(checkNotNull(extras.extras.thresholds.single().eurLevel)))
        val blankQuote =
            LedgerBackupText.encode(
                PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
                LedgerBackupExtras(
                    watchlist = WatchlistSnapshot(
                        items = listOf(WatchlistItem("w2", "IBM", "IBM", AssetType.STOCK, Currency.USD, "")),
                        quotes = listOf(DailyMarketData("w2", asOf, bd("1"), AnalystRating.NONE)),
                    ),
                    templates = listOf(LedgerTemplate("t2", "X", TransactionType.BUY, "", "1", "1", "0")),
                    thresholds = listOf(PriceAlertThreshold("y")),
                ),
            )
        val blankDecoded = LedgerBackupText.decode(blankQuote) as LedgerBackupResult.Restored
        assertEquals(null, blankDecoded.extras.watchlist.items.single().quoteSymbol)
        assertEquals(null, blankDecoded.extras.templates.single().assetId)
        assertTrue(blankDecoded.extras.thresholds.single().isEmpty)
        assertTrue(LedgerBackupText.decode("FSILO-LEDGER-1\nM\t1") is LedgerBackupResult.Refused)
        assertTrue(LedgerBackupText.decode("FSILO-LEDGER-1\nX\t1") is LedgerBackupResult.Refused)
        assertTrue(LedgerBackupText.decode("FSILO-LEDGER-1\nG\t1") is LedgerBackupResult.Refused)
        assertTrue(LedgerBackupText.decode("") is LedgerBackupResult.Refused)
        val soldNoBar =
            ctBook().copy(
                transactions = ctBook().transactions +
                    Transaction("s", ct.id, asOf, TransactionType.SELL, bd("3000"), bd("1"), BigDecimal.ONE, bd("1"), BigDecimal.ZERO, 3),
            )
        assertTrue(PortfolioValuator().allocation(soldNoBar, asOf).holdings.none { it.asset.id == ct.id })
        val touchNow =
            usdBook(
                listOf(
                    DailyMarketData(apple.id, asOf.minusDays(1), bd("100")),
                    DailyMarketData(apple.id, asOf, bd("105")),
                ),
                fx = listOf(CurrencyRate(asOf, bd("1"))),
            )
        assertEquals(
            1,
            GetPriceThresholdAlertsUseCase()(touchNow, listOf(PriceAlertThreshold(apple.id, eurLevel = bd("105"))), asOf).size,
        )
        val nl = blank.copy(name = "line\nbreak")
        val again = LedgerBackupText.decode(LedgerBackupText.encode(PortfolioSnapshot(listOf(nl), emptyList(), emptyList(), emptyList(), emptyList()))) as LedgerBackupResult.Restored
        assertEquals("line\nbreak", again.snapshot.assets.single().name)
    }

    private fun emptyBook() = PortfolioSnapshot(listOf(cash, apple), emptyList(), emptyList(), emptyList(), emptyList())

    private fun ctBook() = PortfolioSnapshot(listOf(cash, ct), listOf(deposit(), buy("ctb", ct.id, asOf.minusDays(5), bd("3000"), bd("1"), 2)), emptyList(), emptyList(), emptyList())

    private fun usdBook(market: List<DailyMarketData>, fx: List<CurrencyRate>) = PortfolioSnapshot(
        listOf(apple, cash),
        listOf(deposit(), buy("b", apple.id, asOf.minusDays(2), bd("1"), bd("100"), 2)),
        market,
        fx,
        emptyList(),
    )

    private fun deposit() = Transaction(
        "c",
        cash.id,
        asOf.minusDays(10),
        TransactionType.DEPOSIT_CASH,
        bd("10000"),
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ZERO,
        1,
    )

    private fun buy(
        id: String,
        assetId: String,
        date: LocalDate,
        qty: BigDecimal,
        price: BigDecimal,
        sequence: Long,
    ) = Transaction(id, assetId, date, TransactionType.BUY, qty, price, BigDecimal.ONE, price, BigDecimal.ZERO, sequence)
}

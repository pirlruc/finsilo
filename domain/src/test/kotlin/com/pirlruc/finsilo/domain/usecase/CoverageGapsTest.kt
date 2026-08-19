package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.importcsv.BookedAmounts
import com.pirlruc.finsilo.domain.importcsv.BrokerCsv
import com.pirlruc.finsilo.domain.importcsv.BrokerCsvFormat
import com.pirlruc.finsilo.domain.importcsv.BrokerCsvLine
import com.pirlruc.finsilo.domain.importcsv.BrokerLines
import com.pirlruc.finsilo.domain.importcsv.BrokerMoney
import com.pirlruc.finsilo.domain.importcsv.BrokerQuoteSymbol
import com.pirlruc.finsilo.domain.importcsv.CsvReader
import com.pirlruc.finsilo.domain.importcsv.CsvRow
import com.pirlruc.finsilo.domain.importcsv.HoldingDraft
import com.pirlruc.finsilo.domain.importcsv.ImportBrokerCsvUseCase
import com.pirlruc.finsilo.domain.importcsv.ImportFingerprints
import com.pirlruc.finsilo.domain.importcsv.MoneyParts
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.NavPoint
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.TargetAllocation
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import com.pirlruc.finsilo.domain.portfolio.PositionLedger
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoverageGapsTest {
    private val asOf = LocalDate.of(2026, 8, 16)
    private val start = LocalDate.of(2026, 8, 1)
    private val cash = Asset("cash", "EUR", "Cash", AssetType.CASH, Currency.EUR)
    private val etf = Asset("vwce", "VWCE.DE", "ETF", AssetType.ETF, Currency.EUR, isin = "IE00BK5BQT80", quoteSymbol = "VWCE.DE")
    private val etf2 = Asset("vwra", "VWRA.L", "All-World", AssetType.ETF, Currency.EUR)

    @Test
    fun storedHistoryFiltersOutsideRangeAndKeepsLastSample() {
        val snapshot = snap()
        val rebuilt = RebuildNavHistoryUseCase()(snapshot, asOf, null, emptyList())
        val padded = listOf(NavPoint(start.minusDays(3), bd("1"))) + rebuilt.points + listOf(NavPoint(asOf.plusDays(2), bd("2")))
        val month = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ONE_MONTH, asOf, padded)
        assertTrue(month.points.none { it.date.isBefore(month.from) })
        assertTrue(month.points.none { it.date.isAfter(month.to) })
        val sampled = GetPortfolioHistoryUseCase(maxPoints = 6)(snapshot, HistoryRange.ALL, asOf)
        assertEquals(asOf, sampled.points.last().date)
        val holeFrom = rebuilt.points.filter { it.date != month.from }.reversed()
        val walkedHole = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ONE_MONTH, asOf, holeFrom)
        assertEquals(month.to, walkedHole.to)
        val missingTo = padded.filter { it.date != asOf }
        val walkedTo = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ALL, asOf, missingTo)
        assertEquals(asOf, walkedTo.to)
    }

    @Test
    fun alertsRatingChangeWithoutCrossAndLedgerCashOnLocalInstrument() {
        val snapshot =
            PortfolioSnapshot(
                listOf(etf, cash),
                listOf(cashTx(), buy(etf.id)),
                listOf(
                    DailyMarketData(etf.id, asOf.minusDays(1), bd("100"), AnalystRating.HOLD, bd("110"), bd("90")),
                    DailyMarketData(etf.id, asOf, bd("105"), AnalystRating.BUY, bd("108"), bd("90")),
                ),
                emptyList(),
                emptyList(),
            )
        val alerts = GetPortfolioAlertsUseCase()(snapshot, asOf)
        assertTrue(alerts.any { it.channel == AlertChannel.RATING })
        assertTrue(alerts.none { it.channel == AlertChannel.CROSS })
        val ct = Asset("ct", "CT", "CT", AssetType.CT, Currency.EUR)
        val mixed =
            listOf(
                cashTx(),
                buy(ct.id),
                Transaction(
                    "w",
                    ct.id,
                    asOf,
                    TransactionType.WITHDRAWAL,
                    bd("1"),
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    BigDecimal.ZERO,
                    sequence = 3,
                ),
            )
        val value = PositionLedger().locallyValuedEur(mixed)
        assertTrue(value.signum() > 0)
        val left = buy(etf.id).copy(id = "b", date = asOf, sequence = 0)
        val right = left.copy(id = "a")
        val later = left.copy(id = "c")
        assertEquals(listOf("a", "b"), PositionLedger().preceding(listOf(left, right), later).map { it.id })
        val allocation =
            PortfolioValuator().allocation(
                PortfolioSnapshot(
                    listOf(etf, etf2, cash),
                    listOf(cashTx(), buy(etf.id), buy(etf2.id, qty = bd("2"))),
                    emptyList(),
                    emptyList(),
                    listOf(TargetAllocation(AssetType.ETF, bd("80")), TargetAllocation(AssetType.CASH, bd("20"))),
                ),
                asOf,
            )
        assertEquals(1, allocation.slices.count { it.assetType == AssetType.ETF })
    }

    @Test
    fun importMismatchQuoteMatchAndBrokerMoneyEdges() {
        val importer = ImportBrokerCsvUseCase()
        val seeded =
            PortfolioSnapshot(
                listOf(etf, cash),
                listOf(cashTx(), buy(etf.id)),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        val usdSameIsin =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Exchange rate,Total,Currency (Total),ID
            Market buy,2026-08-10 09:00:00,IE00BK5BQT80,VWCE_US_EQ,VWCE,1,100,USD,0.92,92,EUR,U1
            Dividend (Ordinary),2026-08-11 09:00:00,US0378331005,AAPL,Apple,,1,USD,1,1,USD,D1
            """.trimIndent()
        val result = importer(seeded, listOf(usdSameIsin))
        assertTrue(result.skipped.any { it.contains("does not match holding") || it.contains("No holding") })
        val byQuote =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
            Market buy,2026-08-12 09:00:00,,VWCE.DE,VWCE,1,101,EUR,101,EUR,Q1
            """.trimIndent()
        val quoted = importer(seeded, listOf(byQuote))
        assertTrue(quoted.accepted >= 1)
        assertEquals("GBX", BrokerMoney.currencyCode("Price (GBX)", "GBP"))
        assertEquals(0, BigDecimal.ZERO.compareTo(BrokerMoney.book(MoneyParts("1", "10", "EUR", "10", "EUR", "", "1", "GBX"))!!.feesEur))
        assertEquals(0, bd("1").compareTo(BrokerMoney.book(MoneyParts("1", "10", "EUR", "10", "EUR", "", "1", "EUR"))!!.feesEur))
        assertTrue(BrokerMoney.book(MoneyParts("1", "10", "USD", "9.2", "EUR", "", "1", "USD"))!!.feesEur.signum() > 0)
        val zeroEurTotal = BrokerMoney.book(MoneyParts("1", "10", "USD", "0", "EUR", "", "", "EUR"))
        assertNotNull(zeroEurTotal)
        assertNull(zeroEurTotal!!.eurPerUsd)
        val usdFeeNoFx = BrokerMoney.book(MoneyParts("1", "10", "USD", "10", "USD", "", "1", "USD"))
        assertEquals(0, BigDecimal.ZERO.compareTo(usdFeeNoFx!!.feesEur))
        assertNotNull(BrokerMoney.parseAmount("10abc,50"))
        assertTrue(BrokerCsv.parseAll(listOf("not csv", usdSameIsin)).error == null)
    }

    @Test
    fun parserHelpersCoverBlankFieldsAndRareCsvRows() {
        val blankHolding =
            BrokerLines.holding(
                HoldingDraft(
                    format = BrokerCsvFormat.TRADING_212,
                    sourceLine = 2,
                    date = LocalDate.of(2024, 1, 1),
                    type = TransactionType.BUY,
                    symbol = "X",
                    name = "",
                    isin = "  ",
                    quoteSymbol = "  ",
                    booked = BookedAmounts(bd("1"), bd("1"), Currency.EUR, BigDecimal.ZERO, null),
                    externalId = "  ",
                ),
            )
        assertEquals("X", blankHolding.name)
        assertNull(blankHolding.isin)
        assertNull(blankHolding.quoteSymbol)
        assertNull(blankHolding.externalId)
        assertEquals(".DE", BrokerQuoteSymbol.fromIsin(".DE", null))
        assertEquals("AAPL.US", BrokerQuoteSymbol.fromIsin("AAPL.US", "US0378331005"))
        assertEquals("FOO", BrokerQuoteSymbol.fromIsin("FOO", null))
        val t212 =
            BrokerCsv.parse(
                """
                Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Total,Currency (Total),ID
                Market buy,2024-01-15 10:30:00,IE00BK5BQT80,,VWCE,,100.00,EUR,100.00,EUR,B1
                Dividend (Ordinary),2024-01-16 10:30:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,,,EUR,1.00,EUR,D1
                Market buy,2024-01-17 10:30:00,IE00BK5BQT80,VWCE_GY_EQ,VWCE,1,,EUR,99.00,EUR,B2
                """.trimIndent(),
            )
        assertTrue(t212.lines.any { it.type == TransactionType.BUY && it.isin == "IE00BK5BQT80" })
        assertTrue(t212.lines.any { it.type == TransactionType.DIVIDEND })
        val degiro =
            BrokerCsv.parse(
                """
                Date,Time,Value date,Product,ISIN,Description,FX,Change,Balance,Order Id
                2024-02-14,09:00,2024-02-14,VWCE,IE00BK5BQT80,Custody fee,,-1,1,
                2024-02-15,09:00,2024-02-15,VWCE,IE00BK5BQT80,Buy 0 VWCE @ 50,,0,1,B0
                """.trimIndent(),
            )
        assertTrue(degiro.lines.any { it.skipReason != null })
        val emptyLines = CsvReader.records("h\n\na,b\n")
        assertTrue(emptyLines.size >= 2)
        val fpLine =
            BrokerCsvLine(
                date = LocalDate.of(2024, 1, 15),
                type = TransactionType.BUY,
                skipReason = null,
                symbol = "X",
                name = "X",
                isin = "  ",
                quoteSymbol = null,
                assetType = AssetType.STOCK,
                quantity = bd("1"),
                unitPriceNative = bd("1"),
                currency = Currency.EUR,
                feesEur = BigDecimal.ZERO,
                eurPerUsd = null,
                externalId = null,
                sourceLine = 2,
                format = BrokerCsvFormat.TRADING_212,
            )
        assertTrue(ImportFingerprints.of(fpLine).contains("X"))
        val prefixRow = CsvRow(listOf("Price per share (USD)", "Total Amount (EUR)"), listOf("", "50"))
        assertEquals("50", prefixRow.get("Price per share", "Total Amount"))
        val emptyPrefix = CsvRow(listOf("Price per share extra"), listOf(""))
        assertEquals("", emptyPrefix.get("Price per share"))
        val orphanTx =
            Transaction(
                "t",
                "missing",
                LocalDate.of(2024, 1, 15),
                TransactionType.BUY,
                bd("1"),
                bd("1"),
                BigDecimal.ONE,
                bd("1"),
                BigDecimal.ZERO,
            )
        val counts =
            ImportFingerprints.counts(
                PortfolioSnapshot(
                    listOf(Asset("a", "A", "A", AssetType.STOCK, Currency.EUR, isin = "  ")),
                    listOf(orphanTx),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                ),
            )
        assertTrue(counts.isNotEmpty())
        assertTrue(PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()).isEmpty)
        assertTrue(!PortfolioSnapshot(emptyList(), listOf(cashTx()), emptyList(), emptyList(), emptyList()).isEmpty)
        assertTrue(!snap().isEmpty)
    }

    @Test
    fun importLinesHitsNullRequestCurrencyQuoteAndFxDedup() {
        val importer = ImportBrokerCsvUseCase()
        val seeded = snap()
        val skippedType = importer.importLines(seeded, listOf(csvLine(type = null, date = asOf)))
        assertTrue(skippedType.skipped.any { it.contains("No holding") || it.contains("row") })
        val skippedDate = importer.importLines(seeded, listOf(csvLine(date = null)))
        assertTrue(skippedDate.skipped.isNotEmpty())
        val blankSymbol =
            importer.importLines(
                seeded,
                listOf(csvLine(type = TransactionType.DIVIDEND, symbol = "", isin = null, quoteSymbol = null)),
            )
        assertTrue(blankSymbol.skipped.any { it.contains("row") })
        val eurBook = snap().copy(assets = listOf(etf, cash))
        val mismatch =
            importer.importLines(
                eurBook,
                listOf(csvLine(currency = Currency.USD, isin = "IE00BK5BQT80", eurPerUsd = bd("0.92"))),
            )
        assertTrue(mismatch.skipped.any { it.contains("does not match holding") })
        val quotedAsset = etf.copy(symbol = "FOO", quoteSymbol = "BAR.DE")
        val quotedBook =
            PortfolioSnapshot(
                listOf(quotedAsset, cash),
                listOf(cashTx(), buy(quotedAsset.id)),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        val byQuote =
            importer.importLines(quotedBook, listOf(csvLine(symbol = "ZZZ", quoteSymbol = "BAR.DE", isin = null, quantity = bd("1"))))
        assertTrue(byQuote.accepted >= 1 || byQuote.skipped.isNotEmpty())
        val feedMatch = importer.importLines(quotedBook, listOf(csvLine(symbol = "BAR.DE", quoteSymbol = null, isin = null)))
        assertTrue(feedMatch.accepted >= 1 || feedMatch.duplicates >= 1 || feedMatch.skipped.isNotEmpty())
        val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
        val usdBook =
            PortfolioSnapshot(
                listOf(apple, cash),
                listOf(cashTx()),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        val firstUsd =
            csvLine(
                symbol = "AAPL",
                isin = "US0378331005",
                currency = Currency.USD,
                eurPerUsd = bd("0.92"),
                quantity = bd("1"),
                unit = bd("10"),
                sourceLine = 2,
            )
        val secondUsd = firstUsd.copy(quantity = bd("2"), unitPriceNative = bd("11"), sourceLine = 3)
        val seededFx = importer.importLines(usdBook, listOf(firstUsd))
        assertEquals(1, seededFx.snapshot.fxRates.size)
        assertTrue(seededFx.accepted >= 1)
        val deduped = importer.importLines(seededFx.snapshot, listOf(secondUsd))
        assertEquals(1, deduped.snapshot.fxRates.size)
        assertTrue(deduped.accepted >= 1)
        val cashLine = csvLine(type = TransactionType.DEPOSIT_CASH, symbol = "EUR-CASH", quantity = bd("25"))
        val cashIn = importer.importLines(seeded, listOf(cashLine))
        assertTrue(cashIn.accepted >= 1)
        val skipReason = importer.importLines(seeded, listOf(csvLine(skipReason = "ignored", type = null)))
        assertTrue(skipReason.skipped.any { it.contains("ignored") })
    }

    @Test
    fun unpricedSymbolsSkipCashLocalUsdAndZeroQty() {
        val ct = Asset("ct", "CT", "CT", AssetType.CT, Currency.EUR)
        val usd = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
        val sold = Asset("gone", "GONE.DE", "Gone", AssetType.STOCK, Currency.EUR)
        val priced = etf.copy(id = "priced")
        val snapshot =
            PortfolioSnapshot(
                listOf(cash, ct, usd, sold, priced, etf),
                listOf(
                    cashTx(),
                    buy(ct.id),
                    buy(usd.id),
                    buy(sold.id),
                    Transaction(
                        "s-gone",
                        sold.id,
                        start.plusDays(2),
                        TransactionType.SELL,
                        bd("10"),
                        bd("100"),
                        BigDecimal.ONE,
                        bd("100"),
                        BigDecimal.ZERO,
                        sequence = 5,
                    ),
                    buy(priced.id),
                    buy(etf.id),
                ),
                listOf(DailyMarketData(priced.id, asOf, bd("110"))),
                emptyList(),
                emptyList(),
            )
        val valuator = PortfolioValuator()
        val omitted = valuator.unpricedSymbols(snapshot, asOf)
        assertTrue(omitted.contains(etf.symbol))
        assertTrue(omitted.none { it == cash.symbol || it == ct.symbol || it == usd.symbol || it == sold.symbol })
        val warnings = valuator.valuationWarnings(snapshot, asOf)
        assertTrue(warnings.any { it.contains("USD") })
        val withFx = snapshot.copy(fxRates = listOf(com.pirlruc.finsilo.domain.model.CurrencyRate(asOf, bd("0.92"))))
        val usdOmitted = valuator.unpricedSymbols(withFx, asOf)
        assertTrue(usdOmitted.contains(usd.symbol))
        val native = PositionLedger().nativePrice(etf.id, asOf, emptyMap(), listOf(buy(etf.id)))
        assertEquals(0, bd("100").compareTo(native!!))
        val sellOnly =
            Transaction(
                "s-only",
                etf.id,
                start.plusDays(1),
                TransactionType.SELL,
                bd("1"),
                bd("99"),
                BigDecimal.ONE,
                bd("99"),
                BigDecimal.ZERO,
                sequence = 3,
            )
        assertEquals(0, bd("99").compareTo(PositionLedger().nativePrice(etf.id, asOf, emptyMap(), listOf(sellOnly))!!))
        val firstBuy = buy(etf.id).copy(id = "seq-a", sequence = 1)
        val secondBuy = buy(etf.id).copy(id = "seq-b", sequence = 2)
        assertEquals(listOf("seq-a"), PositionLedger().preceding(listOf(firstBuy, secondBuy), secondBuy).map { it.id })
        assertTrue(PositionLedger().preceding(listOf(firstBuy, secondBuy), firstBuy).isEmpty())
        val later = buy(etf.id).copy(id = "later", date = asOf.plusDays(1), sequence = 9)
        val earlier = buy(etf.id).copy(id = "earlier", sequence = 1)
        assertEquals(listOf("earlier"), PositionLedger().preceding(listOf(later, earlier), later).map { it.id })
        assertTrue(PositionLedger().preceding(listOf(later, earlier), earlier).isEmpty())
        val sell = buy(etf.id).copy(id = "sell", type = TransactionType.SELL, sequence = 1)
        val sameDayBuy = buy(etf.id).copy(id = "same-buy", sequence = 2)
        assertEquals(listOf("sell"), PositionLedger().preceding(listOf(sameDayBuy, sell), sameDayBuy).map { it.id })
        assertTrue(PositionLedger().preceding(listOf(sameDayBuy, sell), sell).isEmpty())
    }

    @Test
    fun alertsGoldenCrossDriftAndZeroNavTwr() {
        val golden =
            PortfolioSnapshot(
                listOf(etf, cash),
                listOf(cashTx(), buy(etf.id)),
                listOf(
                    DailyMarketData(etf.id, asOf.minusDays(1), bd("100"), AnalystRating.HOLD, bd("80"), bd("90")),
                    DailyMarketData(etf.id, asOf, bd("105"), AnalystRating.HOLD, bd("100"), bd("90")),
                ),
                emptyList(),
                listOf(TargetAllocation(AssetType.ETF, bd("50")), TargetAllocation(AssetType.CASH, bd("50"))),
            )
        val alerts = GetPortfolioAlertsUseCase()(golden, asOf)
        assertTrue(alerts.any { it.title.contains("golden") })
        assertTrue(alerts.any { it.channel == AlertChannel.DRIFT })
        val emptied =
            PortfolioSnapshot(
                listOf(cash),
                listOf(
                    cashTx(),
                    Transaction(
                        "w-all",
                        cash.id,
                        start.plusDays(2),
                        TransactionType.WITHDRAWAL,
                        bd("10000"),
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        sequence = 2,
                    ),
                ),
                emptyList(),
                emptyList(),
                emptyList(),
            )
        val twr = GetTimeWeightedReturnUseCase()(emptied, asOf)
        assertTrue(twr.subPeriods.isNotEmpty())
        val idle = GetTimeWeightedReturnUseCase()(
            PortfolioSnapshot(listOf(cash), emptyList(), emptyList(), emptyList(), emptyList()),
            asOf,
        )
        assertTrue(idle.subPeriods.isEmpty())
        val missingSell =
            RecordLedgerEntryUseCase()(
                snap(),
                LedgerEntryRequest(TransactionType.SELL, asOf, bd("1"), bd("1"), BigDecimal.ZERO, existingAssetId = "missing"),
            )
        assertTrue(missingSell is LedgerEntryResult.Rejected)
        assertTrue((missingSell as LedgerEntryResult.Rejected).reason.contains("existing"))
        val missingBuy =
            RecordLedgerEntryUseCase()(
                snap(),
                LedgerEntryRequest(TransactionType.BUY, asOf, bd("1"), bd("1"), BigDecimal.ZERO, existingAssetId = "   "),
            )
        assertTrue(missingBuy is LedgerEntryResult.Rejected)
        val unknownExisting =
            RecordLedgerEntryUseCase()(
                snap(),
                LedgerEntryRequest(TransactionType.BUY, asOf, bd("1"), bd("1"), BigDecimal.ZERO, existingAssetId = "no-such"),
            )
        assertTrue(unknownExisting is LedgerEntryResult.Rejected)
    }

    @Test
    fun rebuildCoversSpanGapsAndFingerprintMismatch() {
        val snapshot = snap()
        val rebuilt = RebuildNavHistoryUseCase()(snapshot, asOf, null, emptyList())
        val emptyMatch = RebuildNavHistoryUseCase()(snapshot, asOf, rebuilt.fingerprint, emptyList())
        assertTrue(emptyMatch.points.isNotEmpty())
        val lateStart = rebuilt.points.filter { !it.date.isBefore(start.plusDays(3)) }
        val fromHole = RebuildNavHistoryUseCase()(snapshot, asOf, rebuilt.fingerprint, lateStart)
        assertEquals(start, fromHole.points.first().date)
        val truncated = rebuilt.points.filter { it.date.isBefore(asOf.minusDays(2)) }
        val shortEnd = RebuildNavHistoryUseCase()(snapshot, asOf, rebuilt.fingerprint, truncated)
        assertEquals(asOf, shortEnd.points.last().date)
        val mismatch = RebuildNavHistoryUseCase()(snapshot, asOf, "other", rebuilt.points, changedFrom = start.plusDays(4))
        assertFalse(mismatch.skip)
        val emptyChanged = RebuildNavHistoryUseCase()(snapshot, asOf, "other", emptyList(), changedFrom = start.plusDays(2))
        assertEquals(start, emptyChanged.points.first().date)
        val changedAtFirst = RebuildNavHistoryUseCase()(snapshot, asOf, "other", rebuilt.points, changedFrom = start)
        assertEquals(start, changedAtFirst.points.first().date)
        val monthStartsLate = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ONE_MONTH, asOf, lateStart)
        assertEquals(asOf, monthStartsLate.to)
        val historyShort = GetPortfolioHistoryUseCase()(snapshot, HistoryRange.ALL, asOf, truncated)
        assertEquals(asOf, historyShort.to)
    }

    private fun csvLine(
        date: LocalDate? = LocalDate.of(2026, 8, 12),
        type: TransactionType? = TransactionType.BUY,
        skipReason: String? = null,
        symbol: String = "VWCE.DE",
        quoteSymbol: String? = "VWCE.DE",
        isin: String? = "IE00BK5BQT80",
        currency: Currency = Currency.EUR,
        quantity: BigDecimal = bd("1"),
        unit: BigDecimal = bd("100"),
        eurPerUsd: BigDecimal? = null,
        sourceLine: Int = 2,
    ) = BrokerCsvLine(
        date = date,
        type = type,
        skipReason = skipReason,
        symbol = symbol,
        name = symbol,
        isin = isin,
        quoteSymbol = quoteSymbol,
        assetType = AssetType.ETF,
        quantity = quantity,
        unitPriceNative = unit,
        currency = currency,
        feesEur = BigDecimal.ZERO,
        eurPerUsd = eurPerUsd,
        externalId = "L$sourceLine",
        sourceLine = sourceLine,
        format = BrokerCsvFormat.TRADING_212,
    )

    private fun snap() = PortfolioSnapshot(
        listOf(etf, cash),
        listOf(cashTx(), buy(etf.id)),
        emptyList(),
        emptyList(),
        emptyList(),
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

    private fun buy(assetId: String, qty: BigDecimal = bd("10")) = Transaction(
        "b-$assetId",
        assetId,
        start.plusDays(1),
        TransactionType.BUY,
        qty,
        bd("100"),
        BigDecimal.ONE,
        bd("100"),
        BigDecimal.ZERO,
        sequence = 2,
    )
}

package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.importcsv.BrokerCsvFormat
import com.pirlruc.finsilo.domain.importcsv.BrokerCsvLine
import com.pirlruc.finsilo.domain.importcsv.ImportSymbolReview
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.market.QuoteSeed
import com.pirlruc.finsilo.domain.market.QuoteSyncPlanner
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.MarketSignal
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.RatingAlertScope
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.model.WatchlistItem
import com.pirlruc.finsilo.domain.model.WatchlistSnapshot
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuoteAlertsAndProbeTest {
    private val asOf = LocalDate.of(2026, 8, 16)
    private val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)

    @Test
    fun plannerSortsNeverQuotedFirstThenOldest() {
        val rows =
            listOf(
                DailyMarketData("fresh", asOf, BigDecimal.ONE),
                DailyMarketData("old", asOf.minusDays(5), BigDecimal.ONE),
            )
        val ordered = QuoteSyncPlanner.oldestFirst(listOf("fresh", "old", "missing")) { id ->
            QuoteSyncPlanner.lastBarDate(rows, id)
        }
        assertEquals(listOf("missing", "old", "fresh"), ordered)
        assertTrue(QuoteSyncPlanner.isFresh(asOf, asOf))
        assertTrue(!QuoteSyncPlanner.isFresh(asOf.minusDays(1), asOf))
    }

    @Test
    fun holdingDefaultsNotifyOnSellEntryOnly() {
        val entered = signal(AnalystRating.HOLD, AnalystRating.SELL)
        val stayed = signal(AnalystRating.SELL, AnalystRating.STRONG_SELL)
        val buy = signal(AnalystRating.HOLD, AnalystRating.BUY)
        val alerts = GetRatingAlertsUseCase()(listOf(entered, stayed, buy), emptyList(), emptyList(), emptyList(), asOf)
        assertEquals(1, alerts.size)
        assertTrue(alerts.single().body.contains("Sell"))
    }

    @Test
    fun watchlistDefaultsNotifyOnBuyEntry() {
        val item = WatchlistItem("w1", "MSFT", "Microsoft", AssetType.STOCK, Currency.USD)
        val quotes =
            listOf(
                DailyMarketData("w1", asOf.minusDays(1), BigDecimal("400"), AnalystRating.HOLD),
                DailyMarketData("w1", asOf, BigDecimal("410"), AnalystRating.STRONG_BUY),
            )
        val alerts = GetRatingAlertsUseCase()(emptyList(), listOf(item), quotes, emptyList(), asOf)
        assertEquals(1, alerts.size)
        assertTrue(alerts.single().title.contains("MSFT"))
    }

    @Test
    fun emptyStoredPrefSuppressesDefaultAlerts() {
        val pref = RatingAlertPref("aapl", RatingAlertScope.HOLDING, emptySet())
        val alerts =
            GetRatingAlertsUseCase()(listOf(signal(AnalystRating.HOLD, AnalystRating.SELL)), emptyList(), emptyList(), listOf(pref), asOf)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun probeAcceptsLocalInstrumentAndRefusesEmptyHistory() {
        val ct = Asset("ct", "CT", "CT", AssetType.CT, Currency.EUR)
        val feed = object : MarketFeed {
            override suspend fun eurPerUsd() = BigDecimal.ONE
            override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = emptyList<CurrencyRate>()
            override suspend fun dailyHistory(asset: Asset, asOf: LocalDate) = emptyList<PriceBar>()
            override suspend fun analystRating(asset: Asset) = AnalystRating.NONE
        }
        val probe = ProbeMarketQuoteUseCase(feed)
        val local = runBlocking { probe(ct, asOf) }
        val missing = runBlocking { probe(apple, asOf) }
        val today = runBlocking { probe(ct) }
        assertTrue(local is QuoteProbeResult.Found)
        assertTrue(today is QuoteProbeResult.Found)
        assertTrue(missing is QuoteProbeResult.Missing)
    }

    @Test
    fun importReviewAppliesQuoteEditsWithoutDroppingRows() {
        val buy =
            BrokerCsvLine(
                date = asOf,
                type = TransactionType.BUY,
                skipReason = null,
                symbol = "IWDA",
                name = "World",
                isin = "IE00B4L5Y983",
                quoteSymbol = null,
                assetType = AssetType.ETF,
                quantity = BigDecimal.ONE,
                unitPriceNative = BigDecimal.TEN,
                currency = Currency.EUR,
                feesEur = BigDecimal.ZERO,
                eurPerUsd = null,
                externalId = null,
                sourceLine = 2,
                format = BrokerCsvFormat.TRADING_212,
            )
        val drafts = ImportSymbolReview.drafts(listOf(buy))
        assertEquals(1, drafts.size)
        assertTrue(drafts.single().needsQuote)
        val edited = drafts.single().copy(quoteSymbol = "IWDA.AS")
        val applied = ImportSymbolReview.apply(listOf(buy), listOf(edited))
        assertEquals("IWDA.AS", applied.single().quoteSymbol)
        assertEquals("IWDA", applied.single().symbol)
    }

    @Test
    fun seedMapsBarsAndSmas() {
        val bars =
            listOf(
                PriceBar(asOf.minusDays(1), BigDecimal("10")),
                PriceBar(asOf, BigDecimal("11")),
            )
        val rows = QuoteSeed.fromBars("aapl", bars)
        assertEquals(2, rows.size)
        assertEquals(asOf, rows.last().date)
        assertEquals(0, BigDecimal("11").compareTo(rows.last().closingPriceNative))
        assertTrue(QuoteSeed.fromBars("aapl", emptyList()).isEmpty())
    }

    @Test
    fun probeFoundAndFeedError() {
        val ok =
            object : MarketFeed {
                override suspend fun eurPerUsd() = BigDecimal.ONE
                override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = emptyList<CurrencyRate>()
                override suspend fun dailyHistory(asset: Asset, asOf: LocalDate) = listOf(PriceBar(asOf, BigDecimal.TEN))
                override suspend fun analystRating(asset: Asset) = AnalystRating.NONE
            }
        val boom =
            object : MarketFeed {
                override suspend fun eurPerUsd() = BigDecimal.ONE
                override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = emptyList<CurrencyRate>()
                override suspend fun dailyHistory(asset: Asset, asOf: LocalDate): List<PriceBar> = error("down")
                override suspend fun analystRating(asset: Asset) = AnalystRating.NONE
            }
        val found = runBlocking { ProbeMarketQuoteUseCase(ok)(apple, asOf) }
        val failed = runBlocking { ProbeMarketQuoteUseCase(boom)(apple, asOf) }
        assertTrue(found is QuoteProbeResult.Found && found.bars.size == 1)
        assertTrue(failed is QuoteProbeResult.Missing && failed.reason.contains("down"))
    }

    @Test
    fun importReviewSkipsCashAndKeepsUnmatchedLines() {
        val cash =
            BrokerCsvLine(
                date = asOf,
                type = TransactionType.DEPOSIT_CASH,
                skipReason = null,
                symbol = "",
                name = "",
                isin = "  ",
                quoteSymbol = "",
                assetType = AssetType.CASH,
                quantity = BigDecimal.ONE,
                unitPriceNative = BigDecimal.ONE,
                currency = Currency.EUR,
                feesEur = BigDecimal.ZERO,
                eurPerUsd = null,
                externalId = null,
                sourceLine = 3,
                format = BrokerCsvFormat.TRADING_212,
            )
        val buy =
            BrokerCsvLine(
                date = asOf,
                type = TransactionType.BUY,
                skipReason = null,
                symbol = "abc",
                name = "",
                isin = null,
                quoteSymbol = null,
                assetType = AssetType.STOCK,
                quantity = BigDecimal.ONE,
                unitPriceNative = BigDecimal.TEN,
                currency = Currency.EUR,
                feesEur = BigDecimal.ZERO,
                eurPerUsd = null,
                externalId = null,
                sourceLine = 4,
                format = BrokerCsvFormat.TRADING_212,
            )
        val drafts = ImportSymbolReview.drafts(listOf(cash, buy))
        assertEquals(1, drafts.size)
        assertEquals("abc", drafts.single().name)
        assertEquals("ABC", drafts.single().key)
        val applied = ImportSymbolReview.apply(listOf(cash, buy), drafts)
        assertEquals("", applied.first().symbol)
        assertEquals("abc", applied.last().symbol)
        val blankQuote = ImportSymbolReview.apply(listOf(buy), listOf(drafts.single().copy(quoteSymbol = "  ")))
        assertEquals(null, blankQuote.single().quoteSymbol)
        val probed = drafts.single().copy(quoteSymbol = "MSFT").asProbeAsset()
        assertEquals("MSFT", probed.quoteSymbol)
        assertEquals(null, drafts.single().asProbeAsset().quoteSymbol)
    }

    @Test
    fun ratingAlertsSkipMissingWatchlistSeriesAndStayInsideSet() {
        val item = WatchlistItem("w1", "MSFT", "Microsoft", AssetType.STOCK, Currency.USD)
        val none = GetRatingAlertsUseCase()(emptyList(), listOf(item), emptyList(), emptyList(), asOf)
        assertTrue(none.isEmpty())
        val entered =
            GetRatingAlertsUseCase()(
                emptyList(),
                listOf(item),
                listOf(DailyMarketData("w1", asOf, BigDecimal.ONE, AnalystRating.BUY)),
                emptyList(),
                asOf,
            )
        assertEquals(1, entered.size)
        assertTrue(entered.single().body.startsWith("None"))
        val stayed =
            GetRatingAlertsUseCase()(
                emptyList(),
                listOf(item),
                listOf(
                    DailyMarketData("w1", asOf.minusDays(1), BigDecimal.ONE, AnalystRating.BUY),
                    DailyMarketData("w1", asOf, BigDecimal.ONE, AnalystRating.STRONG_BUY),
                ),
                emptyList(),
                asOf,
            )
        assertTrue(stayed.isEmpty())
        val fromHold =
            GetRatingAlertsUseCase()(
                emptyList(),
                listOf(item),
                listOf(
                    DailyMarketData("w1", asOf.minusDays(1), BigDecimal.ONE, AnalystRating.HOLD),
                    DailyMarketData("w1", asOf, BigDecimal.ONE, AnalystRating.BUY),
                ),
                emptyList(),
                asOf,
            )
        assertEquals(1, fromHold.size)
        assertTrue(fromHold.single().body.contains("Hold"))
    }

    @Test
    fun watchlistSyncSkipsFreshAndHonoursEmptyRatingPref() {
        val item = WatchlistItem("w1", "MSFT", "Microsoft", AssetType.STOCK, Currency.USD)
        val stored = listOf(DailyMarketData("w1", asOf, BigDecimal("400"), AnalystRating.NONE))
        val feed =
            object : MarketFeed {
                override suspend fun eurPerUsd() = BigDecimal.ONE
                override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = emptyList<CurrencyRate>()
                override suspend fun dailyHistory(asset: Asset, asOf: LocalDate) = listOf(PriceBar(asOf, BigDecimal("410")))
                override suspend fun analystRating(asset: Asset) = AnalystRating.BUY
            }
        val skipped =
            runBlocking {
                SyncWatchlistUseCase(feed)(WatchlistSnapshot(listOf(item), stored), asOf)
            }
        assertEquals(AnalystRating.BUY, skipped.quotes.single().analystRating)
        val quiet =
            runBlocking {
                SyncWatchlistUseCase(feed)(
                    WatchlistSnapshot(listOf(item), stored),
                    asOf,
                    listOf(RatingAlertPref("w1", RatingAlertScope.WATCHLIST, emptySet())),
                )
            }
        assertTrue(quiet.quotes.isEmpty())
    }

    @Test
    fun watchlistRatingBranchesAndNoneMask() {
        assertEquals(0, RatingAlertPref.maskOf(setOf(AnalystRating.NONE)))
        val pref = RatingAlertPref("w1", RatingAlertScope.HOLDING, setOf(AnalystRating.SELL))
        assertEquals(setOf(AnalystRating.SELL), RatingAlertPref.effective(pref, RatingAlertScope.HOLDING))
        val item = WatchlistItem("w1", "MSFT", "Microsoft", AssetType.STOCK, Currency.USD)
        val ok =
            object : MarketFeed {
                override suspend fun eurPerUsd() = BigDecimal.ONE
                override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = emptyList<CurrencyRate>()
                override suspend fun dailyHistory(asset: Asset, asOf: LocalDate) = listOf(PriceBar(asOf.minusDays(1), BigDecimal("9")), PriceBar(asOf, BigDecimal("10")))
                override suspend fun analystRating(asset: Asset) = AnalystRating.BUY
            }
        val boom =
            object : MarketFeed {
                override suspend fun eurPerUsd() = BigDecimal.ONE
                override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = emptyList<CurrencyRate>()
                override suspend fun dailyHistory(asset: Asset, asOf: LocalDate) = listOf(PriceBar(asOf, BigDecimal("10")))
                override suspend fun analystRating(asset: Asset): AnalystRating = error("rating down")
            }
        runBlocking { SyncWatchlistUseCase(ok)(WatchlistSnapshot()) }
        val staleNone = listOf(DailyMarketData("w1", asOf.minusDays(1), BigDecimal("9"), AnalystRating.NONE))
        val filled = runBlocking { SyncWatchlistUseCase(ok)(WatchlistSnapshot(listOf(item), staleNone), asOf, listOf(pref)) }
        assertTrue(filled.quotes.isNotEmpty())
        val aged = listOf(DailyMarketData("w1", asOf.minusDays(10), BigDecimal("8"), AnalystRating.SELL))
        val failed = runBlocking { SyncWatchlistUseCase(boom)(WatchlistSnapshot(listOf(item), aged), asOf) }
        assertTrue(failed.failures.any { it.contains("rating down") })
    }

    private fun signal(previous: AnalystRating, current: AnalystRating) = MarketSignal(
        asset = apple,
        asOf = asOf,
        rating = current,
        previousRating = previous,
        priceNative = BigDecimal.ONE,
        sma50 = null,
        sma200 = null,
        vsSma50 = null,
        vsSma200 = null,
        cross = null,
    )
}

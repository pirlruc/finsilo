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
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.LedgerTemplate
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.model.RealizedKind
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.model.WatchlistItem
import com.pirlruc.finsilo.domain.model.WatchlistSnapshot
import com.pirlruc.finsilo.domain.portfolio.MoneyMath.bd
import com.pirlruc.finsilo.domain.portfolio.PortfolioValuator
import com.pirlruc.finsilo.domain.sample.SamplePortfolioFactory
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BacklogFeaturesTest {
    private val asOf = LocalDate.of(2026, 8, 16)
    private val cash = Asset("cash", "EUR-CASH", "Cash", AssetType.CASH, Currency.EUR)
    private val ct = Asset("ct", "CT", "Certificados", AssetType.CT, Currency.EUR)
    private val etf = Asset("vwce", "VWCE.DE", "ETF", AssetType.ETF, Currency.EUR)

    @Test
    fun backfillAssignsMonotonicSequenceInsteadOfUuidTieBreak() {
        val cheap = buy("zzz", etf.id, asOf, bd("1"), bd("10"), 0)
        val expensive = buy("aaa", etf.id, asOf, bd("1"), bd("90"), 0)
        val filled = BackfillLedgerSequenceUseCase()(listOf(cheap, expensive))
        assertEquals(listOf("aaa", "zzz"), filled.map { it.id })
        assertEquals(listOf(1L, 2L), filled.map { it.sequence })
        assertEquals(filled, BackfillLedgerSequenceUseCase()(filled))
    }

    @Test
    fun manualCloseRefusesListedTickersAndValuesLocalInstrument() {
        val listed = RecordManualQuoteUseCase()(book(etfBuy()), etf.id, asOf, bd("110"))
        assertTrue(listed is ManualQuoteResult.Rejected)
        val accepted = RecordManualQuoteUseCase()(book(ctBuy()), ct.id, asOf, bd("1.10"))
        val row = (accepted as ManualQuoteResult.Accepted).row
        val valued =
            PortfolioValuator().allocation(
                book(ctBuy()).copy(marketData = listOf(row)),
                asOf,
            )
        val holding = valued.holdings.single { it.asset.id == ct.id }
        assertEquals(0, bd("3300").compareTo(holding.valueEur))
        assertEquals(0, bd("1.10").compareTo(holding.priceNative))
        assertEquals(Currency.EUR, holding.quoteCurrency)
    }

    @Test
    fun priceThresholdsFireOnPercentAndEurCrossFromStoredBars() {
        val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(apple, cash),
                transactions = listOf(deposit(), buy("b", apple.id, asOf.minusDays(2), bd("1"), bd("100"), 2)),
                marketData =
                listOf(
                    DailyMarketData(apple.id, asOf.minusDays(1), bd("100"), AnalystRating.NONE),
                    DailyMarketData(apple.id, asOf, bd("110"), AnalystRating.NONE),
                ),
                fxRates = listOf(com.pirlruc.finsilo.domain.model.CurrencyRate(asOf, bd("1"))),
                targets = emptyList(),
            )
        val alerts =
            GetPriceThresholdAlertsUseCase()(
                snapshot,
                listOf(PriceAlertThreshold(apple.id, eurLevel = bd("105"), percentMove = bd("5"))),
                asOf,
            )
        assertEquals(2, alerts.size)
        assertTrue(alerts.all { it.channel == AlertChannel.THRESHOLD })
    }

    @Test
    fun realizedGainsCsvKeepsDomainFiguresAndLabelsRedemptions() {
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(cash, ct),
                transactions = listOf(deposit(), ctBuy(), sell(ct.id, bd("400"), bd("1.10"))),
                marketData = emptyList(),
                fxRates = emptyList(),
                targets = emptyList(),
            )
        val report = GetRealizedGainsUseCase()(snapshot, 2026)
        val csv = RealizedGainsCsv.write(report)
        assertTrue(csv.startsWith(RealizedGainsCsv.HEADER))
        assertTrue(csv.contains("Resgate"))
        assertTrue(csv.contains(report.lines.single().gainEur.setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()))
        assertEquals(RealizedKind.REDEMPTION, report.lines.single().kind)
    }

    @Test
    fun backupRoundTripAndTruncatedFileIsRefused() {
        val snapshot = SamplePortfolioFactory.create(asOf)
        val bytes = LedgerBackupCodec.encrypt(snapshot, "ABCD-1234-EFGH-5678")
        val restored = LedgerBackupCodec.decrypt(bytes, "ABCD1234EFGH5678") as LedgerBackupResult.Restored
        assertEquals(snapshot.assets.size, restored.snapshot.assets.size)
        assertEquals(snapshot.transactions.size, restored.snapshot.transactions.size)
        val refused = LedgerBackupCodec.decrypt(bytes.copyOf(10), "ABCD1234EFGH5678")
        assertTrue(refused is LedgerBackupResult.Refused)
        assertTrue((refused as LedgerBackupResult.Refused).reason.contains("truncated"))
    }

    @Test
    fun backupRoundTripKeepsWatchlistTemplatesAndThresholds() {
        val snapshot = SamplePortfolioFactory.create(asOf)
        val extras =
            LedgerBackupExtras(
                watchlist = WatchlistSnapshot(
                    items = listOf(WatchlistItem("w1", "MSFT", "Microsoft", AssetType.STOCK, Currency.USD)),
                    quotes = listOf(DailyMarketData("w1", asOf, bd("400"), AnalystRating.NONE)),
                ),
                templates = listOf(
                    LedgerTemplate("t1", "Cash", TransactionType.DEPOSIT_CASH, quantity = "250"),
                ),
                thresholds = listOf(PriceAlertThreshold(snapshot.assets.first().id, eurLevel = bd("10"))),
            )
        val bytes = LedgerBackupCodec.encrypt(snapshot, "ABCD1234EFGH5678", extras)
        val restored = LedgerBackupCodec.decrypt(bytes, "ABCD1234EFGH5678") as LedgerBackupResult.Restored
        assertEquals("MSFT", restored.extras.watchlist.items.single().symbol)
        assertEquals("Cash", restored.extras.templates.single().label)
        assertEquals(0, bd("10").compareTo(checkNotNull(restored.extras.thresholds.single().eurLevel)))
        val v1 = LedgerBackupText.decode("FSILO-LEDGER-1\nEND") as LedgerBackupResult.Restored
        assertTrue(v1.extras.watchlist.items.isEmpty())
    }

    @Test
    fun watchlistQuotesDoNotChangeLiveAllocation() {
        val live = book(etfBuy())
        val watch =
            WatchlistItem("w1", "MSFT", "Microsoft", AssetType.STOCK, Currency.USD)
        val extra =
            live.copy(
                marketData = live.marketData + DailyMarketData(watch.id, asOf, bd("400"), AnalystRating.NONE),
            )
        val valuator = PortfolioValuator()
        assertEquals(valuator.allocation(live, asOf).totalValueEur, valuator.allocation(extra, asOf).totalValueEur)
        val synced =
            runBlocking {
                SyncWatchlistUseCase(
                    object : MarketFeed {
                        override suspend fun eurPerUsd() = bd("1")
                        override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate) = emptyList<com.pirlruc.finsilo.domain.model.CurrencyRate>()
                        override suspend fun dailyHistory(asset: Asset, asOf: LocalDate) = listOf(PriceBar(asOf.minusDays(1), bd("10")), PriceBar(asOf, bd("11")))
                        override suspend fun analystRating(asset: Asset) = AnalystRating.NONE
                    },
                )(WatchlistSnapshot(listOf(watch)), asOf)
            }
        assertTrue(synced.quotes.isNotEmpty())
        assertTrue(synced.quotes.none { quote -> live.assets.any { it.id == quote.assetId } })
    }

    @Test
    fun dashboardCopyWarnsOnCsvFundedBuysAndSample() {
        val imported = book(deposit(), etfBuy())
        assertTrue(DashboardCopy.csvFundedBuys(imported))
        val sample = SamplePortfolioFactory.create(asOf)
        assertTrue(DashboardCopy.extras(sample).contains(DashboardCopy.SAMPLE_CROSS))
        val report = GetDashboardUseCase()(imported, com.pirlruc.finsilo.domain.model.HistoryRange.ALL, asOf)
        assertTrue(report.warnings.contains(DashboardCopy.CSV_TWR))
        assertTrue(report.allocation.holdings.isNotEmpty())
    }

    @Test
    fun usdHoldingExposesNativeQuoteBesideEurValue() {
        val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(apple, cash),
                transactions = listOf(deposit(), buy("b", apple.id, asOf.minusDays(1), bd("2"), bd("100"), 2)),
                marketData = listOf(DailyMarketData(apple.id, asOf, bd("150"), AnalystRating.NONE)),
                fxRates = listOf(com.pirlruc.finsilo.domain.model.CurrencyRate(asOf, bd("0.90"))),
                targets = emptyList(),
            )
        val holding = PortfolioValuator().allocation(snapshot, asOf).holdings.single { it.asset.id == apple.id }
        assertEquals(0, bd("150").compareTo(holding.priceNative))
        assertEquals(Currency.USD, holding.quoteCurrency)
        assertEquals(0, bd("270").compareTo(holding.valueEur))
    }

    private fun book(vararg txs: Transaction) = PortfolioSnapshot(listOf(cash, ct, etf), txs.toList(), emptyList(), emptyList(), emptyList())

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
        sequence = 1,
    )

    private fun etfBuy() = buy("e", etf.id, asOf.minusDays(10), bd("10"), bd("100"), 2)

    private fun ctBuy() = buy("ctb", ct.id, asOf.minusDays(5), bd("3000"), bd("1"), 2)

    private fun sell(assetId: String, qty: BigDecimal, price: BigDecimal) = Transaction(
        "s",
        assetId,
        asOf,
        TransactionType.SELL,
        qty,
        price,
        BigDecimal.ONE,
        price,
        BigDecimal.ZERO,
        sequence = 3,
    )

    private fun buy(
        id: String,
        assetId: String,
        date: LocalDate,
        qty: BigDecimal,
        price: BigDecimal,
        sequence: Long,
    ) = Transaction(
        id,
        assetId,
        date,
        TransactionType.BUY,
        qty,
        price,
        BigDecimal.ONE,
        price,
        BigDecimal.ZERO,
        sequence = sequence,
    )
}

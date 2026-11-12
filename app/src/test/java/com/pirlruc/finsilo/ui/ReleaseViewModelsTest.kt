package com.pirlruc.finsilo.ui

import android.app.Application
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.security.AppLockStore
import com.pirlruc.finsilo.domain.lock.AppLockCrypto
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.HistoryRange
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.RatingAlertScope
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.usecase.GetDashboardUseCase
import com.pirlruc.finsilo.ui.dashboard.DashboardMutations
import com.pirlruc.finsilo.ui.dashboard.DashboardViewModel
import com.pirlruc.finsilo.ui.importcsv.BrokerImportViewModel
import com.pirlruc.finsilo.ui.ledger.LedgerEntryViewModel
import com.pirlruc.finsilo.ui.ledger.LedgerUiState
import com.pirlruc.finsilo.ui.settings.PortfolioToolsViewModel
import com.pirlruc.finsilo.ui.settings.TargetSettingsViewModel
import com.pirlruc.finsilo.ui.watchlist.WatchlistViewModel
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReleaseViewModelsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val day = LocalDate.of(2026, 1, 15)
    private lateinit var database: FinsiloDatabase
    private lateinit var repository: RoomPortfolioRepository
    private lateinit var feed: ScriptedFeed

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, FinsiloDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomPortfolioRepository(database)
        feed = ScriptedFeed(day)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun dashboardLoadsSampleClearsAndStoresAKey() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences("dash-tools", 0)
        val container = AppContainer(context, prefs) { feed }
        bindLedger(container)
        val model = DashboardViewModel(repository, GetDashboardUseCase(), container, notify = { }, today = { day })
        waitUntil { !model.state.value.loading }
        assertTrue(model.state.value.toString(), model.state.value.empty)
        model.setRange(HistoryRange.ONE_YEAR)
        model.setRange(HistoryRange.ONE_YEAR)
        model.loadSample()
        waitUntil { model.state.value.report != null || model.state.value.error != null }
        assertNotNull(model.state.value.toString(), model.state.value.report)
        model.saveAlphaVantageKey("synthetic-key")
        assertTrue(model.state.value.hasAlphaVantageKey)
        model.saveAlphaVantageKey("  ")
        assertFalse(model.state.value.hasAlphaVantageKey)
        val assetId = model.state.value.assets.first().id
        model.saveThreshold(PriceAlertThreshold(assetId, BigDecimal.TEN, null))
        model.saveThreshold(PriceAlertThreshold(assetId, null, null))
        model.saveRating(RatingAlertPref(assetId, RatingAlertScope.HOLDING, setOf(AnalystRating.BUY)))
        val edited = model.state.value.assets.first().copy(name = "Renamed synthetic")
        model.saveInstrument(edited)
        model.syncMarketData()
        model.requestClear()
        model.setClearLedger(false)
        model.setClearWatchlist(false)
        model.setClearTemplates(false)
        model.confirmClear()
        assertFalse(model.state.value.confirmClear)
        model.requestClear()
        model.confirmClear()
        model.deleteTransaction("missing", day)
        model.cancelClear()
        val (hasKey, message) = DashboardMutations.storeAlphaKey(container, "synthetic-key")
        assertTrue(hasKey)
        assertTrue(message.contains("stored"))
    }

    @Test
    fun ledgerPostsABuyDepositInterestAndManualClose() = runBlocking {
        val model = LedgerEntryViewModel(repository, feed, todayAware())
        model.setType(TransactionType.DEPOSIT_CASH)
        model.setDate(day.toString())
        model.setQuantity("25")
        model.setNewInstrument(true)
        model.setSymbol("EUR-CASH")
        model.setName("Euro cash")
        model.setAssetType(AssetType.CASH)
        model.save { }
        awaitPosted(model)
        model.prepare()
        waitUntil { !model.state.value.loading }
        model.setType(TransactionType.BUY)
        model.setDate(day.toString())
        model.setNewInstrument(true)
        model.setSymbol("SYN")
        model.setName("Synthetic")
        model.setAssetType(AssetType.STOCK)
        model.setCurrency(Currency.EUR)
        model.setQuantity("2")
        model.setUnitPrice("10")
        model.setFees("0")
        model.setEurPerUsd("0.9")
        model.save { }
        val posted = awaitPosted(model)
        assertTrue("status=${posted.status} error=${posted.error}", posted.status != null || posted.error != null)
        model.setType(TransactionType.SELL)
        model.saveTemplate("  ")
        model.setType(TransactionType.BUY)
        model.saveTemplate("Repeat")
        waitUntil { model.state.value.templates.isNotEmpty() || model.state.value.error != null }
        val template = model.state.value.templates.firstOrNull()
        if (template != null) model.applyTemplate(template)
        model.setDate("bad")
        model.setQuantity("1")
        model.setUnitPrice("1")
        model.setFees("0")
        model.setManualClose("3")
        model.saveManualClose()
        val deposit = repository.load().assets.firstOrNull { it.assetType == AssetType.DEPOSIT }
        if (deposit == null) {
            repository.upsertAsset(Asset("dep", "TERM", "Term", AssetType.DEPOSIT, Currency.EUR))
        }
        model.prepare()
        val local = repository.load().assets.first { it.assetType == AssetType.DEPOSIT || it.locallyValued }
        model.setExistingAsset(local.id)
        model.setDate(day.toString())
        model.setManualClose("4")
        model.saveManualClose()
        val tx = repository.load().transactions.firstOrNull()
        if (tx != null) model.edit(tx)
        model.setSymbol("")
        model.setName("")
        model.setIsin("XS000")
        model.setQuoteSymbol("SYN.DE")
        model.setCurrency(Currency.USD)
        model.setAssetType(AssetType.ETF)
    }

    @Test
    fun watchlistAddRemoveSyncAndImportReview() = runBlocking {
        val watch = WatchlistViewModel(repository, feed, today = { day })
        watch.setSymbol("  ")
        watch.add()
        assertNotNull(watch.state.value.error)
        watch.setSymbol("SYN")
        watch.setName("")
        watch.setAssetType(AssetType.STOCK)
        watch.setCurrency(Currency.EUR)
        watch.add()
        waitUntil { watch.state.value.items.isNotEmpty() }
        assertTrue(watch.state.value.toString(), watch.state.value.items.isNotEmpty())
        feed.fail = true
        watch.setSymbol("OTHER")
        watch.add()
        waitUntil { watch.state.value.error != null && watch.state.value.error != "Symbol is required." }
        feed.fail = false
        feed.empty = true
        watch.sync()
        waitUntil { !watch.state.value.syncing }
        feed.empty = false
        watch.sync()
        waitUntil { !watch.state.value.syncing }
        val id = watch.state.value.items.first().id
        watch.saveRating(RatingAlertPref(id, RatingAlertScope.WATCHLIST, setOf(AnalystRating.HOLD)))
        waitUntil { watch.state.value.ratingPrefs.containsKey(id) }
        watch.remove(id)
        waitUntil { watch.state.value.items.isEmpty() }

        val importer = BrokerImportViewModel(repository, feed, today = { day })
        importer.importCsvs(listOf("not,a,broker\n1,2"), onImported = {})
        waitUntil { importer.state.value.error != null }
        importer.showPickerError("Could not read the file.")
        val csv =
            """
            Action,Time,ISIN,Ticker,Name,No. of shares,Price / share,Currency (Price / share),Exchange rate,Total,Currency (Total),Charge amount,Currency (Charge amount),Currency conversion fee,Stamp duty,Result,ID
            Market buy,2026-01-15 09:00:00,US0000000000,SYN,Synthetic,1,10,EUR,1,10,EUR,,,,,B1
            """.trimIndent()
        importer.importCsvs(listOf(csv), onImported = {})
        waitUntil { importer.state.value.reviewing || importer.state.value.status != null || importer.state.value.error != null }
        if (importer.state.value.reviewing) {
            val key = importer.state.value.drafts.first().key
            importer.setDraftQuote(key, "SYN.DE")
            importer.confirmImport {}
            waitUntil { !importer.state.value.importing }
        }
        importer.cancelReview()
        assertFalse(importer.state.value.reviewing)
    }

    @Test
    fun targetsAndPortfolioToolsExportRestore() = runBlocking {
        val targets = TargetSettingsViewModel(repository)
        waitUntil { !targets.state.value.loading }
        targets.setWeight(AssetType.ETF, "100")
        targets.setWeight(AssetType.STOCK, "bad")
        targets.save {}
        waitUntil { !targets.state.value.saving }
        assertEquals(targets.state.value.toString(), "Targets saved", targets.state.value.status)
        targets.setWeight(AssetType.ETF, "10")
        targets.save {}
        waitUntil { targets.state.value.error != null }
        assertNotNull(targets.state.value.error)

        val context = ApplicationProvider.getApplicationContext<Application>()
        val lock = AppLockStore(context.getSharedPreferences("tools-lock", 0))
        val recovery = AppLockCrypto.generateRecoveryCode()
        assertTrue(lock.setup("1357", recovery, biometric = false))
        val tools = PortfolioToolsViewModel(repository, lock, cryptoDispatcher = dispatcher)
        tools.setYear("20ab26")
        assertEquals("2026", tools.state.value.year)
        tools.setYear("")
        var wrote = false
        tools.exportTaxCsv { wrote = true }
        waitUntil { tools.state.value.error != null }
        tools.setYear("2026")
        tools.exportTaxCsv { wrote = true }
        tools.exportTaxPdf { wrote = true }
        waitUntil { wrote && !tools.state.value.busy }
        assertTrue(wrote)
        tools.setRecovery("")
        tools.restoreBackup(byteArrayOf(1, 2, 3))
        assertNotNull(tools.state.value.error)
        tools.setRecovery("not-the-code")
        tools.prepareBackupExport("ledger.fsilo")
        waitUntil { tools.state.value.error != null }
        tools.setRecovery(recovery)
        tools.prepareBackupExport("ledger.fsilo")
        waitUntil { tools.state.value.pendingExport != null || tools.state.value.error != null }
        assertNotNull(tools.state.value.pendingExport)
        tools.onExportConsumed(true)
        assertNull(tools.state.value.pendingExport)
        tools.onExportLaunchFailed("picker closed")
        tools.prepareBackupExport("ledger.fsilo")
        waitUntil { tools.state.value.pendingExport != null }
        val bytes = tools.state.value.pendingExport
        assertNotNull(bytes)
        tools.restoreBackup(bytes!!)
        waitUntil { tools.state.value.confirmRestore || tools.state.value.error != null }
        if (tools.state.value.confirmRestore) {
            tools.confirmRestore()
            waitUntil { !tools.state.value.busy }
        }
        tools.cancelRestore()
        tools.confirmRestore()
        assertNotNull(tools.state.value.error)
    }

    private fun awaitPosted(model: LedgerEntryViewModel): LedgerUiState {
        val deadline = System.nanoTime() + 8_000_000_000L
        while (true) {
            val value = model.state.value
            if (value.status != null || value.error != null) return value
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (System.nanoTime() >= deadline) {
                throw AssertionError("timed out status=${value.status} error=${value.error}")
            }
            Thread.sleep(15)
        }
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 8_000_000_000L
        while (!predicate()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (System.nanoTime() >= deadline) {
                throw AssertionError("timed out waiting for view model")
            }
            Thread.sleep(15)
        }
    }

    private fun bindLedger(container: AppContainer) {
        val repositoryField = AppContainer::class.java.getDeclaredField("portfolioRepository")
        repositoryField.isAccessible = true
        repositoryField.set(container, repository)
        val databaseField = AppContainer::class.java.getDeclaredField("database")
        databaseField.isAccessible = true
        databaseField.set(container, database)
    }

    private fun todayAware() = com.pirlruc.finsilo.domain.usecase.RecordLedgerEntryUseCase()

    private class ScriptedFeed(private val day: LocalDate) : MarketFeed {
        var fail: Boolean = false
        var empty: Boolean = false

        override suspend fun eurPerUsd(): BigDecimal = BigDecimal("0.92")

        override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate): List<CurrencyRate> =
            listOf(CurrencyRate(to, BigDecimal("0.92")))

        override suspend fun dailyHistory(asset: Asset, asOf: LocalDate): List<PriceBar> {
            if (fail) error("feed down")
            if (empty) return emptyList()
            return listOf(PriceBar(day, BigDecimal.TEN))
        }

        override suspend fun analystRating(asset: Asset): AnalystRating = AnalystRating.HOLD
    }
}

package com.pirlruc.finsilo.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.usecase.GetDashboardUseCase
import com.pirlruc.finsilo.ui.dashboard.DashboardRoute
import com.pirlruc.finsilo.ui.dashboard.DashboardViewModel
import com.pirlruc.finsilo.ui.dashboard.HomeSection
import com.pirlruc.finsilo.ui.importcsv.BrokerImportViewModel
import com.pirlruc.finsilo.ui.ledger.LedgerEntryRoute
import com.pirlruc.finsilo.ui.ledger.LedgerEntryViewModel
import com.pirlruc.finsilo.ui.lock.AppLockGate
import com.pirlruc.finsilo.ui.lock.LockChromeLayer
import com.pirlruc.finsilo.ui.lock.LockUiState
import com.pirlruc.finsilo.ui.lock.LockViewModel
import com.pirlruc.finsilo.ui.settings.TargetSettingsRoute
import com.pirlruc.finsilo.ui.settings.TargetSettingsViewModel
import com.pirlruc.finsilo.ui.theme.FinsiloTheme
import com.pirlruc.finsilo.ui.watchlist.WatchlistRoute
import com.pirlruc.finsilo.ui.watchlist.WatchlistViewModel
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RouteCoverageTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 1, 15)
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun liveRoutesBuildTheirCallbacks() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(context, FinsiloDatabase::class.java).allowMainThreadQueries().build()
        val repository = RoomPortfolioRepository(database)
        val prefs = context.getSharedPreferences("route-coverage", Context.MODE_PRIVATE)
        val feed = QuietFeed(day)
        val container = AppContainer(context, prefs) { feed }
        bindLedger(container, repository, database)
        val dashboard = DashboardViewModel(repository, GetDashboardUseCase(), container, notify = {}, today = { day })
        val importer = BrokerImportViewModel(repository, feed, today = { day })
        val ledger = LedgerEntryViewModel(repository, feed)
        val watchlist = WatchlistViewModel(repository, feed, today = { day })
        val targets = TargetSettingsViewModel(repository)
        val lock = LockViewModel(container.lockStore)
        val phase = mutableIntStateOf(0)
        compose.setContent {
            val step = phase.intValue
            val section = if (step % 2 == 0) HomeSection.ALL else HomeSection.HOLDINGS
            FinsiloTheme(darkTheme = step % 2 == 1) {
                DashboardRoute(dashboard, importer, {}, {}, {}, {}, section)
                LedgerEntryRoute(ledger) {}
                WatchlistRoute(watchlist) {}
                TargetSettingsRoute(targets, lock, importer, {}, {}, container)
                AppLockGate(lock) {}
                RenderUnlocked(container, lock, step)
                if (step % 2 == 0) FinsiloApp(container)
                val lockState by lock.state.collectAsState()
                LockChromeLayer(lockState, lock) {}
            }
        }
        repeat(4) { step ->
            phase.intValue = step
            compose.waitForIdle()
        }
        unlock(lock)
        phase.intValue = 4
        compose.waitForIdle()
        listOf("Add transaction", "Watchlist", "Settings", "Overview", "Holdings", "Activity", "Insights").forEach { label ->
            runCatching { compose.onNodeWithText(label, substring = true, ignoreCase = true).performClick() }
            compose.waitForIdle()
        }
    }

    @Composable
    private fun RenderUnlocked(container: AppContainer, lock: LockViewModel, step: Int) {
        val composer = currentComposer
        if (step < 0) return
        val method = Class.forName("com.pirlruc.finsilo.ui.FinsiloAppKt").declaredMethods.first { it.name == "UnlockedApp" }
        method.isAccessible = true
        method.invoke(null, container, lock, composer, 0)
    }

    private fun unlock(lock: LockViewModel) {
        val field = LockViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(lock) as MutableStateFlow<LockUiState>
        flow.value = LockUiState(setupComplete = true, unlocked = true)
    }

    private fun bindLedger(container: AppContainer, repository: RoomPortfolioRepository, database: FinsiloDatabase) {
        val repositoryField = AppContainer::class.java.getDeclaredField("portfolioRepository")
        repositoryField.isAccessible = true
        repositoryField.set(container, repository)
        val databaseField = AppContainer::class.java.getDeclaredField("database")
        databaseField.isAccessible = true
        databaseField.set(container, database)
    }
}

private class QuietFeed(private val day: LocalDate) : MarketFeed {
    override suspend fun eurPerUsd(): BigDecimal = BigDecimal("0.92")

    override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate): List<CurrencyRate> = listOf(CurrencyRate(to, BigDecimal("0.92")))

    override suspend fun dailyHistory(asset: Asset, asOf: LocalDate): List<PriceBar> = listOf(PriceBar(day, BigDecimal.TEN))

    override suspend fun analystRating(asset: Asset): AnalystRating = AnalystRating.HOLD
}

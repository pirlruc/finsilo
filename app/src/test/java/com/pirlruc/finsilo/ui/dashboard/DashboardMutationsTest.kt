package com.pirlruc.finsilo.ui.dashboard

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.AppContainer
import com.pirlruc.finsilo.data.ClearSelection
import com.pirlruc.finsilo.data.RoomPortfolioRepository
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.PriceAlertThreshold
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.domain.model.RatingAlertPref
import com.pirlruc.finsilo.domain.model.RatingAlertScope
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DashboardMutationsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val database = Room.inMemoryDatabaseBuilder(context, FinsiloDatabase::class.java).allowMainThreadQueries().build()
    private val repository = RoomPortfolioRepository(database)

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun clearSelectionThresholdAndRatingUpdates() = runTest {
        val requested = DashboardMutations.requestClear(DashboardUiState())
        assertTrue(requested.confirmClear)
        assertEquals(ClearSelection(ledger = true, watchlist = true, templates = true), DashboardMutations.selection(requested))
        DashboardMutations.applyClear(repository, ClearSelection(ledger = true, watchlist = false, templates = false))
        repository.upsertAsset(Asset("asset-1", "AAA", "Listed", AssetType.STOCK, Currency.EUR))

        val empty = PriceAlertThreshold("asset-1")
        val kept = DashboardMutations.thresholdSaved(DashboardUiState(), empty.copy(eurLevel = BigDecimal.TEN))
        assertEquals(BigDecimal.TEN, kept.thresholds.getValue("asset-1").eurLevel)
        val removed = DashboardMutations.thresholdSaved(kept, empty)
        assertFalse(removed.thresholds.containsKey("asset-1"))

        val rating = RatingAlertPref("asset-1", RatingAlertScope.HOLDING, setOf(AnalystRating.BUY))
        val rated = DashboardMutations.ratingSaved(DashboardUiState(), rating)
        assertEquals(rating, rated.ratingPrefs.getValue("asset-1"))

        DashboardMutations.saveThreshold(repository, empty.copy(eurLevel = BigDecimal.ONE))
        DashboardMutations.saveRating(repository, rating)
        assertEquals(1, repository.loadThresholds().size)
        assertEquals(1, repository.loadRatingAlerts().size)
    }

    @Test
    fun syncQuotesCountsSkippedFeedsAndStoresTheAlphaKey() = runTest {
        val prefs = context.getSharedPreferences("mutations", Context.MODE_PRIVATE)
        val quiet = AppContainer(context, prefs) { QuietFeed() }
        val message = DashboardMutations.syncQuotes(repository, quiet, {}, LocalDate.of(2026, 8, 16))
        assertEquals("Updated 0 daily rows", message)

        val failing = AppContainer(context, prefs) { FailingFeed() }
        val skipped = DashboardMutations.syncQuotes(repository, failing, {}, LocalDate.of(2026, 8, 16))
        assertTrue(skipped.contains("skipped"))

        val cleared = DashboardMutations.storeAlphaKey(quiet, "  ")
        assertFalse(cleared.first)
        assertTrue(cleared.second.contains("cleared"))
        val stored = DashboardMutations.storeAlphaKey(quiet, "demo-key")
        assertTrue(stored.first)
        assertEquals("demo-key", quiet.keys.alphaVantageKey())

        assertFalse(quiet.isLedgerOpen())
        assertThrows(IllegalStateException::class.java) { quiet.repository }
        quiet.closeLedger()
        assertFalse(quiet.isLedgerOpen())
    }
}

private class QuietFeed : MarketFeed {
    override suspend fun eurPerUsd(): BigDecimal = BigDecimal.ONE

    override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate): List<CurrencyRate> = emptyList()

    override suspend fun dailyHistory(asset: Asset, asOf: LocalDate): List<PriceBar> = emptyList()

    override suspend fun analystRating(asset: Asset): AnalystRating = AnalystRating.NONE
}

private class FailingFeed : MarketFeed {
    override suspend fun eurPerUsd(): BigDecimal = error("fx down")

    override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate): List<CurrencyRate> = error("fx history down")

    override suspend fun dailyHistory(asset: Asset, asOf: LocalDate): List<PriceBar> = error("history down")

    override suspend fun analystRating(asset: Asset): AnalystRating = error("rating down")
}

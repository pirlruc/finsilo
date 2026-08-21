package com.pirlruc.finsilo.domain.usecase

import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.PriceBar
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncMarketDataUseCaseTest {
    private val asOf = LocalDate.of(2026, 8, 16)
    private val listedPpr =
        Asset("ppr", "PPR Moderado", "PPR", AssetType.PPR, Currency.EUR, quoteSymbol = "VWCE.DE")
    private val gold = Asset("gold", "XAU", "Gold", AssetType.COMMODITY, Currency.USD)
    private val apple = Asset("aapl", "AAPL", "Apple", AssetType.STOCK, Currency.USD)

    @Test
    fun unlistedPprIsSkipped() {
        val unlisted = Asset("ppr-u", "PTYAAAA00001", "PPR", AssetType.PPR, Currency.EUR)
        val feed = RecordingFeed(history = emptyMap())
        val result =
            kotlinx.coroutines.runBlocking {
                SyncMarketDataUseCase(feed)(snap(unlisted), asOf)
            }
        assertTrue(feed.historySymbols.isEmpty())
        assertTrue(result.marketData.isEmpty())
    }

    @Test
    fun listedPprIsQuotedLikeAnEtf() {
        val feed =
            RecordingFeed(
                history = mapOf(listedPpr.id to listOf(PriceBar(asOf, BigDecimal("110")))),
            )
        val result =
            kotlinx.coroutines.runBlocking {
                SyncMarketDataUseCase(feed)(snap(listedPpr), asOf)
            }
        assertEquals(listOf("VWCE.DE"), feed.historySymbols)
        assertEquals(1, result.marketData.size)
        assertEquals(listedPpr.id, result.marketData.single().assetId)
    }

    @Test
    fun commoditySpotDoesNotReplaceStoredSeries() {
        val feed =
            RecordingFeed(
                history = mapOf(gold.id to listOf(PriceBar(asOf, BigDecimal("2400")))),
            )
        val stored =
            (1..5).map { offset ->
                DailyMarketData(gold.id, asOf.minusDays(offset.toLong()), BigDecimal("2300"))
            }
        val result =
            kotlinx.coroutines.runBlocking {
                SyncMarketDataUseCase(feed)(snap(gold, market = stored), asOf)
            }
        assertEquals(1, result.marketData.size)
        assertEquals(asOf, result.marketData.single().date)
    }

    @Test
    fun overviewIsSkippedWhenRatingIsFresh() {
        val feed =
            RecordingFeed(
                history = mapOf(apple.id to listOf(PriceBar(asOf, BigDecimal("200")))),
            )
        val stored =
            listOf(
                DailyMarketData(apple.id, asOf.minusDays(2), BigDecimal("190"), AnalystRating.BUY),
            )
        kotlinx.coroutines.runBlocking {
            SyncMarketDataUseCase(feed)(snap(apple, market = stored), asOf)
        }
        assertTrue(feed.ratingSymbols.isEmpty())
    }

    @Test
    fun historicalAnalystRatingsAreKeptOnResync() {
        val yesterday = asOf.minusDays(1)
        val feed =
            RecordingFeed(
                history = mapOf(
                    apple.id to listOf(PriceBar(yesterday, BigDecimal("190")), PriceBar(asOf, BigDecimal("200"))),
                ),
            )
        val stored =
            listOf(
                DailyMarketData(apple.id, yesterday, BigDecimal("190"), AnalystRating.HOLD),
            )
        val result =
            kotlinx.coroutines.runBlocking {
                SyncMarketDataUseCase(feed)(snap(apple, market = stored), asOf)
            }
        assertEquals(AnalystRating.HOLD, result.marketData.single { it.date == yesterday }.analystRating)
        assertEquals(AnalystRating.HOLD, result.marketData.single { it.date == asOf }.analystRating)
        assertTrue(feed.ratingSymbols.isEmpty())
    }

    @Test
    fun freshAsOfBarSkipsHistoryAndRequestsOldestFirst() {
        val stale = Asset("msft", "MSFT", "Microsoft", AssetType.STOCK, Currency.USD)
        val feed =
            RecordingFeed(
                history = mapOf(
                    apple.id to listOf(PriceBar(asOf, BigDecimal("200"))),
                    stale.id to listOf(PriceBar(asOf, BigDecimal("400"))),
                ),
            )
        val stored =
            listOf(
                DailyMarketData(apple.id, asOf, BigDecimal("190"), AnalystRating.HOLD),
                DailyMarketData(stale.id, asOf.minusDays(10), BigDecimal("380"), AnalystRating.HOLD),
            )
        val snapshot =
            PortfolioSnapshot(
                assets = listOf(apple, stale),
                transactions = emptyList(),
                marketData = stored,
                fxRates = emptyList(),
                targets = emptyList(),
            )
        kotlinx.coroutines.runBlocking { SyncMarketDataUseCase(feed)(snapshot, asOf) }
        assertEquals(listOf("MSFT"), feed.historySymbols)
    }

    @Test
    fun freshBarStillPatchesNewRating() {
        val feed = RecordingFeed(history = mapOf(apple.id to listOf(PriceBar(asOf, BigDecimal("200")))))
        val stored = listOf(DailyMarketData(apple.id, asOf, BigDecimal("190"), AnalystRating.NONE))
        val result =
            kotlinx.coroutines.runBlocking {
                SyncMarketDataUseCase(feed)(snap(apple, market = stored), asOf)
            }
        assertTrue(feed.historySymbols.isEmpty())
        assertEquals(AnalystRating.HOLD, result.marketData.single().analystRating)
    }

    @Test
    fun defaultAsOfRunsOnEmptyBook() {
        val feed = RecordingFeed(history = emptyMap())
        val result =
            kotlinx.coroutines.runBlocking {
                SyncMarketDataUseCase(feed)(
                    PortfolioSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
                )
            }
        assertTrue(result.marketData.isEmpty())
    }

    private fun snap(asset: Asset, market: List<DailyMarketData> = emptyList()) = PortfolioSnapshot(
        assets = listOf(asset),
        transactions = emptyList(),
        marketData = market,
        fxRates = emptyList(),
        targets = emptyList(),
    )

    private class RecordingFeed(private val history: Map<String, List<PriceBar>>) : MarketFeed {
        val historySymbols = ArrayList<String>()
        val ratingSymbols = ArrayList<String>()

        override suspend fun eurPerUsd(): BigDecimal = BigDecimal("0.92")

        override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate): List<CurrencyRate> = listOf(CurrencyRate(to, BigDecimal("0.92")))

        override suspend fun dailyHistory(asset: Asset, asOf: LocalDate): List<PriceBar> {
            historySymbols += asset.feedSymbol
            return history[asset.id].orEmpty()
        }

        override suspend fun analystRating(asset: Asset): AnalystRating {
            ratingSymbols += asset.feedSymbol
            return AnalystRating.HOLD
        }
    }
}

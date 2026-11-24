package com.pirlruc.finsilo.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.data.local.FinsiloDatabase
import com.pirlruc.finsilo.data.remote.AlphaVantageCallBudget
import com.pirlruc.finsilo.data.remote.CompositeMarketFeed
import com.pirlruc.finsilo.data.remote.HttpGetClient
import com.pirlruc.finsilo.data.remote.OpenBbFundamentalsClient
import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.data.sync.WebDavBackupUpload
import com.pirlruc.finsilo.domain.backup.WebDavClient
import com.pirlruc.finsilo.domain.backup.WebDavExchange
import com.pirlruc.finsilo.domain.fundamentals.FundamentalView
import com.pirlruc.finsilo.domain.fundamentals.ValuationNote
import com.pirlruc.finsilo.domain.market.MarketFeed
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.CurrencyRate
import com.pirlruc.finsilo.domain.model.PriceBar
import com.pirlruc.finsilo.ui.theme.AppearanceStore
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class Wave6CoverageTest {
    @Test
    fun cacheLoaderBudgetOpenBbAndValuationCard() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val database = Room.inMemoryDatabaseBuilder(context, FinsiloDatabase::class.java).allowMainThreadQueries().build()
            val cache = FundamentalCache(database.portfolioDao())
            val day = LocalDate.of(2026, 11, 1)
            val stock = Asset("a", "AAPL", "Apple", AssetType.STOCK, Currency.USD)
            val etf = stock.copy(id = "e", assetType = AssetType.ETF)
            assertNull(cache.read("a", day))
            val feed = object : MarketFeed {
                override suspend fun eurPerUsd(): BigDecimal = BigDecimal.ONE

                override suspend fun eurPerUsdHistory(from: LocalDate, to: LocalDate): List<CurrencyRate> = emptyList()

                override suspend fun dailyHistory(asset: Asset, asOf: LocalDate): List<PriceBar> = emptyList()

                override suspend fun analystRating(asset: Asset): AnalystRating = error("unused")

                override suspend fun fundamentals(asset: Asset, price: BigDecimal?, asOf: LocalDate): FundamentalView = FundamentalView.ratios(
                    com.pirlruc.finsilo.domain.fundamentals.FundamentalsCalculator.metrics(
                        com.pirlruc.finsilo.domain.fundamentals.FundamentalsInputs(price = price, ttmEps = BigDecimal("5")),
                    ),
                    "Alpha Vantage",
                    asOf,
                )
            }
            val keys = DatabaseKeyStore(context.getSharedPreferences("wave6", 0))
            val loader = FundamentalLoader(cache, feed, OpenBbFundamentalsClient(HttpGetClient(), keys)) { day }
            val first = loader.load(stock, BigDecimal("100"))
            assertEquals(ValuationNote.RATIOS, first.note)
            assertEquals(ValuationNote.RATIOS, loader.load(stock, BigDecimal("100")).note)
            assertNull(cache.read(stock.id, day.plusDays(8)))
            assertEquals(ValuationNote.NOT_APPLICABLE, loader.load(etf, null).note)
            var today = day
            val budget = AlphaVantageCallBudget(limit = 1) { today }
            assertTrue(budget.take())
            assertFalse(budget.take())
            today = day.plusDays(1)
            assertTrue(budget.take())
            keys.setAlphaVantageKey("demo")
            val priced = CompositeMarketFeed(scriptedHttp(), keys, AlphaVantageCallBudget(limit = 1) { day })
            assertEquals(ValuationNote.NOT_APPLICABLE, priced.fundamentals(etf, null, day).note)
            assertEquals(ValuationNote.BUDGET, priced.fundamentals(stock, BigDecimal("10"), day).note)
            val statements = CompositeMarketFeed(scriptedHttp(), keys, AlphaVantageCallBudget(limit = 25) { day })
            val filed = statements.fundamentals(stock, BigDecimal("10"), day)
            assertTrue(filed.note == ValuationNote.RATIOS || filed.note == ValuationNote.MISSING)
            val missing = CompositeMarketFeed(scriptedHttp(), DatabaseKeyStore(context.getSharedPreferences("wave6-empty", 0)))
            assertEquals(ValuationNote.MISSING, missing.fundamentals(stock, null, day).note)
            keys.setOpenBb("https://openbb.example", "token")
            val openBb = OpenBbFundamentalsClient(scriptedHttp(), keys)
            assertTrue(openBb.enabled())
            assertEquals(ValuationNote.RATIOS, openBb.load("AAPL", BigDecimal("10"), day).note)
            keys.setOpenBb("https://openbb.example", " ")
            val blankToken = OpenBbFundamentalsClient(scriptedHttp(), keys)
            assertEquals(ValuationNote.RATIOS, blankToken.load("AAPL", null, day).note)
            val failing = HttpGetClient(OkHttpClient.Builder().addInterceptor { error("down") }.build())
            assertEquals(ValuationNote.MISSING, OpenBbFundamentalsClient(failing, keys).load("AAPL", null, day).note)
            keys.setOpenBb("   ", "token")
            assertEquals(ValuationNote.MISSING, OpenBbFundamentalsClient(scriptedHttp(), keys).load("AAPL", null, day).note)
            keys.setOpenBb("http://openbb.example", "")
            assertFalse(OpenBbFundamentalsClient(scriptedHttp(), keys).enabled())
            val upload = WebDavBackupUpload(
                keys,
                WebDavClient { method, _, _, _ ->
                    if (method == "GET") WebDavExchange(200, byteArrayOf(4), "e") else WebDavExchange(500, byteArrayOf(), null)
                },
            )
            assertNull(upload.download())
            keys.setWebDav("https://cloud.example/FSILO-LEDGER", "me", "app")
            assertEquals(4, upload.download()!!.single().toInt())
            val appearance = AppearanceStore(context)
            appearance.setDynamicColor(true)
            assertTrue(appearance.dynamicColor.value)
            database.close()
        }
    }

    private fun scriptedHttp(): HttpGetClient {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            val body = when {
                url.contains("OVERVIEW") -> """{"EPS":"2","MarketCapitalization":"40"}"""
                url.contains("metrics") -> """{"results":[{"pe_ratio":"8"}]}"""
                url.contains("ratios") -> """{"results":[{"interest_coverage":"4"}]}"""
                else -> "{}"
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("ok")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return HttpGetClient(client)
    }
}

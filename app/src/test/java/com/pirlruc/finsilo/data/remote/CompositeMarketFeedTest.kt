package com.pirlruc.finsilo.data.remote

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.pirlruc.finsilo.data.security.DatabaseKeyStore
import com.pirlruc.finsilo.domain.model.AnalystRating
import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CompositeMarketFeedTest {
    private val day = LocalDate.of(2026, 1, 15)

    @Test
    fun frankfurterSpotAndHistoryWinWhenThePayloadParses() = runBlocking {
        val feed = feed(mode = Mode.FRANKFURTER_OK, key = null)
        assertEquals(BigDecimal("0.92"), feed.eurPerUsd())
        val series = feed.eurPerUsdHistory(day, day.minusDays(2))
        assertTrue(series.isNotEmpty())
    }

    @Test
    fun fxFallsBackToAlphaVantageAndRejectsABadPayload() = runBlocking {
        val down = feed(mode = Mode.FX_DOWN, key = null)
        try {
            down.eurPerUsd()
            error("expected missing key")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("No FX"))
        }
        val viaAv = feed(mode = Mode.FX_DOWN, key = "synthetic-key")
        assertEquals(BigDecimal("0.90"), viaAv.eurPerUsd())
        val bad = feed(mode = Mode.FX_AV_BAD, key = "synthetic-key")
        try {
            bad.eurPerUsd()
            error("expected bad fx")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("quota") || error.message!!.contains("Alpha"))
        }
        val emptyHistory = feed(mode = Mode.FX_HISTORY_EMPTY, key = "synthetic-key").eurPerUsdHistory(day, day)
        assertEquals(1, emptyHistory.size)
    }

    @Test
    fun routesCryptoCommoditiesAndListings() = runBlocking {
        val ok = feed(mode = Mode.QUOTES_OK, key = "synthetic-key")
        assertTrue(ok.dailyHistory(asset("BTC", AssetType.CRYPTO), day).isNotEmpty())
        assertTrue(ok.probeHistory(asset("unknown-coin", AssetType.CRYPTO), day).isNotEmpty())
        assertTrue(ok.dailyHistory(asset("XAU", AssetType.COMMODITY), day).isNotEmpty())
        assertTrue(ok.probeHistory(asset("XAUUSD", AssetType.COMMODITY), day).isNotEmpty())
        assertTrue(ok.dailyHistory(asset("WTI", AssetType.COMMODITY), day).isNotEmpty())
        assertTrue(ok.dailyHistory(asset("VWCE.DE", AssetType.ETF), day).isNotEmpty())
        assertTrue(ok.probeHistory(asset("VWCE.DE", AssetType.ETF), day).size <= 100)
        assertTrue(ok.dailyHistory(asset("SYN", AssetType.STOCK), day).isNotEmpty())
        assertEquals(AnalystRating.BUY, ok.analystRating(asset("SYN", AssetType.STOCK)))
        assertEquals(AnalystRating.NONE, ok.analystRating(asset("EUR-CASH", AssetType.CASH)))
        assertEquals(AnalystRating.NONE, feed(mode = Mode.QUOTES_OK, key = null).analystRating(asset("SYN", AssetType.ETF)))
        assertEquals(AnalystRating.BUY, ok.analystRating(asset("PPR1", AssetType.PPR, quote = "PPR.DE")))
    }

    @Test
    fun commodityAndListingFailuresSurfaceAReason() = runBlocking {
        val noKey = feed(mode = Mode.QUOTES_FAIL, key = null)
        try {
            noKey.dailyHistory(asset("WTI", AssetType.COMMODITY), day)
            error("expected commodity failure")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.isNotBlank())
        }
        val keyed = feed(mode = Mode.QUOTES_FAIL, key = "synthetic-key")
        try {
            keyed.dailyHistory(asset("NOPE", AssetType.COMMODITY), day)
            error("expected unknown commodity")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.isNotBlank())
        }
        try {
            keyed.dailyHistory(asset("XAG", AssetType.COMMODITY), day)
            error("expected empty commodity")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.isNotBlank())
        }
        try {
            keyed.probeHistory(asset("MISSING.DE", AssetType.STOCK), day)
            error("expected listing failure")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.isNotBlank())
        }
        try {
            keyed.analystRating(asset("SYN", AssetType.STOCK))
            error("expected rating failure")
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("Alpha"))
        }
    }

    @Test
    fun xauSpotIsUsedWhenStooqIsEmpty() = runBlocking {
        val feed = feed(mode = Mode.XAU_SPOT, key = "synthetic-key")
        val bars = feed.dailyHistory(asset("GOLD", AssetType.COMMODITY), day)
        assertEquals(day, bars.single().date)
        val copper = feed.dailyHistory(asset("COPPER", AssetType.COMMODITY), day)
        assertTrue(copper.isNotEmpty())
    }

    private enum class Mode {
        FRANKFURTER_OK,
        FX_DOWN,
        FX_AV_BAD,
        FX_HISTORY_EMPTY,
        QUOTES_OK,
        QUOTES_FAIL,
        XAU_SPOT,
    }

    private fun feed(mode: Mode, key: String?): CompositeMarketFeed {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = context.getSharedPreferences("feed-${mode.name}-${key != null}", 0)
        val keys = DatabaseKeyStore(prefs)
        if (key != null) keys.setAlphaVantageKey(key)
        val client =
            OkHttpClient.Builder().addInterceptor { chain ->
                val url = chain.request().url.toString()
                val (code, body) = payload(mode, url)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("synthetic")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()
        return CompositeMarketFeed(HttpGetClient(client), keys)
    }

    private fun payload(mode: Mode, url: String): Pair<Int, String> {
        val frankfurter = url.contains("frankfurter")
        val av = url.contains("alphavantage")
        val gecko = url.contains("coingecko")
        val stooq = url.contains("stooq")
        return when (mode) {
            Mode.FRANKFURTER_OK ->
                if (url.contains("/latest")) {
                    200 to """{"rates":{"EUR":"0.92"}}"""
                } else {
                    200 to """{"rates":{"2026-01-13":{"EUR":"0.91"},"2026-01-15":{"EUR":"0.92"}}}"""
                }
            Mode.FX_DOWN -> if (frankfurter) 500 to "down" else 200 to fxRate("0.90")
            Mode.FX_AV_BAD -> if (frankfurter) 500 to "down" else 200 to """{"Note":"limit"}"""
            Mode.FX_HISTORY_EMPTY ->
                when {
                    frankfurter && url.contains("/latest") -> 200 to fxRate("0.90").let { """{"rates":{"EUR":"0.91"}}""" }
                    frankfurter -> 200 to """{"rates":{}}"""
                    else -> 200 to fxRate("0.91")
                }
            Mode.QUOTES_OK -> quoteOk(url, gecko, stooq, av)
            Mode.QUOTES_FAIL -> 200 to if (av) """{"Error Message":"bad"}""" else "Date,Open,High,Low,Close\n"
            Mode.XAU_SPOT ->
                when {
                    stooq -> 200 to "Date,Open,High,Low,Close\n"
                    av && url.contains("CURRENCY_EXCHANGE_RATE") -> 200 to fxRate("1900.5")
                    av && url.contains("COPPER") -> 200 to """{"data":[{"date":"2026-01-15","value":"4.2"}]}"""
                    else -> 500 to "down"
                }
        }
    }

    private fun quoteOk(url: String, gecko: Boolean, stooq: Boolean, av: Boolean): Pair<Int, String> = when {
        gecko -> 200 to """{"prices":[[1736899200000,100.5]]}"""
        stooq && url.contains("xauusd.pl") -> 200 to csv("11.0")
        stooq && url.contains("s=xauusd&") -> 200 to "Date,Open,High,Low,Close\n"
        stooq -> 200 to csv("12.0")
        av && url.contains("OVERVIEW") -> 200 to """{"AnalystRatingBuy":"4","AnalystRatingHold":"1"}"""
        av && url.contains("WTI") -> 200 to """{"data":[{"date":"2026-01-14","value":"70"}]}"""
        av -> 200 to """{"Time Series (Daily)":{"2026-01-14":{"4. close":"10.5"}}}"""
        else -> 500 to "down"
    }

    private fun fxRate(value: String) = """{"Realtime Currency Exchange Rate":{"5. Exchange Rate":"$value"}}"""

    private fun csv(close: String) = "Date,Open,High,Low,Close,Volume\n2026-01-14,1,1,1,$close,1\n"

    private fun asset(symbol: String, type: AssetType, quote: String? = null) = Asset("id-$symbol", symbol, "Synthetic $symbol", type, Currency.USD, quoteSymbol = quote)
}

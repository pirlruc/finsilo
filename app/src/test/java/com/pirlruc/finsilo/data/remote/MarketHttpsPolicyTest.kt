package com.pirlruc.finsilo.data.remote

import java.io.IOException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketHttpsPolicyTest {
    @Test
    fun allowsHttpsQuoteHosts() {
        MarketHttpsPolicy.requireHttpsUrl("https://api.frankfurter.app/latest?from=USD&to=EUR")
        MarketHttpsPolicy.requireHttpsUrl("https://www.alphavantage.co/query?function=OVERVIEW")
        MarketHttpsPolicy.requireHttpsUrl("https://api.coingecko.com/api/v3/coins/bitcoin/market_chart")
        MarketHttpsPolicy.requireHttpsUrl("https://stooq.com/q/d/l/?s=aapl.us&i=d")
    }

    @Test(expected = IOException::class)
    fun rejectsCleartext() {
        MarketHttpsPolicy.requireHttpsUrl("http://api.frankfurter.app/latest")
    }

    @Test(expected = IOException::class)
    fun rejectsUnknownHost() {
        MarketHttpsPolicy.requireHttpsUrl("https://evil.example/steal")
    }

    @Test(expected = IOException::class)
    fun rejectsUserinfo() {
        MarketHttpsPolicy.requireHttpsUrl("https://user:pass@api.frankfurter.app/latest")
    }

    @Test(expected = IOException::class)
    fun rejectsPathTraversalToken() {
        MarketHttpsPolicy.requireSafeToken("bitcoin/../evil", "coin id")
    }

    @Test(expected = IOException::class)
    fun rejectsDotDotTicker() {
        MarketHttpsPolicy.requireSafeToken("aapl..us", "ticker")
    }

    @Test
    fun acceptsNormalTokens() {
        assertEquals("bitcoin", MarketHttpsPolicy.requireSafeToken("bitcoin", "coin id"))
        assertEquals("vwce.de", MarketHttpsPolicy.requireSafeToken("vwce.de", "ticker"))
    }

    @Test
    fun structuredUrlsStayOnAllowlist() {
        val urls =
            listOf(
                MarketFeedUrls.frankfurterLatest(),
                MarketFeedUrls.frankfurterRange(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-31")),
                MarketFeedUrls.coinGeckoChart("bitcoin"),
                MarketFeedUrls.stooqDaily("vwce.de"),
                MarketFeedUrls.alphaVantage("function" to "OVERVIEW", "symbol" to "AAPL", "apikey" to "demo"),
            )
        urls.forEach { url ->
            val uri = MarketHttpsPolicy.requireHttpsUrl(url)
            assertTrue(uri.scheme.equals("https", ignoreCase = true))
        }
        assertTrue(
            MarketFeedUrls.frankfurterRange(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-31"))
                .contains("2024-01-01..2024-01-31"),
        )
    }
}

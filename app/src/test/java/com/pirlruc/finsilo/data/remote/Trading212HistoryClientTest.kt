package com.pirlruc.finsilo.data.remote

import com.pirlruc.finsilo.domain.model.Asset
import com.pirlruc.finsilo.domain.model.AssetType
import com.pirlruc.finsilo.domain.model.Currency
import com.pirlruc.finsilo.domain.model.DailyMarketData
import com.pirlruc.finsilo.domain.model.PortfolioSnapshot
import com.pirlruc.finsilo.domain.model.Transaction
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.ui.dashboard.PerformanceLabels
import java.math.BigDecimal
import java.net.URI
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Trading212HistoryClientTest {
    @Test
    fun requestsStayOnHistoryGets() = runBlocking {
        val calls = mutableListOf<Pair<String, String>>()
        val history = Trading212HistoryClient(
            transport(calls) { url ->
                if (url.endsWith("/orders")) {
                    Trading212HttpResult(200, """{"items":[],"nextPagePath":"/api/v0/equity/history/orders?cursor=2"}""")
                } else {
                    Trading212HttpResult(200, """{"items":[]}""")
                }
            },
            maxRequests = 5,
        )
        val pull = history.pull(KEY, SECRET)
        assertTrue(pull.lines != null)
        assertTrue(calls.all { it.first == "GET" })
        assertTrue(
            calls.none { call ->
                val path = URI(call.second).path
                path.contains("/orders") && !path.contains("/history/")
            },
        )
        assertTrue(calls.none { it.second.contains(KEY) || it.second.contains(SECRET) })
        assertTrue(calls.any { it.second.endsWith(Trading212HistoryClient.SUMMARY) })
        assertTrue(history.pull(KEY, SECRET).lines != null)
    }

    @Test
    fun unauthorizedAndRateLimitDoNotReturnRows() = runBlocking {
        val denied = mutableListOf<Pair<String, String>>()
        val unauthorized = client(denied) { Trading212HttpResult(401, "") }.pull(KEY, SECRET)
        assertNull(unauthorized.lines)
        assertTrue(unauthorized.error!!.contains("rejected"))
        assertEquals(1, denied.size)
        val limited = mutableListOf<Pair<String, String>>()
        var backedOff = 0
        val rate = Trading212HistoryClient(transport(limited) { Trading212HttpResult(429, "") }, backoff = { backedOff += 1 }).pull(KEY, SECRET)
        assertNull(rate.lines)
        assertTrue(rate.error!!.contains("rate limit"))
        assertEquals(1, limited.size)
        assertEquals(1, backedOff)
        val forbidden = client(mutableListOf()) { Trading212HttpResult(403, "") }.pull(KEY, SECRET)
        assertNull(forbidden.lines)
        assertTrue(forbidden.error!!.contains("scope"))
        val cursor = client(mutableListOf()) {
            Trading212HttpResult(200, """{"items":[],"nextPagePath":"/api/v0/equity/orders"}""")
        }.pull(KEY, SECRET)
        assertNull(cursor.lines)
        assertTrue(cursor.error!!.contains("cursor"))
    }

    @Test
    fun labelsCompareStoredHistoryAndRefuseAPromise() {
        val day = LocalDate.of(2024, 1, 1)
        val asset = Asset("vwce", "VWCE.DE", "VWCE", AssetType.ETF, Currency.EUR)
        val deposit = Transaction("d", "cash", day, TransactionType.DEPOSIT_CASH, BigDecimal.ONE, BigDecimal("100"), BigDecimal.ONE, BigDecimal("100"), BigDecimal.ZERO)
        val snapshot = PortfolioSnapshot(
            listOf(asset),
            listOf(deposit),
            listOf(DailyMarketData("vwce", day, BigDecimal("10")), DailyMarketData("vwce", day.plusDays(370), BigDecimal("11"))),
            emptyList(),
            emptyList(),
        )
        val labels = PerformanceLabels.of(snapshot, BigDecimal("110"), day.plusDays(370))
        assertTrue(labels.xirr.startsWith("XIRR"))
        assertTrue(labels.benchmark.contains("Comparison, not a promised return"))
        assertTrue(labels.taxNote.contains("Not tax advice"))
        assertTrue(labels.dividends.contains("No regular"))
    }

    private fun client(calls: MutableList<Pair<String, String>>, body: (String) -> Trading212HttpResult) = Trading212HistoryClient(transport(calls, body))

    private fun transport(calls: MutableList<Pair<String, String>>, body: (String) -> Trading212HttpResult) = Trading212Transport { method, url, _, _ ->
        calls += method to url
        check(trading212RequestAllowed(method, url))
        body(url)
    }

    private companion object {
        const val KEY = "key-value"
        const val SECRET = "secret-value"
    }
}

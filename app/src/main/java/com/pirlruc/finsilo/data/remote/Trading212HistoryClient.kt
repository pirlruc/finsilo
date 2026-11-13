package com.pirlruc.finsilo.data.remote

import com.pirlruc.finsilo.domain.importcsv.BrokerCsvLine
import com.pirlruc.finsilo.domain.importcsv.Trading212History
import java.io.IOException
import java.net.URI

data class Trading212HttpResult(val code: Int, val body: String)

/** GET-only transport. The URL must not contain the key or the secret. */
fun interface Trading212Transport {
    suspend fun get(method: String, url: String, key: String, secret: String): Trading212HttpResult
}

data class Trading212Pull(val lines: List<BrokerCsvLine>?, val error: String?)

/**
 * Read-only Trading 212 history. Order placement paths are refused.
 * HTTP 429 backs off once and stops. 401 and 403 do not return ledger rows.
 */
class Trading212HistoryClient(
    private val transport: Trading212Transport,
    private val backoff: suspend () -> Unit = {},
    private val maxRequests: Int = REQUESTS_PER_MINUTE,
) {
    suspend fun pull(key: String, secret: String): Trading212Pull {
        sent = 0
        cursor = null
        val orders = fetch(ORDERS, key, secret)
        val dividends = if (orders.error == null) fetch(DIVIDENDS, key, secret) else orders
        val cash = if (dividends.error == null) fetch(CASH, key, secret) else dividends
        val summary = if (cash.error == null) fetch(SUMMARY, key, secret) else cash
        if (summary.error != null) return Trading212Pull(null, summary.error)
        return Trading212Pull(Trading212History.lines(orders.bodies, dividends.bodies, cash.bodies), null)
    }

    private suspend fun fetch(start: String, key: String, secret: String): PageChain {
        val bodies = mutableListOf<String>()
        var path: String? = start
        var failure: String? = null
        while (path != null && failure == null) {
            failure = onePage(path, key, secret, bodies)
            path = if (failure == null) cursor else null
        }
        return if (failure == null) PageChain(bodies, null) else PageChain(emptyList(), failure)
    }

    private suspend fun onePage(path: String, key: String, secret: String, bodies: MutableList<String>): String? {
        if (sent >= maxRequests) return RATE_LIMIT
        sent += 1
        val response = read("https://$HOST$path", key, secret) ?: return HOST_FAILURE
        statusFailure(response.code)?.let { return it }
        return append(bodies, response.body)
    }

    private suspend fun read(url: String, key: String, secret: String): Trading212HttpResult? = try {
        transport.get("GET", url, key, secret)
    } catch (_: IOException) {
        null
    }

    private suspend fun statusFailure(code: Int): String? = when (code) {
        HTTP_OK -> null
        HTTP_UNAUTHORIZED -> "Trading 212 rejected the API key or secret (HTTP 401)."
        HTTP_FORBIDDEN -> "Trading 212 API key is missing a read-only scope (HTTP 403)."
        HTTP_TOO_MANY -> {
            backoff()
            "Trading 212 rate limit reached (HTTP 429). Sync stopped."
        }
        else -> "Trading 212 sync failed (HTTP $code)."
    }

    private fun append(bodies: MutableList<String>, body: String): String? {
        bodies += body
        val raw = Trading212History.nextPath(body)
        cursor = raw?.let(Trading212History::cursorPath)
        return if (raw != null && cursor == null) BAD_CURSOR else null
    }

    private var sent: Int = 0
    private var cursor: String? = null

    private class PageChain(val bodies: List<String>, val error: String?)

    companion object {
        const val HOST: String = "live.trading212.com"
        const val ORDERS: String = "/api/v0/equity/history/orders"
        const val DIVIDENDS: String = "/api/v0/equity/history/dividends"
        const val CASH: String = "/api/v0/equity/history/transactions"
        const val SUMMARY: String = "/api/v0/equity/account/summary"
        private const val REQUESTS_PER_MINUTE: Int = 20
        private const val HTTP_OK: Int = 200
        private const val HTTP_UNAUTHORIZED: Int = 401
        private const val HTTP_FORBIDDEN: Int = 403
        private const val HTTP_TOO_MANY: Int = 429
        private const val RATE_LIMIT: String = "Trading 212 allows 20 requests per minute. Sync stopped."
        private const val HOST_FAILURE: String = "Trading 212 sync failed for live.trading212.com."
        private const val BAD_CURSOR: String = "Trading 212 returned a cursor this app will not follow."
    }
}

/** Refuses any method other than GET, and any order path outside history. */
internal fun trading212RequestAllowed(method: String, url: String): Boolean {
    val parsed = runCatching { URI(url) }.getOrNull()
    val path = parsed?.path.orEmpty()
    val orders = path.contains("/orders") && !path.contains("/history/")
    val known = path.startsWith("/api/v0/equity/history/") || path == Trading212HistoryClient.SUMMARY
    return method == "GET" && parsed?.host == Trading212HistoryClient.HOST && !orders && known
}

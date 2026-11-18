package com.pirlruc.finsilo.domain.importcsv

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.util.Base64

data class BrokerExchange(val code: Int, val body: String)

/** One HTTP call. The client refuses a method or path outside its allow-list before this runs. */
fun interface BrokerCall {
    suspend fun call(method: String, url: String, headers: Map<String, String>, body: String): BrokerExchange
}

data class ReadOnlyPull(val lines: List<BrokerCsvLine>?, val error: String?)

/** IBKR Flex: request a statement, then download it. Both calls are GET. */
class IbkrFlexClient(private val broker: BrokerCall) {
    suspend fun pull(token: String, queryId: String): ReadOnlyPull {
        val send = flexUrl("SendRequest", token, queryId)
        if (!ReadOnlyBrokers.ibkrAllowed("GET", send)) return ReadOnlyPull(null, REFUSED)
        val requested = broker.call("GET", send, emptyMap(), "")
        if (requested.code != HTTP_OK) return ReadOnlyPull(null, "IBKR Flex request failed (HTTP ${requested.code}).")
        val reference = REFERENCE.find(requested.body)?.groupValues?.get(1)
            ?: return ReadOnlyPull(null, "IBKR Flex did not return a statement reference.")
        val statement = flexUrl("GetStatement", token, reference)
        if (!ReadOnlyBrokers.ibkrAllowed("GET", statement)) return ReadOnlyPull(null, REFUSED)
        val downloaded = broker.call("GET", statement, emptyMap(), "")
        if (downloaded.code != HTTP_OK) return ReadOnlyPull(null, "IBKR Flex download failed (HTTP ${downloaded.code}).")
        return ReadOnlyPull(ReadOnlyBrokers.ibkrTrades(downloaded.body), null)
    }

    private fun flexUrl(action: String, token: String, query: String): String {
        val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8)
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        return "https://gdcdyn.interactivebrokers.com/Universal/servlet/FlexStatementService.$action?t=$encodedToken&q=$encodedQuery&v=3"
    }

    private companion object {
        const val HTTP_OK: Int = 200
        const val REFUSED: String = "IBKR Flex request is not on the allow-list."
        val REFERENCE: Regex = Regex("<ReferenceCode>([^<]+)</ReferenceCode>")
    }
}

/** Kraken private Ledgers and TradesHistory. AddOrder is not called. */
class KrakenReadClient(private val broker: BrokerCall) {
    suspend fun pull(apiKey: String, secretBase64: String, nonce: String): ReadOnlyPull {
        val secret = runCatching { Base64.getDecoder().decode(secretBase64) }.getOrNull()
            ?: return ReadOnlyPull(null, "Kraken secret is not valid base64.")
        val ledgers = post("/0/private/Ledgers", apiKey, secret, nonce)
        if (ledgers.error != null) return ledgers
        val trades = post("/0/private/TradesHistory", apiKey, secret, nonce)
        if (trades.error != null) return trades
        return ReadOnlyPull(ledgers.lines.orEmpty() + trades.lines.orEmpty(), null)
    }

    private suspend fun post(path: String, apiKey: String, secret: ByteArray, nonce: String): ReadOnlyPull {
        val url = "https://api.kraken.com$path"
        if (!ReadOnlyBrokers.krakenAllowed("POST", url)) return ReadOnlyPull(null, "Kraken request is not on the allow-list.")
        val body = "nonce=$nonce"
        val headers = mapOf(
            "API-Key" to apiKey,
            "API-Sign" to ReadOnlyBrokers.krakenSignature(path, nonce, body, secret),
        )
        val response = broker.call("POST", url, headers, body)
        if (response.code != HTTP_OK) return ReadOnlyPull(null, "Kraken sync failed (HTTP ${response.code}).")
        val lines = if (path.endsWith("Ledgers")) ReadOnlyBrokers.krakenLedgers(response.body) else emptyList()
        return ReadOnlyPull(lines, null)
    }

    private companion object {
        const val HTTP_OK: Int = 200
    }
}

/** Coinbase Advanced Trade fills. The JWT is ES256 and the method is GET. */
class CoinbaseFillsClient(private val broker: BrokerCall) {
    suspend fun pull(keyName: String, privateKey: PrivateKey, nowSeconds: Long, nonce: String): ReadOnlyPull {
        val url = "https://api.coinbase.com/api/v3/brokerage/orders/historical/fills"
        if (!ReadOnlyBrokers.coinbaseAllowed("GET", url)) return ReadOnlyPull(null, "Coinbase request is not on the allow-list.")
        val token = ReadOnlyBrokers.coinbaseJwt(keyName, privateKey, nowSeconds, nonce)
        val response = broker.call("GET", url, mapOf("Authorization" to "Bearer $token"), "")
        if (response.code != HTTP_OK) return ReadOnlyPull(null, "Coinbase sync failed (HTTP ${response.code}).")
        return ReadOnlyPull(ReadOnlyBrokers.coinbaseFills(response.body), null)
    }

    private companion object {
        const val HTTP_OK: Int = 200
    }
}

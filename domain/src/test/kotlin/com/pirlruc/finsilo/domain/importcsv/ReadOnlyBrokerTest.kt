package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.backup.WebDavClient
import com.pirlruc.finsilo.domain.backup.WebDavExchange
import com.pirlruc.finsilo.domain.backup.WebDavSync
import com.pirlruc.finsilo.domain.model.BrokerSource
import com.pirlruc.finsilo.domain.model.TransactionType
import com.pirlruc.finsilo.domain.performance.FirePlanner
import java.math.BigDecimal
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadOnlyBrokerTest {
    @Test
    fun ibkrFlexTradeBecomesABuy() {
        val xml = """<FlexStatement><Trade symbol="AAPL" buySell="BUY" quantity="2" tradeDate="20240115" tradeID="abc"/></FlexStatement>"""
        val line = ReadOnlyBrokers.ibkrTrades(xml).single()
        assertEquals(TransactionType.BUY, line.type)
        assertEquals("AAPL", line.symbol)
        assertEquals("abc", line.brokerReference)
        assertTrue(ReadOnlyBrokers.ibkrAllowed("GET", "https://gdcdyn.interactivebrokers.com/Universal/servlet/FlexStatementService.GetStatement"))
        assertFalse(ReadOnlyBrokers.ibkrAllowed("POST", "https://gdcdyn.interactivebrokers.com/Universal/servlet/FlexStatementService.GetStatement"))
    }

    @Test
    fun krakenLedgerAndSignatureStayOnTheReadOnlyPaths() {
        val body = """{"result":{"ledger":{"L1":{"time":"1700000000","type":"trade","asset":"XXBT","amount":"-0.1"}}}}"""
        val line = ReadOnlyBrokers.krakenLedgers(body).single()
        assertEquals(TransactionType.SELL, line.type)
        assertEquals("XXBT", line.symbol)
        val secret = Base64.getDecoder().decode("dGVzdC1zZWNyZXQ=")
        val first = ReadOnlyBrokers.krakenSignature("/0/private/Ledgers", "1", "nonce=1", secret)
        val second = ReadOnlyBrokers.krakenSignature("/0/private/Ledgers", "1", "nonce=1", secret)
        assertEquals(first, second)
        assertTrue(ReadOnlyBrokers.krakenAllowed("POST", "https://api.kraken.com/0/private/TradesHistory"))
        assertFalse(ReadOnlyBrokers.krakenAllowed("POST", "https://api.kraken.com/0/private/AddOrder"))
    }

    @Test
    fun coinbaseFillJwtVerifies() {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val token = ReadOnlyBrokers.coinbaseJwt("organizations/demo/apiKeys/1", keys.private, 1_700_000_000L, "n")
        val parts = token.split('.')
        assertEquals(3, parts.size)
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(keys.public)
        signature.update((parts[0] + "." + parts[1]).toByteArray())
        assertTrue(signature.verify(p1363ToDer(Base64.getUrlDecoder().decode(parts[2]))))
        val body = """{"fills":[{"entry_id":"e","product_id":"BTC-EUR","side":"BUY","size":"0.2","trade_time":"2024-01-15T00:00:00Z"}]}"""
        assertEquals("BTC-EUR", ReadOnlyBrokers.coinbaseFills(body).single().symbol)
        assertTrue(ReadOnlyBrokers.coinbaseAllowed("GET", "https://api.coinbase.com/api/v3/brokerage/orders/historical/fills"))
    }

    @Test
    fun trading212HistoryAcceptsAFeeThatIsNotAnArray() {
        val page = """
            {"items":[
              {"order":{"status":"FILLED","ticker":"VWCE"},"fill":{"filledAt":"2024-01-01T00:00:00.000Z","type":"BUY","id":"1","walletImpact":{"fees":"no"}}},
              {"order":{"status":"FILLED","ticker":"VWCE"},"fill":{"filledAt":"2024-01-02T00:00:00.000Z","type":"BUY","id":"2","walletImpact":{"fees":[1,{"quantity":"0.10"}]}}}
            ]}
        """.trimIndent()
        assertEquals(2, Trading212History.lines(listOf(page), emptyList(), emptyList()).size)
    }

    @Test
    fun ibkrClientDownloadsTheStatementItRequested() = kotlinx.coroutines.runBlocking {
        val calls = mutableListOf<String>()
        val client = IbkrFlexClient { _, url, _, _ ->
            calls += url
            if (url.contains("SendRequest")) {
                BrokerExchange(200, "<ReferenceCode>ref-1</ReferenceCode>")
            } else {
                BrokerExchange(200, """<Trade symbol="AAPL" buySell="BUY" quantity="1" tradeDate="20240115" tradeID="t1"/>""")
            }
        }
        val pull = client.pull("token", "query")
        assertEquals("AAPL", pull.lines?.single()?.symbol)
        assertEquals(2, calls.size)
        assertTrue(calls[0].contains("SendRequest"))
        assertTrue(calls[1].contains("q=ref-1"))
    }

    @Test
    fun webDavUploadStopsWhenTheEtagDiffers() = kotlinx.coroutines.runBlocking {
        var puts = 0
        val client = WebDavClient { method, _, _, _ ->
            if (method == "PUT") puts += 1
            WebDavExchange(207, "<getetag>remote</getetag>".toByteArray(), "remote")
        }
        val result = client.upload("https://cloud.example/FSILO-LEDGER", "me", "app", byteArrayOf(1), "local")
        assertEquals("WebDAV has a newer backup. Download it before uploading.", result.error)
        assertEquals(0, puts)
    }

    @Test
    fun webDavRejectsAPasswordInTheUrlAndDetectsAnEtagConflict() {
        assertTrue(WebDavSync.allowed("PUT", "https://cloud.example/remote.php/dav/files/me/FSILO-LEDGER"))
        assertFalse(WebDavSync.allowed("PUT", "https://user:secret@cloud.example/FSILO-LEDGER"))
        assertFalse(WebDavSync.allowed("DELETE", "https://cloud.example/FSILO-LEDGER"))
        assertTrue(WebDavSync.conflict("a", "b"))
        assertFalse(WebDavSync.conflict("a", "a"))
    }

    @Test
    fun ibkrClientReportsAFailedRequestAndAFailedDownload() = kotlinx.coroutines.runBlocking {
        val refused = IbkrFlexClient { _, _, _, _ -> BrokerExchange(503, "") }.pull("t", "q")
        assertEquals("IBKR Flex request failed (HTTP 503).", refused.error)
        val missing = IbkrFlexClient { _, _, _, _ -> BrokerExchange(200, "<Status>Fail</Status>") }.pull("t", "q")
        assertEquals("IBKR Flex did not return a statement reference.", missing.error)
        val download = IbkrFlexClient { _, url, _, _ ->
            if (url.contains("SendRequest")) BrokerExchange(200, "<ReferenceCode>ref</ReferenceCode>") else BrokerExchange(500, "")
        }.pull("t", "q")
        assertEquals("IBKR Flex download failed (HTTP 500).", download.error)
    }

    @Test
    fun krakenClientRejectsABadSecretAndAFailedPost() = kotlinx.coroutines.runBlocking {
        val bad = KrakenReadClient { _, _, _, _ -> BrokerExchange(200, "{}") }.pull("key", "%%%", "1")
        assertEquals("Kraken secret is not valid base64.", bad.error)
        val failed = KrakenReadClient { _, _, _, _ -> BrokerExchange(401, "") }.pull("key", "dGVzdA==", "1")
        assertEquals("Kraken sync failed (HTTP 401).", failed.error)
        val body = """{"result":{"L1":{"time":"1700000000.5","type":"trade","asset":"EUR","amount":"1"}}}"""
        val ok = KrakenReadClient { _, url, _, _ ->
            if (url.endsWith("Ledgers")) BrokerExchange(200, body) else BrokerExchange(200, "{}")
        }.pull("key", "dGVzdA==", "1")
        assertEquals(TransactionType.BUY, ok.lines?.single()?.type)
        var calls = 0
        val second = KrakenReadClient { _, _, _, _ ->
            calls += 1
            if (calls == 1) BrokerExchange(200, body) else BrokerExchange(500, "")
        }.pull("key", "dGVzdA==", "1")
        assertEquals("Kraken sync failed (HTTP 500).", second.error)
    }

    @Test
    fun coinbaseClientReportsAnHttpFailure() = kotlinx.coroutines.runBlocking {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val failed = CoinbaseFillsClient { _, _, _, _ -> BrokerExchange(403, "") }.pull("name", keys.private, 1L, "n")
        assertEquals("Coinbase sync failed (HTTP 403).", failed.error)
        val sparse = """{"fills":[{"side":"SELL"},{"entry_id":"e","product_id":"ETH-EUR","side":"BUY","size":"nope"}]}"""
        val ok = CoinbaseFillsClient { _, _, _, _ -> BrokerExchange(200, sparse) }.pull("name", keys.private, 1L, "n")
        assertEquals(2, ok.lines?.size)
        assertEquals(emptyList<String>(), ReadOnlyBrokers.coinbaseFills("nope").map { it.symbol })
    }

    @Test
    fun parsersDropUnsafeAndIncompleteRows() {
        assertTrue(ReadOnlyBrokers.ibkrTrades("<!DOCTYPE html>").isEmpty())
        assertTrue(ReadOnlyBrokers.ibkrTrades("<Trade quantity=\"1\"").isEmpty())
        assertEquals("", ReadOnlyBrokers.ibkrTrades("<Trade quantity=\"1\" tradeDate=\"2024\">").single().brokerReference)
        assertTrue(ReadOnlyBrokers.krakenLedgers("nope").isEmpty())
        assertFalse(ReadOnlyBrokers.ibkrAllowed("GET", "http://gdcdyn.interactivebrokers.com/Universal/servlet/FlexStatementService.GetStatement"))
        assertFalse(ReadOnlyBrokers.krakenAllowed("POST", "https://evil.example/0/private/Ledgers"))
        assertFalse(ReadOnlyBrokers.coinbaseAllowed("GET", "https://user:pw@api.coinbase.com/api/v3/brokerage/orders/historical/fills"))
        assertFalse(WebDavSync.allowed("GET", "http://cloud.example/FSILO-LEDGER"))
        assertFalse(WebDavSync.allowed("GET", "https://cloud.example/other"))
        assertFalse(WebDavSync.conflict(null, "a"))
        assertFalse(WebDavSync.conflict(" ", "a"))
        assertFalse(WebDavSync.conflict("a", null))
        assertFalse(WebDavSync.conflict("a", " "))
        assertFalse(ReadOnlyBrokers.ibkrAllowed("GET", "not a url"))
        assertFalse(ReadOnlyBrokers.krakenAllowed("POST", "https://api.kraken.com/0/public/Ticker"))
        assertFalse(ReadOnlyBrokers.coinbaseAllowed("POST", "https://api.coinbase.com/api/v3/brokerage/orders/historical/fills"))
        assertFalse(WebDavSync.allowed("GET", "https:///FSILO-LEDGER"))
        assertFalse(WebDavSync.allowed("GET", "::::"))
        val dated = """{"fills":[{"entry_id":"e","side":"SELL","trade_time":"2024-02-02T01:00:00Z","size":"-1","product_id":"ETH-EUR"}]}"""
        val sell = ReadOnlyBrokers.coinbaseFills(dated).single()
        assertEquals(TransactionType.SELL, sell.type)
        assertEquals("ETH-EUR", sell.symbol)
        val brokenDate = """{"fills":[{"trade_time":"not-a-date","size":null}]}"""
        assertEquals(null, ReadOnlyBrokers.coinbaseFills(brokenDate).single().date)
        assertTrue(ReadOnlyBrokers.coinbaseFills("""{"fills":"no"}""").isEmpty())
        val buy = """{"result":{"ledger":{"L2":{"time":"nope","asset":"EUR","amount":"2"}}}}"""
        assertEquals(TransactionType.BUY, ReadOnlyBrokers.krakenLedgers(buy).single().type)
        assertEquals("", ReadOnlyBrokers.ibkrTrades("""<Trade symbol="ABC>""").single().symbol)
        val missing = """{"result":{"ledger":{"L":{"amount":"abc"}}}}"""
        assertEquals(TransactionType.BUY, ReadOnlyBrokers.krakenLedgers(missing).single().type)
        val bare = """{"result":{"L":{"amount":"-3","time":"40000000000000000"}}}"""
        assertEquals(null, ReadOnlyBrokers.krakenLedgers(bare).single().date)
        assertTrue(ReadOnlyBrokers.krakenLedgers("{}").isEmpty())
        assertTrue(ReadOnlyBrokers.coinbaseFills("[]").isEmpty())
        val odd = """{"fills":[1,{"side":{"nested":true},"size":"","product_id":"SOL-EUR"}]}"""
        assertEquals("SOL-EUR", ReadOnlyBrokers.coinbaseFills(odd)[1].symbol)
        assertEquals(BrokerSource.IBKR, BrokerCsvFormat.IBKR_FLEX.source)
        assertEquals(BrokerSource.KRAKEN, BrokerCsvFormat.KRAKEN.source)
        assertEquals(BrokerSource.COINBASE, BrokerCsvFormat.COINBASE.source)
        val empty = CsvTable(emptyList(), emptyList())
        assertTrue(BrokerCsv.parseLines(BrokerCsvFormat.IBKR_FLEX, empty).isEmpty())
        assertTrue(BrokerCsv.parseLines(BrokerCsvFormat.KRAKEN, empty).isEmpty())
        assertTrue(BrokerCsv.parseLines(BrokerCsvFormat.COINBASE, empty).isEmpty())
        assertEquals(null, FirePlanner.years(BigDecimal("10"), BigDecimal("1000"), BigDecimal.ZERO, BigDecimal("-1")))
        assertEquals(null, FirePlanner.years(BigDecimal.ZERO, BigDecimal("1000"), BigDecimal.ZERO, BigDecimal("0.05")))
        assertEquals(null, FirePlanner.years(BigDecimal("100"), BigDecimal("1000"), BigDecimal("3000"), BigDecimal("-2")))
        val badDay = ReadOnlyBrokers.ibkrTrades("""<Trade symbol="A" buySell="SELL" quantity="x" tradeDate="20241399" tradeID="z"/>""")
        assertEquals(TransactionType.SELL, badDay.single().type)
        assertEquals(null, badDay.single().date)
    }

    @Test
    fun webDavUploadWritesWhenTheCopyStillMatches() = kotlinx.coroutines.runBlocking {
        val seen = mutableListOf<Int>()
        val client = WebDavClient { method, _, headers, _ ->
            if (method == "PROPFIND") {
                WebDavExchange(200, "<D:getetag>same</D:getetag>".toByteArray(), null)
            } else {
                seen += headers.size
                WebDavExchange(if (seen.size == 1) 201 else 204, byteArrayOf(), if (seen.size == 1) "\"same\"" else null)
            }
        }
        val first = client.upload("https://cloud.example/FSILO-LEDGER", "me", "app", byteArrayOf(1), null)
        assertEquals("\"same\"", first.etag)
        val second = client.upload("https://cloud.example/FSILO-LEDGER", "me", "app", byteArrayOf(1), "same")
        assertEquals("same", second.etag)
        val refused = client.upload("http://cloud.example/FSILO-LEDGER", "me", "app", byteArrayOf(1), null)
        assertEquals("WebDAV URL is not an https FSILO-LEDGER path.", refused.error)
        val lookup = WebDavClient { _, _, _, _ -> WebDavExchange(404, byteArrayOf(), null) }
        assertTrue(lookup.upload("https://cloud.example/FSILO-LEDGER", "me", "app", byteArrayOf(1), null).error!!.contains("404"))
        val rejected = WebDavClient { method, _, _, _ ->
            if (method == "PROPFIND") WebDavExchange(207, byteArrayOf(), null) else WebDavExchange(409, byteArrayOf(), null)
        }
        assertTrue(rejected.upload("https://cloud.example/FSILO-LEDGER", "me", "app", byteArrayOf(1), "").error!!.contains("409"))
        val ok = WebDavClient { method, _, _, _ ->
            if (method == "PROPFIND") WebDavExchange(207, byteArrayOf(), "tag") else WebDavExchange(200, byteArrayOf(), null)
        }.upload("https://cloud.example/FSILO-LEDGER", "me", "app", byteArrayOf(1), "tag")
        assertEquals("tag", ok.etag)
    }

    private fun p1363ToDer(raw: ByteArray): ByteArray {
        val r = integer(raw.copyOfRange(0, 32))
        val s = integer(raw.copyOfRange(32, 64))
        val body = r + s
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    private fun integer(value: ByteArray): ByteArray {
        val trimmed = value.toList().dropWhile { it == 0.toByte() }.ifEmpty { listOf(0.toByte()) }
        val body = if (trimmed.first() < 0) listOf(0.toByte()) + trimmed else trimmed
        val out = ByteArray(body.size + 2)
        out[0] = 0x02
        out[1] = body.size.toByte()
        body.forEachIndexed { index, byte -> out[index + 2] = byte }
        return out
    }
}

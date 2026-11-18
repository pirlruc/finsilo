package com.pirlruc.finsilo.domain.importcsv

import com.pirlruc.finsilo.domain.backup.WebDavClient
import com.pirlruc.finsilo.domain.backup.WebDavExchange
import com.pirlruc.finsilo.domain.backup.WebDavSync
import com.pirlruc.finsilo.domain.model.TransactionType
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
